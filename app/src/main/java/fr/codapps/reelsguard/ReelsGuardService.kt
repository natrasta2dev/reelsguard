package fr.codapps.reelsguard

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Surcouche au-dessus d'Instagram, YouTube et TikTok (aucune de ces apps n'est modifiée) :
 *  - compte le temps passé sur les vidéos courtes (Reels, Shorts, fil TikTok) ;
 *  - bloque quand : hors plage horaire, limite commune ou quota de l'app atteint, pause forcée ;
 *    le bouton est alors recouvert d'un cache de la couleur exacte de la barre, et on sort
 *    des vidéos courtes (« retour », ou onglet Profil sur TikTok) ;
 *  - affiche un écran « Respire » quelques secondes avant chaque ouverture ;
 *  - notification discrète du temps restant pendant le visionnage, widget mis à jour ;
 *  - mode strict : empêche d'ouvrir les pages de ReelsGuard dans les Paramètres.
 */
class ReelsGuardService : AccessibilityService() {

    private lateinit var store: UsageStore
    private lateinit var schedule: Schedule
    private lateinit var notifier: Notifier
    private lateinit var family: FamilyConfig
    private lateinit var windowManager: WindowManager
    private lateinit var powerManager: PowerManager
    private val handler = Handler(Looper.getMainLooper())

    private var currentRule: AppRule? = null
    private var lastTick = 0L
    private var lastKickOut = 0L
    private var warnedFiveMin = false
    private var lastWidgetUpdate = 0L

    // Détection stabilisée : il faut 2 s de suite pour entrer, 3 s pour sortir (évite les faux positifs)
    private var onTicks = 0
    private var offTicks = 0
    private var stableWatching = false

    // Versions web : dernière adresse lue par navigateur (la barre se masque pendant le défilement)
    private val lastUrl = HashMap<String, String>()
    private var webKicks = 0

    // Session de visionnage (pause forcée + délai)
    private var lastWatchingAt = 0L
    private var lastBreatheAt = 0L
    private var continuousMs = 0L
    private var pauseUntil = 0L // heure murale (ms)

    // Écran « Respire »
    private var breatheView: View? = null
    private var breatheCount: TextView? = null
    private var breatheEndsAt = 0L

    // Cache sur le bouton
    private var tabCover: View? = null
    private val coverBounds = Rect()
    private var wantedBounds: Rect? = null
    private var screenshotInFlight = false
    private val colorCache = HashMap<String, Int>()

    private val screenHeight get() = resources.displayMetrics.heightPixels

    // ---------- cycle de vie ----------

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = UsageStore(this)
        schedule = Schedule(this)
        notifier = Notifier(this)
        family = FamilyConfig(this)
        if (Edition.isFamily) notifier.showParental()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        lastTick = SystemClock.elapsedRealtime()
        handler.post(ticker)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        idle()
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    // Tout se fait dans la boucle d'une seconde.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    /** Racine de l'app réellement au premier plan (ignore clavier, barre système, nos surcouches). */
    private fun foregroundRoot(): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { r ->
            val pkg = r.packageName?.toString()
            if (pkg != null && pkg !in IGNORED_PACKAGES && pkg != packageName) return r
        }
        return windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedByDescending { it.isFocused || it.isActive }
            .firstNotNullOfOrNull { w ->
                w.root?.takeIf { r ->
                    val p = r.packageName?.toString().orEmpty()
                    Edition.isFamily || AppRules.forPackage(p) != null || WebRules.isBrowser(p)
                }
            }
    }

    // ---------- boucle principale (1 fois par seconde) ----------

    private val ticker = object : Runnable {
        override fun run() {
            try {
                tick()
            } catch (_: Exception) {
                // Une app peut changer son interface : on ne plante jamais le service pour ça.
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val elapsed = (now - lastTick).coerceAtMost(TICK_MS * 3) // plafonné si le téléphone a dormi
        lastTick = now

        if (now - lastWidgetUpdate > 60_000L) {
            lastWidgetUpdate = now
            RemainingWidget.update(this, force = false)
            UpdateChecker.check(this) // limité en interne à une fois toutes les 6 h
            if (Edition.isFamily) notifier.showParental() // reste visible en permanence
        }

        val root = if (powerManager.isInteractive) foregroundRoot() else null

        // Mode strict : pas d'accès aux pages de ReelsGuard dans les Paramètres.
        if (root != null && schedule.isLocked() && guardSettings(root, now)) return

        // Édition Famille : navigation privée, couvre-feu / temps scolaire, apps entières limitées.
        if (Edition.isFamily && root != null && ParentLock.isSetUp(this) && !ParentLock.sessionActive(this)) {
            if (familyTick(root, now, elapsed)) return
        }

        val pkg = root?.packageName?.toString()
        var rule = pkg?.let { AppRules.forPackage(it) }

        // Version web dans un navigateur : l'adresse dit de quel site et de quelle page il s'agit.
        var web = false
        var webWatching = false
        if (root != null && pkg != null && rule == null && WebRules.isBrowser(pkg)) {
            WebRules.readUrl(root, pkg)?.let { lastUrl[pkg] = it }
            lastUrl[pkg]?.let { WebRules.match(it) }?.let { m ->
                rule = m.rule; web = true; webWatching = m.watching
            }
        }

        if (rule != currentRule) {
            removeCover()
            onTicks = 0; offTicks = 0; stableWatching = false
        }
        currentRule = rule

        if (root == null || rule == null) {
            idle()
            return
        }
        val r = rule!!

        val raw = if (web) webWatching else isWatching(r, root)
        if (raw) { onTicks++; offTicks = 0 } else { offTicks++; onTicks = 0 }
        if (!stableWatching && onTicks >= 2) stableWatching = true
        if (stableWatching && offTicks >= 3) stableWatching = false
        val watching = if (web || r.wholeApp) raw else stableWatching
        if (!watching) webKicks = 0
        if (!web) reportDiagnostics(r, root, raw, now)

        // App désélectionnée ou jour non actif : aucune restriction, aucun comptage.
        if (!schedule.isAppEnabled(r) || !schedule.isActiveToday()) {
            idle()
            return
        }

        // Session : une coupure de plus de 2 min remet le compteur de visionnage continu à zéro.
        val gap = now - lastWatchingAt
        if (watching) {
            if (gap > ENTRY_GAP_MS && now - lastBreatheAt > BREATHE_COOLDOWN_MS &&
                blockReason(r) == null && schedule.delayEnabled && breatheView == null) {
                startBreathe(now)
            }
            lastWatchingAt = now
        } else {
            stopBreathe(resume = false)
            if (gap > SESSION_GAP_MS) continuousMs = 0
        }

        // Écran « Respire » : compte à rebours, pas de comptage pendant ce temps.
        breatheView?.let {
            val left = breatheEndsAt - now
            if (left <= 0) stopBreathe(resume = true)
            else breatheCount?.text = "${(left + 999) / 1000}"
        }
        val breathing = breatheView != null

        // Comptage
        if (watching && !breathing && blockReason(r) == null) {
            store.addUsage(r, elapsed)
            maybeWarn(r)
            if (schedule.pauseEnabled) {
                continuousMs += elapsed
                if (continuousMs >= schedule.pauseAfterMin * 60_000L) {
                    continuousMs = 0
                    pauseUntil = System.currentTimeMillis() + schedule.pauseLengthMin * 60_000L
                }
            }
        }

        // Blocage
        val reason = blockReason(r)
        if (reason != null) {
            stopBreathe(resume = false)
            notifier.cancel()
            updateCover(if (web) null else findTab(r, root), root.packageName.toString())
            if (watching && now - lastKickOut > KICK_COOLDOWN_MS) {
                lastKickOut = now
                kickOut(r, root, reason, web)
            }
        } else {
            removeCover()
            if (watching && !breathing) notifier.show("${store.remainingFor(r) / 60_000 + 1} min restantes")
            else notifier.cancel()
        }
    }

    /** Raison du blocage pour cette app, ou null si les vidéos courtes sont autorisées. */
    private fun blockReason(rule: AppRule): String? = when {
        !schedule.isInWindow() -> "Hors plage horaire"
        store.isLimitReached -> "Limite atteinte"
        store.remainingFor(rule) <= 0 -> "Quota de l'app atteint"
        System.currentTimeMillis() < pauseUntil ->
            "Pause : encore ${(pauseUntil - System.currentTimeMillis()) / 60_000 + 1} min"
        else -> null
    }

    private fun idle() {
        hideKidBlockIfStale()
        removeCover()
        stopBreathe(resume = false)
        if (::notifier.isInitialized) notifier.cancel()
    }

    private fun kickOut(rule: AppRule, root: AccessibilityNodeInfo, msg: String, web: Boolean = false) {
        if (web) {
            // Navigateur : page précédente ; si on y est toujours, retour à l'écran d'accueil.
            webKicks++
            performGlobalAction(if (webKicks >= 3) GLOBAL_ACTION_HOME else GLOBAL_ACTION_BACK)
            toastOrKid(msg)
            return
        }
        if (rule.wholeApp) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            toastOrKid(msg)
            return
        }
        if (rule.redirectTabLabels.isNotEmpty()) {
            val target = findBottomNode(root, rule.redirectTabLabels, rule.navTopToo)
            if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                toastOrKid(msg)
                return
            }
        }
        performGlobalAction(GLOBAL_ACTION_BACK)
        toastOrKid(msg)
    }

    private fun toastOrKid(msg: String) {
        if (Edition.isFamily) showKidBlock(msg, "Demande à un parent si tu as besoin de plus de temps.", canRequest = true)
        else toast(msg)
    }

    // ---------- édition Famille ----------

    private var browserCache: Set<String> = emptySet()
    private var browserCacheAt = 0L
    private var privKicks = 0

    private fun browsers(now: Long): Set<String> {
        if (now - browserCacheAt > 10 * 60_000L || browserCache.isEmpty()) {
            browserCache = runCatching { FamilyConfig.browserPackages(this) }.getOrDefault(WebRules.BROWSERS.keys)
            browserCacheAt = now
        }
        return browserCache
    }

    /** Retourne true si l'app au premier plan a été traitée (bloquée ou comptée) ici. */
    private fun familyTick(root: AccessibilityNodeInfo, now: Long, elapsed: Long): Boolean {
        val pkg = root.packageName?.toString() ?: return false
        if (pkg == packageName || pkg in IGNORED_PACKAGES) return false

        // 1. Navigation privée : désactivée dans tous les navigateurs
        if (pkg in browsers(now)) {
            if (isPrivateBrowsing(root)) {
                idle()
                if (now - lastKickOut > KICK_COOLDOWN_MS) {
                    lastKickOut = now
                    privKicks++
                    performGlobalAction(if (privKicks >= 3) GLOBAL_ACTION_HOME else GLOBAL_ACTION_BACK)
                    store.logEvent(UsageStore.EV_PRIVATE)
                    toast("La navigation privée est désactivée")
                }
                return true
            }
            privKicks = 0
        }

        // 2. Couvre-feu / temps scolaire : seules les apps autorisées restent utilisables
        family.activeRestriction()?.let { name ->
            if (pkg !in family.allowedNow()) {
                idle()
                if (now - lastKickOut > KICK_COOLDOWN_MS) {
                    lastKickOut = now
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    store.logEvent(UsageStore.EV_RESTRICTED)
                    showKidBlock(name, "Cette app n'est pas disponible pour le moment.", canRequest = false)
                }
                return true
            }
        }

        // 3. App entière limitée par le parent (ne compte pas dans la limite des vidéos courtes)
        val rule = family.ruleFor(pkg) ?: return false
        if (rule != currentRule) { removeCover(); currentRule = rule }
        val limitMs = (family.limitFor(pkg) + store.bonusToday()) * 60_000L
        val used = store.usedToday(rule)
        if (limitMs <= 0 || used >= limitMs) {
            idle()
            if (now - lastKickOut > KICK_COOLDOWN_MS) {
                lastKickOut = now
                performGlobalAction(GLOBAL_ACTION_HOME)
                showKidBlock("Temps écoulé", "C'est fini pour aujourd'hui avec cette app.", canRequest = true)
            }
        } else {
            store.addAppOnlyUsage(rule, elapsed)
            notifier.show("${(limitMs - used) / 60_000 + 1} min restantes")
        }
        return true
    }

    /** Navigation privée : textes propres à ce mode dans l'interface du navigateur (hors contenu des pages). */
    private fun isPrivateBrowsing(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var n = 0
        while (queue.isNotEmpty() && n < 700) {
            val node = queue.removeFirst(); n++
            if (node.className?.toString()?.contains("WebView") == true) continue // contenu des sites ignoré
            val t = ((node.text ?: "").toString() + " " + (node.contentDescription ?: "").toString()).lowercase()
            if (t.isNotBlank() && PRIVATE_KEYS.any { it in t }) return true
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return false
    }

    // Écran de blocage pour l'enfant
    private var kidView: View? = null
    private var kidShownAt = 0L

    private fun showKidBlock(title: String, body: String, canRequest: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (kidView != null || now - kidShownAt < 15_000L) { toast(title); return }
        kidShownAt = now
        val d = resources.displayMetrics.density
        fun t(s: String, size: Float, color: Int, face: String) = TextView(this).apply {
            text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER
            typeface = Typeface.create(face, Typeface.NORMAL); includeFontPadding = false
        }
        fun btn(label: String, bg: Int, fg: Int, onClick: () -> Unit) = t(label, 16f, fg, "sans-serif-medium").apply {
            background = GradientDrawable().apply { setColor(bg); cornerRadius = 28 * d }
            setPadding((30 * d).toInt(), (15 * d).toInt(), (30 * d).toInt(), (15 * d).toInt())
            setOnClickListener { onClick() }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(0xF20E0E0E.toInt()); isClickable = true
            setPadding((32 * d).toInt(), 0, (32 * d).toInt(), 0)
        }
        col.addView(t("⏳", 56f, 0xFFF2F2F2.toInt(), "sans-serif"))
        col.addView(t(title, 30f, 0xFFF2F2F2.toInt(), "sans-serif-medium"), LinearLayout.LayoutParams(-2, -2).apply { topMargin = (20 * d).toInt() })
        col.addView(t(body, 16f, 0xFF8A8A8A.toInt(), "sans-serif").apply { setLineSpacing(0f, 1.25f) },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = (14 * d).toInt() })
        if (canRequest) {
            col.addView(btn("Demander plus de temps", 0xFFFF5A36.toInt(), 0xFFFFFFFF.toInt()) {
                hideKidBlock()
                startActivity(android.content.Intent(this, RequestTimeActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = (36 * d).toInt() })
        }
        col.addView(btn("OK", 0xFFF2F2F2.toInt(), 0xFF111111.toInt()) { hideKidBlock() },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = (14 * d).toInt() })
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        runCatching { windowManager.addView(col, params) }.onSuccess {
            kidView = col
            handler.postDelayed({ hideKidBlock() }, 12_000L)
        }
    }

    /** L'écran enfant se ferme seul ; rien à faire ici sinon. */
    private fun hideKidBlockIfStale() {
        if (kidView != null && SystemClock.elapsedRealtime() - kidShownAt > 12_000L) hideKidBlock()
    }

    private fun hideKidBlock() {
        kidView?.let { runCatching { windowManager.removeView(it) } }
        kidView = null
    }

    private fun maybeWarn(rule: AppRule) {
        val remaining = store.remainingFor(rule)
        if (!warnedFiveMin && remaining in 1..5 * 60_000L && store.limitMinutes > 5) {
            warnedFiveMin = true
            toast("5 min restantes")
        }
        if (remaining > 5 * 60_000L) warnedFiveMin = false
    }

    // ---------- mode strict ----------

    /** Si une page des Paramètres mentionne ReelsGuard, on revient en arrière. */
    private fun guardSettings(root: AccessibilityNodeInfo, now: Long): Boolean {
        val pkg = root.packageName?.toString() ?: return false
        if (pkg !in SETTINGS_PACKAGES) return false
        val hit = root.findAccessibilityNodeInfosByText(getString(R.string.app_name)).any { it.isVisibleToUser }
        if (!hit) return false
        idle()
        if (now - lastKickOut > KICK_COOLDOWN_MS) {
            lastKickOut = now
            performGlobalAction(GLOBAL_ACTION_BACK)
            if (Edition.isFamily) {
                store.logEvent(UsageStore.EV_SETTINGS)
                toast("Protégé par le code parent")
            } else toast("Mode strict : déverrouille d'abord dans ReelsGuard")
        }
        return true
    }

    // ---------- écran « Respire » ----------

    private fun startBreathe(now: Long) {
        lastBreatheAt = now
        breatheEndsAt = now + schedule.delaySeconds * 1000L
        mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)

        val dark = 0xF20E0E0E.toInt()
        val fg = 0xFFF2F2F2.toInt()
        val muted = 0xFF8A8A8A.toInt()
        val d = resources.displayMetrics.density
        fun t(s: String, size: Float, color: Int, face: String) = TextView(this).apply {
            text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER
            typeface = Typeface.create(face, Typeface.NORMAL); includeFontPadding = false
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(dark)
            isClickable = true // absorbe les appuis
        }
        col.addView(t("Respire", 32f, fg, "sans-serif-light"))
        val count = t("", 72f, fg, "sans-serif-light").apply { setPadding(0, (24 * d).toInt(), 0, (24 * d).toInt()) }
        col.addView(count)
        col.addView(t("Tu veux vraiment regarder ?", 15f, muted, "sans-serif"))
        col.addView(t("Quitter", 15f, 0xFF111111.toInt(), "sans-serif-medium").apply {
            background = GradientDrawable().apply { setColor(fg); cornerRadius = 28 * d }
            setPadding((32 * d).toInt(), (14 * d).toInt(), (32 * d).toInt(), (14 * d).toInt())
            setOnClickListener {
                stopBreathe(resume = false)
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = (40 * d).toInt() })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        runCatching { windowManager.addView(col, params) }.onSuccess {
            breatheView = col
            breatheCount = count
            count.text = "${schedule.delaySeconds}"
        }
    }

    private fun stopBreathe(resume: Boolean) {
        val v = breatheView ?: return
        runCatching { windowManager.removeView(v) }
        breatheView = null
        breatheCount = null
        if (resume) mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
    }

    private fun mediaKey(code: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        runCatching {
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }

    // ---------- détection dans l'interface des apps ----------

    /** Vrai si l'écran affiche des vidéos courtes pour cette app. */
    private fun isWatching(rule: AppRule, root: AccessibilityNodeInfo): Boolean {
        if (rule.wholeApp) return true
        val pkg = root.packageName.toString()
        if (rule.viewerIds.any { id ->
                root.findAccessibilityNodeInfosByViewId("$pkg:id/$id").any { it.isVisibleToUser }
            }) return true

        // Textes propres au fil de vidéos, cherchés dans le haut de l'écran.
        if (rule.viewerTexts.any { label ->
                root.findAccessibilityNodeInfosByText(label).any { node ->
                    node.isVisibleToUser && node.label().equals(label, ignoreCase = true) &&
                        node.bounds().top < screenHeight * rule.viewerTextMaxTop
                }
            }) return true

        if (rule.markerIdParts.isNotEmpty() && hasMarker(rule, root)) return true

        // Repli : un élément quasi plein écran dont l'identifiant évoque des vidéos courtes.
        if (rule.viewerIdParts.isNotEmpty() && hasFullscreenViewer(rule, root, 0)) return true

        // Dernier repli : l'onglet concerné de la barre du bas est sélectionné.
        return findTab(rule, root)?.isSelected == true
    }

    /** Parcours en largeur de l'arbre, sans filtre de visibilité (cf. Shorts-Blocker). */
    private fun hasMarker(rule: AppRule, root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var scanned = 0
        while (queue.isNotEmpty() && scanned < 1500) {
            val n = queue.removeFirst()
            scanned++
            val id = n.viewIdResourceName?.lowercase()
            if (id != null && n.isVisibleToUser && rule.markerIdParts.any { it in id }) return true
            for (i in 0 until n.childCount) n.getChild(i)?.let(queue::add)
        }
        return false
    }

    private fun hasFullscreenViewer(rule: AppRule, node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > 30) return false
        val id = node.viewIdResourceName?.substringAfter(":id/")?.lowercase()
        if (id != null && node.isVisibleToUser &&
            rule.viewerIdParts.any { it in id } &&
            rule.viewerIdExclusions.none { it in id } &&
            node.bounds().height() > screenHeight * rule.fullscreenRatio
        ) return true
        for (i in 0 until node.childCount) {
            if (hasFullscreenViewer(rule, node.getChild(i), depth + 1)) return true
        }
        return false
    }

    private var lastDump = 0L

    private fun reportDiagnostics(rule: AppRule, root: AccessibilityNodeInfo, watching: Boolean, now: Long) {
        Diagnostics.setStatus(this, rule.name, watching, findTab(rule, root) != null)
        if (Diagnostics.isEnabled(this) && now - lastDump > 3_000L) {
            lastDump = now
            Diagnostics.saveDump(
                this,
                "${rule.name} (${root.packageName}) · vidéos courtes détectées : $watching · écran ${screenHeight}px",
                root
            )
        }
    }

    /** Le bouton à cacher dans la barre du bas, s'il est à l'écran. */
    private fun findTab(rule: AppRule, root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (rule.wholeApp) return null
        val pkg = root.packageName.toString()
        for (id in rule.tabIds) {
            root.findAccessibilityNodeInfosByViewId("$pkg:id/$id")
                .firstOrNull { it.isVisibleToUser }
                ?.let { return it }
        }
        return findBottomNode(root, rule.tabLabels, rule.navTopToo)
    }

    /**
     * Élément cliquable de la barre d'onglets dont le libellé commence par l'un des [labels].
     * Barre du bas uniquement, ou aussi barre du haut si [allowTop].
     */
    private fun findBottomNode(
        root: AccessibilityNodeInfo,
        labels: List<String>,
        allowTop: Boolean = false,
    ): AccessibilityNodeInfo? {
        for (label in labels) {
            for (node in root.findAccessibilityNodeInfosByText(label)) {
                if (!node.isVisibleToUser) continue
                if (!node.label().startsWith(label, ignoreCase = true)) continue
                val clickable = node.clickableSelfOrParent() ?: continue
                val top = clickable.bounds().top
                if (top > screenHeight * 0.8) return clickable
                if (allowTop && top < screenHeight * 0.25 && clickable.bounds().height() < screenHeight * 0.12) return clickable
            }
        }
        return null
    }

    private fun AccessibilityNodeInfo.label(): String =
        (text ?: contentDescription)?.toString()?.trim().orEmpty()

    private fun AccessibilityNodeInfo.bounds(): Rect = Rect().also { getBoundsInScreen(it) }

    private fun AccessibilityNodeInfo.clickableSelfOrParent(): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = this
        repeat(4) {
            if (n == null) return null
            if (n!!.isClickable) return n
            n = n!!.parent
        }
        return null
    }

    // ---------- cache par-dessus le bouton ----------

    private fun updateCover(tab: AccessibilityNodeInfo?, pkg: String) {
        val bounds = tab?.bounds()
        if (bounds == null || bounds.isEmpty) {
            removeCover()
            return
        }
        wantedBounds = Rect(bounds)
        val existing = tabCover
        if (existing != null) {
            if (bounds != coverBounds) {
                coverBounds.set(bounds)
                runCatching { windowManager.updateViewLayout(existing, coverParams(bounds)) }
            }
            return
        }

        // Couleur exacte de la barre, prélevée une fois par app/thème puis mémorisée.
        val key = "$pkg|${isNight()}|${bounds.top}"
        colorCache[key]?.let { addCover(bounds, it); return }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (screenshotInFlight) return
            screenshotInFlight = true
            val target = Rect(bounds)
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    screenshotInFlight = false
                    val color = runCatching { sampleBarColor(result, target) }.getOrNull() ?: fallbackColor()
                    runCatching { result.hardwareBuffer.close() }
                    colorCache[key] = color
                    if (tabCover == null && wantedBounds == target) addCover(target, color)
                }

                override fun onFailure(errorCode: Int) {
                    screenshotInFlight = false
                    if (tabCover == null && wantedBounds == target) addCover(target, fallbackColor())
                }
            })
        } else {
            addCover(bounds, fallbackColor())
        }
    }

    /** Prend plusieurs points en bordure du bouton et garde la couleur la plus fréquente. */
    private fun sampleBarColor(result: ScreenshotResult, r: Rect): Int? {
        val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace) ?: return null
        val bmp = hw.copy(Bitmap.Config.ARGB_8888, false)
        hw.recycle()
        val points = listOf(
            r.left + 4 to r.centerY(), r.right - 5 to r.centerY(),
            r.centerX() to r.top + 3, r.centerX() to r.bottom - 4,
            r.left + 4 to r.top + 3, r.right - 5 to r.bottom - 4,
        )
        val colors = points.mapNotNull { (x, y) ->
            if (x in 0 until bmp.width && y in 0 until bmp.height) bmp.getPixel(x, y) else null
        }
        bmp.recycle()
        return colors.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }

    private fun coverParams(b: Rect) = WindowManager.LayoutParams(
        b.width(),
        b.height(),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = b.left
        y = b.top
    }

    private fun addCover(bounds: Rect, color: Int) {
        if (tabCover != null) return
        val view = View(this).apply {
            setBackgroundColor(color)
            isClickable = true // absorbe les appuis
            setOnClickListener { toast("Indisponible pour l'instant") }
        }
        runCatching { windowManager.addView(view, coverParams(bounds)) }.onSuccess {
            tabCover = view
            coverBounds.set(bounds)
        }
    }

    private fun removeCover() {
        wantedBounds = null
        tabCover?.let { runCatching { windowManager.removeView(it) } }
        tabCover = null
        coverBounds.setEmpty()
    }

    private fun isNight() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun fallbackColor() = if (isNight()) Color.BLACK else Color.WHITE

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val TICK_MS = 1_000L
        private const val KICK_COOLDOWN_MS = 1_500L
        /** Absence de vidéos courtes au-delà de laquelle un retour compte comme une nouvelle ouverture. */
        private const val ENTRY_GAP_MS = 60_000L
        /** Délai minimal entre deux écrans « Respire ». */
        private const val BREATHE_COOLDOWN_MS = 5 * 60_000L
        /** Absence au-delà de laquelle le visionnage continu (pause forcée) repart de zéro. */
        private const val SESSION_GAP_MS = 2 * 60_000L

        private val IGNORED_PACKAGES = setOf(
            "com.android.systemui",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.touchtype.swiftkey",
        )

        /** Textes qui n'apparaissent qu'en navigation privée (ou dans le menu pour l'ouvrir). */
        private val PRIVATE_KEYS = listOf(
            "vous êtes en navigation privée", "onglet de navigation privée", "nouvel onglet privé", "fenêtre privée",
            "mythes courants sur la navigation privée", "mode secret", "navigation inprivate", "inprivate",
            "onglets privés", "you've gone incognito", "you’ve gone incognito", "new incognito tab",
            "new private tab", "private window", "common myths about private browsing", "secret mode",
            "private tabs", "new inprivate tab",
        )

        private val SETTINGS_PACKAGES = setOf(
            "com.samsung.android.lool", "com.samsung.android.sm", "com.samsung.android.sm_cn",
            "com.miui.securitycenter", "com.coloros.safecenter", "com.huawei.systemmanager",
            "com.android.settings",
            "com.samsung.accessibility",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.permissioncontroller",
        )
    }
}

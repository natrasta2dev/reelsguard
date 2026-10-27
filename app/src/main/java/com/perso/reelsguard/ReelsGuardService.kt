package com.perso.reelsguard

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Surcouche au-dessus d'Instagram, YouTube et TikTok (aucune de ces apps n'est modifiée) :
 *  - compte le temps passé sur les vidéos courtes (Reels, Shorts, fil TikTok) dans UN compteur commun ;
 *  - limite atteinte : le bouton correspondant est recouvert (il disparaît et ne réagit plus) ;
 *  - si on se retrouve quand même sur des vidéos courtes : « retour » (Instagram, YouTube)
 *    ou bascule directe vers l'onglet Profil (TikTok).
 */
class ReelsGuardService : AccessibilityService() {

    private lateinit var store: UsageStore
    private lateinit var schedule: Schedule
    private lateinit var windowManager: WindowManager
    private lateinit var powerManager: PowerManager
    private val handler = Handler(Looper.getMainLooper())

    /** App surveillée actuellement au premier plan, ou null. */
    private var currentRule: AppRule? = null
    private var lastTick = 0L
    private var lastKickOut = 0L
    private var warnedFiveMin = false

    private var tabCover: View? = null
    private val coverBounds = Rect()

    private val screenHeight get() = resources.displayMetrics.heightPixels

    // ---------- cycle de vie ----------

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = UsageStore(this)
        schedule = Schedule(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        lastTick = SystemClock.elapsedRealtime()
        handler.post(ticker)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeCover()
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    // Tout se fait dans la boucle d'une seconde ; les événements ne servent qu'à réveiller le système.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    /** Racine de l'app réellement au premier plan (ignore clavier, barre système, notre cache). */
    private fun foregroundRoot(): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { r ->
            val pkg = r.packageName?.toString()
            if (pkg != null && pkg !in IGNORED_PACKAGES && pkg != packageName) return r
        }
        return windows
            .filter { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedByDescending { it.isFocused || it.isActive }
            .firstNotNullOfOrNull { w ->
                w.root?.takeIf { AppRules.forPackage(it.packageName?.toString().orEmpty()) != null }
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
        val elapsed = now - lastTick
        lastTick = now

        // On relit la fenêtre au premier plan à chaque passage plutôt que de se fier
        // aux événements (YouTube en émet depuis d'autres paquets, ce qui faussait le suivi).
        val root = if (powerManager.isInteractive) foregroundRoot() else null
        val rule = root?.packageName?.toString()?.let { AppRules.forPackage(it) }
        if (rule != currentRule) removeCover()
        currentRule = rule

        if (root == null || rule == null) {
            removeCover()
            return
        }

        val watching = isWatching(rule, root)
        reportDiagnostics(rule, root, watching, now)

        // App désélectionnée ou jour non actif : aucune restriction, aucun comptage.
        if (!schedule.isAppEnabled(rule) || !schedule.isActiveToday()) {
            removeCover()
            return
        }

        val inWindow = schedule.isInWindow()

        // 1) Comptage, seulement dans la plage horaire (plafonné si le téléphone a dormi).
        if (watching && inWindow && !store.isLimitReached) {
            store.addUsage(elapsed.coerceAtMost(TICK_MS * 3))
            maybeWarn()
        }

        // 2) Hors plage ou limite atteinte : bouton caché + sortie des vidéos courtes.
        if (!inWindow || store.isLimitReached) {
            updateCover(findTab(rule, root))
            if (watching && now - lastKickOut > KICK_COOLDOWN_MS) {
                lastKickOut = now
                kickOut(rule, root, if (inWindow) "Limite atteinte" else "Hors plage horaire")
            }
        } else {
            removeCover()
        }
    }

    private fun kickOut(rule: AppRule, root: AccessibilityNodeInfo, msg: String) {
        if (rule.redirectTabLabels.isNotEmpty()) {
            val target = findBottomNode(root, rule.redirectTabLabels)
            if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                toast(msg)
                return
            }
        }
        performGlobalAction(GLOBAL_ACTION_BACK)
        toast(msg)
    }

    private fun maybeWarn() {
        val remaining = store.remainingMs
        if (!warnedFiveMin && remaining in 1..5 * 60_000L && store.limitMinutes > 5) {
            warnedFiveMin = true
            toast("5 min restantes")
        }
        if (remaining > 5 * 60_000L) warnedFiveMin = false
    }

    // ---------- détection dans l'interface des apps ----------

    /** Vrai si l'écran affiche des vidéos courtes pour cette app. */
    private fun isWatching(rule: AppRule, root: AccessibilityNodeInfo): Boolean {
        val pkg = root.packageName.toString()
        if (rule.viewerIds.any { id ->
                root.findAccessibilityNodeInfosByViewId("$pkg:id/$id").any { it.isVisibleToUser }
            }) return true

        // Textes propres au fil de vidéos, cherchés dans le haut de l'écran.
        if (rule.viewerTexts.any { label ->
                root.findAccessibilityNodeInfosByText(label).any { node ->
                    node.isVisibleToUser && node.label().equals(label, ignoreCase = true) &&
                        node.bounds().top < screenHeight * 0.3
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
            if (id != null && rule.markerIdParts.any { it in id }) return true
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
            node.bounds().height() > screenHeight * 0.5
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
        val pkg = root.packageName.toString()
        for (id in rule.tabIds) {
            root.findAccessibilityNodeInfosByViewId("$pkg:id/$id")
                .firstOrNull { it.isVisibleToUser }
                ?.let { return it }
        }
        return findBottomNode(root, rule.tabLabels)
    }

    /** Élément cliquable de la barre du bas dont le libellé commence par l'un des [labels]. */
    private fun findBottomNode(root: AccessibilityNodeInfo, labels: List<String>): AccessibilityNodeInfo? {
        for (label in labels) {
            for (node in root.findAccessibilityNodeInfosByText(label)) {
                if (!node.isVisibleToUser) continue
                if (!node.label().startsWith(label, ignoreCase = true)) continue
                val clickable = node.clickableSelfOrParent() ?: continue
                if (clickable.bounds().top > screenHeight * 0.8) return clickable
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

    private fun updateCover(tab: AccessibilityNodeInfo?) {
        if (tab == null) {
            removeCover()
            return
        }
        val bounds = tab.bounds()
        if (bounds.isEmpty) {
            removeCover()
            return
        }
        val existing = tabCover
        if (existing != null && bounds == coverBounds) return

        coverBounds.set(bounds)
        val params = WindowManager.LayoutParams(
            bounds.width(),
            bounds.height(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bounds.left
            y = bounds.top
        }

        if (existing != null) {
            windowManager.updateViewLayout(existing, params)
        } else {
            val view = View(this).apply {
                // Couleur de la barre (les apps suivent le thème du téléphone) :
                // le bouton disparaît visuellement, et le cache absorbe les appuis.
                background = GradientDrawable().apply { setColor(barColor()) }
                isClickable = true
                setOnClickListener { toast("Indisponible pour l'instant") }
            }
            windowManager.addView(view, params)
            tabCover = view
        }
    }

    private fun removeCover() {
        tabCover?.let { runCatching { windowManager.removeView(it) } }
        tabCover = null
        coverBounds.setEmpty()
    }

    private fun barColor(): Int {
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (night == Configuration.UI_MODE_NIGHT_YES) Color.BLACK else Color.WHITE
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val TICK_MS = 1_000L
        private const val KICK_COOLDOWN_MS = 1_500L

        private val IGNORED_PACKAGES = setOf(
            "com.android.systemui",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.touchtype.swiftkey",
        )
    }
}

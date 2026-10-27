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
 * Surcouche au-dessus d'Instagram :
 *  - compte le temps passé à regarder des Reels (onglet Reels ou Reel ouvert depuis le fil / les DM) ;
 *  - une fois la limite du jour atteinte, recouvre le bouton Reels de la barre du bas
 *    (il disparaît et ne réagit plus) et fait « retour » si un Reel s'ouvre quand même.
 *
 * Instagram n'est jamais modifiée : tout passe par l'API d'accessibilité d'Android.
 */
class ReelsGuardService : AccessibilityService() {

    private lateinit var store: UsageStore
    private lateinit var windowManager: WindowManager
    private lateinit var powerManager: PowerManager
    private val handler = Handler(Looper.getMainLooper())

    private var instagramInFront = false
    private var lastTick = 0L
    private var lastKickOut = 0L
    private var warnedFiveMin = false

    private var tabCover: View? = null
    private val coverBounds = Rect()

    // ---------- cycle de vie ----------

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = UsageStore(this)
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        when {
            pkg == INSTAGRAM -> instagramInFront = true
            // Clavier, barre de notifs et notre propre overlay ne comptent pas comme « quitter Instagram ».
            pkg in IGNORED_PACKAGES || pkg == packageName -> Unit
            else -> {
                instagramInFront = false
                removeCover()
            }
        }
    }

    // ---------- boucle principale (1 fois par seconde) ----------

    private val ticker = object : Runnable {
        override fun run() {
            try {
                tick()
            } catch (_: Exception) {
                // Instagram peut changer son interface : on ne plante jamais le service pour ça.
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastTick
        lastTick = now

        val root = rootInActiveWindow
        val onInstagram = instagramInFront &&
            powerManager.isInteractive &&
            root?.packageName?.toString() == INSTAGRAM

        if (!onInstagram || root == null) {
            removeCover()
            return
        }

        val watchingReels = isWatchingReels(root)

        // 1) Comptage du temps (on plafonne pour ignorer les trous si le téléphone a dormi).
        if (watchingReels && !store.isLimitReached) {
            store.addUsage(elapsed.coerceAtMost(TICK_MS * 3))
            maybeWarn()
        }

        // 2) Limite atteinte : on cache le bouton et on éjecte des Reels.
        if (store.isLimitReached) {
            updateCover(findReelsTab(root))
            if (watchingReels && now - lastKickOut > KICK_COOLDOWN_MS) {
                lastKickOut = now
                performGlobalAction(GLOBAL_ACTION_BACK)
                toast("Temps Reels écoulé pour aujourd'hui 🙂")
            }
        } else {
            removeCover()
        }
    }

    private fun maybeWarn() {
        val remaining = store.remainingMs
        if (!warnedFiveMin && remaining in 1..5 * 60_000L && store.limitMinutes > 5) {
            warnedFiveMin = true
            toast("Plus que 5 min de Reels aujourd'hui")
        }
        if (remaining > 5 * 60_000L) warnedFiveMin = false
    }

    // ---------- détection dans l'interface d'Instagram ----------

    /** Vrai si un lecteur de Reels est affiché (onglet Reels ou Reel ouvert ailleurs). */
    private fun isWatchingReels(root: AccessibilityNodeInfo): Boolean {
        if (REELS_VIEWER_IDS.any { root.hasVisibleId(it) }) return true
        // Repli : l'onglet Reels de la barre du bas est sélectionné.
        return findReelsTab(root)?.isSelected == true
    }

    /** Le bouton Reels de la barre de navigation du bas, s'il est à l'écran. */
    private fun findReelsTab(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (id in REELS_TAB_IDS) {
            root.findAccessibilityNodeInfosByViewId("$INSTAGRAM:id/$id")
                .firstOrNull { it.isVisibleToUser }
                ?.let { return it }
        }
        // Repli si l'identifiant change : un élément cliquable nommé « Reels » dans le bas de l'écran.
        val screenH = resources.displayMetrics.heightPixels
        return root.findAccessibilityNodeInfosByText("Reels").firstOrNull { node ->
            val r = Rect().also { node.getBoundsInScreen(it) }
            node.isVisibleToUser && node.isClickable && r.top > screenH * 0.8
        }
    }

    private fun AccessibilityNodeInfo.hasVisibleId(id: String): Boolean =
        findAccessibilityNodeInfosByViewId("$INSTAGRAM:id/$id").any { it.isVisibleToUser }

    // ---------- cache par-dessus le bouton Reels ----------

    private fun updateCover(tab: AccessibilityNodeInfo?) {
        if (tab == null) {
            removeCover()
            return
        }
        val bounds = Rect().also { tab.getBoundsInScreen(it) }
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
                // Même couleur que la barre d'Instagram (qui suit le thème du téléphone) :
                // le bouton disparaît visuellement, et le cache absorbe les appuis.
                background = GradientDrawable().apply { setColor(barColor()) }
                isClickable = true
                setOnClickListener { toast("Reels bloqués jusqu'à demain") }
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
        private const val INSTAGRAM = "com.instagram.android"
        private const val TICK_MS = 1_000L
        private const val KICK_COOLDOWN_MS = 1_500L

        /** Identifiants internes d'Instagram (peuvent évoluer avec les mises à jour). */
        private val REELS_TAB_IDS = listOf("clips_tab")
        private val REELS_VIEWER_IDS = listOf(
            "clips_viewer_view_pager",
            "clips_viewer_container",
            "clips_video_container",
            "clips_single_media_component",
        )

        private val IGNORED_PACKAGES = setOf(
            "com.android.systemui",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.touchtype.swiftkey",
        )
    }
}

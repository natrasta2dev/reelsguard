package fr.codapps.reelsguard

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Écran d'accueil : temps restant, détail par app, historique, série. Réglages à part. */
class MainActivity : Activity() {

    private lateinit var ui: Ui
    private lateinit var store: UsageStore
    private lateinit var schedule: Schedule
    private val c get() = ui.c

    private lateinit var bigNumber: TextView
    private lateinit var bigCaption: TextView
    private lateinit var bar: ProgressLine
    private lateinit var usage: TextView
    private lateinit var perApp: TextView
    private lateinit var chart: HistoryChart
    private lateinit var average: TextView
    private lateinit var streak: TextView
    private lateinit var serviceButton: TextView
    private lateinit var guide: LinearLayout
    private lateinit var guideToggle: TextView
    private var guideOpen = false
    private lateinit var mainScreen: View
    private lateinit var setupScreen: View
    private lateinit var updateBanner: TextView
    private lateinit var notifBanner: TextView
    /** Écran affiché : "main", "setup" (service inactif) ou "update" (mise à jour obligatoire). */
    private var shown = ""

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        store = UsageStore(this)
        schedule = Schedule(this)
        ui.applyWindow()

        val root = ui.column()

        // Titre discret (appui long = diagnostic)
        root.addView(ui.centered(ui.text("REELSGUARD", 12f, c.muted, ui.medium).apply {
            letterSpacing = 0.25f
            setOnLongClickListener {
                // Le diagnostic peut contenir des textes de l'écran : réservé au parent dans l'édition Famille
                if (Edition.isFamily) PinDialog.require(this@MainActivity, "Diagnostic") { showDiagnostics() }
                else showDiagnostics()
                true
            }
        }))

        bigNumber = ui.text("", 96f, c.fg, ui.light)
        root.addView(ui.centered(bigNumber, top = 40))
        bigCaption = ui.text("", 15f, c.muted, ui.regular)
        root.addView(ui.centered(bigCaption, top = 8))

        bar = ProgressLine(this, c)
        root.addView(bar, LinearLayout.LayoutParams(ui.dp(220), ui.dp(6)).apply {
            topMargin = ui.dp(32); gravity = Gravity.CENTER_HORIZONTAL
        })
        usage = ui.text("", 13f, c.muted, ui.regular)
        root.addView(ui.centered(usage, top = 12))
        perApp = ui.text("", 13f, c.muted, ui.regular)
        root.addView(ui.centered(perApp, top = 6))

        // Historique
        root.addView(ui.centered(ui.section("7 derniers jours"), top = 48))
        chart = HistoryChart(this, c)
        root.addView(chart, LinearLayout.LayoutParams(-1, ui.dp(130)).apply { topMargin = ui.dp(16) })
        average = ui.text("", 13f, c.muted, ui.regular)
        root.addView(ui.centered(average, top = 12))
        streak = ui.text("", 15f, c.fg, ui.medium)
        root.addView(ui.centered(streak, top = 8))

        // Mise à jour facultative disponible
        updateBanner = ui.pill("", filled = c.accent, textColor = Color.WHITE) { UpdateChecker.openDownload(this) }
        root.addView(ui.centered(updateBanner, top = 40))

        // Notifications refusées : Android ne redemande plus, on propose d'ouvrir le réglage
        notifBanner = ui.pill("🔔  Activer les notifications", filled = null, textColor = c.accent) { fixNotifications() }
        (notifBanner.background as GradientDrawable).setStroke(ui.dp(2), c.accent)
        root.addView(ui.centered(notifBanner, top = 16))

        // Actions
        if (Edition.isFamily) {
            root.addView(ui.centered(ui.pill("Demander plus de temps", filled = c.accent, textColor = Color.WHITE) {
                startActivity(Intent(this, RequestTimeActivity::class.java))
            }, top = 44))
            root.addView(ui.centered(ui.pill("Espace parent", filled = c.fg, textColor = c.bg) {
                startActivity(Intent(this, FamilyActivity::class.java))
            }, top = 12))
        }
        root.addView(ui.centered(ui.pill("Réglages", filled = c.fg, textColor = c.bg) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }, top = if (Edition.isFamily) 12 else 44))

        serviceButton = ui.pill("", filled = null, textColor = c.fg) {
            val open = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            if (Edition.isFamily && ParentLock.isSetUp(this)) PinDialog.require(this, "Code parent") { open() } else open()
        }
        root.addView(ui.centered(serviceButton, top = 12))

        guideToggle = ui.link("Revoir le mode d'emploi") { guideOpen = !guideOpen; refresh() }
        root.addView(ui.centered(guideToggle, top = 8))
        guide = buildSteps()
        root.addView(guide, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(16) })

        root.addView(ui.footer(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(48) })

        mainScreen = ui.screen(root)
        setupScreen = buildSetupScreen()
        showScreen(if (isServiceEnabled()) "main" else "setup")
        askNotificationPermission()
    }

    private fun showScreen(which: String) {
        if (which == shown) return
        shown = which
        setContentView(when (which) {
            "update" -> buildUpdateScreen()
            "setup" -> setupScreen
            else -> mainScreen
        })
    }

    /** Priorité : mise à jour obligatoire, puis activation du service, puis accueil. */
    private fun pickScreen() = when {
        UpdateChecker.kind(this) == UpdateChecker.Kind.MAJOR -> "update"
        !isServiceEnabled() -> "setup"
        else -> "main"
    }

    override fun onResume() {
        super.onResume()
        // Édition Famille : accord du parent et code parent avant tout le reste
        if (Edition.isFamily && !ParentLock.isSetUp(this)) {
            startActivity(Intent(this, FamilySetupActivity::class.java))
            return
        }
        handler.post(ticker)
        RemainingWidget.update(this)
        applyUpdateState()
        UpdateChecker.check(this) { applyUpdateState() }
    }

    // ---------- mises à jour ----------

    /** Majeure : écran bloquant. Mineure : bandeau discret. */
    private fun applyUpdateState() {
        val kind = UpdateChecker.kind(this)
        showScreen(pickScreen())
        if (kind == UpdateChecker.Kind.MAJOR) return
        val r = UpdateChecker.cached(this)
        updateBanner.visibility = if (kind == UpdateChecker.Kind.MINOR) View.VISIBLE else View.GONE
        if (r != null) updateBanner.text = "Mettre à jour vers la v${r.version}"
    }

    private fun buildUpdateScreen(): View {
        val r = UpdateChecker.cached(this)
        val col = ui.column().apply { gravity = Gravity.CENTER }
        col.addView(ui.centered(ui.text("REELSGUARD", 12f, c.muted, ui.medium).apply { letterSpacing = 0.25f }))
        col.addView(ui.centered(ui.text("Mise à jour\nobligatoire", 34f, c.fg, ui.light).apply {
            setLineSpacing(0f, 1.1f)
        }, top = 40))
        col.addView(ui.centered(ui.text("v${UpdateChecker.currentVersion()}  →  v${r?.version ?: "?"}", 15f, c.muted, ui.regular), top = 16))
        col.addView(ui.centered(ui.text(
            "Cette version n'est plus prise en charge. La protection continue de fonctionner, " +
                "mais l'application doit être mise à jour pour être utilisée.",
            13f, c.muted, ui.regular).apply { setLineSpacing(0f, 1.2f) }, top = 24))
        val notes = r?.notes?.lines()?.filter { it.isNotBlank() }?.take(8)?.joinToString("\n")
        if (!notes.isNullOrBlank()) {
            col.addView(ui.text(notes, 13f, c.fg, ui.regular).apply {
                gravity = Gravity.START
                setLineSpacing(0f, 1.2f)
                background = GradientDrawable().apply { cornerRadius = ui.dp(16).toFloat(); setStroke(ui.dp(1), c.line) }
                setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16))
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(24) })
        }
        col.addView(ui.centered(ui.pill("Télécharger la mise à jour", filled = c.accent, textColor = Color.WHITE) {
            UpdateChecker.openDownload(this)
        }, top = 32))
        col.addView(ui.centered(ui.link("Voir sur GitHub") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r?.pageUrl ?: UpdateChecker.REPO_URL)))
        }, top = 8))
        col.addView(ui.footer(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(48) })
        return ui.screen(col)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun askNotificationPermission() {
        val prefs = getSharedPreferences("reelsguard_ui", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("notif_asked", false) &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            prefs.edit().putBoolean("notif_asked", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun notificationsEnabled() = getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    /**
     * Après un refus, Android n'affiche plus la demande (ou seulement une fois de plus) :
     * on redemande si c'est encore possible, sinon on ouvre directement le réglage des notifications.
     */
    private fun fixNotifications() {
        if (Edition.isFamily && ParentLock.isSetUp(this) && !ParentLock.sessionActive(this)) {
            PinDialog.require(this, "Code parent") { fixNotifications() }
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            return
        }
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    // ---------- affichage ----------

    private fun refresh() {
        showScreen(pickScreen())
        if (shown != "main") return
        val limit = store.limitMinutes
        val used = store.usedMs
        val left = store.remainingMs

        when {
            !schedule.isActiveToday() -> {
                bigNumber.text = "∞"; bigNumber.setTextColor(c.fg)
                bigCaption.text = "pas de limite aujourd'hui"
            }
            left <= 0 -> {
                bigNumber.text = "0"; bigNumber.setTextColor(c.accent)
                bigCaption.text = "à demain"
            }
            !schedule.isInWindow() -> {
                bigNumber.text = "${left / 60_000}"; bigNumber.setTextColor(c.muted)
                bigCaption.text = "dispo : ${schedule.windowsLabel()}"
            }
            else -> {
                val min = left / 60_000
                bigNumber.text = if (min >= 1) "$min" else "${left / 1000}"
                bigNumber.setTextColor(c.fg)
                bigCaption.text = if (min >= 1) "min restantes" else "s restantes"
            }
        }

        bar.progress = if (limit == 0) 1f else (used / (limit * 60_000f)).coerceIn(0f, 1f)
        usage.text = "${used / 60_000} / $limit min"
        // Seulement les apps utilisées aujourd'hui, de la plus à la moins utilisée.
        perApp.text = APPS.map { (rule, name) -> name to store.usedToday(rule) }
            .filter { it.second >= 60_000 }
            .sortedByDescending { it.second }
            .joinToString("  ·  ") { (name, ms) -> "$name ${ms / 60_000}" }
        perApp.visibility = if (perApp.text.isEmpty()) View.GONE else View.VISIBLE

        val days = store.history(7)
        chart.days = days
        val avg = days.dropLast(1).map { it.usedMs }.average().takeIf { !it.isNaN() } ?: 0.0
        average.text = "Moyenne : ${(avg / 60_000).toInt()} min / jour"
        val s = store.streak()
        streak.text = when (s) {
            0 -> "Pas encore de série"
            1 -> "1 jour sous la limite"
            else -> "$s jours d'affilée sous la limite"
        }

        val on = isServiceEnabled()
        serviceButton.text = if (on) "● Service actif" else "Activer le service"
        serviceButton.setTextColor(if (on) c.muted else Color.WHITE)
        (serviceButton.background as GradientDrawable).apply {
            if (on) { setColor(Color.TRANSPARENT); setStroke(ui.dp(1), c.line) }
            else { setColor(c.accent); setStroke(0, Color.TRANSPARENT) }
        }

        notifBanner.visibility = if (notificationsEnabled()) View.GONE else View.VISIBLE
        guide.visibility = if (guideOpen) View.VISIBLE else View.GONE
        guideToggle.text = if (guideOpen) "Masquer le mode d'emploi" else "Revoir le mode d'emploi"
    }

    // ---------- activation (mode d'emploi) ----------

    /**
     * Écran affiché tant que le service d'accessibilité n'est pas actif : il ne peut pas être fermé
     * et disparaît tout seul dès que le service est activé (vérifié chaque seconde).
     */
    private fun buildSetupScreen(): View {
        val col = ui.column()
        col.addView(ui.centered(ui.text("REELSGUARD", 12f, c.muted, ui.medium).apply { letterSpacing = 0.25f }))
        col.addView(ui.centered(ui.text("Active ReelsGuard", 34f, c.fg, ui.light), top = 32))
        col.addView(ui.centered(ui.text(
            "Une seule fois, environ 2 minutes.\nSuis les étapes dans l'ordre : chaque bouton t'emmène au bon endroit.",
            14f, c.muted, ui.regular).apply { setLineSpacing(0f, 1.25f) }, top = 12))

        col.addView(ui.centered(ui.text("● Service inactif", 14f, c.accent, ui.medium).apply {
            background = GradientDrawable().apply { cornerRadius = ui.dp(20).toFloat(); setStroke(ui.dp(1), c.accent) }
            setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8))
        }, top = 20))

        col.addView(buildSteps(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(28) })

        col.addView(ui.centered(ui.text(
            "Cet écran disparaîtra dès que le service sera actif.",
            12f, c.muted, ui.regular), top = 20))
        col.addView(ui.footer(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(40) })
        return ui.screen(col)
    }

    private data class Step(val title: String, val body: String, val button: String?, val action: (() -> Unit)?)

    private fun buildSteps(): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = ui.dp(20).toFloat()
                setStroke(ui.dp(1), c.line)
            }
            setPadding(ui.dp(20), ui.dp(22), ui.dp(20), ui.dp(6))
        }
        // Édition Famille : ces pages sont protégées, le code parent ouvre une session de 10 min
        fun guarded(open: () -> Unit): () -> Unit = {
            if (Edition.isFamily && ParentLock.isSetUp(this)) PinDialog.require(this, "Code parent") { open() } else open()
        }
        val appSettings = guarded {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$packageName")))
        }
        val a11y = guarded { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        val restricted = Build.VERSION.SDK_INT >= 33
        val steps = mutableListOf<Step>()
        if (restricted) {
            steps += Step(
                "Provoquer le message de refus",
                "Accessibilité → Applications installées (ou « Services installés ») → ReelsGuard → " +
                    "appuie sur l'interrupteur. Android affiche « Paramètre restreint » : appuie sur OK.\n" +
                    "C'est normal : ce refus est obligatoire pour débloquer l'étape suivante.",
                "Ouvrir l'accessibilité", a11y)
            steps += Step(
                "Autoriser les paramètres restreints",
                "Dans « Infos de l'appli » ReelsGuard, appuie sur ⋮ en haut à droite → " +
                    "« Autoriser les paramètres restreints », puis confirme avec ton code ou ton empreinte.\n" +
                    "Le ⋮ n'apparaît qu'après l'étape 1.",
                "Ouvrir les infos de l'appli", appSettings)
        }
        steps += Step(
            "Activer le service",
            (if (restricted) "Retourne dans " else "") +
                "Accessibilité → Applications installées → ReelsGuard → active l'interrupteur, puis appuie sur « Autoriser ».",
            "Ouvrir l'accessibilité", a11y)
        if (Edition.isFamily) steps += Step(
            "Activer l'anti-désinstallation",
            "Android demandera alors de désactiver cette protection avant toute désinstallation, " +
                "et cette page est protégée par le code parent.",
            "Activer (code parent)", {
                PinDialog.require(this, "Anti-désinstallation") {
                    startActivity(Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        .putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, AdminReceiver.component(this))
                        .putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Empêche la désinstallation de ReelsGuard Famille sans le code parent."))
                }
            })
        steps += Step(
            "Batterie non restreinte",
            "Infos de l'appli → Batterie → « Non restreinte ». Sans ça, Android peut mettre ReelsGuard en veille " +
                "et il arrête de compter.",
            "Ouvrir les infos de l'appli", appSettings)
        steps += Step(
            "Autoriser les notifications",
            "Pour voir le temps restant et être prévenu des mises à jour. Si tu as déjà refusé, " +
                "le bouton ouvre directement le réglage : active « Autoriser les notifications ».",
            "Activer les notifications", { fixNotifications() })
        steps += Step(
            "Régler ta limite",
            "Dans Réglages : minutes par jour en semaine et le week-end, apps, jours, horaires, pause, délai et mode strict.",
            null, null)

        steps.forEachIndexed { i, st -> box.addView(stepView(i + 1, st)) }
        if (!restricted) box.addView(ui.text("Ton Android n'a pas besoin des étapes de déblocage : commence directement par activer le service.",
            12f, c.muted, ui.regular).apply { gravity = Gravity.START; setPadding(0, 0, 0, ui.dp(14)) })
        box.addView(ui.text("Après une mise à jour de ReelsGuard, vérifie que le service est toujours actif.",
            12f, c.muted, ui.regular).apply { gravity = Gravity.START; setPadding(0, 0, 0, ui.dp(14)) })
        return box
    }

    private fun stepView(n: Int, st: Step): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, ui.dp(22))
        }
        row.addView(ui.text("$n", 13f, c.bg, ui.medium).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c.fg) }
        }, LinearLayout.LayoutParams(ui.dp(26), ui.dp(26)).apply { rightMargin = ui.dp(14) })

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(ui.text(st.title, 16f, c.fg, ui.medium).apply { gravity = Gravity.START })
        col.addView(ui.text(st.body, 13f, c.muted, ui.regular).apply {
            gravity = Gravity.START
            setPadding(0, ui.dp(6), 0, 0)
            setLineSpacing(0f, 1.25f)
        })
        if (st.button != null && st.action != null) {
            col.addView(ui.text(st.button + "  ›", 13f, c.fg, ui.medium).apply {
                background = GradientDrawable().apply { cornerRadius = ui.dp(18).toFloat(); setStroke(ui.dp(1), c.line) }
                setPadding(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(10))
                setOnClickListener { st.action.invoke() }
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = ui.dp(12) })
        }
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        return row
    }

    // ---------- service et diagnostic ----------

    private fun isServiceEnabled(): Boolean {
        val expected = ComponentName(this, ReelsGuardService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun showDiagnostics() {
        val on = Diagnostics.isEnabled(this)
        AlertDialog.Builder(this)
            .setTitle("Diagnostic")
            .setMessage(Diagnostics.status(this))
            .setPositiveButton("Partager") { _, _ -> shareDiagnostic() }
            .setNeutralButton(if (on) "Désactiver" else "Activer") { _, _ -> Diagnostics.setEnabled(this, !on) }
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun shareDiagnostic() {
        val dump = Diagnostics.dump(this)
        if (dump.isBlank()) {
            Toast.makeText(this, "Active le diagnostic puis ouvre l'écran concerné", Toast.LENGTH_SHORT).show()
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, Diagnostics.status(this@MainActivity) + "\n\n" + dump)
        }
        startActivity(Intent.createChooser(send, null))
    }

    companion object {
        val APPS = listOf(AppRules.INSTAGRAM to "Instagram", AppRules.YOUTUBE to "YouTube", AppRules.TIKTOK to "TikTok",
            AppRules.FACEBOOK to "Facebook",
            AppRules.SNAPCHAT to "Snapchat",
            AppRules.X to "X",
            AppRules.REDDIT to "Reddit",
            AppRules.PINTEREST to "Pinterest",
            AppRules.TWITCH to "Twitch",
            AppRules.THREADS to "Threads",
        )
    }
}

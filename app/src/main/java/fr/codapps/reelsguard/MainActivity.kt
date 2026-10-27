package fr.codapps.reelsguard

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
    private lateinit var updateBanner: TextView
    private var blockingShown = false

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
            setOnLongClickListener { showDiagnostics(); true }
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

        // Actions
        root.addView(ui.centered(ui.pill("Réglages", filled = c.fg, textColor = c.bg) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }, top = 44))

        serviceButton = ui.pill("", filled = null, textColor = c.fg) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        root.addView(ui.centered(serviceButton, top = 12))

        guideToggle = ui.link("Comment l'activer ?") { guideOpen = !guideOpen; refresh() }
        root.addView(ui.centered(guideToggle, top = 8))
        guide = buildGuide()
        root.addView(guide, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(16) })

        root.addView(ui.footer(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(48) })

        mainScreen = ui.screen(root)
        setContentView(mainScreen)
        askNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
        RemainingWidget.update(this)
        applyUpdateState()
        UpdateChecker.check(this) { applyUpdateState() }
    }

    // ---------- mises à jour ----------

    /** Majeure : écran bloquant. Mineure : bandeau discret. */
    private fun applyUpdateState() {
        val kind = UpdateChecker.kind(this)
        if (kind == UpdateChecker.Kind.MAJOR) {
            if (!blockingShown) { setContentView(buildUpdateScreen()); blockingShown = true }
            return
        }
        if (blockingShown) { setContentView(mainScreen); blockingShown = false }
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
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    // ---------- affichage ----------

    private fun refresh() {
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

        guide.visibility = if (!on || guideOpen) View.VISIBLE else View.GONE
        guideToggle.visibility = if (on) View.VISIBLE else View.GONE
        guideToggle.text = if (guideOpen) "Masquer l'aide" else "Comment l'activer ?"
    }

    // ---------- mode d'emploi ----------

    private fun buildGuide(): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = ui.dp(20).toFloat()
                setStroke(ui.dp(1), c.line)
            }
            setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(8))
        }
        val appSettings = {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$packageName")))
        }
        val a11y = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        val settings = { startActivity(Intent(this, SettingsActivity::class.java)) }
        val steps = listOf(
            Triple("Autoriser les paramètres restreints",
                "Infos de l'appli → ⋮ en haut à droite → Autoriser les paramètres restreints. " +
                    "L'option n'apparaît qu'après une première tentative d'activation refusée.", appSettings),
            Triple("Activer le service",
                "Accessibilité → Applications installées → ReelsGuard → activer.", a11y),
            Triple("Batterie non restreinte",
                "Infos de l'appli → Batterie → Non restreinte, sinon le téléphone peut endormir ReelsGuard.", appSettings),
            Triple("Autoriser les notifications",
                "Pour voir le temps restant pendant que tu regardes.", appSettings),
            Triple("Tout régler",
                "Limites semaine et week-end, apps, jours, plages horaires, pause, délai et mode strict.", settings),
        )
        steps.forEachIndexed { i, (title, body, action) -> box.addView(guideStep(i + 1, title, body, action)) }
        box.addView(ui.text("Après une mise à jour de ReelsGuard, vérifie que le service est toujours actif.",
            12f, c.muted, ui.regular).apply { setPadding(0, ui.dp(4), 0, ui.dp(12)) })
        return box
    }

    private fun guideStep(n: Int, title: String, body: String, action: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, ui.dp(16))
            setOnClickListener { action() }
        }
        row.addView(ui.text("$n", 13f, c.bg, ui.medium).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c.fg) }
        }, LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)).apply { rightMargin = ui.dp(14) })

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(ui.text("$title  ›", 15f, c.fg, ui.medium).apply { gravity = Gravity.START })
        col.addView(ui.text(body, 13f, c.muted, ui.regular).apply {
            gravity = Gravity.START
            setPadding(0, ui.dp(4), 0, 0)
            setLineSpacing(0f, 1.2f)
        })
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

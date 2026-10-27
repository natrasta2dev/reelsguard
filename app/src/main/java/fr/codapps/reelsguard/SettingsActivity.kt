package fr.codapps.reelsguard

import android.app.Activity
import android.app.TimePickerDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tous les réglages. Les puces s'appliquent tout de suite ; les nombres avec « Enregistrer ».
 * En mode strict, tout est verrouillé jusqu'à un déverrouillage (2 min d'attente, 5 min ouvert).
 */
class SettingsActivity : Activity() {

    private lateinit var ui: Ui
    private lateinit var store: UsageStore
    private lateinit var schedule: Schedule
    private val c get() = ui.c

    private lateinit var content: LinearLayout
    private lateinit var lockText: TextView
    private lateinit var lockButton: TextView

    private lateinit var weekdayField: EditText
    private lateinit var weekendField: EditText
    private lateinit var limitNote: TextView
    private val quotaFields = mutableListOf<Pair<AppRule, EditText>>()
    private val appChips = mutableListOf<Pair<AppRule, TextView>>()
    private val dayChips = mutableListOf<Pair<Int, TextView>>()
    private lateinit var windowsBox: LinearLayout
    private lateinit var pauseChip: TextView
    private lateinit var pauseAfterField: EditText
    private lateinit var pauseLenField: EditText
    private lateinit var delayChip: TextView
    private lateinit var delayField: EditText
    private lateinit var strictChip: TextView

    private var wasLocked: Boolean? = null

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refreshLock()
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

        // En-tête
        root.addView(ui.centered(ui.text("RÉGLAGES", 12f, c.muted, ui.medium).apply { letterSpacing = 0.25f }))
        root.addView(ui.centered(ui.link("‹ Retour") { finish() }, top = 4))

        // Verrou du mode strict
        lockText = ui.text("", 13f, c.muted, ui.regular)
        root.addView(ui.centered(lockText, top = 16))
        lockButton = ui.pill("Déverrouiller (2 min)", filled = c.accent, textColor = 0xFFFFFFFF.toInt()) {
            schedule.requestUnlock(); refreshLock()
        }
        root.addView(ui.centered(lockButton, top = 12))

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(content, LinearLayout.LayoutParams(-1, -2))
        buildContent()
        // Pied de page hors du bloc verrouillable : les mentions légales restent accessibles.
        root.addView(ui.footer(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(48) })

        setContentView(ui.screen(root))
        loadFields()
    }

    override fun onResume() {
        super.onResume()
        // Mise à jour obligatoire : retour à l'accueil, qui affiche l'écran de mise à jour.
        if (UpdateChecker.isUpdateRequired(this)) { finish(); return }
        wasLocked = null
        handler.post(ticker)
        refreshChips()
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    // ---------- construction ----------

    private fun buildContent() {
        // Limites
        content.addView(ui.centered(ui.section("Limite par jour (min)"), top = 32))
        val limits = ui.row()
        weekdayField = ui.numberField("60", 22f)
        weekendField = ui.numberField("= semaine", 22f)
        limits.addView(labeled("Semaine", weekdayField))
        limits.addView(labeled("Week-end", weekendField), ui.lp(-2, -2, left = 16))
        content.addView(ui.centered(limits, top = 12))
        limitNote = ui.text("", 12f, c.muted, ui.regular)
        content.addView(ui.centered(limitNote, top = 8))

        // Apps + quota propre
        content.addView(ui.centered(ui.section("Apps et quota propre (min)"), top = 32))
        MainActivity.APPS.forEach { (rule, name) ->
            val r = ui.row()
            val chip = ui.chip(name) { schedule.setAppEnabled(rule, !schedule.isAppEnabled(rule)); refreshChips() }
            r.addView(chip, LinearLayout.LayoutParams(ui.dp(120), -2))
            val field = ui.numberField("aucun", 16f)
            r.addView(field, ui.lp(ui.dp(96), -2, left = 12))
            content.addView(ui.centered(r, top = 10))
            appChips += rule to chip
            quotaFields += rule to field
        }

        // Jours
        content.addView(ui.centered(ui.section("Jours actifs"), top = 32))
        val daysRow = ui.row()
        listOf("L", "M", "M", "J", "V", "S", "D").forEachIndexed { i, letter ->
            val chip = ui.chip(letter, round = true) { schedule.toggleDay(i + 1); refreshChips() }
            daysRow.addView(chip, LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)).apply { if (i > 0) leftMargin = ui.dp(6) })
            dayChips += (i + 1) to chip
        }
        content.addView(ui.centered(daysRow, top = 12))

        // Plages horaires
        content.addView(ui.centered(ui.section("Plages horaires"), top = 32))
        windowsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        content.addView(ui.centered(windowsBox, top = 4))
        content.addView(ui.centered(ui.link("+ Ajouter une plage") { addWindow() }))

        // Pause forcée
        content.addView(ui.centered(ui.section("Pause forcée"), top = 28))
        pauseChip = ui.chip("") { schedule.pauseEnabled = !schedule.pauseEnabled; refreshChips() }
        content.addView(ui.centered(pauseChip, top = 12))
        val pauseRow = ui.row()
        pauseAfterField = ui.numberField("15", 16f)
        pauseLenField = ui.numberField("5", 16f)
        pauseRow.addView(ui.text("Toutes les", 13f, c.muted, ui.regular))
        pauseRow.addView(pauseAfterField, ui.lp(ui.dp(64), -2, left = 8))
        pauseRow.addView(ui.text("min, pause de", 13f, c.muted, ui.regular), ui.lp(-2, -2, left = 8))
        pauseRow.addView(pauseLenField, ui.lp(ui.dp(64), -2, left = 8))
        content.addView(ui.centered(pauseRow, top = 12))

        // Délai avant ouverture
        content.addView(ui.centered(ui.section("Délai avant ouverture"), top = 28))
        delayChip = ui.chip("") { schedule.delayEnabled = !schedule.delayEnabled; refreshChips() }
        content.addView(ui.centered(delayChip, top = 12))
        val delayRow = ui.row()
        delayField = ui.numberField("10", 16f)
        delayRow.addView(ui.text("Écran « Respire » pendant", 13f, c.muted, ui.regular))
        delayRow.addView(delayField, ui.lp(ui.dp(64), -2, left = 8))
        delayRow.addView(ui.text("s", 13f, c.muted, ui.regular), ui.lp(-2, -2, left = 8))
        content.addView(ui.centered(delayRow, top = 12))

        // Mode strict
        content.addView(ui.centered(ui.section("Mode strict"), top = 28))
        strictChip = ui.chip("") {
            schedule.strictEnabled = !schedule.strictEnabled
            wasLocked = null
            refreshChips(); refreshLock()
        }
        content.addView(ui.centered(strictChip, top = 12))
        content.addView(ui.centered(ui.text(
            "Verrouille ces réglages et la désactivation de ReelsGuard.\nPour modifier, il faut attendre 2 min.",
            12f, c.muted, ui.regular).apply { setLineSpacing(0f, 1.2f) }, top = 10))

        content.addView(ui.centered(ui.pill("Enregistrer", filled = c.fg, textColor = c.bg) { save() }, top = 36))
    }

    private fun labeled(label: String, field: EditText) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(field, LinearLayout.LayoutParams(ui.dp(120), -2))
        addView(ui.text(label, 12f, c.muted, ui.regular), ui.lp(-2, -2, top = 6))
    }

    // ---------- valeurs ----------

    private fun loadFields() {
        weekdayField.setText((store.pending(false) ?: store.weekdayLimit).toString())
        val we = store.pending(true) ?: store.weekendLimit
        weekendField.setText(if (we >= 0) we.toString() else "")
        quotaFields.forEach { (rule, f) -> store.appQuota(rule).let { f.setText(if (it > 0) "$it" else "") } }
        pauseAfterField.setText("${schedule.pauseAfterMin}")
        pauseLenField.setText("${schedule.pauseLengthMin}")
        delayField.setText("${schedule.delaySeconds}")
        refreshLimitNote()
    }

    private fun save() {
        if (schedule.isLocked()) return
        val wd = weekdayField.text.toString().toIntOrNull()
        if (wd == null) {
            Toast.makeText(this, "Entre une limite pour la semaine", Toast.LENGTH_SHORT).show()
            return
        }
        val weNow = store.setLimit(weekendField.text.toString().toIntOrNull() ?: -1, weekend = true)
        val wdNow = store.setLimit(wd, weekend = false)
        quotaFields.forEach { (rule, f) -> store.setAppQuota(rule, f.text.toString().toIntOrNull() ?: 0) }
        pauseAfterField.text.toString().toIntOrNull()?.let { schedule.pauseAfterMin = it }
        pauseLenField.text.toString().toIntOrNull()?.let { schedule.pauseLengthMin = it }
        delayField.text.toString().toIntOrNull()?.let { schedule.delaySeconds = it }

        Toast.makeText(this, if (wdNow && weNow) "Enregistré" else "Enregistré · hausse de limite active demain", Toast.LENGTH_SHORT).show()
        currentFocus?.let {
            it.clearFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken, 0)
        }
        loadFields()
        RemainingWidget.update(this)
    }

    private fun refreshLimitNote() {
        val parts = mutableListOf<String>()
        store.pending(false)?.let { parts += "semaine : $it min" }
        store.pending(true)?.let { parts += "week-end : " + if (it < 0) "comme la semaine" else "$it min" }
        limitNote.text = if (parts.isEmpty()) "" else "À partir de demain · " + parts.joinToString(" · ")
        limitNote.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
    }

    // ---------- puces et plages ----------

    private fun refreshChips() {
        appChips.forEach { (rule, chip) -> ui.styleChip(chip, schedule.isAppEnabled(rule)) }
        val days = schedule.activeDays
        dayChips.forEach { (d, chip) -> ui.styleChip(chip, d in days) }
        pauseChip.text = if (schedule.pauseEnabled) "Activée" else "Désactivée"
        ui.styleChip(pauseChip, schedule.pauseEnabled)
        delayChip.text = if (schedule.delayEnabled) "Activé" else "Désactivé"
        ui.styleChip(delayChip, schedule.delayEnabled)
        strictChip.text = if (schedule.strictEnabled) "Activé" else "Désactivé"
        ui.styleChip(strictChip, schedule.strictEnabled)
        renderWindows()
        applyLock(schedule.isLocked())
    }

    private fun renderWindows() {
        windowsBox.removeAllViews()
        val list = schedule.windows
        if (list.isEmpty()) {
            windowsBox.addView(ui.centered(ui.text("Toute la journée", 14f, c.fg, ui.medium), top = 12))
            return
        }
        list.forEachIndexed { i, w ->
            val r = ui.row()
            val start = ui.chip(Schedule.fmt(w.start)) { pickTime(w.start) { schedule.replaceWindow(i, w.copy(start = it)) } }
            val end = ui.chip(Schedule.fmt(w.end)) { pickTime(w.end) { schedule.replaceWindow(i, w.copy(end = it)) } }
            ui.styleChip(start, true); ui.styleChip(end, true)
            r.addView(start)
            r.addView(ui.text("→", 15f, c.muted, ui.regular), ui.lp(-2, -2, left = 10))
            r.addView(end, ui.lp(-2, -2, left = 10))
            r.addView(ui.text("✕", 15f, c.muted, ui.regular).apply {
                setPadding(ui.dp(12), ui.dp(8), ui.dp(4), ui.dp(8))
                setOnClickListener { schedule.removeWindow(i); refreshChips() }
            })
            windowsBox.addView(ui.centered(r, top = 10))
        }
    }

    private fun addWindow() {
        if (schedule.isLocked()) return
        val last = schedule.windows.lastOrNull()
        val w = if (last == null) Schedule.Window(8 * 60, 16 * 60)
        else Schedule.Window((last.end + 60) % 1440, (last.end + 180) % 1440)
        schedule.addWindow(w)
        refreshChips()
    }

    private fun pickTime(initial: Int, onPicked: (Int) -> Unit) {
        if (schedule.isLocked()) return
        TimePickerDialog(this, { _, h, m ->
            onPicked(h * 60 + m)
            refreshChips()
        }, initial / 60, initial % 60, true).show()
    }

    // ---------- mode strict ----------

    private fun refreshLock() {
        val locked = schedule.isLocked()
        if (locked != wasLocked) {
            wasLocked = locked
            applyLock(locked)
        }
        when {
            !schedule.strictEnabled -> {
                lockText.visibility = View.GONE
                lockButton.visibility = View.GONE
            }
            locked && schedule.isUnlockPending() -> {
                val s = schedule.unlockWaitMs() / 1000
                lockText.text = "Déverrouillage dans %d:%02d".format(s / 60, s % 60)
                lockText.visibility = View.VISIBLE
                lockButton.visibility = View.GONE
            }
            locked -> {
                lockText.text = "Mode strict : réglages verrouillés"
                lockText.visibility = View.VISIBLE
                lockButton.visibility = View.VISIBLE
            }
            else -> {
                val until = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(schedule.unlockedUntil()))
                lockText.text = "Déverrouillé jusqu'à $until"
                lockText.visibility = View.VISIBLE
                lockButton.visibility = View.GONE
            }
        }
    }

    private fun applyLock(locked: Boolean) {
        ui.setEnabledDeep(content, !locked)
        content.alpha = if (locked) 0.35f else 1f
    }
}

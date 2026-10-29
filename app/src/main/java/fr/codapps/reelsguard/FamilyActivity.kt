package fr.codapps.reelsguard

import android.app.Activity
import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/**
 * Espace parent (édition Famille), protégé par le code parent :
 * rapport, couvre-feu, temps scolaire, apps entières et apps autorisées, protection, code.
 */
class FamilyActivity : Activity() {

    private lateinit var ui: Ui
    private lateinit var store: UsageStore
    private lateinit var family: FamilyConfig
    private val c get() = ui.c

    private lateinit var sessionText: TextView
    private lateinit var adminButton: TextView
    private lateinit var saveButton: TextView
    private val limitFields = mutableListOf<Pair<String, EditText>>()
    private var snapshot = emptyList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        store = UsageStore(this)
        family = FamilyConfig(this)
        ui.applyWindow()
        build()
    }

    private val sessionCheck = object : Runnable {
        override fun run() {
            // Session expirée pendant que l'écran est ouvert : on le referme.
            if (!ParentLock.sessionActive(this@FamilyActivity)) finish()
            else window.decorView.postDelayed(this, 15_000L)
        }
    }

    override fun onResume() {
        super.onResume()
        val content = window.decorView
        if (!ParentLock.sessionActive(this)) {
            // Rien n'est visible ni modifiable tant que le code n'a pas été saisi.
            content.visibility = View.INVISIBLE
            PinDialog.require(this, "Espace parent", onCancel = { finish() }) {
                content.visibility = View.VISIBLE; refresh(); content.postDelayed(sessionCheck, 15_000L)
            }
        } else {
            content.visibility = View.VISIBLE
            content.removeCallbacks(sessionCheck); content.postDelayed(sessionCheck, 15_000L)
        }
        refresh()
    }

    override fun onPause() {
        window.decorView.removeCallbacks(sessionCheck)
        super.onPause()
    }

    private fun build() {
        val root = ui.column()
        root.setPadding(root.paddingLeft, root.paddingTop, root.paddingRight, ui.dp(140))
        root.addView(ui.centered(ui.text("ESPACE PARENT", 12f, c.muted, ui.medium).apply { letterSpacing = 0.25f }))
        root.addView(ui.centered(ui.link("‹ Retour") { leave() }, top = 4))
        sessionText = ui.text("", 13f, c.muted, ui.regular)
        root.addView(ui.centered(sessionText, top = 8))
        root.addView(ui.centered(ui.link("Fermer la session parent") {
            ParentLock.closeSession(this); finish()
        }))

        // ---------- rapport ----------
        root.addView(ui.centered(ui.section("Rapport · 7 derniers jours"), top = 32))
        root.addView(card(buildReport()), LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(12) })

        // ---------- temps bonus ----------
        root.addView(ui.centered(ui.section("Temps en plus aujourd'hui"), top = 32))
        val bonusText = ui.text("${store.bonusToday()} min accordées", 15f, c.fg, ui.medium)
        root.addView(ui.centered(bonusText, top = 10))
        val bonusRow = ui.row()
        listOf(15, 30, 60).forEachIndexed { i, m ->
            bonusRow.addView(ui.chip("+$m min") {
                store.addBonus(m); bonusText.text = "${store.bonusToday()} min accordées"
            }.also { ui.styleChip(it, false) }, ui.lp(-2, -2, left = if (i > 0) 8 else 0))
        }
        root.addView(ui.centered(bonusRow, top = 10))

        // ---------- plages ----------
        root.addView(ui.centered(ui.section("Couvre-feu"), top = 32))
        root.addView(periodEditor({ family.curfew }, { family.curfew = it }), LinearLayout.LayoutParams(-1, -2))
        root.addView(ui.centered(ui.section("Temps scolaire"), top = 28))
        root.addView(periodEditor({ family.school }, { family.school = it }), LinearLayout.LayoutParams(-1, -2))
        root.addView(ui.centered(ui.text(
            "Pendant ces plages, seules les apps essentielles (téléphone, SMS, horloge) et les apps marquées « autorisée » s'ouvrent.",
            12f, c.muted, ui.regular).apply { setLineSpacing(0f, 1.2f) }, top = 10))

        // ---------- apps ----------
        root.addView(ui.centered(ui.section("Apps"), top = 32))
        root.addView(ui.centered(ui.text(
            "Min/J pour l'app entière · vide = pas de limite · 0 = bloquée.\nInstagram, YouTube, TikTok… se règlent dans Réglages.",
            12f, c.muted, ui.regular).apply { setLineSpacing(0f, 1.2f) }, top = 6))
        val search = ui.numberField("Rechercher une app", 15f).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        root.addView(search, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(12) })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })

        val known = AppRules.ALL.flatMap { it.packages }.toSet()
        val apps = FamilyConfig.launchableApps(this).filter { it.first !in known }
        val limits = family.appLimits()
        val allowed = family.allowedExtra()
        val essentials = family.essentials()
        val rows = mutableListOf<Pair<String, View>>()
        apps.forEach { (pkg, name) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, ui.dp(6), 0, ui.dp(6))
            }
            row.addView(ui.text(name, 15f, c.fg, ui.medium).apply { gravity = Gravity.START; maxLines = 1 },
                LinearLayout.LayoutParams(0, -2, 1f))
            if (pkg in essentials) {
                row.addView(ui.text("essentielle", 12f, c.muted, ui.regular))
            } else {
                val chip = ui.chip("autorisée") {}
                chip.setOnClickListener {
                    val on = pkg !in family.allowedExtra()
                    family.setAllowed(pkg, on); ui.styleChip(chip, on)
                }
                ui.styleChip(chip, pkg in allowed)
                row.addView(chip, ui.lp(-2, -2, left = 8))
            }
            val f = ui.numberField("Min", 15f)
            limits[pkg]?.let { f.setText("$it") }
            row.addView(f, ui.lp(ui.dp(80), -2, left = 8))
            limitFields += pkg to f
            list.addView(row)
            rows += name.lowercase() to row
        }
        search.addTextChangedListener(watcher {
            val q = search.text.toString().trim().lowercase()
            rows.forEach { (n, v) -> v.visibility = if (q.isEmpty() || q in n) View.VISIBLE else View.GONE }
        })

        // ---------- protection ----------
        root.addView(ui.centered(ui.section("Protection"), top = 32))
        adminButton = ui.pill("", filled = null, textColor = c.fg) {
            if (!AdminReceiver.isActive(this)) {
                startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, AdminReceiver.component(this))
                    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Empêche la désinstallation de ReelsGuard Famille sans le code parent."))
            }
        }
        root.addView(ui.centered(adminButton, top = 12))
        root.addView(ui.centered(ui.pill("Changer le code parent", filled = null, textColor = c.fg) {
            PinDialog.create(this, "Nouveau code parent") { refresh() }
        }, top = 12))
        root.addView(ui.centered(ui.text(
            "Sans le code, impossible de désactiver le service, de forcer l'arrêt, d'effacer les données ou de désinstaller. " +
                "La navigation privée est bloquée dans tous les navigateurs.",
            12f, c.muted, ui.regular).apply { setLineSpacing(0f, 1.2f) }, top = 12))

        root.addView(ui.footer(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(40) })

        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(c.bg); addView(root, -1, -2) }
        saveButton = ui.pill("Enregistrer les limites", filled = c.accent, textColor = Color.WHITE) { saveLimits() }.apply {
            elevation = ui.dp(8).toFloat(); visibility = View.GONE
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(c.bg)
            addView(scroll, FrameLayout.LayoutParams(-1, -1))
            addView(saveButton, FrameLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; bottomMargin = ui.dp(28)
            })
        })
        snapshot = limitFields.map { it.second.text.toString().trim() }
        val w = watcher { updateSave() }
        limitFields.forEach { it.second.addTextChangedListener(w) }
    }

    private fun watcher(f: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        override fun afterTextChanged(s: Editable?) = f()
    }

    private fun isDirty() = limitFields.map { it.second.text.toString().trim() } != snapshot
    private fun updateSave() { saveButton.visibility = if (isDirty()) View.VISIBLE else View.GONE }

    private fun saveLimits() {
        if (!ParentLock.sessionActive(this)) { PinDialog.require(this) { saveLimits() }; return }
        limitFields.forEach { (pkg, f) -> family.setAppLimit(pkg, f.text.toString().trim().toIntOrNull() ?: -1) }
        snapshot = limitFields.map { it.second.text.toString().trim() }
        updateSave()
        Toast.makeText(this, "Limites enregistrées", Toast.LENGTH_SHORT).show()
    }

    private fun leave() {
        if (isDirty()) saveLimits()
        finish()
    }

    @Deprecated("Retour : enregistre les limites modifiées")
    override fun onBackPressed() = leave()

    private fun refresh() {
        if (!::sessionText.isInitialized) return
        sessionText.text = if (ParentLock.sessionActive(this))
            "Session parent jusqu'à " + SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(ParentLock.sessionUntil(this)))
        else "Code parent requis"
        val on = AdminReceiver.isActive(this)
        adminButton.text = if (on) "● Anti-désinstallation active" else "Activer l'anti-désinstallation"
        adminButton.setTextColor(if (on) c.muted else Color.WHITE)
        (adminButton.background as GradientDrawable).apply {
            if (on) { setColor(Color.TRANSPARENT); setStroke(ui.dp(1), c.line) } else { setColor(c.accent); setStroke(0, Color.TRANSPARENT) }
        }
    }

    // ---------- briques ----------

    private fun card(v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply { cornerRadius = ui.dp(20).toFloat(); setStroke(ui.dp(1), c.line) }
        setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(16))
        addView(v)
    }

    private fun buildReport(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val days = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
        val names = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")
        val short = MainActivity.APPS
        val custom = family.appLimits().keys.mapNotNull { family.ruleFor(it) }
        val labels = FamilyConfig.launchableApps(this).toMap()
        days.forEach { d ->
            val parts = (short.map { (r, n) -> n to store.usedOn(d, r) } +
                custom.map { r -> (labels[r.packages.first()] ?: r.packages.first()) to store.usedOn(d, r) })
                .filter { it.second >= 60_000 }.sortedByDescending { it.second }
            val total = parts.sumOf { it.second } / 60_000
            val line = "${names[d.dayOfWeek.value - 1]}  ·  $total min" +
                if (parts.isEmpty()) "" else "\n" + parts.take(4).joinToString("  ·  ") { "${it.first} ${it.second / 60_000}" }
            col.addView(ui.text(line, 13f, if (d == LocalDate.now()) c.fg else c.muted, ui.regular).apply {
                gravity = Gravity.START; setLineSpacing(0f, 1.2f); setPadding(0, 0, 0, ui.dp(10))
            })
        }
        fun ev(t: String) = days.sumOf { store.eventsOn(it, t) }
        val events = listOf(
            "Pages protégées refermées" to ev(UsageStore.EV_SETTINGS),
            "Navigation privée bloquée" to ev(UsageStore.EV_PRIVATE),
            "Apps bloquées (couvre-feu / école)" to ev(UsageStore.EV_RESTRICTED),
            "Demandes de temps" to ev(UsageStore.EV_REQUEST),
            "Codes parent erronés" to ev(UsageStore.EV_PIN_FAIL),
        )
        col.addView(ui.text(events.joinToString("\n") { "${it.first} : ${it.second}" }, 13f, c.fg, ui.regular).apply {
            gravity = Gravity.START; setLineSpacing(0f, 1.3f); setPadding(0, ui.dp(6), 0, 0)
        })
        return col
    }

    private fun periodEditor(get: () -> FamilyConfig.Period, set: (FamilyConfig.Period) -> Unit): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val onChip = ui.chip("") {}
        val times = ui.row()
        val start = ui.chip("") {}
        val end = ui.chip("") {}
        val days = ui.row()
        val dayChips = listOf("L", "M", "M", "J", "V", "S", "D").mapIndexed { i, l ->
            ui.chip(l, round = true) {}.also { days.addView(it, LinearLayout.LayoutParams(ui.dp(34), ui.dp(34)).apply { if (i > 0) leftMargin = ui.dp(5) }) }
        }
        fun render() {
            val p = get()
            onChip.text = if (p.enabled) "Activé" else "Désactivé"; ui.styleChip(onChip, p.enabled)
            start.text = Schedule.fmt(p.start); end.text = Schedule.fmt(p.end)
            ui.styleChip(start, true); ui.styleChip(end, true)
            dayChips.forEachIndexed { i, ch -> ui.styleChip(ch, (i + 1) in p.days) }
        }
        onChip.setOnClickListener { set(get().copy(enabled = !get().enabled)); render() }
        fun pick(isStart: Boolean) {
            val p = get(); val v = if (isStart) p.start else p.end
            TimePickerDialog(this, { _, h, m ->
                set(if (isStart) get().copy(start = h * 60 + m) else get().copy(end = h * 60 + m)); render()
            }, v / 60, v % 60, true).show()
        }
        start.setOnClickListener { pick(true) }
        end.setOnClickListener { pick(false) }
        dayChips.forEachIndexed { i, ch -> ch.setOnClickListener {
            val d = get().days.toMutableSet(); if (!d.add(i + 1)) d.remove(i + 1); set(get().copy(days = d)); render()
        } }
        times.addView(start)
        times.addView(ui.text("→", 15f, c.muted, ui.regular), ui.lp(-2, -2, left = 10))
        times.addView(end, ui.lp(-2, -2, left = 10))
        col.addView(ui.centered(onChip, top = 10))
        col.addView(ui.centered(times, top = 10))
        col.addView(ui.centered(days, top = 10))
        render()
        return col
    }
}

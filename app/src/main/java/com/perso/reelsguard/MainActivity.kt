package com.perso.reelsguard

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Écran unique : temps restant, progression, limite quotidienne. */
class MainActivity : Activity() {

    private lateinit var store: UsageStore
    private lateinit var c: Palette

    private lateinit var bigNumber: TextView
    private lateinit var bigCaption: TextView
    private lateinit var bar: ProgressLine
    private lateinit var usage: TextView
    private lateinit var limitInput: EditText
    private lateinit var pendingNote: TextView
    private lateinit var serviceButton: TextView
    private lateinit var guide: LinearLayout
    private lateinit var guideToggle: TextView
    private var guideOpen = false

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = UsageStore(this)
        c = Palette.of(this)
        window.statusBarColor = c.bg
        window.navigationBarColor = c.bg
        if (!c.dark) window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(48), dp(32), dp(48))
        }

        // Titre discret (appui long = diagnostic)
        root.addView(centered(text("REELSGUARD", 12f, c.muted, medium).apply {
            letterSpacing = 0.25f
            setOnLongClickListener { showDiagnostics(); true }
        }))

        bigNumber = text("", 96f, c.fg, light)
        root.addView(centered(bigNumber, top = 40))
        bigCaption = text("", 15f, c.muted, regular)
        root.addView(centered(bigCaption, top = 8))

        bar = ProgressLine(this, c)
        root.addView(bar, LinearLayout.LayoutParams(dp(220), dp(6)).apply {
            topMargin = dp(32); gravity = Gravity.CENTER_HORIZONTAL
        })

        usage = text("", 13f, c.muted, regular)
        root.addView(centered(usage, top = 12))

        // Réglage de la limite : saisie en minutes + Enregistrer
        root.addView(centered(text("Limite par jour (min)", 13f, c.muted, regular), top = 56))

        limitInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            gravity = Gravity.CENTER
            textSize = 28f
            typeface = medium
            setTextColor(c.fg)
            setHintTextColor(c.muted)
            hint = "60"
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), c.line)
            }
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) { saveLimit(); true } else false
            }
        }
        root.addView(limitInput, LinearLayout.LayoutParams(dp(160), -2).apply {
            topMargin = dp(12); gravity = Gravity.CENTER_HORIZONTAL
        })

        root.addView(centered(pill("Enregistrer", filled = c.fg, textColor = c.bg) { saveLimit() }, top = 16))

        pendingNote = text("", 12f, c.muted, regular)
        root.addView(centered(pendingNote, top = 10))

        serviceButton = pill("", filled = null, textColor = c.fg) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        root.addView(centered(serviceButton, top = 40))

        // Mode d'emploi : affiché d'office tant que le service est coupé
        guideToggle = text("Comment l'activer ?", 13f, c.muted, regular).apply {
            paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { guideOpen = !guideOpen; refresh() }
        }
        root.addView(centered(guideToggle, top = 8))

        guide = buildGuide()
        root.addView(guide, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(c.bg)
            addView(root, -1, -1)
        })
        limitInput.setText(targetLimit().toString())
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    // ---------- logique ----------

    /** Valeur affichée : la limite de demain si une hausse est en attente, sinon celle du jour. */
    private fun targetLimit(): Int = store.pendingLimitMinutes.takeIf { it >= 0 } ?: store.limitMinutes

    private fun saveLimit() {
        val minutes = limitInput.text.toString().toIntOrNull()
        if (minutes == null) {
            Toast.makeText(this, "Entre un nombre de minutes", Toast.LENGTH_SHORT).show()
            return
        }
        val now = store.setLimit(minutes)
        Toast.makeText(this, if (now) "Enregistré" else "Appliquée demain", Toast.LENGTH_SHORT).show()
        limitInput.clearFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(limitInput.windowToken, 0)
        refresh()
    }

    private fun refresh() {
        val limit = store.limitMinutes
        val used = store.usedMs
        val left = store.remainingMs

        if (left <= 0) {
            bigNumber.text = "0"
            bigNumber.setTextColor(c.accent)
            bigCaption.text = "à demain"
        } else {
            val min = left / 60_000
            bigNumber.text = if (min >= 1) "$min" else "${left / 1000}"
            bigNumber.setTextColor(c.fg)
            bigCaption.text = if (min >= 1) "min restantes" else "s restantes"
        }

        bar.progress = if (limit == 0) 1f else (used / (limit * 60_000f)).coerceIn(0f, 1f)
        usage.text = "${used / 60_000} / $limit min"

        val pending = store.pendingLimitMinutes
        pendingNote.text = if (pending >= 0) "$pending min à partir de demain" else ""
        pendingNote.visibility = if (pending >= 0) View.VISIBLE else View.GONE

        val on = isServiceEnabled()
        serviceButton.text = if (on) "● Service actif" else "Activer le service"
        serviceButton.setTextColor(if (on) c.muted else Color.WHITE)
        (serviceButton.background as GradientDrawable).apply {
            if (on) { setColor(Color.TRANSPARENT); setStroke(dp(1), c.line) }
            else { setColor(c.accent); setStroke(0, Color.TRANSPARENT) }
        }

        val showGuide = !on || guideOpen
        guide.visibility = if (showGuide) View.VISIBLE else View.GONE
        guideToggle.visibility = if (on) View.VISIBLE else View.GONE
        guideToggle.text = if (guideOpen) "Masquer l'aide" else "Comment l'activer ?"
    }

    private fun buildGuide(): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), c.line)
            }
            setPadding(dp(20), dp(20), dp(20), dp(8))
        }
        val appSettings = {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.parse("package:$packageName")))
        }
        val a11y = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        val steps = listOf(
            Triple("Autoriser les paramètres restreints",
                "Infos de l'appli → ⋮ en haut à droite → Autoriser les paramètres restreints. " +
                    "L'option n'apparaît qu'après une première tentative d'activation refusée.", appSettings),
            Triple("Activer le service",
                "Accessibilité → Applications installées → ReelsGuard → activer.", a11y),
            Triple("Batterie non restreinte",
                "Infos de l'appli → Batterie → Non restreinte, sinon le téléphone peut endormir ReelsGuard.", appSettings),
            Triple("Régler ta limite",
                "Tape le nombre de minutes par jour puis Enregistrer. Le compteur est commun à " +
                    "Reels, Shorts et TikTok et repart à zéro à minuit.", null),
        )
        steps.forEachIndexed { i, (title, body, action) ->
            box.addView(guideStep(i + 1, title, body, action))
        }
        box.addView(text("Après une mise à jour de ReelsGuard, vérifie que le service est toujours actif.",
            12f, c.muted, regular).apply { setPadding(0, dp(4), 0, dp(12)) })
        return box
    }

    private fun guideStep(n: Int, title: String, body: String, action: (() -> Unit)?): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(16))
            if (action != null) setOnClickListener { action() }
        }
        row.addView(text("$n", 13f, c.bg, medium).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c.fg) }
        }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(14) })

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(text(title + if (action != null) "  ›" else "", 15f, c.fg, medium).apply { gravity = Gravity.START })
        col.addView(text(body, 13f, c.muted, regular).apply {
            gravity = Gravity.START
            setPadding(0, dp(4), 0, 0)
            setLineSpacing(0f, 1.2f)
        })
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        return row
    }

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

    // ---------- petites briques d'interface ----------

    private val light = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private fun text(s: String, size: Float, color: Int, face: Typeface) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        typeface = face
        gravity = Gravity.CENTER
        includeFontPadding = false
    }

    private fun pill(label: String, filled: Int?, textColor: Int, onClick: () -> Unit) =
        text(label, 15f, textColor, medium).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                if (filled != null) setColor(filled) else setStroke(dp(1), c.line)
            }
            setPadding(dp(28), dp(14), dp(28), dp(14))
            setOnClickListener { onClick() }
        }

    /** Ajoute la vue centrée horizontalement, avec une marge au-dessus. */
    private fun centered(v: View, top: Int = 0): View = v.apply {
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(top)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** Fine barre de progression arrondie. */
    private class ProgressLine(ctx: Context, private val c: Palette) : View(ctx) {
        var progress = 0f
            set(v) { field = v; invalidate() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()

        override fun onDraw(canvas: Canvas) {
            val h = height.toFloat()
            r.set(0f, 0f, width.toFloat(), h)
            paint.color = c.line
            canvas.drawRoundRect(r, h / 2, h / 2, paint)
            if (progress > 0f) {
                r.right = width * progress
                paint.color = if (progress >= 1f) c.accent else c.fg
                canvas.drawRoundRect(r, h / 2, h / 2, paint)
            }
        }
    }

    /** Couleurs : noir et blanc, une seule couleur d'accent. */
    private class Palette(val dark: Boolean, val bg: Int, val fg: Int, val muted: Int, val line: Int, val accent: Int) {
        companion object {
            fun of(ctx: Context): Palette {
                val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
                return if (dark) {
                    Palette(true, 0xFF0E0E0E.toInt(), 0xFFF2F2F2.toInt(), 0xFF8A8A8A.toInt(), 0xFF2A2A2A.toInt(), 0xFFFF5A36.toInt())
                } else {
                    Palette(false, 0xFFFAFAF7.toInt(), 0xFF111111.toInt(), 0xFF8A8A8A.toInt(), 0xFFE6E6E1.toInt(), 0xFFFF5A36.toInt())
                }
            }
        }
    }
}

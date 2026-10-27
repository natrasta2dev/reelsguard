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
import android.view.View
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
    private lateinit var limitValue: TextView
    private lateinit var pendingNote: TextView
    private lateinit var enableButton: TextView

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
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(c.bg)
            setPadding(dp(32), dp(56), dp(32), dp(40))
        }

        // Titre discret (appui long = diagnostic)
        root.addView(text("REELSGUARD", 12f, c.muted, medium).apply {
            letterSpacing = 0.25f
            setOnLongClickListener { showDiagnostics(); true }
        })

        root.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        bigNumber = text("", 96f, c.fg, light)
        root.addView(bigNumber)
        bigCaption = text("", 15f, c.muted, regular)
        root.addView(bigCaption)

        bar = ProgressLine(this, c)
        root.addView(bar, LinearLayout.LayoutParams(dp(220), dp(6)).apply { topMargin = dp(36) })

        usage = text("", 13f, c.muted, regular).apply { setPadding(0, dp(12), 0, 0) }
        root.addView(usage)

        root.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        // Réglage de la limite
        root.addView(text("Limite par jour", 13f, c.muted, regular))
        val stepper = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        stepper.addView(roundButton("−") { step(-STEP) })
        limitValue = text("", 22f, c.fg, medium).apply { minWidth = dp(120) }
        stepper.addView(limitValue)
        stepper.addView(roundButton("+") { step(+STEP) })
        root.addView(stepper)

        pendingNote = text("", 12f, c.muted, regular).apply { setPadding(0, dp(10), 0, 0) }
        root.addView(pendingNote)

        enableButton = text("Activer le service", 15f, Color.WHITE, medium).apply {
            background = GradientDrawable().apply { setColor(c.accent); cornerRadius = dp(28).toFloat() }
            setPadding(dp(28), dp(16), dp(28), dp(16))
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        root.addView(enableButton, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(32) })

        setContentView(root)
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

    private fun step(delta: Int) {
        val next = (targetLimit() + delta).coerceIn(0, MAX)
        store.setLimit(next)
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

        limitValue.text = "${targetLimit()} min"
        val pending = store.pendingLimitMinutes
        pendingNote.text = if (pending >= 0) "Appliquée demain" else ""

        enableButton.visibility = if (isServiceEnabled()) View.GONE else View.VISIBLE
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

    private fun roundButton(label: String, onClick: () -> Unit) = text(label, 22f, c.fg, regular).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(1), c.line)
        }
        layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
        setOnClickListener { onClick() }
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

    companion object {
        private const val STEP = 5
        private const val MAX = 24 * 60
    }
}

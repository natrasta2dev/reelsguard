package fr.codapps.reelsguard

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Couleurs : noir et blanc, une seule couleur d'accent. */
class Palette(val dark: Boolean, val bg: Int, val fg: Int, val muted: Int, val line: Int, val accent: Int) {
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

/** Petites briques d'interface partagées par les écrans. */
class Ui(private val a: Activity) {

    val c = Palette.of(a)

    val light: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    val regular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun dp(v: Int) = (v * a.resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    fun applyWindow() {
        a.window.statusBarColor = c.bg
        a.window.navigationBarColor = c.bg
        if (!c.dark) a.window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }

    fun column() = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(24), dp(48), dp(24), dp(48))
    }

    fun screen(content: View) = ScrollView(a).apply {
        isFillViewport = true
        setBackgroundColor(c.bg)
        addView(content, -1, -1)
    }

    fun text(s: String, size: Float, color: Int, face: Typeface) = TextView(a).apply {
        text = s
        textSize = size
        setTextColor(color)
        typeface = face
        gravity = Gravity.CENTER
        includeFontPadding = false
    }

    /** Titre de section, discret. */
    fun section(label: String) = text(label, 13f, c.muted, regular)

    fun pill(label: String, filled: Int?, textColor: Int, onClick: () -> Unit) =
        text(label, 15f, textColor, medium).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                if (filled != null) setColor(filled) else setStroke(dp(1), c.line)
            }
            setPadding(dp(28), dp(14), dp(28), dp(14))
            setOnClickListener { onClick() }
        }

    /** Puce sélectionnable : pleine quand active, contour quand inactive. */
    fun chip(label: String, round: Boolean = false, onClick: () -> Unit) =
        text(label, 14f, c.fg, medium).apply {
            background = GradientDrawable().apply {
                if (round) shape = GradientDrawable.OVAL else cornerRadius = dp(20).toFloat()
            }
            if (!round) setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener { onClick() }
        }

    fun styleChip(chip: TextView, on: Boolean) {
        (chip.background as GradientDrawable).apply {
            if (on) { setColor(c.fg); setStroke(0, Color.TRANSPARENT) }
            else { setColor(Color.TRANSPARENT); setStroke(dp(1), c.line) }
        }
        chip.setTextColor(if (on) c.bg else c.muted)
    }

    fun row() = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    /** Lien souligné discret. */
    fun link(label: String, onClick: () -> Unit) = text(label, 13f, c.muted, regular).apply {
        paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setOnClickListener { onClick() }
    }

    /** Champ numérique arrondi. */
    fun numberField(hint: String, size: Float = 18f) = EditText(a).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        gravity = Gravity.CENTER
        textSize = size
        typeface = medium
        setTextColor(c.fg)
        setHintTextColor(c.muted)
        this.hint = hint
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), c.line)
        }
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    /** Ajoute la vue centrée horizontalement, avec une marge au-dessus. */
    fun centered(v: View, top: Int = 0): View = v.apply {
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(top)
        }
    }

    fun lp(w: Int, h: Int, left: Int = 0, top: Int = 0) = LinearLayout.LayoutParams(w, h).apply {
        leftMargin = dp(left); topMargin = dp(top)
    }

    /** Pied de page : « Made by Cod'Apps » (lien codapps.fr), mentions légales, version. */
    fun footer(): View {
        val col = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val made = row()
        made.addView(text("Made by ", 12f, c.muted, regular))
        made.addView(text("Cod'Apps", 12f, c.fg, medium).apply {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            setPadding(0, dp(8), 0, dp(8))
            setOnClickListener { open(Legal.WEBSITE) }
        })
        col.addView(made)
        val links = row()
        links.addView(text("Mentions légales", 11f, c.muted, regular).apply {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            setPadding(dp(8), dp(4), dp(8), dp(8))
            setOnClickListener { a.startActivity(android.content.Intent(a, LegalActivity::class.java)) }
        })
        links.addView(text("·  v${UpdateChecker.currentVersion()}", 11f, c.muted, regular).apply {
            setPadding(0, dp(4), 0, dp(8))
        })
        col.addView(links)
        return col
    }

    fun open(url: String) {
        runCatching {
            a.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        }
    }

    /** Active / désactive une vue et tout son contenu (mode strict). */
    fun setEnabledDeep(v: View, on: Boolean) {
        v.isEnabled = on
        if (v is ViewGroup) for (i in 0 until v.childCount) setEnabledDeep(v.getChildAt(i), on)
    }
}

/** Fine barre de progression arrondie. */
class ProgressLine(ctx: Context, private val c: Palette) : View(ctx) {
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

/** Histogramme des 7 derniers jours, avec la limite en pointillés. */
class HistoryChart(ctx: Context, private val c: Palette) : View(ctx) {
    var days: List<UsageStore.Day> = emptyList()
        set(v) { field = v; invalidate() }

    private val d = ctx.resources.displayMetrics.density
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.line }
    private val limitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * d
        pathEffect = DashPathEffect(floatArrayOf(4 * d, 4 * d), 0f)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11 * d
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val r = RectF()
    private val letters = arrayOf("L", "M", "M", "J", "V", "S", "D")

    override fun onDraw(canvas: Canvas) {
        if (days.isEmpty()) return
        val labelH = 20 * d
        val chartH = height - labelH
        val maxMin = maxOf(days.maxOf { it.usedMs / 60_000f }, days.maxOf { it.limitMin.toFloat() }, 1f)
        val slot = width / days.size.toFloat()
        val barW = minOf(slot * 0.42f, 18 * d)

        days.forEachIndexed { i, day ->
            val cx = slot * i + slot / 2
            val used = day.usedMs / 60_000f
            val h = (used / maxMin) * (chartH - 4 * d)
            val isToday = i == days.lastIndex
            val over = day.limitMin > 0 && used >= day.limitMin || day.limitMin == 0 && used > 0
            bar.color = when {
                over -> c.accent
                isToday -> c.fg
                else -> c.muted
            }
            // trace de fond
            r.set(cx - barW / 2, 0f, cx + barW / 2, chartH)
            canvas.drawRoundRect(r, barW / 2, barW / 2, trackPaint)
            if (h > 0) {
                r.set(cx - barW / 2, chartH - maxOf(h, barW), cx + barW / 2, chartH)
                canvas.drawRoundRect(r, barW / 2, barW / 2, bar)
            }
            // limite du jour
            val ly = chartH - (day.limitMin / maxMin) * (chartH - 4 * d)
            limitPaint.color = c.fg
            limitPaint.alpha = 90
            canvas.drawLine(cx - slot * 0.4f, ly, cx + slot * 0.4f, ly, limitPaint)

            label.color = if (isToday) c.fg else c.muted
            canvas.drawText(letters[day.date.dayOfWeek.value - 1], cx, height - 4 * d, label)
        }
    }
}

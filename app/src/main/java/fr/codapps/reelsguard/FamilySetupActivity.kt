package fr.codapps.reelsguard

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.CheckBox
import android.widget.LinearLayout

/**
 * Premier lancement de l'édition Famille : accord du parent, puis création du code parent.
 * Tant que ce n'est pas fait, l'application n'affiche que cet écran.
 */
class FamilySetupActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = Ui(this)
        val c = ui.c
        ui.applyWindow()

        val col = ui.column()
        col.addView(ui.centered(ui.text("REELSGUARD FAMILLE", 12f, c.muted, ui.medium).apply { letterSpacing = 0.25f }))
        col.addView(ui.centered(ui.text("Bienvenue", 34f, c.fg, ui.light), top = 32))
        col.addView(ui.centered(ui.text(
            "Cette édition sert au contrôle parental. Elle se règle par un parent, avec un code que l'enfant ne connaît pas.",
            14f, c.muted, ui.regular).apply { setLineSpacing(0f, 1.25f) }, top = 12))

        val points = listOf(
            "Tout reste sur ce téléphone : pas de compte, pas de serveur, aucune donnée envoyée.",
            "L'application reste visible : une notification « Contrôle parental actif » est affichée en permanence.",
            "Explique à ton enfant ce que fait l'application et pourquoi tu l'installes.",
        )
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = ui.dp(20).toFloat(); setStroke(ui.dp(1), c.line) }
            setPadding(ui.dp(20), ui.dp(18), ui.dp(20), ui.dp(4))
        }
        points.forEach { p ->
            box.addView(ui.text("•  $p", 14f, c.fg, ui.regular).apply {
                gravity = Gravity.START; setLineSpacing(0f, 1.25f); setPadding(0, 0, 0, ui.dp(14))
            })
        }
        col.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(28) })

        val check = CheckBox(this).apply {
            text = "Je suis le parent ou le responsable légal de l'enfant qui utilise cet appareil, " +
                "et je l'ai informé de l'installation de ReelsGuard Famille."
            setTextColor(c.fg)
            textSize = 14f
            buttonTintList = android.content.res.ColorStateList.valueOf(c.accent)
        }
        col.addView(check, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(24) })

        val go = ui.pill("Continuer et créer le code parent", filled = c.accent, textColor = Color.WHITE) {
            if (!check.isChecked) return@pill
            ParentLock.giveConsent(this)
            PinDialog.create(this) { finish() }
        }
        go.alpha = 0.4f
        check.setOnCheckedChangeListener { _, on -> go.alpha = if (on) 1f else 0.4f }
        col.addView(ui.centered(go, top = 24))

        col.addView(ui.footer(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(40) })
        setContentView(ui.screen(col))
    }

    @Deprecated("Impossible de quitter la configuration sans la terminer")
    override fun onBackPressed() { moveTaskToBack(true) }
}

package fr.codapps.reelsguard

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast

/**
 * Édition Famille : l'enfant demande plus de temps, le parent accorde 15, 30 ou 60 minutes
 * en tapant son code sur le téléphone. Le temps bonus vaut pour la journée en cours.
 */
class RequestTimeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = Ui(this)
        val c = ui.c
        ui.applyWindow()
        val store = UsageStore(this)
        store.logEvent(UsageStore.EV_REQUEST)

        val col = ui.column().apply { gravity = android.view.Gravity.CENTER }
        col.addView(ui.centered(ui.text("REELSGUARD FAMILLE", 12f, c.muted, ui.medium).apply { letterSpacing = 0.25f }))
        col.addView(ui.centered(ui.text("Plus de temps ?", 34f, c.fg, ui.light), top = 40))
        col.addView(ui.centered(ui.text(
            "Montre cet écran à un parent : il choisit combien de temps t'accorder et tape son code.",
            15f, c.muted, ui.regular).apply { setLineSpacing(0f, 1.25f) }, top = 14))

        val bonus = store.bonusToday()
        if (bonus > 0) col.addView(ui.centered(ui.text("Déjà accordé aujourd'hui : $bonus min", 13f, c.muted, ui.regular), top = 16))

        listOf(15, 30, 60).forEachIndexed { i, m ->
            col.addView(ui.centered(ui.pill("+ $m min", filled = if (i == 0) c.accent else null,
                textColor = if (i == 0) Color.WHITE else c.fg) {
                PinDialog.require(this, "Accorder $m min") {
                    store.addBonus(m)
                    ParentLock.closeSession(this) // le parent n'a pas besoin de rester connecté
                    RemainingWidget.update(this)
                    Toast.makeText(this, "$m min ajoutées pour aujourd'hui", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }, top = if (i == 0) 40 else 12))
        }
        col.addView(ui.centered(ui.link("Annuler") { finish() }, top = 20))
        col.addView(ui.footer(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(40) })
        setContentView(ui.screen(col))
    }
}

package fr.codapps.reelsguard

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout

/** Mentions légales, confidentialité et licence. */
class LegalActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = Ui(this)
        val c = ui.c
        ui.applyWindow()

        val root = ui.column()
        root.addView(ui.centered(ui.text("MENTIONS LÉGALES", 12f, c.muted, ui.medium).apply { letterSpacing = 0.25f }))
        root.addView(ui.centered(ui.link("‹ Retour") { finish() }, top = 4))

        Legal.sections().forEach { section ->
            root.addView(ui.text(section.title, 16f, c.fg, ui.medium).apply { gravity = Gravity.START },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(28) })
            root.addView(ui.text(section.body, 13f, c.muted, ui.regular).apply {
                gravity = Gravity.START
                setLineSpacing(0f, 1.25f)
                setTextIsSelectable(true)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8) })
        }

        root.addView(ui.centered(ui.link("Code source sur GitHub") { ui.open(UpdateChecker.REPO_URL) }, top = 32))
        root.addView(ui.centered(ui.link("Texte de la licence") { ui.open(Legal.LICENSE_URL) }))
        root.addView(ui.centered(ui.link("Vérifier les mises à jour") {
            UpdateChecker.check(this, force = true) {
                val k = UpdateChecker.kind(this)
                android.widget.Toast.makeText(
                    this,
                    if (k == UpdateChecker.Kind.NONE) "Tu as la dernière version" else "Une mise à jour est disponible",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }))
        root.addView(ui.footer(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(32) })

        setContentView(ui.screen(root))
    }
}

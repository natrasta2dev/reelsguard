package fr.codapps.reelsguard

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Fenêtres de saisie du code parent (vérifier, créer, récupérer). */
object PinDialog {

    private fun field(a: Activity, hint: String, numeric: Boolean = true) = EditText(a).apply {
        inputType = if (numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        this.hint = hint
        gravity = Gravity.CENTER
        textSize = 24f
    }

    private fun box(a: Activity, vararg views: android.view.View) = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        val p = (20 * a.resources.displayMetrics.density).toInt()
        setPadding(p, p / 2, p, 0)
        views.forEach { addView(it) }
    }

    private fun toast(a: Activity, s: String) = Toast.makeText(a, s, Toast.LENGTH_LONG).show()

    private fun lockedMsg(a: Activity): String {
        val s = (ParentLock.lockedForMs(a) / 1000).toInt()
        return "Trop d'essais. Réessaie dans ${s / 60}:${String.format("%02d", s % 60)}."
    }

    /** Demande le code parent ; [onOk] est appelé si la session parent est (ou était déjà) ouverte. */
    fun require(a: Activity, reason: String = "Code parent", onCancel: (() -> Unit)? = null, onOk: () -> Unit) {
        if (ParentLock.sessionActive(a)) { onOk(); return }
        if (ParentLock.lockedForMs(a) > 0) { toast(a, lockedMsg(a)); onCancel?.invoke(); return }
        val f = field(a, "Code parent")
        AlertDialog.Builder(a)
            .setTitle(reason)
            .setView(box(a, f))
            .setPositiveButton("Valider") { _, _ ->
                when (ParentLock.checkPin(a, f.text.toString())) {
                    ParentLock.Result.OK -> onOk()
                    ParentLock.Result.LOCKED -> { toast(a, lockedMsg(a)); onCancel?.invoke() }
                    ParentLock.Result.WRONG -> {
                        toast(a, if (ParentLock.lockedForMs(a) > 0) lockedMsg(a) else "Code incorrect")
                        onCancel?.invoke()
                    }
                }
            }
            .setNeutralButton("Code oublié ?") { _, _ -> recover(a, onOk) }
            .setNegativeButton("Annuler") { _, _ -> onCancel?.invoke() }
            .setOnCancelListener { onCancel?.invoke() }
            .show()
    }

    /** Création (ou changement) du code parent ; affiche ensuite le code de secours. */
    fun create(a: Activity, title: String = "Crée le code parent", onDone: () -> Unit) {
        val f1 = field(a, "4 à 8 chiffres")
        val f2 = field(a, "Confirme le code")
        val info = TextView(a).apply {
            text = "Ce code protège les réglages, la désactivation et la désinstallation. Ne le donne pas à l'enfant."
            textSize = 13f
        }
        AlertDialog.Builder(a)
            .setTitle(title)
            .setView(box(a, info, f1, f2))
            .setCancelable(false)
            .setPositiveButton("Enregistrer") { _, _ ->
                val p1 = f1.text.toString(); val p2 = f2.text.toString()
                when {
                    !ParentLock.isValidPin(p1) -> { toast(a, "Le code doit faire 4 à 8 chiffres"); create(a, title, onDone) }
                    p1 != p2 -> { toast(a, "Les deux codes ne sont pas identiques"); create(a, title, onDone) }
                    else -> showRecovery(a, ParentLock.setPin(a, p1), onDone)
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showRecovery(a: Activity, code: String, onDone: () -> Unit) {
        val t = TextView(a).apply {
            text = code
            textSize = 30f
            gravity = Gravity.CENTER
            setTextIsSelectable(true)
            letterSpacing = 0.08f
        }
        val info = TextView(a).apply {
            text = "Note ce code de secours et garde-le en lieu sûr. Il permet de choisir un nouveau code parent " +
                "si tu oublies le tien. Il ne sera plus jamais affiché."
            textSize = 13f
        }
        AlertDialog.Builder(a)
            .setTitle("Code de secours")
            .setView(box(a, info, t))
            .setCancelable(false)
            .setPositiveButton("Je l'ai noté") { _, _ -> onDone() }
            .show()
    }

    private fun recover(a: Activity, onOk: () -> Unit) {
        val f = field(a, "XXXX-XXXX-XXXX", numeric = false)
        AlertDialog.Builder(a)
            .setTitle("Code de secours")
            .setView(box(a, f))
            .setPositiveButton("Valider") { _, _ ->
                when (ParentLock.checkRecovery(a, f.text.toString())) {
                    ParentLock.Result.OK -> create(a, "Nouveau code parent") { onOk() }
                    ParentLock.Result.LOCKED -> toast(a, lockedMsg(a))
                    ParentLock.Result.WRONG -> toast(a, "Code de secours incorrect")
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}

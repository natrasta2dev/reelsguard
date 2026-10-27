package fr.codapps.reelsguard

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Mode diagnostic : quand il est activé, le service note ce qu'il voit à l'écran
 * (identifiants, libellés, positions) dans l'app surveillée. Le texte peut ensuite
 * être partagé depuis l'écran principal pour ajuster la détection.
 */
object Diagnostics {

    private const val PREFS = "reelsguard_diag"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_STATUS = "status"
    private const val KEY_DUMP = "dump"
    private const val MAX_LINES = 400

    fun isEnabled(ctx: Context) = prefs(ctx).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, on: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_ENABLED, on).apply()

    /** Dernier état détecté, toujours mis à jour (diagnostic actif ou non). */
    fun status(ctx: Context): String = prefs(ctx).getString(KEY_STATUS, null) ?: "Aucune détection pour l'instant"

    fun setStatus(ctx: Context, app: String, watching: Boolean, tabFound: Boolean) {
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        val s = "$time · $app · vidéos courtes : ${if (watching) "OUI" else "non"} · bouton trouvé : ${if (tabFound) "oui" else "non"}"
        prefs(ctx).edit().putString(KEY_STATUS, s).apply()
    }

    fun dump(ctx: Context): String = prefs(ctx).getString(KEY_DUMP, null) ?: ""

    fun saveDump(ctx: Context, header: String, root: AccessibilityNodeInfo) {
        val lines = mutableListOf(header)
        walk(root, 0, lines)
        prefs(ctx).edit().putString(KEY_DUMP, lines.joinToString("\n")).apply()
    }

    private fun walk(node: AccessibilityNodeInfo?, depth: Int, out: MutableList<String>) {
        if (node == null || out.size >= MAX_LINES || depth > 40) return
        if (node.isVisibleToUser) {
            val id = node.viewIdResourceName?.substringAfter(":id/") ?: "-"
            val label = (node.text ?: node.contentDescription)?.toString()?.replace('\n', ' ')?.take(40) ?: ""
            val r = Rect().also { node.getBoundsInScreen(it) }
            val flags = buildString {
                if (node.isClickable) append("C")
                if (node.isSelected) append("S")
                if (node.isScrollable) append("Sc")
            }
            if (id != "-" || label.isNotEmpty() || flags.isNotEmpty()) {
                out += "${" ".repeat(depth.coerceAtMost(20))}$id \"$label\" [$flags] ${r.toShortString()}"
            }
        }
        for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1, out)
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

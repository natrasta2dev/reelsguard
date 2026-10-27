package com.perso.reelsguard

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Écran de réglage : limite quotidienne, temps restant, activation du service. */
class MainActivity : Activity() {

    private lateinit var store: UsageStore
    private lateinit var status: TextView
    private lateinit var remaining: TextView
    private lateinit var limitInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = UsageStore(this)

        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "ReelsGuard"
            textSize = 28f
        })

        status = TextView(this).apply { textSize = 16f; setPadding(0, pad / 2, 0, 0) }
        root.addView(status)

        remaining = TextView(this).apply { textSize = 22f; setPadding(0, pad, 0, pad) }
        root.addView(remaining)

        root.addView(TextView(this).apply { text = "Limite quotidienne commune Reels + Shorts + TikTok (minutes, 0 = toujours bloqué)" })

        limitInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(limitInput)

        root.addView(Button(this).apply {
            text = "Enregistrer la limite"
            setOnClickListener { saveLimit() }
        })

        root.addView(Button(this).apply {
            text = "Activer / vérifier le service d'accessibilité"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        root.addView(TextView(this).apply {
            text = "Baisser la limite s'applique tout de suite. L'augmenter ne compte qu'à partir de demain, " +
                "pour éviter de craquer en cours de journée."
            setPadding(0, pad, 0, 0)
            alpha = 0.7f
        })

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun saveLimit() {
        val minutes = limitInput.text.toString().toIntOrNull()
        if (minutes == null) {
            Toast.makeText(this, "Entre un nombre de minutes", Toast.LENGTH_SHORT).show()
            return
        }
        val now = store.setLimit(minutes)
        Toast.makeText(
            this,
            if (now) "Limite appliquée" else "Nouvelle limite active demain",
            Toast.LENGTH_SHORT
        ).show()
        refresh()
    }

    private fun refresh() {
        status.text = if (isServiceEnabled()) "✅ Service actif" else "⚠️ Service désactivé — active-le ci-dessous"

        val left = store.remainingMs
        val usedMin = store.usedMs / 60_000
        remaining.text = if (left <= 0) {
            "Vidéos courtes bloquées jusqu'à demain\n(utilisé : $usedMin min)"
        } else {
            "Restant aujourd'hui : ${left / 60_000} min ${(left / 1000) % 60} s\n" +
                "Utilisé : $usedMin / ${store.limitMinutes} min"
        }

        val pending = store.pendingLimitMinutes
        limitInput.setText(store.limitMinutes.toString())
        if (pending >= 0) remaining.append("\nDemain : $pending min")
    }

    private fun isServiceEnabled(): Boolean {
        val expected = ComponentName(this, ReelsGuardService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}

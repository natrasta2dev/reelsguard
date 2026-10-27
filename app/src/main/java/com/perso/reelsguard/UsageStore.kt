package com.perso.reelsguard

import android.content.Context
import java.time.LocalDate

/**
 * Stocke la limite quotidienne et le temps déjà consommé sur les Reels.
 * Le compteur repart à zéro automatiquement chaque jour (minuit, heure du téléphone).
 *
 * Anti-triche simple : baisser la limite s'applique tout de suite,
 * l'augmenter ne prend effet que le lendemain.
 */
class UsageStore(context: Context) {

    private val prefs = context.getSharedPreferences("reelsguard", Context.MODE_PRIVATE)

    private fun today(): String = LocalDate.now().toString()

    /** Remet le compteur à zéro et applique une limite en attente si on a changé de jour. */
    private fun rolloverIfNeeded() {
        val day = prefs.getString(KEY_DAY, null)
        if (day != today()) {
            val editor = prefs.edit()
                .putString(KEY_DAY, today())
                .putLong(KEY_USED_MS, 0L)
            val pending = prefs.getInt(KEY_PENDING_LIMIT, -1)
            if (pending >= 0) {
                editor.putInt(KEY_LIMIT_MIN, pending).remove(KEY_PENDING_LIMIT)
            }
            editor.apply()
        }
    }

    /** Limite du jour en minutes (0 = Reels toujours bloqués). */
    val limitMinutes: Int
        get() {
            rolloverIfNeeded()
            return prefs.getInt(KEY_LIMIT_MIN, DEFAULT_LIMIT_MIN)
        }

    /** Limite qui s'appliquera demain, ou -1 s'il n'y en a pas. */
    val pendingLimitMinutes: Int
        get() {
            rolloverIfNeeded()
            return prefs.getInt(KEY_PENDING_LIMIT, -1)
        }

    val usedMs: Long
        get() {
            rolloverIfNeeded()
            return prefs.getLong(KEY_USED_MS, 0L)
        }

    val remainingMs: Long
        get() = (limitMinutes * 60_000L - usedMs).coerceAtLeast(0L)

    val isLimitReached: Boolean
        get() = remainingMs <= 0L

    fun addUsage(deltaMs: Long) {
        if (deltaMs <= 0) return
        rolloverIfNeeded()
        prefs.edit().putLong(KEY_USED_MS, usedMs + deltaMs).apply()
    }

    /**
     * Change la limite. Retourne true si elle s'applique tout de suite,
     * false si elle est programmée pour demain (cas d'une augmentation).
     */
    fun setLimit(minutes: Int): Boolean {
        rolloverIfNeeded()
        val m = minutes.coerceIn(0, 24 * 60)
        return if (m <= limitMinutes) {
            prefs.edit().putInt(KEY_LIMIT_MIN, m).remove(KEY_PENDING_LIMIT).apply()
            true
        } else {
            prefs.edit().putInt(KEY_PENDING_LIMIT, m).apply()
            false
        }
    }

    companion object {
        private const val KEY_DAY = "day"
        private const val KEY_USED_MS = "used_ms"
        private const val KEY_LIMIT_MIN = "limit_min"
        private const val KEY_PENDING_LIMIT = "pending_limit_min"
        const val DEFAULT_LIMIT_MIN = 60
    }
}

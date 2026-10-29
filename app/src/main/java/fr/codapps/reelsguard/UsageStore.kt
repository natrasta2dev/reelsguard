package fr.codapps.reelsguard

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Temps passé et limites.
 *  - Temps enregistré par jour, au total et par app (historique sur 30 jours).
 *  - Limite « semaine » et limite « week-end » (week-end vide = même que la semaine).
 *  - Quota propre à chaque app (0 = pas de quota propre), inclus dans la limite commune.
 *
 * Anti-triche : baisser une limite s'applique tout de suite, l'augmenter seulement le lendemain.
 */
class UsageStore(context: Context) {

    private val prefs = context.getSharedPreferences("reelsguard", Context.MODE_PRIVATE)

    init {
        if (!prefs.contains(KEY_INSTALL)) prefs.edit().putString(KEY_INSTALL, today().toString()).apply()
        migrateV1()
        migrateQuotas()
    }

    private fun today(): LocalDate = LocalDate.now()

    private fun isWeekend(d: LocalDate) = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY

    /** Applique les hausses de limite en attente quand on change de jour, et purge l'historique. */
    private fun rolloverIfNeeded() {
        val t = today().toString()
        if (prefs.getString(KEY_DAY, null) == t) return
        val e = prefs.edit().putString(KEY_DAY, t)
        for (k in listOf(KEY_WD, KEY_WE)) {
            val pending = prefs.getInt("pending_$k", Int.MIN_VALUE)
            if (pending != Int.MIN_VALUE) e.putInt(k, pending).remove("pending_$k")
        }
        val oldest = today().minusDays(HISTORY_DAYS.toLong())
        prefs.all.keys.filter { it.startsWith("u_") || it.startsWith("l_") }.forEach { key ->
            val date = runCatching { LocalDate.parse(key.substring(2, 12)) }.getOrNull()
            if (date != null && date.isBefore(oldest)) e.remove(key)
        }
        e.apply()
    }

    // ---------- limites ----------

    /** Limite en semaine (minutes). */
    val weekdayLimit: Int get() { rolloverIfNeeded(); return prefs.getInt(KEY_WD, DEFAULT_LIMIT_MIN) }

    /** Limite du week-end, ou -1 si identique à la semaine. */
    val weekendLimit: Int get() { rolloverIfNeeded(); return prefs.getInt(KEY_WE, -1) }

    /** Hausse en attente pour demain, ou null. Pour le week-end, -1 = « comme la semaine ». */
    fun pending(weekend: Boolean): Int? {
        val k = "pending_" + if (weekend) KEY_WE else KEY_WD
        return if (prefs.contains(k)) prefs.getInt(k, 0) else null
    }

    fun limitFor(d: LocalDate): Int =
        if (isWeekend(d) && weekendLimit >= 0) weekendLimit else weekdayLimit

    val limitMinutes: Int get() = limitFor(today())

    /**
     * Change une limite (weekend = true pour le week-end ; -1 = « comme la semaine »).
     * Retourne true si appliquée tout de suite, false si programmée pour demain.
     */
    fun setLimit(minutes: Int, weekend: Boolean): Boolean {
        rolloverIfNeeded()
        val key = if (weekend) KEY_WE else KEY_WD
        val m = if (weekend && minutes < 0) -1 else minutes.coerceIn(0, 24 * 60)
        val current = if (weekend) weekendLimit else weekdayLimit
        val effCurrent = if (weekend && current < 0) weekdayLimit else current
        val effNew = if (weekend && m < 0) weekdayLimit else m
        return if (effNew <= effCurrent) {
            prefs.edit().putInt(key, m).remove("pending_$key").apply(); true
        } else {
            prefs.edit().putInt("pending_$key", m).apply(); false
        }
    }

    // ---------- quota par app ----------

    /** Minuteur propre à l'app : -1 = aucun (seule la limite commune compte), 0 = app bloquée. */
    fun appQuota(rule: AppRule): Int = prefs.getInt("q_${rule.name}", -1)

    fun setAppQuota(rule: AppRule, minutes: Int) =
        prefs.edit().putInt("q_${rule.name}", if (minutes < 0) -1 else minutes.coerceAtMost(24 * 60)).apply()

    /** Avant, 0 voulait dire « aucun minuteur » : on convertit une seule fois les anciennes valeurs. */
    private fun migrateQuotas() {
        if (prefs.getBoolean("quota_v2", false)) return
        val e = prefs.edit()
        prefs.all.forEach { (k, v) -> if (k.startsWith("q_") && v == 0) e.putInt(k, -1) }
        e.putBoolean("quota_v2", true).apply()
    }

    // ---------- temps utilisé ----------

    private fun usedKey(d: LocalDate, rule: AppRule? = null) = "u_$d" + (rule?.let { "_${it.name}" } ?: "")

    fun usedOn(d: LocalDate, rule: AppRule? = null): Long = prefs.getLong(usedKey(d, rule), 0L)

    val usedMs: Long get() { rolloverIfNeeded(); return usedOn(today()) }

    fun usedToday(rule: AppRule): Long = usedOn(today(), rule)

    fun addUsage(rule: AppRule, deltaMs: Long) {
        if (deltaMs <= 0) return
        rolloverIfNeeded()
        val d = today()
        prefs.edit()
            .putLong(usedKey(d), usedOn(d) + deltaMs)
            .putLong(usedKey(d, rule), usedOn(d, rule) + deltaMs)
            .putInt("l_$d", limitFor(d))
            .apply()
    }

    /** Temps restant sur la limite commune. */
    val remainingMs: Long get() = (limitMinutes * 60_000L - usedMs).coerceAtLeast(0L)

    val isLimitReached: Boolean get() = remainingMs <= 0L

    /** Temps restant pour une app : le plus petit entre la limite commune et son quota propre. */
    fun remainingFor(rule: AppRule): Long {
        val q = appQuota(rule)
        if (q < 0) return remainingMs
        if (q == 0) return 0L
        return minOf(remainingMs, (q * 60_000L - usedToday(rule)).coerceAtLeast(0L))
    }

    // ---------- historique et série ----------

    data class Day(val date: LocalDate, val usedMs: Long, val limitMin: Int)

    /** Les [n] derniers jours, du plus ancien à aujourd'hui. */
    fun history(n: Int = 7): List<Day> = (n - 1 downTo 0).map { i ->
        val d = today().minusDays(i.toLong())
        Day(d, usedOn(d), prefs.getInt("l_$d", limitFor(d)))
    }

    /** Jours consécutifs, d'hier en remontant, passés sous la limite (depuis l'installation). */
    fun streak(): Int {
        val install = runCatching { LocalDate.parse(prefs.getString(KEY_INSTALL, null)) }.getOrNull() ?: today()
        var d = today().minusDays(1)
        var count = 0
        while (!d.isBefore(install) && count < 365) {
            val limit = prefs.getInt("l_$d", limitFor(d))
            if (usedOn(d) >= limit * 60_000L) break
            count++
            d = d.minusDays(1)
        }
        return count
    }

    /** Reprend les réglages de la version précédente (limite unique, compteur du jour). */
    private fun migrateV1() {
        if (prefs.contains("limit_min") && !prefs.contains(KEY_WD)) {
            prefs.edit().putInt(KEY_WD, prefs.getInt("limit_min", DEFAULT_LIMIT_MIN)).remove("limit_min").apply()
        }
        if (prefs.contains("used_ms")) {
            val e = prefs.edit()
            if (prefs.getString(KEY_DAY, null) == today().toString()) {
                e.putLong(usedKey(today()), prefs.getLong("used_ms", 0L))
            }
            e.remove("used_ms").apply()
        }
    }

    companion object {
        private const val KEY_DAY = "day"
        private const val KEY_INSTALL = "install"
        private const val KEY_WD = "limit_wd"
        private const val KEY_WE = "limit_we"
        private const val HISTORY_DAYS = 30
        const val DEFAULT_LIMIT_MIN = 60
    }
}

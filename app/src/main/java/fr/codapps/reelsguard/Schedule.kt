package fr.codapps.reelsguard

import android.content.Context
import java.time.LocalDate
import java.time.LocalTime

/**
 * Réglages de planning et de comportement :
 *  - apps surveillées, jours actifs ;
 *  - plages horaires (aucune = toute la journée) où les vidéos courtes sont autorisées ;
 *  - pause forcée, délai avant ouverture, mode strict.
 */
class Schedule(context: Context) {

    private val prefs = context.getSharedPreferences("reelsguard_schedule", Context.MODE_PRIVATE)

    init { migrateV1() }

    // ---------- apps ----------

    fun isAppEnabled(rule: AppRule) = prefs.getBoolean("app_${rule.name}", true)

    fun setAppEnabled(rule: AppRule, on: Boolean) = prefs.edit().putBoolean("app_${rule.name}", on).apply()

    // ---------- jours (1 = lundi … 7 = dimanche) ----------

    val activeDays: Set<Int>
        get() = prefs.getString(KEY_DAYS, "1234567")!!.mapNotNull { it.digitToIntOrNull() }.toSet()

    fun toggleDay(day: Int) {
        val days = activeDays.toMutableSet()
        if (!days.add(day)) days.remove(day)
        prefs.edit().putString(KEY_DAYS, days.sorted().joinToString("")).apply()
    }

    fun isActiveToday() = LocalDate.now().dayOfWeek.value in activeDays

    // ---------- plages horaires (minutes depuis minuit) ----------

    data class Window(val start: Int, val end: Int) {
        fun contains(m: Int) = if (start < end) m in start until end else m >= start || m < end
        override fun toString() = "${fmt(start)} → ${fmt(end)}"
    }

    val windows: List<Window>
        get() = prefs.getString(KEY_WINDOWS, "")!!.split(',').mapNotNull { part ->
            val (a, b) = part.split('-').takeIf { it.size == 2 } ?: return@mapNotNull null
            val s = a.toIntOrNull() ?: return@mapNotNull null
            val e = b.toIntOrNull() ?: return@mapNotNull null
            if (s == e) null else Window(s, e)
        }

    private fun saveWindows(list: List<Window>) =
        prefs.edit().putString(KEY_WINDOWS, list.joinToString(",") { "${it.start}-${it.end}" }).apply()

    fun addWindow(w: Window) = saveWindows(windows + w)
    fun replaceWindow(index: Int, w: Window) = saveWindows(windows.toMutableList().also { it[index] = w })
    fun removeWindow(index: Int) = saveWindows(windows.toMutableList().also { it.removeAt(index) })

    val isAllDay get() = windows.isEmpty()

    fun isInWindow(now: LocalTime = LocalTime.now()): Boolean {
        val list = windows
        if (list.isEmpty()) return true
        val m = now.hour * 60 + now.minute
        return list.any { it.contains(m) }
    }

    /** Texte « 08:00 → 16:00, 19:00 → 21:00 ». */
    fun windowsLabel() = if (isAllDay) "toute la journée" else windows.joinToString(", ")

    // ---------- pause forcée ----------

    var pauseEnabled: Boolean
        get() = prefs.getBoolean("pause_on", true)
        set(v) = prefs.edit().putBoolean("pause_on", v).apply()

    /** Minutes de visionnage continu avant la pause. */
    var pauseAfterMin: Int
        get() = prefs.getInt("pause_after", 15)
        set(v) = prefs.edit().putInt("pause_after", v.coerceIn(1, 240)).apply()

    /** Durée de la pause en minutes. */
    var pauseLengthMin: Int
        get() = prefs.getInt("pause_len", 5)
        set(v) = prefs.edit().putInt("pause_len", v.coerceIn(1, 240)).apply()

    // ---------- délai avant ouverture ----------

    var delayEnabled: Boolean
        get() = prefs.getBoolean("delay_on", true)
        set(v) = prefs.edit().putBoolean("delay_on", v).apply()

    var delaySeconds: Int
        get() = prefs.getInt("delay_s", 10)
        set(v) = prefs.edit().putInt("delay_s", v.coerceIn(3, 120)).apply()

    // ---------- mode strict ----------

    var strictEnabled: Boolean
        get() = prefs.getBoolean("strict_on", false)
        set(v) = prefs.edit().putBoolean("strict_on", v).apply()

    /** Demande de déverrouillage : effective 2 min après, valable 5 min. */
    fun requestUnlock() = prefs.edit().putLong("unlock_req", System.currentTimeMillis()).apply()

    private val unlockReq get() = prefs.getLong("unlock_req", 0L)

    /** Millisecondes avant le déverrouillage, ou 0 si pas de demande en cours. */
    fun unlockWaitMs(): Long {
        val req = unlockReq
        if (req == 0L) return 0L
        val left = req + UNLOCK_WAIT_MS - System.currentTimeMillis()
        return if (left > 0) left else 0L
    }

    fun isUnlockPending() = unlockWaitMs() > 0

    /** Vrai si le mode strict est actif et qu'aucun déverrouillage n'est en cours. */
    fun isLocked(): Boolean {
        if (!strictEnabled) return false
        val now = System.currentTimeMillis()
        val opensAt = unlockReq + UNLOCK_WAIT_MS
        return !(unlockReq != 0L && now >= opensAt && now < opensAt + UNLOCK_OPEN_MS)
    }

    /** Fin de la fenêtre de déverrouillage (pour l'afficher). */
    fun unlockedUntil(): Long = unlockReq + UNLOCK_WAIT_MS + UNLOCK_OPEN_MS

    private fun migrateV1() {
        if (prefs.contains("start")) {
            val s = prefs.getInt("start", 0)
            val e = prefs.getInt("end", 0)
            val edit = prefs.edit().remove("start").remove("end")
            if (s != e && !prefs.contains(KEY_WINDOWS)) edit.putString(KEY_WINDOWS, "$s-$e")
            edit.apply()
        }
    }

    companion object {
        private const val KEY_DAYS = "days"
        private const val KEY_WINDOWS = "windows"
        const val UNLOCK_WAIT_MS = 2 * 60_000L
        const val UNLOCK_OPEN_MS = 5 * 60_000L

        fun fmt(min: Int) = "%02d:%02d".format(min / 60, min % 60)
    }
}

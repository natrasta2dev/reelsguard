package com.perso.reelsguard

import android.content.Context
import java.time.LocalDate
import java.time.LocalTime

/**
 * Réglages de planning :
 *  - apps surveillées (on peut en désactiver) ;
 *  - jours de la semaine où ReelsGuard est actif (les autres jours : aucune limite) ;
 *  - plage horaire où les vidéos courtes sont autorisées (dans la limite du quota).
 *    En dehors de la plage, elles sont cachées. Début = fin signifie « toute la journée ».
 */
class Schedule(context: Context) {

    private val prefs = context.getSharedPreferences("reelsguard_schedule", Context.MODE_PRIVATE)

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

    // ---------- plage horaire (minutes depuis minuit) ----------

    val startMin get() = prefs.getInt(KEY_START, 0)
    val endMin get() = prefs.getInt(KEY_END, 0)
    val isAllDay get() = startMin == endMin

    fun setWindow(start: Int, end: Int) = prefs.edit().putInt(KEY_START, start).putInt(KEY_END, end).apply()

    fun isInWindow(now: LocalTime = LocalTime.now()): Boolean {
        if (isAllDay) return true
        val m = now.hour * 60 + now.minute
        return if (startMin < endMin) m in startMin until endMin
        else m >= startMin || m < endMin // plage qui passe minuit (ex. 20:00 → 02:00)
    }

    companion object {
        private const val KEY_DAYS = "days"
        private const val KEY_START = "start"
        private const val KEY_END = "end"

        fun fmt(min: Int) = "%02d:%02d".format(min / 60, min % 60)
    }
}

package fr.codapps.reelsguard

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import android.telecom.TelecomManager
import java.time.LocalDate
import java.time.LocalTime

/**
 * Réglages de l'édition Famille, stockés uniquement sur l'appareil :
 *  - limite de temps pour n'importe quelle app (app entière) ;
 *  - couvre-feu et temps scolaire : pendant ces plages, seules les apps autorisées s'ouvrent ;
 *  - liste des apps toujours autorisées.
 */
class FamilyConfig(private val context: Context) {

    private val prefs = context.getSharedPreferences("reelsguard_family", Context.MODE_PRIVATE)

    // ---------- apps entières ----------

    /** Paquet → minutes par jour (0 = bloquée). */
    fun appLimits(): Map<String, Int> = prefs.getString("apps", "")!!.split(';').mapNotNull {
        val (p, m) = it.split('=').takeIf { x -> x.size == 2 } ?: return@mapNotNull null
        m.toIntOrNull()?.let { v -> p to v }
    }.toMap()

    /** minutes < 0 : retire la limite. */
    fun setAppLimit(pkg: String, minutes: Int) {
        val map = appLimits().toMutableMap()
        if (minutes < 0) map.remove(pkg) else map[pkg] = minutes.coerceAtMost(24 * 60)
        prefs.edit().putString("apps", map.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()
    }

    /** Règle « app entière » pour un paquet limité par le parent, ou null. */
    fun ruleFor(pkg: String): AppRule? {
        if (pkg !in appLimits()) return null
        return AppRule(name = "app:$pkg", packages = setOf(pkg), tabIds = emptyList(), tabLabels = emptyList(),
            viewerIds = emptyList(), wholeApp = true)
    }

    fun limitFor(pkg: String): Int = appLimits()[pkg] ?: -1

    // ---------- plages de restriction ----------

    data class Period(val enabled: Boolean, val start: Int, val end: Int, val days: Set<Int>) {
        fun isActive(now: LocalTime = LocalTime.now(), today: Int = LocalDate.now().dayOfWeek.value): Boolean {
            if (!enabled || start == end) return false
            val m = now.hour * 60 + now.minute
            return if (start < end) m in start until end && today in days
            else {
                // plage qui passe minuit : la partie du matin appartient à la veille
                val yesterday = if (today == 1) 7 else today - 1
                (m >= start && today in days) || (m < end && yesterday in days)
            }
        }
        fun label() = "${Schedule.fmt(start)} → ${Schedule.fmt(end)}"
    }

    private fun readPeriod(key: String, def: Period) = Period(
        prefs.getBoolean("${key}_on", def.enabled),
        prefs.getInt("${key}_start", def.start),
        prefs.getInt("${key}_end", def.end),
        prefs.getString("${key}_days", def.days.sorted().joinToString(""))!!.mapNotNull { it.digitToIntOrNull() }.toSet(),
    )

    private fun writePeriod(key: String, p: Period) = prefs.edit()
        .putBoolean("${key}_on", p.enabled).putInt("${key}_start", p.start).putInt("${key}_end", p.end)
        .putString("${key}_days", p.days.sorted().joinToString("")).apply()

    var curfew: Period
        get() = readPeriod("curfew", Period(false, 21 * 60, 7 * 60, (1..7).toSet()))
        set(v) = writePeriod("curfew", v)

    var school: Period
        get() = readPeriod("school", Period(false, 8 * 60 + 30, 16 * 60 + 30, (1..5).toSet()))
        set(v) = writePeriod("school", v)

    /** Nom de la plage active (« Couvre-feu », « Temps scolaire ») ou null. */
    fun activeRestriction(): String? = when {
        curfew.isActive() -> "Couvre-feu"
        school.isActive() -> "Temps scolaire"
        else -> null
    }

    // ---------- apps toujours autorisées ----------

    fun allowedExtra(): Set<String> = prefs.getStringSet("allowed", emptySet())!!.toSet()

    fun setAllowed(pkg: String, allowed: Boolean) {
        val s = allowedExtra().toMutableSet()
        if (allowed) s.add(pkg) else s.remove(pkg)
        prefs.edit().putStringSet("allowed", s).apply()
    }

    /** Apps qui restent utilisables pendant une restriction : essentielles + choix du parent. */
    fun allowedNow(): Set<String> = essentials() + allowedExtra()

    private var essentialsCache: Set<String>? = null

    fun essentials(): Set<String> {
        essentialsCache?.let { return it }
        val pm = context.packageManager
        val s = mutableSetOf(
            context.packageName, "com.android.systemui", "com.android.phone", "com.android.server.telecom",
            "com.android.emergency", "com.google.android.dialer", "com.samsung.android.dialer",
            "com.android.dialer", "com.google.android.apps.messaging", "com.samsung.android.messaging",
            "com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.android.deskclock",
            "com.android.settings", // les pages sensibles restent protégées par le code parent
        )
        // écran d'accueil, appli Téléphone et SMS par défaut
        pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.let { s += it }
        runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()?.let { s += it }
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()?.let { s += it }
        essentialsCache = s
        return s
    }

    companion object {
        /** Apps installées qui ont une icône dans le lanceur : paquet → nom. */
        fun launchableApps(c: Context): List<Pair<String, String>> {
            val pm = c.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            return pm.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                .filter { it.first != c.packageName }
                .sortedBy { it.second.lowercase() }
        }

        /** Tous les navigateurs installés (pour bloquer la navigation privée, quel que soit le navigateur). */
        fun browserPackages(c: Context): Set<String> {
            val pm = c.packageManager
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
            return pm.queryIntentActivities(i, PackageManager.MATCH_ALL).map { it.activityInfo.packageName }.toSet() +
                WebRules.BROWSERS.keys
        }
    }
}

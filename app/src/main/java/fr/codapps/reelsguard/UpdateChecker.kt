package fr.codapps.reelsguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Vérifie les nouvelles versions publiées sur GitHub (dernière release du dépôt).
 *
 * Règle de versionnage MAJEUR.MINEUR.CORRECTIF :
 *  - même majeur, version plus récente → mise à jour facultative : simple notification ;
 *  - majeur plus élevé → mise à jour obligatoire : l'app affiche un écran bloquant
 *    tant qu'elle n'est pas mise à jour (le service de protection, lui, continue de tourner).
 *
 * Seule donnée envoyée : une requête HTTPS anonyme à l'API publique de GitHub.
 */
object UpdateChecker {

    const val REPO = "natrasta2dev/reelsguard"
    const val REPO_URL = "https://github.com/$REPO"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val PREFS = "reelsguard_update"
    private const val CHANNEL = "updates"
    private const val NOTIF_ID = 2
    private const val MIN_INTERVAL_MS = 6 * 60 * 60_000L

    private val executor = Executors.newSingleThreadExecutor()

    data class Release(val version: String, val pageUrl: String, val apkUrl: String?, val notes: String)

    enum class Kind { NONE, MINOR, MAJOR }

    /** Version installée, sans suffixe (-debug…). */
    fun currentVersion(): String = BuildConfig.VERSION_NAME.substringBefore('-')

    /** Dernière release connue (mise en cache), sans appel réseau. */
    fun cached(ctx: Context): Release? {
        val p = prefs(ctx)
        val v = p.getString("version", null) ?: return null
        return Release(v, p.getString("page", REPO_URL)!!, p.getString("apk", null), p.getString("notes", "")!!)
    }

    fun kind(ctx: Context): Kind = cached(ctx)?.let { compare(it.version) } ?: Kind.NONE

    /** Vrai si une mise à jour majeure (obligatoire) est disponible. */
    fun isUpdateRequired(ctx: Context) = kind(ctx) == Kind.MAJOR

    fun compare(latest: String): Kind {
        val cur = parse(currentVersion()) ?: return Kind.NONE
        val new = parse(latest) ?: return Kind.NONE
        for (i in 0..2) {
            if (new[i] > cur[i]) return if (i == 0) Kind.MAJOR else Kind.MINOR
            if (new[i] < cur[i]) return Kind.NONE
        }
        return Kind.NONE
    }

    private fun parse(v: String): List<Int>? {
        val parts = v.trim().removePrefix("v").substringBefore('-').split('.')
        if (parts.isEmpty()) return null
        val nums = parts.map { it.toIntOrNull() ?: return null }
        return (nums + listOf(0, 0, 0)).take(3)
    }

    /**
     * Vérifie en arrière-plan (au plus toutes les 6 h, sauf si [force]).
     * Notifie une fois par version trouvée ; [onDone] est appelé sur le fil principal.
     */
    fun check(ctx: Context, force: Boolean = false, onDone: (() -> Unit)? = null) {
        val app = ctx.applicationContext
        val p = prefs(app)
        if (!force && System.currentTimeMillis() - p.getLong("checked_at", 0L) < MIN_INTERVAL_MS) return
        p.edit().putLong("checked_at", System.currentTimeMillis()).apply()

        executor.execute {
            val release = runCatching { fetch() }.getOrNull()
            if (release != null) {
                p.edit()
                    .putString("version", release.version)
                    .putString("page", release.pageUrl)
                    .putString("apk", release.apkUrl)
                    .putString("notes", release.notes)
                    .apply()
                val kind = compare(release.version)
                if (kind != Kind.NONE && p.getString("notified", null) != release.version) {
                    notify(app, release, kind)
                    p.edit().putString("notified", release.version).apply()
                }
            }
            if (onDone != null) android.os.Handler(android.os.Looper.getMainLooper()).post(onDone)
        }
    }

    /**
     * Édition perso : dernière release (tags « vX.Y.Z »).
     * Édition Famille : release la plus récente dont le tag commence par « famille-v ».
     */
    private fun fetch(): Release {
        val prefix = BuildConfig.RELEASE_TAG_PREFIX
        val url = if (Edition.isFamily) "https://api.github.com/repos/$REPO/releases?per_page=30" else API
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "ReelsGuard/${currentVersion()}")
        }
        try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = if (Edition.isFamily) {
                val arr = org.json.JSONArray(body)
                (0 until arr.length()).map { arr.getJSONObject(it) }.firstOrNull {
                    !it.optBoolean("draft") && !it.optBoolean("prerelease") && it.optString("tag_name").startsWith(prefix)
                } ?: error("aucune release Famille")
            } else JSONObject(body)
            val assets = json.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); break }
            }
            return Release(
                version = json.getString("tag_name").removePrefix(prefix),
                pageUrl = json.optString("html_url", REPO_URL),
                apkUrl = apk,
                notes = json.optString("body", ""),
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Ouvre le téléchargement de l'APK (ou la page de la release) dans le navigateur. */
    fun openDownload(ctx: Context) {
        val r = cached(ctx)
        val url = r?.apkUrl ?: r?.pageUrl ?: "$REPO_URL/releases/latest"
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun notify(ctx: Context, r: Release, kind: Kind) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Mises à jour", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val tap = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (kind == Kind.MAJOR) "Mise à jour obligatoire : v${r.version}" else "Mise à jour disponible : v${r.version}"
        val text = if (kind == Kind.MAJOR) "Installe-la pour continuer à utiliser ReelsGuard." else "Touche pour voir les nouveautés."
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_fg)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        runCatching { nm.notify(NOTIF_ID, n) }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

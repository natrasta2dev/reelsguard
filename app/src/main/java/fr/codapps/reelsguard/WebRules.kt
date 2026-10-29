package fr.codapps.reelsguard

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Versions web des apps surveillées, ouvertes dans un navigateur.
 *
 * L'adresse de la page est lue dans la barre d'adresse du navigateur, comparée sur l'appareil
 * aux règles ci-dessous, puis oubliée : elle n'est ni enregistrée ni transmise.
 */
object WebRules {

    /** Navigateurs pris en charge → identifiants possibles de leur barre d'adresse. */
    val BROWSERS: Map<String, List<String>> = mapOf(
        "com.android.chrome" to listOf("url_bar"),
        "com.chrome.beta" to listOf("url_bar"),
        "com.brave.browser" to listOf("url_bar"),
        "com.microsoft.emmx" to listOf("url_bar"),
        "com.sec.android.app.sbrowser" to listOf("location_bar_edit_text", "custom_tab_toolbar_url_bar_text"),
        "org.mozilla.firefox" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
        "org.mozilla.firefox_beta" to listOf("mozac_browser_toolbar_url_view"),
        "com.opera.browser" to listOf("url_field"),
        "com.opera.mini.native" to listOf("url_field"),
        "com.duckduckgo.mobile.android" to listOf("omnibarTextInput"),
        "com.vivaldi.browser" to listOf("url_bar"),
        "com.kiwibrowser.browser" to listOf("url_bar"),
    )

    fun isBrowser(pkg: String) = pkg in BROWSERS

    /** Adresse affichée dans la barre du navigateur, ou null si elle n'est pas visible. */
    fun readUrl(root: AccessibilityNodeInfo, pkg: String): String? {
        for (id in BROWSERS[pkg].orEmpty()) {
            val node = root.findAccessibilityNodeInfosByViewId("$pkg:id/$id").firstOrNull() ?: continue
            val txt = node.text?.toString()?.trim()
            if (!txt.isNullOrEmpty() && !node.isFocused) return txt // ignorée pendant la saisie
        }
        return null
    }

    /** Résultat : l'app concernée, et vrai si la page est un fil de vidéos courtes. */
    data class Match(val rule: AppRule, val watching: Boolean)

    fun match(rawUrl: String): Match? {
        val url = rawUrl.lowercase()
            .removePrefix("https://").removePrefix("http://")
            .removePrefix("www.").removePrefix("m.").removePrefix("mobile.")
        val host = url.substringBefore('/').substringBefore('?')
        val path = "/" + url.substringAfter('/', "")

        fun h(vararg domains: String) = domains.any { host == it || host.endsWith(".$it") }

        return when {
            h("instagram.com") -> Match(AppRules.INSTAGRAM,
                path.startsWith("/reels") || path.startsWith("/reel/"))
            h("youtube.com") -> Match(AppRules.YOUTUBE, path.startsWith("/shorts"))
            h("youtu.be") -> null
            h("tiktok.com") -> Match(AppRules.TIKTOK,
                // fil « Pour toi », « Abonnements », vidéos ; pas les pages de profil seules
                !(path.startsWith("/@") && "/video/" !in path))
            h("facebook.com") -> Match(AppRules.FACEBOOK,
                path.startsWith("/reel") || path.startsWith("/watch"))
            h("snapchat.com") -> Match(AppRules.SNAPCHAT, path.startsWith("/spotlight"))
            h("x.com", "twitter.com") -> Match(AppRules.X, "/video/" in path)
            h("reddit.com") -> Match(AppRules.REDDIT, false)
            h("pinterest.com", "pinterest.fr") -> Match(AppRules.PINTEREST, true)
            h("twitch.tv") -> Match(AppRules.TWITCH, true)
            h("threads.net", "threads.com") -> Match(AppRules.THREADS, true)
            else -> null
        }
    }
}

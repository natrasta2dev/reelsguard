package com.perso.reelsguard

/**
 * Description d'une app surveillée : comment reconnaître l'écran de vidéos courtes,
 * quel bouton cacher une fois la limite atteinte, et quoi faire si on y est quand même.
 *
 * Les identifiants internes peuvent changer avec les mises à jour des apps :
 * les libellés (texte / description d'accessibilité) servent de repli.
 */
data class AppRule(
    val name: String,
    val packages: Set<String>,
    /** Identifiants (sans préfixe) du bouton à cacher dans la barre du bas. */
    val tabIds: List<String>,
    /** Libellés possibles de ce bouton (FR + EN). */
    val tabLabels: List<String>,
    /** Identifiants d'éléments présents seulement quand on regarde des vidéos courtes. */
    val viewerIds: List<String>,
    /** Textes présents seulement sur le fil de vidéos (ex. « Pour toi » sur TikTok). */
    val viewerTexts: List<String> = emptyList(),
    /** Hauteur max (fraction de l'écran) où chercher ces textes. */
    val viewerTextMaxTop: Float = 0.3f,
    /** Vrai si la barre d'onglets peut aussi être en haut de l'écran (Facebook). */
    val navTopToo: Boolean = false,
    /**
     * App sans section vidéo distincte : tout le temps passé dans l'app compte, et une fois
     * bloquée on revient à l'écran d'accueil du téléphone.
     */
    val wholeApp: Boolean = false,
    /** Part de la hauteur d'écran qu'un lecteur doit occuper pour compter comme « plein écran ». */
    val fullscreenRatio: Float = 0.5f,
    /**
     * Repli : un élément plein écran dont l'identifiant contient l'un de ces morceaux
     * (ex. « reel » chez YouTube) signale un lecteur de vidéos courtes.
     */
    val viewerIdParts: List<String> = emptyList(),
    /**
     * Marqueurs fiables : si un élément dont l'identifiant contient l'un de ces morceaux existe
     * dans l'arbre (même non « visible »), on est sur des vidéos courtes.
     * Méthode reprise du projet open source Shorts-Blocker (reel_progress_bar chez YouTube).
     */
    val markerIdParts: List<String> = emptyList(),
    /** Morceaux d'identifiants à ignorer (ex. l'étagère de Shorts sur l'accueil YouTube). */
    val viewerIdExclusions: List<String> = emptyList(),
    /** Si non vide : une fois bloqué, on bascule vers cet onglet au lieu de faire « retour ». */
    val redirectTabLabels: List<String> = emptyList(),
)

object AppRules {

    val INSTAGRAM = AppRule(
        name = "Reels",
        packages = setOf("com.instagram.android"),
        tabIds = listOf("clips_tab"),
        tabLabels = listOf("Reels"),
        viewerIds = listOf(
            "clips_viewer_view_pager",
            "clips_viewer_container",
            "clips_video_container",
            "clips_single_media_component",
        ),
    )

    val YOUTUBE = AppRule(
        name = "Shorts",
        packages = setOf("com.google.android.youtube"),
        tabIds = emptyList(),
        tabLabels = listOf("Shorts"),
        viewerIds = listOf(
            "reel_recycler",
            "reel_player_page_container",
            "reel_watch_player",
            "shorts_container",
            "reel_watch_fragment_root",
            "reel_player_overlay_v2",
            "reel_time_bar",
        ),
        markerIdParts = listOf("reel_progress_bar"),
        viewerIdParts = listOf("reel", "shorts"),
        viewerIdExclusions = listOf("shelf", "pivot", "tab", "thumbnail", "chip"),
    )

    val TIKTOK = AppRule(
        name = "TikTok",
        packages = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
        tabIds = emptyList(),
        tabLabels = listOf("Accueil", "Home"),
        viewerIds = emptyList(),
        viewerTexts = listOf("Pour toi", "For You", "Abonnements", "Following"),
        redirectTabLabels = listOf("Profil", "Profile"),
    )

    /**
     * Facebook (app classique, pas Lite) : peu d'identifiants exploitables, on se base sur
     * l'onglet Reels/Vidéo (en haut ou en bas selon les versions) et l'en-tête « Reels » du lecteur.
     */
    val FACEBOOK = AppRule(
        name = "Facebook",
        packages = setOf("com.facebook.katana"),
        tabIds = emptyList(),
        tabLabels = listOf("Reels", "Vidéo", "Video"),
        viewerIds = emptyList(),
        viewerTexts = listOf("Reels"),
        viewerTextMaxTop = 0.15f,
        navTopToo = true,
    )

    /** Snapchat : seul Spotlight est concerné, les discussions restent libres. */
    val SNAPCHAT = AppRule(
        name = "Snapchat",
        packages = setOf("com.snapchat.android"),
        tabIds = emptyList(),
        tabLabels = listOf("Spotlight"),
        viewerIds = emptyList(),
        viewerTexts = listOf("Spotlight"),
        viewerTextMaxTop = 0.15f,
    )

    /** X : le lecteur vidéo plein écran (défilement vertical). */
    val X = AppRule(
        name = "X",
        packages = setOf("com.twitter.android"),
        tabIds = emptyList(),
        tabLabels = emptyList(),
        viewerIds = emptyList(),
        viewerIdParts = listOf("immersive", "video_player", "fullscreen", "media_viewer"),
        viewerIdExclusions = listOf("thumbnail", "preview", "avatar", "inline"),
        fullscreenRatio = 0.85f,
    )

    /** Reddit : le lecteur vidéo plein écran. */
    val REDDIT = AppRule(
        name = "Reddit",
        packages = setOf("com.reddit.frontpage"),
        tabIds = emptyList(),
        tabLabels = emptyList(),
        viewerIds = emptyList(),
        viewerIdParts = listOf("video", "fullscreen", "media_screen", "player"),
        viewerIdExclusions = listOf("thumbnail", "preview", "avatar", "inline", "icon"),
        fullscreenRatio = 0.85f,
    )

    val PINTEREST = AppRule(
        name = "Pinterest", packages = setOf("com.pinterest"),
        tabIds = emptyList(), tabLabels = emptyList(), viewerIds = emptyList(), wholeApp = true,
    )

    val TWITCH = AppRule(
        name = "Twitch", packages = setOf("tv.twitch.android.app"),
        tabIds = emptyList(), tabLabels = emptyList(), viewerIds = emptyList(), wholeApp = true,
    )

    val THREADS = AppRule(
        name = "Threads", packages = setOf("com.instagram.barcelona"),
        tabIds = emptyList(), tabLabels = emptyList(), viewerIds = emptyList(), wholeApp = true,
    )

    val ALL = listOf(INSTAGRAM, YOUTUBE, TIKTOK, FACEBOOK, SNAPCHAT, X, REDDIT, PINTEREST, TWITCH, THREADS)

    fun forPackage(pkg: String): AppRule? = ALL.firstOrNull { pkg in it.packages }
}

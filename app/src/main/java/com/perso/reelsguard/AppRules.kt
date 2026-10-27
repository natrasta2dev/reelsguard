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
    /**
     * Repli : un élément plein écran dont l'identifiant contient l'un de ces morceaux
     * (ex. « reel » chez YouTube) signale un lecteur de vidéos courtes.
     */
    val viewerIdParts: List<String> = emptyList(),
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

    val ALL = listOf(INSTAGRAM, YOUTUBE, TIKTOK)

    fun forPackage(pkg: String): AppRule? = ALL.firstOrNull { pkg in it.packages }
}

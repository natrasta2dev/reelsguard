package fr.codapps.reelsguard

/**
 * Mentions légales, confidentialité et licence affichées dans l'application.
 * Les mêmes textes figurent dans LEGAL.md et PRIVACY.md à la racine du dépôt.
 *
 * Identité de l'éditeur : à compléter ci-dessous (les champs null ne sont pas affichés).
 */
object Legal {

    const val PUBLISHER = "Cod'Apps"
    const val WEBSITE = "https://codapps.fr"

    /** Nom ou raison sociale, forme juridique (ex. « Jean Dupont, entrepreneur individuel »). */
    val LEGAL_NAME: String? = null
    /** Numéro SIREN / SIRET, le cas échéant. */
    val SIREN: String? = null
    /** Adresse postale (ou domiciliation). */
    val ADDRESS: String? = null
    /** Adresse e-mail de contact. */
    val EMAIL: String? = null
    /** Directeur ou directrice de la publication. */
    val DIRECTOR: String? = null

    const val LICENSE_NAME = "GNU GPL v3.0"
    const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
    const val GITHUB_PRIVACY_URL = "https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement"

    data class Section(val title: String, val body: String)

    fun sections(): List<Section> {
        val editor = buildList {
            add("$PUBLISHER — $WEBSITE")
            LEGAL_NAME?.let { add(it) }
            SIREN?.let { add("SIREN : $it") }
            ADDRESS?.let { add(it) }
            EMAIL?.let { add("Contact : $it") }
            DIRECTOR?.let { add("Directeur de la publication : $it") }
            if (EMAIL == null) add("Contact : via le formulaire du site $WEBSITE")
        }.joinToString("\n")

        return listOf(
            Section("Éditeur", editor),
            Section(
                "Hébergement",
                "Le code source et les fichiers d'installation sont hébergés par GitHub, Inc., " +
                    "88 Colin P. Kelly Jr. Street, San Francisco, CA 94107, États-Unis — github.com.\n" +
                    "Dépôt : ${UpdateChecker.REPO_URL}"
            ),
            Section(
                "Licence",
                "ReelsGuard est un logiciel libre et gratuit, distribué sous licence $LICENSE_NAME. " +
                    "Vous pouvez l'utiliser, l'étudier, le modifier et le redistribuer selon les termes de cette licence. " +
                    "Le texte complet est disponible sur $LICENSE_URL et dans le fichier LICENSE du dépôt."
            ),
            Section(
                "Marques et absence d'affiliation",
                "Instagram, Facebook et Threads sont des marques de Meta Platforms, Inc. ; YouTube de Google LLC ; " +
                    "TikTok de ByteDance Ltd. ; Snapchat de Snap Inc. ; X de X Corp. ; Reddit de Reddit, Inc. ; " +
                    "Pinterest de Pinterest, Inc. ; Twitch de Twitch Interactive, Inc. " +
                    "Ces noms sont utilisés uniquement pour désigner les applications concernées. " +
                    "ReelsGuard n'est ni affilié, ni soutenu, ni approuvé par ces sociétés, et ne modifie pas leurs applications."
            ),
            Section(
                "Absence de garantie",
                "ReelsGuard est fourni « tel quel », sans garantie d'aucune sorte. Les applications surveillées " +
                    "évoluent régulièrement : la détection peut cesser de fonctionner après l'une de leurs mises à jour. " +
                    "L'éditeur ne saurait être tenu responsable d'un dysfonctionnement, d'une perte de données " +
                    "ou de tout dommage lié à l'utilisation de l'application, dans les limites prévues par la loi."
            ),
            Section(
                "Utilisation du service d'accessibilité",
                "ReelsGuard utilise l'API d'accessibilité d'Android uniquement pour : savoir quelle application est " +
                    "au premier plan, détecter si des vidéos courtes sont affichées, masquer le bouton correspondant " +
                    "et revenir en arrière une fois la limite atteinte. Dans un navigateur, il lit l'adresse de la page " +
                    "affichée pour reconnaître les sites concernés (Instagram, YouTube, TikTok…) : cette adresse est " +
                    "comparée sur l'appareil puis oubliée, jamais enregistrée ni transmise. " +
                    "En mode strict, il ferme les pages des Paramètres " +
                    "qui concernent ReelsGuard. Aucune saisie (mots de passe, messages) n'est lue ni enregistrée."
            ),
            Section(
                "Données personnelles",
                "ReelsGuard ne collecte, ne vend et ne transmet aucune donnée personnelle à l'éditeur ni à des tiers. " +
                    "Il n'y a ni compte, ni publicité, ni outil de mesure d'audience.\n\n" +
                    "• Stocké uniquement sur l'appareil : vos réglages et le temps passé par jour et par application " +
                    "(30 derniers jours).\n" +
                    "• Captures d'écran : pour donner au cache la couleur de la barre de navigation, quelques pixels " +
                    "sont lus en mémoire puis l'image est immédiatement détruite. Rien n'est enregistré ni envoyé.\n" +
                    "• Diagnostic (désactivé par défaut) : il enregistre sur l'appareil la structure de l'écran " +
                    "de l'application surveillée, qui peut contenir des textes visibles. Il n'est partagé que si vous " +
                    "le décidez, avec l'application de votre choix.\n" +
                    "• Mises à jour : au plus toutes les 6 heures, l'application interroge l'API publique de GitHub. " +
                    "GitHub reçoit alors votre adresse IP et la version de ReelsGuard ($GITHUB_PRIVACY_URL).\n" +
                    "• Notifications : affichées localement, sans serveur.\n\n" +
                    "Vous pouvez effacer toutes les données à tout moment : Paramètres Android → Applications → " +
                    "ReelsGuard → Stockage → Effacer les données, ou en désinstallant l'application. " +
                    "Pour toute question, contactez l'éditeur. Vous pouvez aussi adresser une réclamation à la CNIL (cnil.fr)."
            ),
            Section(
                "Santé et bien-être",
                "ReelsGuard est un outil d'aide à la maîtrise du temps d'écran. Il ne constitue ni un dispositif " +
                    "médical, ni un contrôle parental, et ne remplace pas l'avis d'un professionnel de santé."
            ),
        )
    }
}

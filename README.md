# ReelsGuard

Petite app Android perso qui se pose par-dessus Instagram (sans la modifier) :

- elle compte le temps passé sur les Reels (onglet Reels **et** Reels ouverts depuis le fil ou les DM) ;
- une fois la limite du jour atteinte, le bouton Reels de la barre du bas est recouvert (il disparaît et ne répond plus) ;
- si un Reel s'ouvre quand même, l'app fait « retour » automatiquement ;
- le compteur repart à zéro à minuit ; alerte à 5 min de la fin.

Baisser la limite s'applique tout de suite ; l'augmenter ne compte qu'à partir du lendemain (anti-craquage). `0` = Reels toujours bloqués.

## Installation

1. Ouvrir le dossier dans **Android Studio** (il télécharge Gradle et le SDK tout seul).
2. Brancher le téléphone (débogage USB activé) → **Run ▶**.
3. Dans l'app : « Activer le service d'accessibilité » → ReelsGuard → activer.
   - Sur Android 13+, si l'option est grisée : Paramètres → Applis → ReelsGuard → ⋮ → *Autoriser les paramètres restreints*, puis réessayer.
4. Régler la limite (60 min par défaut).

## Si ça ne détecte plus après une mise à jour d'Instagram

Instagram renomme parfois ses éléments internes. Les identifiants sont en haut de
`ReelsGuardService.kt` (`REELS_TAB_IDS`, `REELS_VIEWER_IDS`). Pour retrouver les bons :
Android Studio → *Layout Inspector*, ou l'app « Developer Assistant », sur l'écran des Reels.
Un repli par texte (« Reels ») existe déjà pour le bouton.

## Récupérer l'APK sans Android Studio

Chaque push sur `main` compile l'APK via GitHub Actions et le publie dans
**Releases** (dernière release → `ReelsGuard.apk`). Ouvre ce lien depuis le téléphone,
télécharge, puis autorise l'installation depuis le navigateur.
On peut aussi relancer la compilation à la main : onglet *Actions* → *Build APK* → *Run workflow*.

# Contribuer à ReelsGuard

Merci de ton intérêt ! Toute aide est bienvenue : signalement de bug, correction de détection, nouvelle app, traduction, design.

## Signaler un problème

Ouvre une [issue](https://github.com/natrasta2dev/reelsguard/issues/new/choose) en précisant ton téléphone, ta version d'Android, la version de ReelsGuard et celle de l'app concernée. Pour un problème de détection, joins le diagnostic (voir le README), **après en avoir retiré toute information personnelle**.

## Proposer une modification

1. Forke le dépôt et crée une branche.
2. Compile avec `gradle assembleDebug` et teste sur un vrai téléphone.
3. Ouvre une pull request qui explique le changement. La CI doit être verte.

## Ajouter ou corriger une app

Toute la détection est décrite dans `app/src/main/java/fr/codapps/reelsguard/AppRules.kt` :

- `packages` : identifiant(s) Android de l'app ;
- `tabLabels` / `tabIds` : le bouton à masquer ;
- `viewerIds`, `viewerTexts`, `markerIdParts`, `viewerIdParts` : comment reconnaître l'écran de vidéos courtes ;
- `redirectTabLabels` : onglet vers lequel rediriger (sinon retour arrière) ;
- `wholeApp` : toute l'app compte (pas de section vidéo distincte).

Le diagnostic intégré donne les identifiants et libellés visibles à l'écran. Ajoute ensuite l'app à la liste `APPS` de `MainActivity.kt`.

## Principes

- **Aucune collecte de données**, aucune bibliothèque de publicité ou d'analytics.
- **Ne jamais modifier** les applications tierces : uniquement les API publiques d'Android.
- Interface sobre, en français, cohérente avec la direction artistique existante.

## Licence

En contribuant, tu acceptes que ta contribution soit publiée sous licence [GPL-3.0](LICENSE).

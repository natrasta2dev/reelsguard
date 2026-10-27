# Changelog

Toutes les évolutions notables de ReelsGuard sont listées ici.
Le projet suit le [versionnage sémantique](https://semver.org/lang/fr/) : une hausse du numéro **majeur** rend la mise à jour obligatoire dans l'application.

## [1.0.0] - 2026-10-27

Première version publique.

### Limiter
- Limite quotidienne commune à toutes les apps choisies, avec limite différente le week-end en option.
- Quota propre à chaque app, inclus dans la limite commune.
- Jours actifs et plages horaires multiples (une plage peut passer minuit).
- Anti-craquage : baisser une limite s'applique tout de suite, l'augmenter seulement le lendemain.

### Bloquer
- Bouton Reels / Shorts / Spotlight masqué par un cache de la couleur exacte de la barre (Android 11+).
- Sortie automatique des vidéos courtes (retour, ou profil sur TikTok ; écran d'accueil pour Pinterest, Twitch et Threads).
- Pause forcée après un temps de visionnage continu (15 min → 5 min par défaut, réglable).
- Écran « Respire » avant chaque ouverture (10 s par défaut, réglable).
- Mode strict : réglages verrouillés, déverrouillage après 2 min d'attente, pages Paramètres de ReelsGuard inaccessibles.

### Apps prises en charge
- Instagram (Reels), YouTube (Shorts), TikTok (fil), Facebook (Reels), Snapchat (Spotlight), X et Reddit (lecteur vidéo plein écran), Pinterest, Twitch et Threads (app entière).

### Suivre
- Temps restant en direct, détail par app, historique des 7 derniers jours, moyenne et série de jours sous la limite.
- Widget d'écran d'accueil et notification discrète du temps restant.

### Application
- Mises à jour : notification pour une version mineure, écran obligatoire pour une version majeure.
- Mode d'emploi intégré, diagnostic partageable, mentions légales et politique de confidentialité intégrées.

[1.0.0]: https://github.com/natrasta2dev/reelsguard/releases/tag/v1.0.0

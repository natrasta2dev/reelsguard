<div align="center">

# ReelsGuard

**Reprends le contrôle de ton temps face aux vidéos courtes.**

Une app Android libre et gratuite qui limite les Reels, Shorts, TikTok et autres fils addictifs,
sans modifier les applications et sans collecter aucune donnée.

[![Dernière version](https://img.shields.io/github/v/release/natrasta2dev/reelsguard?label=version)](https://github.com/natrasta2dev/reelsguard/releases/latest)
[![Licence GPL-3.0](https://img.shields.io/badge/licence-GPL--3.0-blue)](LICENSE)
![Android 8+](https://img.shields.io/badge/Android-8.0%2B-3ddc84)

[**Télécharger l'APK**](https://github.com/natrasta2dev/reelsguard/releases/latest) · [Installation](#installation) · [Confidentialité](PRIVACY.md) · [Mentions légales](LEGAL.md)

</div>

---

## Fonctionnalités

- **Une limite quotidienne commune** à toutes les apps choisies, avec une limite différente le week-end si tu veux.
- **Bouton masqué** : une fois la limite atteinte, le bouton Reels / Shorts / Spotlight disparaît, recouvert d'un cache de la couleur exacte de la barre.
- **Sortie automatique** si tu tombes quand même sur une vidéo courte (retour en arrière, ou profil sur TikTok).
- **Quota par app** inclus dans la limite commune (ex. 20 min de TikTok maximum).
- **Jours actifs** et **plages horaires** multiples : en dehors, les vidéos courtes sont cachées.
- **Pause forcée** après un temps de visionnage continu (15 min → 5 min de pause par défaut).
- **Écran « Respire »** de quelques secondes avant chaque ouverture, pour casser le réflexe.
- **Mode strict** : réglages verrouillés, déverrouillage après 2 min d'attente, impossible de couper le service sur un coup de tête.
- **Historique** des 7 derniers jours, moyenne, détail par app et **série** de jours sous la limite.
- **Widget** d'écran d'accueil et **notification discrète** du temps restant.
- **Mises à jour** : notification pour une version mineure, mise à jour obligatoire pour une version majeure.

## Deux éditions

| | **ReelsGuard** | **ReelsGuard Famille** |
|---|---|---|
| Pour qui | Toi, pour arrêter de scroller | Un parent, pour le téléphone de son enfant |
| Protection des réglages | Mode strict (attente de 2 min) | Code parent |
| Anti-contournement | Pages Paramètres protégées en mode strict | Paramètres, désinstallation, forcer l'arrêt, navigation privée |
| En plus | — | N'importe quelle app, couvre-feu, temps scolaire, demande de temps, rapport |
| Téléchargement | Releases `vX.Y.Z` | Releases `famille-vX.Y.Z` (branche `ReelGuardFamille`) |

Les deux éditions partagent le même code (variantes Gradle `perso` et `famille`), peuvent être installées côte à côte, et ne collectent aucune donnée. Détails de l'édition Famille : [CHANGELOG-FAMILLE.md](CHANGELOG-FAMILLE.md).

## Apps prises en charge

| App | Ce qui est limité |
|---|---|
| Instagram | Reels (onglet et Reels ouverts depuis le fil / les messages) |
| YouTube | Shorts |
| TikTok | Fil « Pour toi » / « Abonnements » (redirection vers le profil) |
| Facebook | Reels / Vidéo (app classique, pas Lite) |
| Snapchat | Spotlight (les discussions restent libres) |
| X | Lecteur vidéo plein écran |
| Reddit | Lecteur vidéo plein écran |
| Pinterest, Twitch, Threads | L'app entière |

Par défaut, seuls **Instagram, YouTube et TikTok** sont surveillés ; les autres apps s'activent en un appui dans les réglages.

**Versions web** : ces sites sont aussi limités dans le navigateur (Chrome, Samsung Internet, Firefox, Edge, Brave, Opera, DuckDuckGo, Vivaldi, Kiwi), avec la même limite que les apps — par exemple `youtube.com/shorts`, `instagram.com/reels` ou `tiktok.com`. L'adresse de la page est seulement comparée sur le téléphone, jamais enregistrée ni envoyée.

> Les applications surveillées changent régulièrement leur interface : la détection peut cesser de fonctionner après l'une de leurs mises à jour. Le [diagnostic intégré](#signaler-un-problème) aide à la corriger.

## Installation

1. Sur ton téléphone, télécharge `ReelsGuard-vX.Y.Z.apk` depuis la [dernière release](https://github.com/natrasta2dev/reelsguard/releases/latest) et ouvre-le.
2. **Play Protect** peut bloquer l'installation (comme pour toute app hors Play Store qui utilise l'accessibilité) : Play Store → profil → Play Protect → ⚙️ → désactive temporairement « Analyser les applis », installe, puis réactive-le. Sur Samsung, désactive aussi temporairement le *Blocage automatique*.
3. Ouvre ReelsGuard et suis le **mode d'emploi intégré** :
   - **Paramètres restreints** (Android 13+) : Paramètres → Applications → ReelsGuard → ⋮ → *Autoriser les paramètres restreints* ;
   - **Service d'accessibilité** : Accessibilité → Applications installées → ReelsGuard → activer ;
   - **Batterie** : Applications → ReelsGuard → Batterie → *Non restreinte* ;
   - **Notifications** : autorise-les pour voir le temps restant et les mises à jour.
4. Règle ta limite dans **Réglages**.

### Vérifier l'authenticité

Les APK officiels sont publiés uniquement dans les [Releases](https://github.com/natrasta2dev/reelsguard/releases) de ce dépôt, avec leur empreinte SHA-256. Ils sont signés par le certificat dont l'empreinte SHA-256 est :

```
D1:4E:0F:35:BA:B8:A4:D2:B7:AD:FD:3C:CF:8C:59:73:4F:D0:7B:AF:0C:0E:1D:2C:A8:0A:89:6C:17:A1:BD:6C
```

Vérification : `apksigner verify --print-certs ReelsGuard-vX.Y.Z.apk`.

## Confidentialité

ReelsGuard ne collecte **aucune** donnée : pas de compte, pas de publicité, pas de statistiques. Tout reste sur ton téléphone.
La seule connexion réseau est une vérification des mises à jour auprès de l'API publique de GitHub.
Détails dans [PRIVACY.md](PRIVACY.md).

Le service d'accessibilité sert uniquement à savoir quelle app est ouverte, détecter les vidéos courtes, masquer le bouton concerné et revenir en arrière. Aucune saisie (mots de passe, messages) n'est lue ni enregistrée.

## Mises à jour

Les versions suivent le schéma `MAJEUR.MINEUR.CORRECTIF` :

- **mineure ou correctif** (ex. 1.0.0 → 1.1.0) : une notification te prévient, la mise à jour est facultative ;
- **majeure** (ex. 1.x → 2.0.0) : l'app affiche un écran de mise à jour obligatoire (la protection, elle, continue de tourner).

Les nouveautés sont listées dans le [CHANGELOG](CHANGELOG.md).

## Signaler un problème

Si une app n'est plus détectée : ouvre ReelsGuard, **appui long sur « REELSGUARD »** en haut → *Activer* le diagnostic, va sur l'écran concerné quelques secondes, reviens et appuie sur *Partager*. Joins ce texte à une [issue](https://github.com/natrasta2dev/reelsguard/issues/new/choose).

⚠️ Le diagnostic contient la structure de l'écran, qui peut inclure des textes visibles (noms, légendes). Relis-le et retire ce qui est personnel avant de le publier.

## Compiler soi-même

Prérequis : Android Studio (ou JDK 17 + Android SDK 34 + Gradle 8.7).

```bash
git clone https://github.com/natrasta2dev/reelsguard.git
cd reelsguard
gradle assemblePersoDebug assembleFamilleDebug   # APK : app/build/outputs/apk/<édition>/debug/
```

La version debug s'installe à côté de la version officielle (identifiant `fr.codapps.reelsguard.debug`).

### Publier une version (mainteneurs)

1. Mettre à jour `versionCode` et `versionName` dans `app/build.gradle.kts`, et ajouter la section correspondante dans `CHANGELOG.md`.
2. Pousser sur `main` : le workflow *Release* détecte la nouvelle version, compile et signe l'APK, puis publie la release `vX.Y.Z` avec les notes du CHANGELOG.
3. La clé de signature n'est jamais dans le dépôt : elle vient des secrets `RG_KEYSTORE_B64`, `RG_KEYSTORE_PASSWORD` et `RG_KEY_ALIAS`.

## Contribuer

Les contributions sont bienvenues : voir [CONTRIBUTING.md](CONTRIBUTING.md).

## Licence et mentions légales

ReelsGuard est un logiciel libre sous licence [GNU GPL v3.0](LICENSE).
Voir aussi les [mentions légales](LEGAL.md).

Instagram, Facebook, Threads, YouTube, TikTok, Snapchat, X, Reddit, Pinterest et Twitch sont des marques de leurs propriétaires respectifs. ReelsGuard n'est ni affilié, ni soutenu, ni approuvé par ces sociétés.

---

<div align="center">

Made by [Cod'Apps](https://codapps.fr)

</div>

<details>
<summary>English summary</summary>

ReelsGuard is a free, open-source (GPL-3.0) Android app that limits short-form video feeds — Instagram Reels, YouTube Shorts, TikTok, Facebook Reels, Snapchat Spotlight, X and Reddit video players, plus Pinterest, Twitch and Threads — using a shared daily time budget, schedules, forced breaks, a "breathe" delay screen and a strict mode. It works through Android's accessibility service, never modifies the target apps, and collects no data. Download the APK from [Releases](https://github.com/natrasta2dev/reelsguard/releases/latest). The app UI is in French.

</details>

# Changelog — ReelsGuard Famille

Édition contrôle parental de ReelsGuard, développée sur la branche `ReelGuardFamille`.
Les releases portent le tag `famille-vX.Y.Z`. Une hausse du numéro **majeur** rend la mise à jour obligatoire.

## [1.0.1] - 2026-10-29

### Modifié
- Les réglages ne sont plus accessibles depuis l'accueil : ils se trouvent uniquement dans l'espace parent (code parent requis). L'enfant ne voit que son temps restant et « Demander plus de temps ».

## [1.0.0] - 2026-10-29

Première version de l'édition Famille. Elle reprend tout ReelsGuard (limites, apps, versions web, horaires, pause, « Respire », historique) et ajoute :

### Code parent
- Accord du parent et création d'un code parent (4 à 8 chiffres) au premier lancement.
- Code de secours affiché une seule fois pour choisir un nouveau code en cas d'oubli.
- Code stocké uniquement sous forme d'empreinte chiffrée ; blocage progressif après 5 essais ratés.
- Session parent de 10 minutes après saisie du code ; les réglages sont verrouillés le reste du temps.

### Anti-contournement
- Sans le code : impossible d'ouvrir les pages des Paramètres de l'app (désactiver le service, forcer l'arrêt, effacer les données, batterie, notifications) ni de la désinstaller.
- Anti-désinstallation : l'app peut être déclarée administrateur de l'appareil (sans aucun pouvoir), et la page pour la retirer est protégée.
- Navigation privée bloquée dans tous les navigateurs installés.

### Espace parent
- Limite de temps pour n'importe quelle app (app entière ; 0 = bloquée).
- Couvre-feu et temps scolaire : pendant ces plages, seules les apps essentielles (téléphone, SMS, horloge) et les apps autorisées s'ouvrent.
- « Demander plus de temps » : l'enfant demande, le parent accorde 15, 30 ou 60 min avec son code.
- Rapport des 7 derniers jours : temps par app, pages protégées refermées, navigation privée bloquée, apps bloquées pendant les plages, demandes de temps, codes erronés.

### Transparence
- Notification permanente « Contrôle parental actif ».
- Écrans de blocage adaptés aux enfants.
- Mentions légales et confidentialité propres à l'édition Famille ; tout reste sur l'appareil, sans compte ni serveur.

[1.0.1]: https://github.com/natrasta2dev/reelsguard/releases/tag/famille-v1.0.1
[1.0.0]: https://github.com/natrasta2dev/reelsguard/releases/tag/famille-v1.0.0

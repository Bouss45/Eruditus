# CLAUDE.md

Eruditus (« érudit » en latin) : app Android native de culture générale **en ligne** (comptes,
parties, classement). La v1 est un squelette : compte, partie test, classement. Périmètre et
décisions : `SPEC.md`.

## Identité

- `applicationId` / namespace `com.eruditus.android`, libellé « Eruditus », minSdk 26,
  compileSdk / targetSdk 37, portrait seul, textes en français seul.
- Kotlin, Jetpack Compose, Material 3. Installée depuis les Releases GitHub avec Obtainium
  (Release `v1.0.<n>`, versionCode 1000 + n) ; pas de Play Store.
- Serveur : PocketBase **0.42.3** sur un VPS (SPEC D3). Depuis le 26-10-2026, PocketBase publie
  sur ganigeorgiev.com : le dépôt GitHub est archivé à la v0.40.6, qui n'est pas la dernière
  version. L'app appelle l'API REST sans SDK. L'adresse du serveur et l'installation du VPS sont
  hors du dépôt : elles sont décrites dans `server/deploy.env` (local, non versionné).

## Dossiers

- `android-app/` : l'app (Gradle Kotlin DSL, catalogue de versions).
- `server/pb_migrations/` : collections, règles d'accès, réglages. Un fichier déployé n'est
  plus jamais modifié : un changement = une nouvelle migration.
- `server/pb_hooks/` : routes `/api/eruditus/v1/…` et logique de jeu.
- `server/tests/` : tests serveur (module JVM, binaire PocketBase épinglé, base vide).
- `server/deploy.sh`, `server/questions.sh` : déploiement et import de la banque de questions,
  lancés par le mainteneur seul.
- `SPEC.md`, `spec/` : la spec. `DESIGN.md` : l'apparence (direction B · Glacier) ;
  `design/claude-design/` : l'export Claude Design ; `design/icone/` : les SVG de l'icône.

## Commandes

Depuis `android-app/`, une fois le code écrit :
- `./gradlew :app:testDebugUnitTest` : tests JVM de l'app.
- `./gradlew :server-tests:test` : tests serveur, tentatives de triche comprises.
- `./gradlew :app:assembleDebug` : APK de debug. Les APK signés ne se font qu'en CI.

Ne jamais lancer `server/deploy.sh` ni `server/questions.sh` sans demande explicite.

## Méthode

- Un sujet = une branche `feat/<sujet>` ou `fix/<sujet>` depuis `main`. SPEC d'abord
  (Décisions, État d'avancement, Évolutions), ajouts présentés avant de coder ; tests JVM
  d'abord ; PR vers `main`, que le mainteneur merge
  lui-même (squash).
- Commits en français, écrits pour l'utilisateur (« Classement : ma position sous le top 10 »,
  pas « Refactor LeaderboardRepository ») : ils deviennent les notes de version.
- Tout nouvel accès réseau, permission, route, collection ou règle d'accès = une décision
  écrite dans la SPEC.

## Interface

- `DESIGN.md` fait foi sur l'apparence. Toute interface utilise les tokens et composants du
  design system (`ui/designsystem/`) : jamais de couleur ni de taille en dur dans un écran.
- Une fonctionnalité = un gabarit + des composants existants. Un nouveau composant seulement si
  aucun ne convient, avec ses `@Preview` sombre et clair, ajouté à `DESIGN.md` dans le même
  changement.
- Accessibilité : cibles de 48 dp, TalkBack, texte à 200 % sans coupure.

## Sécurité de l'API

Détail et liste des tentatives de triche : `spec/serveur.md` § 6 et § 7.
- Le serveur décide de tout : bonne réponse, chrono, points, score, classement. L'app n'envoie
  que des choix ; rien de ce qu'elle envoie n'est cru sans vérification.
- Règles de toutes les collections fermées (superutilisateur seul) ; chaque action passe par
  une route des hooks qui valide ses entrées ; champs sensibles `hidden`. Les routes acceptent
  seulement un jeton de `eruditus_joueurs`.
- Dépôt public : jamais d'identifiant de superutilisateur, de secret ni de bonne réponse dans le
  dépôt ou l'app ; la banque de questions vit hors dépôt.
- Sur GitHub, dépôt privé compris : seulement ce qui sert au projet. Rien sur le mainteneur ni sur
  son installation (adresse du serveur, outils, chemins, comptes, prénom, exemples tirés de sa
  vie) ; ces détails restent dans `server/deploy.env`, local (D36).
- App : HTTPS seulement ; jeton chiffré ; mot de passe et code de récupération jamais stockés
  ni écrits dans un log.
- Compatibilité : un changement serveur laisse la version précédente de l'app fonctionner
  (routes `/v1` changées seulement par ajout, serveur déployé avant l'app).
- Toute route ou règle nouvelle arrive avec ses tests serveur, dont au moins une tentative de
  triche qui doit échouer.

## Où lire

Lire seulement le fichier du sujet :
- `SPEC.md` : vue d'ensemble, Décisions, État d'avancement, Évolutions et pistes.
- `spec/ecrans.md` : écrans, navigation, textes et messages d'erreur.
- `spec/regles.md` : compte, partie, score, classement, version minimale.
- `spec/serveur.md` : instance, collections, règles d'accès, routes, sécurité, triches,
  questions, mise à jour du serveur, tests serveur.
- `spec/donnees.md` : ce que le téléphone garde, et comment il l'efface.

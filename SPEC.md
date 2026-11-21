# SPEC.md — Eruditus

> **Rôle de ce document.** Référence unique du comportement, des règles, du serveur et des
> décisions d'Eruditus. Ce fichier donne la vue d'ensemble, les Décisions (§ 3), l'État
> d'avancement (§ 4) et les Évolutions (§ 5) ; le détail est dans `spec/`, un fichier par sujet
> (§ 2). Ne lire que le fichier du sujet traité.
>
> **Dn** renvoie à la décision n° n du § 3 ; **Tn** à la tentative de triche n° n de
> `spec/serveur.md` § 7.

---

## Sommaire

1. [Vue d'ensemble](#1-vue-densemble)
2. [Fichiers de spec](#2-fichiers-de-spec)
3. [Décisions](#3-décisions)
4. [État d'avancement](#4-état-davancement)
5. [Évolutions et pistes](#5-évolutions-et-pistes)

---

## 1. Vue d'ensemble

### 1.1 But

Eruditus (« érudit » en latin) est une app Android native de culture générale **en ligne** : on
joue avec un compte, le serveur pose les questions, vérifie les réponses et compte les points,
et un classement compare les joueurs.

### 1.2 Périmètre de la v1 : un squelette

La v1 pose les fondations de bout en bout (compte, jeu vérifié par le serveur, classement) avant
le contenu (D1) :

- **Compte** : pseudo et mot de passe, sans e-mail. Créer, se connecter, se déconnecter,
  supprimer son compte ; mot de passe oublié par code de récupération (D9 – D14).
- **Partie test** : 5 questions à 4 choix servies par le serveur, 20 s chacune ; la bonne réponse
  n'est jamais envoyée à l'app avant qu'on ait répondu, et c'est le serveur qui vérifie et compte
  les points (D15 – D17).
- **Classement** : le top 10 des pseudos et scores, et ma position (D18).

**Hors périmètre** : e-mail, réseaux sociaux, amis, chat, multijoueur en direct, badges, séries,
notifications, IA, achats. L'app de culture générale complète (session du jour, thèmes, révision
espacée, contenu sourcé) est au § 5.

### 1.3 Deux parties

| partie | dossier | rôle |
|---|---|---|
| app | `android-app/` | Kotlin, Jetpack Compose, Material 3. Affiche, envoie les choix du joueur, ne décide de rien (D8). |
| serveur | `server/` | PocketBase 0.42.3 sur un VPS (D3). Le dépôt ne contient que les migrations (collections, règles d'accès, réglages), les hooks (routes `/api/eruditus/v1/…`), les tests serveur et deux scripts (D4). |

L'app appelle l'API REST directement, sans SDK (il n'existe pas de SDK officiel en Kotlin). Le
serveur est installé, sécurisé et en service hors du dépôt ; son installation est hors sujet.

### 1.4 Identité et installation

- `applicationId` / namespace `com.eruditus.android`, libellé « Eruditus », minSdk 26,
  compileSdk / targetSdk 37, portrait seul, textes en français seul (D2).
- Installation depuis les Releases GitHub du dépôt public `Bouss45/Eruditus` avec Obtainium :
  chaque push sur `main` qui touche l'app publie une Release `v1.0.<n>` (versionCode 1000 + n)
  avec un seul APK, `Eruditus-v1.0.<n>.apk` (D48, D49). Pas de Play Store.
- Une seule permission : `INTERNET` (D26).

---

## 2. Fichiers de spec

| fichier | contenu | à lire pour |
|---|---|---|
| [`spec/ecrans.md`](spec/ecrans.md) | navigation, aiguillage au démarrage, chaque écran, textes, messages d'erreur | toute interface |
| [`spec/regles.md`](spec/regles.md) | compte, partie, score, classement, version minimale, nouvelles versions | toute logique de compte, de jeu ou de mise à jour |
| [`spec/serveur.md`](spec/serveur.md) | instance, collections, règles d'accès, réglages, routes, **sécurité de l'API**, tentatives de triche, banque de questions, mise à jour du serveur, tests serveur | tout ce qui touche `server/` ou un appel réseau |
| [`spec/donnees.md`](spec/donnees.md) | ce que le téléphone garde, comment il le protège et l'efface | stockage local, session |
| [`spec/publication.md`](spec/publication.md) | vérification des PR, Releases, notes de version, secrets, ordre serveur puis app | `.github/`, `release-notes`, déploiement |

`DESIGN.md` fait foi sur l'apparence des écrans décrits dans `spec/ecrans.md` (tokens, polices,
composants, gabarits, mouvement, icône). L'export Claude Design est dans `design/claude-design/`,
l'icône dans `design/icone/`.

---

## 3. Décisions

D1 – D26 : interview de lancement ; D27 – D29 : étape Design ; D30 – D35 : étape Serveur ;
D36 – D40 : étape Serveur, partie 2 (19 novembre 2026) ; D41 – D46 : étape Fondation de l'app
(19 novembre 2026) ; D47 – D51 : étape Publication (20 novembre 2026) ; D52 – D60 : étape Écrans
du compte (20 novembre 2026) ; D61 – D64 : étape Mises à jour (20 novembre 2026) ; D65 – D70 :
étape Partie (20 novembre 2026) ; D71 – D75 : étape Classement (21 novembre 2026).

| n° | Sujet | Décision | Où |
|---|---|---|---|
| D1 | Périmètre v1 | Squelette en ligne : compte, partie test, classement. Hors périmètre : e-mail, réseaux sociaux, amis, chat, multijoueur en direct, badges, séries, notifications, IA, achats. L'app complète va dans Évolutions. | § 1.2, § 5 |
| D2 | Identité | `com.eruditus.android`, « Eruditus », minSdk 26, compile/targetSdk 37, portrait, français seul ; Kotlin, Compose, Material 3 ; Releases GitHub `v1.0.<n>` (versionCode 1000 + n) suivies par Obtainium. | § 1.4 |
| D3 | Serveur | PocketBase **0.42.3** (conteneur Docker derrière un proxy HTTPS) sur un VPS, dont l'adresse n'est pas écrite dans le dépôt (D36). Depuis le 26-10-2026, PocketBase publie sur ganigeorgiev.com (dépôt GitHub archivé à la v0.40.6, qui n'est pas la dernière version). Le dépôt épingle la même version (zip et SHA-256) pour les tests ; une montée de version est une nouvelle décision. | serveur § 1 |
| D4 | Contenu de `server/` | Migrations, hooks, tests serveur, `deploy.sh`, `questions.sh` (et `commun.sh`, leur code commun). Installation (outil de gestion des conteneurs, proxy HTTPS), sauvegardes régulières et superutilisateurs : hors dépôt (`deploy.sh` n'archive qu'avant chaque déploiement, D38). | serveur § 1, § 2 |
| D5 | Instance | Dédiée à Eruditus. Tout est quand même préfixé `eruditus` (collections, routes `/api/eruditus/v1/`, fichiers). Les migrations règlent le limiteur et l'en-tête d'IP du proxy, désactivent l'API batch et ferment toutes les règles de la collection `users` par défaut (inutilisée), sans la supprimer. | serveur § 3.5, § 4 |
| D6 | Déploiement | `server/deploy.sh`, lancé par le mainteneur depuis son PC : tests serveur, copie des seuls fichiers Eruditus par SSH, redémarrage du conteneur (les migrations s'appliquent au démarrage). Hôte et chemins dans un fichier non versionné ; aucune clé du VPS dans GitHub. Précisée par D36 – D39. | serveur § 9 |
| D7 | Questions | Dépôt public ; la banque (énoncés et réponses) vit hors dépôt, importée par `server/questions.sh`. Le dépôt ne garde que des questions factices pour les tests. Précisée par D40. | serveur § 8 |
| D8 | Le serveur fait foi | Toutes les règles d'accès des collections sont fermées (superutilisateur seul) ; chaque action passe par une route des hooks qui valide ses entrées. Seules exceptions : connexion et renouvellement du jeton par l'API standard de PocketBase. | serveur § 3, § 6 |
| D9 | Pseudo | 3 à 20 caractères parmi `A–Z a–z 0–9 _` ; casse gardée à l'affichage, unicité et connexion insensibles à la casse (index `COLLATE NOCASE`) ; noms réservés refusés ; non modifiable en v1 ; libéré à la suppression du compte. | règles § 1.1 |
| D10 | Mot de passe | 8 à 71 caractères **et** 72 octets au plus en UTF-8 (limite de bcrypt : un accent compte pour 2, un emoji pour 4), sans règle de composition ; l'app applique exactement la même règle avant l'envoi. Précisée à l'étape Serveur. | règles § 1.2 |
| D11 | Mot de passe oublié | Code de récupération de 16 caractères (80 bits), affiché une seule fois : à la création et après chaque usage. Le serveur n'en garde que l'empreinte. Pseudo + code donnent un nouveau mot de passe, un nouveau code, et ferment toutes les sessions. Sans le code, le compte est perdu. | règles § 1.6 |
| D12 | Session | Jeton valable 30 jours, renouvelé à chaque démarrage de l'app ; gardé chiffré (Android Keystore), exclu des sauvegardes Android ; jamais le mot de passe. Précisée par D54. | règles § 1.4, données |
| D13 | Déconnexion | Invalide côté serveur tous les jetons du compte, puis efface la session locale ; sans réseau, effacement local seul. | règles § 1.5 |
| D14 | Suppression du compte | Mot de passe redemandé ; le serveur efface le compte, ses parties et ses réponses ; il quitte le classement. | règles § 1.7 |
| D15 | Partie test | 5 questions à 4 choix, 20 s chacune mesurées par le serveur (+ 3 s de marge réseau) ; hors délai = fausse ; questions jamais servies d'abord ; commencer une partie abandonne la précédente ; une partie coupée est perdue, les réponses envoyées restent acquises. | règles § 2 |
| D16 | Bonne réponse | Champ caché, jamais envoyé avant que le joueur ait répondu à cette question ; renvoyé dans la correction. | règles § 2.3, serveur § 6.3 |
| D17 | Score | 1 point par bonne réponse dans le délai ; une question ne rapporte qu'une fois par joueur ; cumul de toutes les parties, sans remise à zéro en v1. Calculé par le serveur à partir des réponses enregistrées, jamais stocké ni écrit par l'app. | règles § 2.4, § 3 |
| D18 | Classement | Top 10 + ma ligne ; joueurs à 0 point non classés ; ex æquo au même rang (1, 2, 2, 4), puis ordre alphabétique ; réservé aux comptes. | règles § 4 |
| D19 | Sans compte | Seul l'écran Bienvenue est accessible (« Créer un compte », « Se connecter »). | écrans § 3 |
| D20 | Sans réseau | Écran « Pas de connexion » avec « Réessayer » ; aucune donnée en cache. | écrans § 13, données |
| D21 | Version minimale | Route publique `/info` ; une app plus ancienne affiche « Mise à jour nécessaire » (Obtainium, sinon la page des Releases). « Mettre à jour » : D63. | règles § 5 |
| D22 | Compatibilité | Routes `/v1` changées seulement par ajout ; l'app ignore les champs inconnus ; migrations toujours nouvelles ; serveur déployé avant l'app ; un test de contrat publié n'est jamais modifié. | serveur § 6.6, § 9 |
| D23 | Limite de tentatives | Limiteur intégré de PocketBase activé, par IP : connexion, création de compte, récupération, suppression, parties, reste de l'API. IP réelle lue dans `X-Forwarded-For` posé par le proxy HTTPS (prérequis : PocketBase joignable seulement par ce proxy). | serveur § 4 |
| D24 | HTTPS seulement | Trafic en clair interdit dans l'app ; le build échoue si l'URL du serveur n'est pas en `https://` ; pas d'épinglage de certificat. | serveur § 6.5 |
| D25 | Tests serveur | Module JVM qui lance le binaire épinglé sur une base vide avec les migrations et hooks du dépôt, puis joue chaque route et les tentatives de triche T1 – T33 ; lancé par `deploy.sh` avant toute copie, puis par la CI. | serveur § 7, § 10 |
| D26 | Permissions et réseau | `INTERNET` seule ; aucun autre accès réseau que le serveur ; ni jeton, ni mot de passe, ni code de récupération dans les logs. Précisée par D61 : en release, aussi l'API publique des Releases GitHub. | données |
| D27 | Apparence | Direction B · Glacier (Claude Design) ; `DESIGN.md` fait foi. Sombre par défaut, clair selon le réglage du téléphone, sans réglage dans l'app ; pas de couleurs dynamiques. Polices Newsreader et Inter embarquées (OFL), jamais téléchargées. Animations désactivées dans Android : état final direct. | `DESIGN.md` |
| D28 | Vibrations | Confirm à une bonne réponse, Reject à une mauvaise réponse ou au temps écoulé, par le retour haptique du système : aucune permission (D26 inchangée), suit le réglage « Vibreur au toucher ». Aucun son. | `DESIGN.md` § 6 |
| D29 | Icône | Piste 3 « Le E chronométré » : icône adaptative (fond, premier plan, monochrome pour les icônes à thème) ; l'écran de démarrage reprend le E aux couleurs du thème. | `DESIGN.md` § 10, `design/icone/` |
| D30 | Jeton et routes publiques | L'app n'envoie **jamais** de jeton sur une route publique (`/info`, `/comptes`, `/comptes/recuperation`, `auth-with-password`) : depuis PocketBase 0.42.0, un jeton invalide ou expiré y donne 401, ce qui ferait échouer une reconnexion. Le jeton n'accompagne que `auth-refresh` et les routes protégées. | serveur § 1, § 5, données § 1 |
| D31 | Erreurs des routes | Champ absent, mauvais type JSON, corps qui n'est pas un objet, `Content-Type` autre que JSON ou corps de plus de 16 Ko : `requete_invalide` (400). Bon type mais valeur refusée : le code précis. Jeton d'une autre collection : 401 (et non le 403 de PocketBase). Les routes écrivent elles-mêmes leurs erreurs `{ status, message, data: { code } }` (PocketBase réécrit le `data` d'une erreur levée). Succès : 200, ou 204 là où c'est prévu. | serveur § 5.3 |
| D32 | Correction | Hors délai = `correcte: false` (D15) ; une seule raison, dans cet ordre : `hors_delai`, `sans_reponse`, `fausse`, `deja_reussie`, `gagne` ; `bilan.bonnes` compte les réponses correctes. | règles § 2.3, serveur § 5.2 |
| D33 | Réponses de connexion | Un hook retire `email`, `emailVisibility` et `verified` du `record` renvoyé par `auth-with-password` et `auth-refresh` (T7). | serveur § 3.1 |
| D34 | Limiteur | Les règles Eruditus sont placées **avant** les règles par défaut : PocketBase prend la première règle de préfixe qui correspond, dans l'ordre de la liste, et `/api/` masquerait `/api/eruditus/`. | serveur § 4.1 |
| D35 | Réglages de jeu | Délai, marge et inactivité lus dans `ERUDITUS_DELAI_MS`, `ERUDITUS_MARGE_MS`, `ERUDITUS_INACTIVITE_MS` (20 000, 3 000, 1 800 000 par défaut ; non définies sur le VPS, raccourcies par les tests). `versionMinimale` : constante des hooks, 1000 au départ. Code de récupération renvoyé au format `XXXX-XXXX-XXXX-XXXX`. | serveur § 5, § 10, règles § 5 |
| D36 | Accès au VPS et discrétion | Sur GitHub, dépôt privé compris, seulement ce qui sert au projet : ni adresse du serveur, ni outils, chemins ou comptes du VPS, ni information sur le mainteneur. Ces détails restent dans `server/deploy.env` (local, non versionné) ; l'app recevra l'adresse du serveur au build, hors dépôt. La configuration du conteneur (montages, commande) est faite hors dépôt, par le mainteneur seul ; les scripts n'y touchent jamais. Accès par alias SSH ; commandes d'administration par `sudo -n`. Une copie de la clé de chiffrement des réglages est gardée hors du VPS, dans le gestionnaire de mots de passe du mainteneur. | serveur § 1 |
| D37 | Montages et options du service | `pb_migrations` et `pb_hooks` dans `DOSSIER_VPS` (`deploy.env`), propriété de l'utilisateur SSH, montés **en lecture seule** là où PocketBase les cherche par défaut. Commande complétée de `--dev=false` (en 0.42.3, le mode dev est actif par défaut et écrit chaque requête SQL, valeurs comprises, dans les logs) et `--automigrate=false` (le dépôt est la seule source des migrations). `deploy.sh` vérifie montages et options et s'arrête si l'un manque. | serveur § 1, § 9 |
| D38 | Sauvegarde à froid | À chaque déploiement, conteneur arrêté : une archive `eruditus-<date>.tar.gz` de `pb_data`, `pb_migrations` et `pb_hooks` dans `SAUVEGARDES` (`deploy.env`, root seul), avant toute copie ; les 10 plus récentes sont gardées, le tri se fait après un déploiement réussi. Restaurer suppose la même clé de chiffrement des réglages. | serveur § 9 |
| D39 | `deploy.sh` | Déploie toujours un commit : refuse les changements non commités de `pb_migrations` et `pb_hooks`, et une migration déjà déployée puis modifiée (D22). `--essai` : vérifications en lecture seule et liste de ce qui serait copié, sans tests, ni arrêt, ni copie. Rien à copier : pas de redémarrage. Échec avant la copie : conteneur redémarré tel quel. Échec pendant la copie ou à la vérification finale : arrêt avec le journal et les commandes exactes du retour arrière, jamais automatique. | serveur § 9 |
| D40 | Banque et `questions.sh` | La banque vit dans un dépôt privé, cloné dans `server/questions/` (ignoré). `questions.sh` envoie le fichier par SSH sur l'entrée standard de `docker exec -i` : fichier temporaire de `/tmp` du conteneur (droits 600), effacé même en cas d'échec, jamais écrit sur l'hôte. L'import reprend `--dir` et `--encryptionEnv` de la commande du service, avec `--dev=false`. | serveur § 8 |
| D41 | Vibrations avant Android 11 | Confirm et Reject (D28) passent par `ViewCompat.performHapticFeedback` (Compose UI 1.12, androidx.core 1.19) qui, avant Android 11, joue `VIRTUAL_KEY` (tic court) pour Confirm et `LONG_PRESS` (plus appuyé) pour Reject : les équivalents les plus proches, soumis au même réglage « Vibreur au toucher ». Ni code à part, ni permission `VIBRATE`. | `DESIGN.md` § 6 |
| D42 | Catalogue et build `preview` | Builds debug et `preview` : l'app s'ouvre sur un catalogue du design system (tous les états, sombre et clair, vibrations, ressorts, gabarits), pour l'essayer sur un téléphone. Son code et les `@Preview` vivent dans `src/catalogue/`, ajouté à ces deux builds seulement. Release : écran vide au thème de l'app jusqu'à l'étape Écrans. Build type `preview` : `com.eruditus.android.preview`, « Eruditus Preview », signé avec la clé de debug, installable à côté de l'app, jamais publié. Ouverture sur le catalogue et écran vide de la release remplacés par D58. | `DESIGN.md` § 12 |
| D43 | Adresse du serveur au build | `BuildConfig.SERVEUR_URL`, lue dans la variable d'environnement `ERUDITUS_URL` (CI), sinon dans la ligne `URL=` de `server/deploy.env` (local, D36). Le build de l'app échoue si elle manque ou ne commence pas par `https://` (D24), sans jamais l'afficher. Les tests serveur n'en dépendent pas. | serveur § 6.5 |
| D44 | Polices | Newsreader et Inter variables (`wght` + `opsz`) du dépôt `google/fonts`, sans les italiques, dans `res/font/` ; leur `OFL.txt` dans `assets/licences/`. Chaque style typographique règle `opsz` sur sa propre taille. | `DESIGN.md` § 4 |
| D45 | Numéro de version | `-Pnumero=<n>` donne versionCode 1000 + n et versionName `1.0.<n>` (D2) ; sans numéro (build local), 1000 et `1.0.0`. | § 1.4 |
| D46 | Précisions du design system | Fixées à la Fondation dans `DESIGN.md` : paires de contraste ajoutées au tableau (§ 3) ; indicateur en quart de cercle, un tour par seconde ; lien pressé ; sorties de la boîte et du message ; seuls les changements d'état s'animent (§ 6, § 7) ; `RangeeBoutons` (§ 7) ; place réservée du verdict, zone du bas au-dessus du clavier (§ 8) ; taille du E au démarrage (§ 10). | `DESIGN.md` |
| D47 | Vérification des PR | `checks.yml` sur chaque PR vers `main` : un job « Changements » lit les fichiers modifiés ; « Tests et build » (tests JVM de l'app et de `release-notes`, `assembleDebug`, `assemblePreview`, `assembleRelease` non signé) si `android-app/`, `DESIGN.md`, `spec/ecrans.md`, `server/pb_hooks/` (relus par les tests de l'app, D55) ou `.github/workflows/` change ; « Tests serveur » si `server/`, `android-app/` ou `.github/workflows/` change. Un job sauté compte comme réussi. Aucun secret : adresse factice `https://serveur.invalid` (D43). Cache Gradle et cache du zip PocketBase (SHA-256 toujours vérifié). | publication § 1 |
| D48 | Publication | `release.yml` à chaque push sur `main` qui touche l'app (D49) : tests JVM, notes, APK release signé (`-Pnumero=<n>`, numéro du run, D45), signature vérifiée, Release `v1.0.<n>` marquée Latest avec un seul APK, `Eruditus-v1.0.<n>.apk`, comme MuscleNote. Relance : APK remplacé ; ancien run déjà contenu dans une Release plus récente : rien. | publication § 2 |
| D49 | Ce qui touche l'app | Fichier sous `android-app/`, sauf `*.md`, le module `release-notes`, les tests (`src/test*`, `src/androidTest*`) et ce qui ne sert qu'à debug et preview (`src/catalogue`, `src/debug`, `src/preview`). Une seule liste de motifs, dans `paths` de `release.yml` et dans `release-notes`, gardées identiques par un test JVM. Notes : titres des commits qui touchent l'app depuis la Release précédente, sans les fusions, 50 au plus, puis le lien de comparaison. | publication § 3 |
| D50 | Secrets | `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, `ERUDITUS_URL` : lus par `release.yml` seul, chacun dans la seule étape qui en a besoin, jamais affichés. Keystore dans le dossier temporaire du runner, effacé même après un échec ; clé sauvegardée hors dépôt par le mainteneur. Jeton de la CI au minimum, actions épinglées par empreinte. | publication § 4 |
| D51 | Ordre serveur puis app | Précise D22 : le merge publie l'app aussitôt, donc un changement de `pb_migrations` ou `pb_hooks` se déploie par `deploy.sh` depuis sa branche, checks passés, avant le merge. `main` protégée : PR obligatoire, « Tests et build » et « Tests serveur » requis, squash ; plus de commit direct sur `main`. | publication § 5, serveur § 6.6 |
| D52 | Client de l'API | `HttpsURLConnection` du système, sans bibliothèque réseau ni SDK ; JSON lu en arbre par `kotlinx-serialization-json`, sans plugin. Délais : 10 s pour se connecter, 10 s pour la réponse. Une seule liste des routes, chacune publique ou à jeton : l'en-tête `Authorization` n'est posé que sur les routes à jeton, même quand une session existe (D30), vérifié par un test JVM pour chaque route publique. Aucune journalisation HTTP, debug compris (D26). | données § 1, § 3 |
| D53 | Réseau ou serveur | Échec avant que la connexion soit établie (pas de réseau, nom introuvable, connexion refusée ou trop longue, TLS) : « Pas de connexion », texte réseau. Connexion établie puis pas de réponse dans le délai, 5xx, ou réponse qui n'est pas le JSON attendu : texte serveur. Au démarrage, tout autre échec de `/info` ou d'`auth-refresh` (429, code inattendu) donne aussi le texte serveur : aucun autre écran n'est atteignable. | écrans § 1.2, § 13, § 15 |
| D54 | Fichier de session | Précise D12 : `session` dans `noBackupFilesDir`, IV de 12 octets puis le JSON `{ version: 1, jeton, idJoueur, pseudo }` chiffré en AES-256-GCM par une clé du Keystore (`eruditus_session`, non exportable, sans authentification de l'utilisateur) ; écriture atomique. Fichier tronqué, clé perdue, JSON illisible ou version inconnue : fichier et clé effacés, Bienvenue sans message. Pseudo : celui renvoyé par la création ou la récupération ; après une connexion, le pseudo saisi jusqu'au premier `/moi`, qui donne la casse de création (des routes standard, l'app ne lit que `token` et `record.id`). | données § 1, § 2 |
| D55 | Vérifications avant l'envoi | Pseudo sans espaces au début ni à la fin (jamais le mot de passe). Créer un compte : bouton actif quand D9 et D10 sont respectées ; erreur sous le champ dès que la saisie ne peut plus devenir valide (caractère refusé, trop long, plus de 72 octets), l'aide seule tant qu'elle est juste trop courte ; même règle pour « Nouveau mot de passe ». Se connecter : bouton actif quand les deux champs sont remplis, sans autre vérification. Mot de passe oublié : bouton actif quand les trois champs sont remplis ; à l'envoi, le mot de passe (D10) d'abord, puis pseudo (motif D9) et code : mal formés, « Pseudo ou code incorrect. » sans appel au serveur, ce qui ne coûte aucun des 5 essais par heure (D23) et ne révèle rien de plus. Le code est jugé après la saisie tolérante (casse, espaces, tirets, O, I, L), avec exactement la règle de format du serveur (64 caractères saisis au plus, puis 16 symboles de l'alphabet de Crockford). Motif du pseudo, règle du mot de passe, alphabet, motif et normalisation du code : un test JVM les confronte aux hooks (`comptes.js`), et « Tests et build » tourne aussi quand `server/pb_hooks/` change (D47). | écrans § 4, § 6, § 7, règles § 1.6 |
| D56 | Navigation | Pile d'écrans en mémoire (`ViewModel` de l'activité), sans bibliothèque de navigation ; rien dans l'état sauvegardé, ni mot de passe ni code : si Android tue l'app, elle repart de l'aiguillage. Bienvenue, Code de récupération, Accueil, Pas de connexion et Mise à jour nécessaire remplacent la pile ; Créer un compte, Se connecter, Mot de passe oublié et Compte s'y empilent. L'écran de lancement reste affiché pendant l'aiguillage ; « Réessayer » le relance avec l'indicateur dans le bouton. | écrans § 1 |
| D57 | Accueil avant la partie | Jusqu'aux sujets Partie et Classement, l'Accueil montre le pseudo, le score, le rang et le seul petit bouton « Compte » : jamais un bouton qui ne fait rien dans une Release. Premier chargement : `ChargementEcran` à la place du contenu. Erreur de `/moi` : 401 → Bienvenue ; réseau, 5xx → Pas de connexion ; 429 et autres → message en haut, valeurs déjà affichées gardées. | écrans § 8 |
| D58 | Debug, preview et catalogue | Remplace l'ouverture sur le catalogue de D42 : debug et preview s'ouvrent sur l'app, comme la release (dont l'écran vide disparaît). Le catalogue a sa propre activité et sa propre icône « Catalogue » dans le lanceur (le E sur fond bleu), déclarées par le manifeste de `src/catalogue/`, ajouté à ces deux builds seulement : l'APK release n'a ni l'activité, ni l'icône, ni le code. | `DESIGN.md` § 12 |
| D59 | Comptes d'essai | Debug et preview parlent au vrai serveur (même adresse, D43) : ni serveur ni base d'essai. Un compte créé pour essayer est un vrai compte, soumis aux mêmes règles (D9, 5 créations par heure et par IP, D23) et classé dès son premier point (D18). Le mainteneur supprime lui-même ses comptes d'essai depuis l'écran Compte (D14) : ils quittent aussitôt le classement. | écrans § 12 |
| D60 | Mettre à jour | « Mettre à jour » lance Obtainium par son intent de lancement (`dev.imranr.obtainium`, puis `dev.imranr.obtainium.fdroid`), sans `<queries>` ni permission : `startActivity`, et en cas d'`ActivityNotFoundException`, la page des Releases dans le navigateur. Aucun accès réseau de l'app (D26). Remplacée par D63 (Obtainium seulement s'il a installé l'app). | écrans § 14 |
| D61 | Vérification des nouvelles versions | Précise D26 : nouvelle destination réseau, en **build release seulement** (`BuildConfig.VERIFIER_MISES_A_JOUR`). Au chargement de l'Accueil, si elle est due, comme MuscleNote : 6 h après une vérification réussie, 1 h après un échec (jamais, ou date dans le futur après un changement d'heure, comptent comme dues) : `GET https://api.github.com/repos/Bouss45/Eruditus/releases/latest` (dernière Release, ni brouillon ni préversion), HTTPS, sans jeton, sans cookie, sans aucune donnée de l'app ni du joueur ; en-têtes `Accept: application/vnd.github+json`, `X-GitHub-Api-Version: 2022-11-28` et `User-Agent: Eruditus-Android` (exigé par GitHub, sans version ni appareil). Délais 10 s et 10 s, réponse de 1 Mo au plus, une vérification à la fois, aucune journalisation. Échec (réseau, statut autre que 200, limite de 60 requêtes par heure, réponse illisible) : rien d'affiché, jamais « Pas de connexion », la dernière Release connue reste, nouvel essai au plus tôt 1 h après. Pas de notification, pas de tâche de fond. | règles § 6, données § 1 |
| D62 | Annonce sur l'Accueil | Dernière Release connue plus récente que l'app installée et pas ignorée : composant `Annonce` (nouveau, `DESIGN.md` § 7, aperçus sombre, clair et 200 %, visible dans le catalogue) sous les statistiques, « Mise à jour disponible », « Eruditus 1.0.13 est disponible. Version installée : 1.0.12. », « Plus tard » et « Télécharger ». « Plus tard » : cette version n'est plus jamais annoncée, une plus récente le sera. Comparaison reprise de MuscleNote : tag `v1.0.n` → versionCode 1000 + n contre le versionCode installé ; autre forme, nombres un à un contre le versionName (`1.0.10` après `1.0.9`) ; tag sans nombre ignoré. APK : premier fichier `.apk` de la Release. | écrans § 8, règles § 6 |
| D63 | Télécharger et Mettre à jour | Une seule fonction pour « Télécharger » (D62) et « Mettre à jour » (D21) : Obtainium **s'il a installé l'app** (installateur ou initiateur `dev.imranr.obtainium`, `dev.imranr.obtainium.fdroid`, ou `com.android.vending` qu'il emprunte par Shizuku ; `getInstallSourceInfo` dès Android 11, `getInstallerPackageName` avant), lancé comme en D60 (sa variante d'abord, sans `<queries>`) ; sinon, ou s'il ne s'ouvre pas, l'APK de la dernière Release connue dans le navigateur, à défaut sa page, à défaut `https://github.com/Bouss45/Eruditus/releases/latest`. Rien n'est téléchargé ni installé par l'app. | écrans § 8, § 14 |
| D64 | Fichier des mises à jour | `mises-a-jour.json` dans `noBackupFilesDir`, en clair (rien de secret) : `{ version: 1, reussieLe, echecLe, derniereVersion, apk, page, versionIgnoree }` ; écriture atomique ; illisible ou d'une autre version : effacé, comme si rien n'avait été vérifié. Propre à l'appareil, gardé à la déconnexion et à la suppression du compte (il ne dit rien du joueur), effacé à la désinstallation. Absent des builds debug et preview, qui ne vérifient rien. | données § 1, § 5 |
| D65 | Partie depuis l'Accueil | Précise D56 et D57 : « Jouer » (grand bouton principal) arrive sur l'Accueil ; « Classement » reste absent jusqu'à son sujet. « Jouer » appelle `POST /parties` depuis l'Accueil (indicateur dans le bouton, « Compte » désactivé) : la Partie ne s'empile sur l'Accueil qu'au succès, avec la question 1. Le Résultat remplace la Partie ; « Rejouer » remplace le Résultat par une nouvelle Partie (même appel, indicateur dans « Rejouer ») ; « Accueil », le retour système depuis le Résultat et « Abandonner » reviennent à l'Accueil, qui se recharge. La pile ne dépasse jamais Accueil puis Partie ou Résultat. Partie et Résultat vivent en mémoire seulement : si Android tue l'app, elle repart de l'aiguillage et la partie est perdue (D15). | écrans § 1, § 8 – § 10 |
| D66 | Chrono de l'app | Compte à rebours local, calé sur le serveur : il part toujours de `restantMs` (20 s pour une question neuve, moins pour une question redemandée), jamais de 20 s fixes. Fin = horloge monotone (`elapsedRealtime`) à la réception de la question + `restantMs` ; barre = restant / `delaiMs`. Arrêté dès qu'un choix part. À 0, un seul envoi automatique « sans réponse » (`choix: null`) par question. Écran arrêté (arrière-plan, écran éteint) : aucun envoi automatique. Au retour au premier plan ou à la recréation de l'activité, une question qui attend encore sa réponse (ni envoi en cours, ni correction) est redemandée (`POST /parties/{id}/question`) et le chrono recalé sur le nouveau `restantMs`, jamais reparti de 20 s ; à 0, envoi « sans réponse » aussitôt (au-delà de 23 s, le serveur répond `hors_delai`). Pendant la redemande, les choix restent touchables et l'envoi automatique attend sa réponse. Correction affichée : rien n'est redemandé (ce serait servir la question suivante). | règles § 2.2, écrans § 9 |
| D67 | Un envoi, une correction | Le premier choix touché part ; tout autre appui est ignoré jusqu'à la question suivante. Correction : verdict et détail selon `raison` (écrans § 9) ; vert sur `bonneReponse`, rouge sur le choix du joueur s'il en diffère. Mouvement et vibration (`DESIGN.md` § 6) : `gagne` et `deja_reussie` → bonne réponse choisie, Confirm ; `fausse` → « non » et Reject, puis la bonne révélée ; `sans_reponse`, ou `hors_delai` sans choix ou sur la bonne réponse → bonne réponse en fondu, Reject ; `hors_delai` sur un autre choix → comme `fausse`, verdict « Temps écoulé. ». Les états des choix ne dépendent que de `correcte`, `choix` et `bonneReponse`. `raison` inconnue (serveur plus récent, D22) : la partie continue, verdict tiré des champs toujours présents : `correcte` → « Bonne réponse ! », suivi de « +1 point » si `point` vaut 1 ; sinon « Mauvaise réponse. » et « La bonne réponse était X. ». TalkBack : le temps à 10 s et à 5 s, puis le verdict et le détail avec le texte de la bonne réponse. Seule une question ou une correction vraiment illisible (champ obligatoire absent ou de mauvais type, 4 choix attendus, autre numéro, `bonneReponse` hors de 0 – 3, identifiant de partie qui n'est pas fait de lettres et de chiffres) donne « Pas de connexion », texte serveur (D53). | écrans § 9, `DESIGN.md` § 6 |
| D68 | Erreurs de la partie | `POST /parties` : `banque_insuffisante` (503 avec son code : un refus, pas une panne) → message info en haut de l'Accueil, où « Rejouer » ramène aussi ; 429 et tout autre refus → message en haut de l'écran en cours (Accueil ou Résultat). Question et réponse : `partie_introuvable`, `partie_finie`, `numero_invalide`, `deja_repondu` → Accueil avec « Cette partie n'est plus valable. » (info) ; 429 et tout autre refus → message alerte en haut de la Partie, et l'action redevient possible (« Question suivante », ou les choix tant que le chrono tourne ; l'envoi automatique de 0 s n'est pas répété, il reste « fermer ») ; réseau, 5xx → Pas de connexion, partie perdue, « Réessayer » relance l'aiguillage qui mène à l'Accueil ; 401 → Bienvenue, session expirée. Un message d'arrivée sur l'Accueil reste pendant le rechargement de `/moi`. Une réponse du serveur arrivée après qu'on a quitté la partie est ignorée. | écrans § 9, § 15 |
| D69 | Résultat | Gabarit Information, d'après le `bilan` de la 5e correction : titre « 3 bonnes réponses sur 5 » (« 1 bonne réponse sur 5 », « 0 bonne réponse sur 5 ») ; statistique sans libellé « +2 points » (« +1 point » ; 0 : « Aucun point cette fois ») ; « Score : 14 points » en `texte-secondaire` ; « Accueil » (secondaire) et « Rejouer » (principal). | écrans § 10 |
| D70 | Partie à 200 % | Pendant le chrono, les 4 choix restent visibles sans faire défiler : la zone qui défile se cale sur le choix D, et c'est le haut de l'énoncé qui sort de l'écran. À la correction, elle se cale sur le bas du verdict (`duree.resultat`, direct si les animations sont coupées) : verdict et détail toujours visibles au-dessus du bouton, seul fixé en bas ; le joueur peut remonter. | écrans § 9, `DESIGN.md` § 8 |
| D71 | Classement depuis l'Accueil | Précise D57 et D65 : « Classement » arrive sur l'Accueil, petit `BoutonSecondaire` à gauche de « Compte » dans la `RangeeBoutons`, comme dans les maquettes ; désactivé avec « Compte » pendant la création d'une partie. Le Classement s'empile sur l'Accueil (D56) ; retour : l'Accueil, qui se recharge. | écrans § 1, § 8 |
| D72 | Écran Classement | `GET /classement`, serveur inchangé. L'app affiche les rangs, l'ordre et les scores du serveur sans rien recalculer. Ma ligne dans le top 10 : celle dont le pseudo est celui de `moi`, casse ignorée (pseudo unique, D9 ; aucune ligne n'a d'identifiant, T30), mise en avant et marquée « Toi ». Classé hors du top 10, ex æquo coupé par la 10e ligne compris (il garde son rang, « 10e ») : `SeparateurPoints` puis ma ligne seule dans une `ListeGroupee` teintée. `moi` nul : `Encadre` « Réussis une question pour entrer au classement. » sous le top 10. Top vide et `moi` nul : « Personne n'est encore classé. Lance une partie ! » seul, centré, sans pied. Pied : « 48 joueurs classés » (« 1 joueur classé »), d'après `classes`. TalkBack lit chaque ligne d'un seul bloc : « rang 27, Hibou_42, 4 points, toi » (« rang 1, Hypatie, 19 points »). Accords (« 1er », « 2e », « 0 point », « 1 point », « 2 points », « 1 joueur classé ») vérifiés par un test JVM sur `strings.xml` même, avec les règles de pluriel d'ICU, celles qu'Android applique (`android.icu.text.PluralRules.forLocale` dans `ResourcesImpl`, d'Android 8 à aujourd'hui) : en français, 0 et 1 au singulier (CLDR) ; `many` (millions, Android 12 et plus) a le texte d'`other`, pour que toutes les versions disent la même chose. ICU4J en test seulement, absent de l'APK. Plus de 10 lignes dans `top` : les 10 premières, le reste ignoré (un serveur plus récent peut en envoyer plus, D22, comme une raison inconnue, D67). Réponse illisible → « Pas de connexion », texte serveur (D53), seulement si un champ obligatoire manque ou a le mauvais type : `top` absent ou pas un tableau ; une des 10 premières lignes qui n'est pas un objet ; `rang`, `score` ou `classes` absent ou pas un entier ; `pseudo` absent ou pas un texte ; `moi` absent, ni `null` ni un objet. Champs inconnus ignorés (D22). | écrans § 11, règles § 4 |
| D73 | Chargement du Classement | À chaque ouverture : `ChargementEcran` centré jusqu'à la première réponse. Ensuite, tirer la liste vers le bas recharge : indicateur de tirage, et les lignes restent à l'écran jusqu'à la nouvelle réponse ; un seul chargement à la fois. En mémoire seulement, oublié en quittant l'écran (D20) ; une réponse arrivée après qu'on l'a quitté est ignorée. Erreurs : 401 → Bienvenue, session expirée ; réseau → Pas de connexion ; 5xx, pas de réponse, réponse illisible → Pas de connexion, texte serveur ; 429 et tout autre refus → message en haut (alerte), lignes déjà affichées gardées ; au premier chargement, le message seul, et tirer vers le bas réessaie. Le message s'efface au tirage suivant. | écrans § 1, § 11 |
| D74 | `LigneClassement` d'un seul bloc | Précise `DESIGN.md` § 7 : la ligne prend la description TalkBack que lui donne l'écran (`description`), qui remplace la lecture de ses textes (rang, pseudo, « Toi », nombre, unité) ; apparence inchangée. | `DESIGN.md` § 7 |
| D75 | `LigneClassement` à 200 % | Précise `DESIGN.md` § 7, comme `LigneInfo` : les points restent à droite du pseudo, ou passent dessous, alignés à gauche, quand les deux ne tiennent pas côte à côte ; le pseudo garde alors toute la largeur de la ligne. Il ne passe à la ligne au milieu du mot que s'il est plus large que toute la ligne (un pseudo n'a pas d'espace où couper). À 100 %, les lignes des maquettes ne changent pas. | `DESIGN.md` § 7 |

---

## 4. État d'avancement

### 4.1 Fait

| étape | quoi | où |
|---|---|---|
| Spec | interview de lancement, décisions D1 – D26, spec découpée par sujet, CLAUDE.md | `SPEC.md`, `spec/`, `CLAUDE.md` |
| Design | direction B · Glacier, design system, écrans et icône dans Claude Design ; `DESIGN.md` ; décisions D27 – D29 | `design/`, `DESIGN.md` |
| Serveur, partie 1 | PocketBase 0.42.3 épinglé ; migrations (collections, règles fermées, limiteur, IP réelle, `users` fermée) ; routes `/api/eruditus/v1/…` et commande `eruditus-questions` ; 73 tests serveur (contrat v1, chaque erreur, T1 – T33, D30, import, migrations et redémarrage) ; squelette Gradle d'`android-app/` avec le module `:server-tests` ; décisions D30 – D35, D10 précisée | `server/`, `android-app/` |
| Serveur, partie 2 | `deploy.sh` (tests, vérifications du VPS, `--essai`, sauvegarde à froid, copie, vérification en ligne, retour arrière affiché), `questions.sh` et `commun.sh`, vérifiés par shellcheck ; banque initiale de 30 questions vérifiées, dans un dépôt privé ; montages en lecture seule et `--automigrate=false` sur le VPS ; décisions D36 – D40 ; premier déploiement et import des 30 questions par le mainteneur (19 novembre 2026), qui ont fait apparaître puis corriger la détection des fichiers envoyés par rsync | `server/`, dépôt privé |
| Fondation de l'app | module `:app` (minSdk 26, targetSdk 37, portrait, français) ; design system `ui/designsystem/` : thème sombre et clair, tokens, Newsreader et Inter embarquées (OFL), les 23 composants de `DESIGN.md` § 7 et `RangeeBoutons` avec tous leurs états, les 5 gabarits, ressorts et durées, animations coupées quand Android les désactive ; vibrations Confirm et Reject ; icône adaptative (monochrome comprise) et écran de démarrage ; build type `preview` ; catalogue des composants en debug et preview, écran vide en release ; aperçus sombre, clair et 200 % comparés aux planches ; adresse du serveur au build ; 24 tests JVM (contrastes, couleurs et typographie confrontées à `DESIGN.md`, mouvement, chrono, code, rangée) ; décisions D41 – D46 (20 novembre 2026) | `android-app/app/`, `DESIGN.md` |
| Publication | `checks.yml` sur chaque PR (« Tests et build », « Tests serveur », sautés quand rien ne les concerne, caches Gradle et PocketBase, sans secret) ; `release.yml` à chaque push sur `main` qui touche l'app (tests JVM, APK signé, Release `v1.0.<n>` avec `Eruditus-v1.0.<n>.apk`) ; module `release-notes` repris de MuscleNote (notes depuis les seuls commits qui touchent l'app, même liste de motifs que le déclenchement) ; décisions D47 – D51 (20 novembre 2026) ; `main` protégée par le mainteneur (PR, deux checks requis) | `.github/workflows/`, `android-app/release-notes/`, `spec/publication.md` |
| Écrans du compte | client de l'API (aucun jeton sur les routes publiques), session chiffrée par le Keystore, aiguillage au démarrage ; Démarrage, Bienvenue, Créer un compte, Code de récupération, Se connecter, Mot de passe oublié, Accueil (sans « Jouer » ni « Classement »), Compte (déconnexion, suppression), Pas de connexion, Mise à jour nécessaire ; debug et preview ouverts sur l'app, catalogue derrière sa propre icône ; 71 tests JVM (règles du pseudo, du mot de passe et du code confrontées aux hooks, messages d'erreur confrontés au § 15, client de l'API, aiguillage, jeton, fichier de session, effacement) ; 19 aperçus d'écran, sombre, clair et 200 % ; décisions D52 – D60 (20 novembre 2026) | `android-app/app/`, `spec/` |
| Mises à jour | annonce des nouvelles versions sur l'Accueil, comme MuscleNote mais sans notification : vérification sur l'API des Releases GitHub 6 h après une réussite, 1 h après un échec, en release seulement, échec silencieux ; « Plus tard » et « Télécharger » ; Obtainium s'il a installé l'app, sinon l'APK dans le navigateur, même règle pour « Mettre à jour » ; composant `Annonce`, visible dans le catalogue ; 31 tests JVM repris de MuscleNote (`UpdateRules`), Shizuku compris ; aperçus de l'`Annonce` et de l'Accueil qui l'affiche, sombre, clair et 200 % ; décisions D61 – D64 (20 novembre 2026) | `android-app/app/`, `spec/`, `DESIGN.md` |
| Partie et Résultat | « Jouer » sur l'Accueil (« Classement » toujours absent) ; partie de 5 questions servies, chronométrées et corrigées par le serveur ; compte à rebours qui part du temps restant envoyé par le serveur, « sans réponse » à 0 et jamais en arrière-plan, question redemandée au retour ou à la recréation de l'écran ; un seul envoi par question ; correction avec ses mouvements et vibrations, raison inconnue lue sans casser la partie ; abandon ; Résultat et « Rejouer » ; « Aucune partie disponible » et « Cette partie n'est plus valable » sur l'Accueil ; texte à 200 % : défilement jusqu'au verdict ; 48 tests JVM (routes et lecture de la partie, verdicts et états des choix, déroulement en temps virtuel, erreurs) ; 9 aperçus d'écran, sombre, clair et 200 % ; décisions D65 – D70 (20 novembre 2026) ; serveur inchangé | `android-app/app/`, `spec/`, `DESIGN.md` |
| Classement | « Classement » sur l'Accueil, à gauche de « Compte » ; écran Classement : top 10 avec les rangs du serveur (ex æquo compris), ma ligne reconnue à mon pseudo, mise en avant dans le top 10 ou seule sous le séparateur (ex æquo coupé par la 10e ligne compris), « Réussis une question pour entrer au classement. », « Personne n'est encore classé. Lance une partie ! », « 48 joueurs classés » ; indicateur centré à chaque ouverture, rechargement en tirant sans effacer les lignes ; 10 premières lignes lues, le reste ignoré ; chaque ligne lue d'un seul bloc par TalkBack ; 43 tests JVM (lecture de la réponse, ma ligne à chaque place, accords lus dans `strings.xml` avec les pluriels d'ICU comme Android, chargement et erreurs) ; 7 aperçus d'écran et l'Accueil avec « Classement », sombre, clair et 200 % ; décisions D71 – D75 (21 novembre 2026), D70 rétablie ; à 200 %, les points passent sous le pseudo plutôt que de le couper ; serveur inchangé | `android-app/app/`, `spec/`, `DESIGN.md` |

Le serveur est en ligne, avec la banque initiale ; l'app s'ouvre sur l'aiguillage et les écrans du
compte, dans les trois builds, et l'on joue et consulte le classement depuis l'Accueil ; le catalogue a sa propre icône en debug et preview ; la release
annonce ses nouvelles versions sur l'Accueil. Spec, design,
serveur et fondation ont été commités directement sur `main` ; depuis la Publication, tout passe
par une PR (D51).

### 4.2 À faire, dans l'ordre prévu

Le squelette de la v1 (§ 1.2) est complet : compte, partie test, classement. La suite se choisit
dans les Évolutions (§ 5).

### 4.3 Écarts connus

| écart | détail | à régler |
|---|---|---|
| Partie à 200 % | Pour garder les 4 choix visibles pendant le chrono, un énoncé long sort par le haut ; après la réponse, le verdict, sous le choix D, peut rester sous le bord (seul le bouton est fixé, TalkBack l'annonce). | Écrans, Partie |
| Contour des choix | `contour` sur `surface` : 2,0:1 (sombre), 1,7:1 (clair), sous 3:1 ; accepté, la pastille et le texte identifient le choix (`DESIGN.md` § 3). | — |
| Chrono arrêté en clair | Barre `neutre` sur `piste` : 3,7:1 (sombre), 2,7:1 (clair), sous 3:1 ; accepté, les secondes affichées donnent la même information et la barre arrêtée ne compte plus (`DESIGN.md` § 3). | — |
| Marge de l'action du bas | L'action du bas est à 16 au-dessus de la barre de navigation (`DESIGN.md` § 5) ; les maquettes la posent directement sur la marge de 24 de leur cadre, soit environ 16 plus bas. | — |
| Marges des maquettes d'écran | Trois blocs ont 4 à 8 de différence avec les maquettes, qui ne sont pas régulières entre elles : premier champ de « Mot de passe oublié » (8 au lieu de 4), lien sous le dernier champ de « Se connecter » en 429 (12 au lieu de 16), liste du Compte (12 sous la barre au lieu de 20). | Écrans |
| Titres avant Android 13 | Les titres sont équilibrés sur leurs lignes comme dans les maquettes (`LineBreak.Heading`) à partir d'Android 13 ; avant, coupure simple. | — |
| Permission interne d'androidx.core | Le manifeste fusionné déclare aussi `<app>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, permission de signature propre à l'app ajoutée par androidx.core : ni demandée ni visible, elle ne donne aucun accès. Seule permission demandée : `INTERNET` (D26). | — |
| Vibrations et boîtes sur le téléphone | Confirm et Reject avant Android 11 (D41), la fenêtre de la `Boite` (voile, ombre, retour système) et le clavier sur les formulaires ne se voient pas dans les aperçus : à vérifier sur le téléphone, avec le catalogue. De même pour les boîtes des écrans du compte (retour sur le code, déconnexion, suppression), le presse-papiers et le lancement d'Obtainium. | Test du mainteneur |
| Tests serveur sur Linux amd64 | Le binaire épinglé est `linux_amd64` : les tests serveur ne tournent ni sur macOS ni sur ARM. Suffisant pour le PC du mainteneur et la CI. | — |
| Adresse du serveur dans l'APK | Hors du dépôt (D36), mais lisible dans l'APK publié (`BuildConfig`), comme pour toute app en ligne ; la sécurité repose sur le serveur (D8, D23), pas sur le secret de l'adresse (`spec/publication.md` § 6). | — |
| Annonce de mise à jour sur le téléphone | Release seulement (D61) : la variante preview ne vérifie rien. Sur le téléphone, l'annonce se voit dans le catalogue ; la vraie ne paraît qu'une fois la release installée et une Release plus récente publiée. | Test du mainteneur |
| Partie sur le téléphone | Les aperçus montrent l'état final de chaque correction : les vibrations Confirm et Reject, le « non », le rebond, la question suivante, la boîte d'abandon, le retour d'arrière-plan (question redemandée, chrono recalé) et les annonces TalkBack à 10 s, à 5 s et au verdict se vérifient sur le téléphone. | Test du mainteneur |
| Démarrage sans réponse du serveur | L'écran de lancement reste le temps de l'aiguillage (D56) : jusqu'à 20 s si le serveur accepte la connexion mais ne répond pas (D52), avant « Pas de connexion ». | — |
| Erreur sur un formulaire | Réseau coupé ou 5xx sur Créer un compte, Se connecter ou Mot de passe oublié : « Pas de connexion », et la saisie est perdue (§ 15 de `spec/ecrans.md`, rien n'est gardé). | — |
| Classement à 200 % | Les points passent sous le pseudo quand les deux ne tiennent pas côte à côte (D75) ; seul un pseudo plus large que toute la ligne (« MarieCurieDuDimanche ») passe encore à la ligne au milieu du mot, faute d'espace où couper. Rien n'est coupé ni caché. | — |
| Classement sur le téléphone | Les aperçus montrent l'état final : le geste de tirage, l'indicateur qui tourne pendant le rechargement, la sortie de l'écran et la lecture de chaque ligne par TalkBack se vérifient sur le téléphone. | Test du mainteneur |
| `/moi` et `/classement` hors transaction | Score, rang et nombre de classés sont lus en trois requêtes : une réponse enregistrée au même instant peut décaler l'un d'eux d'un point, jusqu'à la requête suivante. Accepté : rien n'est écrit, et tout est recalculé à chaque demande. | — |

---

## 5. Évolutions et pistes

### 5.1 L'app de culture générale complète

Après le squelette, chacune avec ses décisions (effet sur le score D17 et le classement D18) :

- **Session du jour** : une série de questions par jour, avec son propre résultat.
- **Thèmes** : questions classées par thème (histoire, géographie, sciences, arts…), partie par
  thème, résultats par thème.
- **Révision espacée** : les questions ratées reviennent à intervalles croissants. Le serveur sait
  déjà quelles questions chaque joueur a vues et réussies (`eruditus_reponses`).
- **Contenu sourcé** : chaque question avec sa source et une courte explication montrée dans la
  correction ; banque relue.

### 5.2 Pistes

- **Changer de mot de passe** une fois connecté, et **générer un nouveau code de récupération**
  (mot de passe redemandé) : en v1, seule la récupération change le mot de passe.
- **Saisons** : remise à zéro périodique du classement et historique (D17).
- **Classement par période** (semaine, mois) : les points gagnés sur la période, à côté du
  classement de toujours, sans remise à zéro du score.
- **Recherche d'un joueur** dans le classement, par son pseudo.
- **Temps prolongé** (accessibilité) : 20 s est court avec TalkBack ou en lecture lente ; un
  réglage par compte, lu par le serveur.
- **Choix mélangés** à chaque partie.
- **Verrou par compte** après des échecs de connexion répétés (en v1, limite par IP seulement,
  D23) ; risque : permettre de bloquer exprès le compte de quelqu'un d'autre.
- **Comptes multiples** : freiner la récolte des bonnes réponses par un second compte
  (`spec/serveur.md` § 6.7).
- **Reprise d'une partie** coupée par le réseau, ou par Android qui tue l'app en arrière-plan.
- **Changer de pseudo.**
- **Notification de nouvelle version** (tâche de fond, comme MuscleNote D39) : hors périmètre de
  D61 ; l'annonce de l'Accueil suffit tant qu'Obtainium prévient la plupart des joueurs.
- **Serveur d'essai** pour debug et preview, si les comptes d'essai sur le vrai serveur (D59)
  finissent par gêner le classement.
- **Anglais.**

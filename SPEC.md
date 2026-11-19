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
- Installation depuis les Releases GitHub du dépôt public `Bouss45/Eruditus` avec Obtainium ;
  une fois la publication en place (§ 4.2), chaque push sur `main` publiera une Release
  `v1.0.<n>` (versionCode 1000 + n). Pas de Play Store.
- Une seule permission : `INTERNET` (D26).

---

## 2. Fichiers de spec

| fichier | contenu | à lire pour |
|---|---|---|
| [`spec/ecrans.md`](spec/ecrans.md) | navigation, aiguillage au démarrage, chaque écran, textes, messages d'erreur | toute interface |
| [`spec/regles.md`](spec/regles.md) | compte, partie, score, classement, version minimale | toute logique de compte ou de jeu |
| [`spec/serveur.md`](spec/serveur.md) | instance, collections, règles d'accès, réglages, routes, **sécurité de l'API**, tentatives de triche, banque de questions, mise à jour du serveur, tests serveur | tout ce qui touche `server/` ou un appel réseau |
| [`spec/donnees.md`](spec/donnees.md) | ce que le téléphone garde, comment il le protège et l'efface | stockage local, session |

`DESIGN.md` fait foi sur l'apparence des écrans décrits dans `spec/ecrans.md` (tokens, polices,
composants, gabarits, mouvement, icône). L'export Claude Design est dans `design/claude-design/`,
l'icône dans `design/icone/`.

---

## 3. Décisions

D1 – D26 : interview de lancement ; D27 – D29 : étape Design ; D30 – D35 : étape Serveur ;
D36 – D40 : étape Serveur, partie 2 (19 novembre 2026).

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
| D12 | Session | Jeton valable 30 jours, renouvelé à chaque démarrage de l'app ; gardé chiffré (Android Keystore), exclu des sauvegardes Android ; jamais le mot de passe. | règles § 1.4, données |
| D13 | Déconnexion | Invalide côté serveur tous les jetons du compte, puis efface la session locale ; sans réseau, effacement local seul. | règles § 1.5 |
| D14 | Suppression du compte | Mot de passe redemandé ; le serveur efface le compte, ses parties et ses réponses ; il quitte le classement. | règles § 1.7 |
| D15 | Partie test | 5 questions à 4 choix, 20 s chacune mesurées par le serveur (+ 3 s de marge réseau) ; hors délai = fausse ; questions jamais servies d'abord ; commencer une partie abandonne la précédente ; une partie coupée est perdue, les réponses envoyées restent acquises. | règles § 2 |
| D16 | Bonne réponse | Champ caché, jamais envoyé avant que le joueur ait répondu à cette question ; renvoyé dans la correction. | règles § 2.3, serveur § 6.3 |
| D17 | Score | 1 point par bonne réponse dans le délai ; une question ne rapporte qu'une fois par joueur ; cumul de toutes les parties, sans remise à zéro en v1. Calculé par le serveur à partir des réponses enregistrées, jamais stocké ni écrit par l'app. | règles § 2.4, § 3 |
| D18 | Classement | Top 10 + ma ligne ; joueurs à 0 point non classés ; ex æquo au même rang (1, 2, 2, 4), puis ordre alphabétique ; réservé aux comptes. | règles § 4 |
| D19 | Sans compte | Seul l'écran Bienvenue est accessible (« Créer un compte », « Se connecter »). | écrans § 3 |
| D20 | Sans réseau | Écran « Pas de connexion » avec « Réessayer » ; aucune donnée en cache. | écrans § 13, données |
| D21 | Version minimale | Route publique `/info` ; une app plus ancienne affiche « Mise à jour nécessaire » (Obtainium, sinon la page des Releases). | règles § 5 |
| D22 | Compatibilité | Routes `/v1` changées seulement par ajout ; l'app ignore les champs inconnus ; migrations toujours nouvelles ; serveur déployé avant l'app ; un test de contrat publié n'est jamais modifié. | serveur § 6.6, § 9 |
| D23 | Limite de tentatives | Limiteur intégré de PocketBase activé, par IP : connexion, création de compte, récupération, suppression, parties, reste de l'API. IP réelle lue dans `X-Forwarded-For` posé par le proxy HTTPS (prérequis : PocketBase joignable seulement par ce proxy). | serveur § 4 |
| D24 | HTTPS seulement | Trafic en clair interdit dans l'app ; le build échoue si l'URL du serveur n'est pas en `https://` ; pas d'épinglage de certificat. | serveur § 6.5 |
| D25 | Tests serveur | Module JVM qui lance le binaire épinglé sur une base vide avec les migrations et hooks du dépôt, puis joue chaque route et les tentatives de triche T1 – T33 ; lancé par `deploy.sh` avant toute copie, puis par la CI. | serveur § 7, § 10 |
| D26 | Permissions et réseau | `INTERNET` seule ; aucun autre accès réseau que le serveur ; ni jeton, ni mot de passe, ni code de récupération dans les logs. | données |
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

---

## 4. État d'avancement

### 4.1 Fait

| étape | quoi | où |
|---|---|---|
| Spec | interview de lancement, décisions D1 – D26, spec découpée par sujet, CLAUDE.md | `SPEC.md`, `spec/`, `CLAUDE.md` |
| Design | direction B · Glacier, design system, écrans et icône dans Claude Design ; `DESIGN.md` ; décisions D27 – D29 | `design/`, `DESIGN.md` |
| Serveur, partie 1 | PocketBase 0.42.3 épinglé ; migrations (collections, règles fermées, limiteur, IP réelle, `users` fermée) ; routes `/api/eruditus/v1/…` et commande `eruditus-questions` ; 73 tests serveur (contrat v1, chaque erreur, T1 – T33, D30, import, migrations et redémarrage) ; squelette Gradle d'`android-app/` avec le module `:server-tests` ; décisions D30 – D35, D10 précisée | `server/`, `android-app/` |
| Serveur, partie 2 | `deploy.sh` (tests, vérifications du VPS, `--essai`, sauvegarde à froid, copie, vérification en ligne, retour arrière affiché), `questions.sh` et `commun.sh`, vérifiés par shellcheck ; banque initiale de 30 questions vérifiées, dans un dépôt privé ; montages en lecture seule et `--automigrate=false` sur le VPS ; décisions D36 – D40 ; premier déploiement et import des 30 questions par le mainteneur (19 novembre 2026), qui ont fait apparaître puis corriger la détection des fichiers envoyés par rsync | `server/`, dépôt privé |

Pas encore d'app (module `:app`) ; le serveur est en ligne, avec la banque initiale. Exception de
démarrage : spec, design et serveur sont commités directement sur `main` (pas encore de CI).

### 4.2 À faire, dans l'ordre prévu

1. **Fondation de l'app** : projet Gradle, design system `ui/designsystem/` (tokens, polices,
   composants et gabarits de `DESIGN.md`) avec `@Preview` sombre et clair, icône et écran de
   démarrage, variante `.preview` installable à côté de l'app ; adresse du serveur fournie au
   build, hors dépôt (D36).
2. **Publication** : CI (tests JVM et serveur, APK signé, Release, notes de version), `main`
   protégée ; fin de l'exception « commit direct sur `main` ».
3. **Écrans** : compte (Bienvenue, création, code, connexion, récupération, Compte), partie et
   résultat, classement, états « Pas de connexion » et « Mise à jour nécessaire ».

### 4.3 Écarts connus

| écart | détail | à régler |
|---|---|---|
| Vibrations sous Android 11 | `HapticFeedbackType.Confirm` et `Reject` (D28) s'appuient sur des retours haptiques d'Android 11 ; leur effet sur Android 8 à 10 (minSdk 26) reste à vérifier. | Fondation |
| Partie à 200 % | Pour garder les 4 choix visibles pendant le chrono, un énoncé long sort par le haut ; après la réponse, le verdict, sous le choix D, peut rester sous le bord (seul le bouton est fixé, TalkBack l'annonce). | Écrans, Partie |
| Contour des choix | `contour` sur `surface` : 2,0:1 (sombre), 1,7:1 (clair), sous 3:1 ; accepté, la pastille et le texte identifient le choix (`DESIGN.md` § 3). | — |
| Tests serveur sur Linux amd64 | Le binaire épinglé est `linux_amd64` : les tests serveur ne tournent ni sur macOS ni sur ARM. Suffisant pour le PC du mainteneur et la CI. | — |
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
- **Temps prolongé** (accessibilité) : 20 s est court avec TalkBack ou en lecture lente ; un
  réglage par compte, lu par le serveur.
- **Choix mélangés** à chaque partie.
- **Verrou par compte** après des échecs de connexion répétés (en v1, limite par IP seulement,
  D23) ; risque : permettre de bloquer exprès le compte de quelqu'un d'autre.
- **Comptes multiples** : freiner la récolte des bonnes réponses par un second compte
  (`spec/serveur.md` § 6.7).
- **Reprise d'une partie** coupée par le réseau.
- **Changer de pseudo.**
- **Anglais.**

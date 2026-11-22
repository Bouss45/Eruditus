# Serveur : PocketBase

> Partie de la spec (`SPEC.md` § 2). **Dn** renvoie aux Décisions de `SPEC.md` § 3, **Tn** aux
> tentatives de triche du § 7. Les règles de jeu appliquées par ces routes sont dans
> `regles.md`.

---

## Sommaire

1. Instance et prérequis
2. Contenu de `server/`
3. Collections et règles d'accès
4. Réglages
5. Routes
6. Sécurité de l'API
7. Tentatives de triche à faire échouer
8. Banque de questions
9. Mise à jour du serveur
10. Tests serveur

---

## 1. Instance et prérequis

- **PocketBase 0.42.3**, dans un conteneur Docker derrière un proxy HTTPS, sur un VPS dont
  l'adresse n'est pas écrite dans le dépôt (D36). Instance dédiée à Eruditus (D3, D5).
- **Où se publie PocketBase** : depuis le 26-10-2026, le dépôt `github.com/pocketbase/pocketbase`
  est archivé et figé à la v0.40.6. **0.40.6 n'est pas la dernière version** : les versions 0.41
  et suivantes sont publiées uniquement sur `https://ganigeorgiev.com/pocketbase/pocketbase`.
  - Binaire : `https://ganigeorgiev.com/meta/pocketbase/pocketbase/download/v0.42.3/pocketbase_0.42.3_linux_amd64.zip`,
    empreintes dans `checksums.txt` du même dossier.
  - Documentation de l'API REST, à jour pour 0.42.3 : `https://pocketbase.io/docs`, en
    particulier `api-records` et `authentication`.
  - Changements cassants entre 0.40.6 et 0.42.3 : le chemin du module Go (sans effet pour des
    migrations et hooks en JavaScript) et, depuis 0.42.0, un intergiciel global qui répond **401 à
    tout jeton inconnu ou expiré, sur n'importe quelle route, publique comprise**. D'où D30 : l'app
    n'envoie jamais de jeton sur une route publique (§ 5).
  - Autres comportements de 0.42.3 dont dépend le code : le `data` d'une erreur levée par un hook
    est réécrit (D31) ; `requireAuth` répond 403 à un jeton d'une autre collection (D31) ; le
    `record` des réponses de connexion contient `email` (D33) ; le limiteur prend la première
    règle de préfixe dans l'ordre de la liste (D34) ; les commandes ajoutées par les hooks
    n'appliquent pas les migrations (§ 8).
- **Version épinglée** : `server/pocketbase.version` (version, URL du zip, SHA-256 lu dans
  `checksums.txt`). Les tests serveur utilisent exactement ce binaire. Monter de version = une
  décision, les tests serveur passés sur la nouvelle version, puis la mise à jour du VPS par le
  mainteneur.
- **Sur le VPS** (D36, D37) : la configuration du conteneur (montages, commande) est faite hors
  dépôt, par le mainteneur seul ; les scripts n'y touchent jamais et ne font que la vérifier. Le
  détail de l'installation (adresse, outil de gestion, chemins, comptes) n'est écrit nulle part
  sur GitHub : il vit dans `server/deploy.env` (local, non versionné). Accès par un alias SSH ;
  les commandes d'administration passent par `sudo -n`.
  - Montages : `pb_data` dans un volume ; `pb_migrations` et `pb_hooks` de `DOSSIER_VPS` (§ 9),
    propriété de l'utilisateur SSH, **en lecture seule** là où PocketBase les cherche par défaut,
    à côté de son dossier de données (`--dir`). Le conteneur ne peut pas modifier son propre code.
  - Commande : `serve` avec `--dir`, `--encryptionEnv` (réglages chiffrés), **`--dev=false`** et
    **`--automigrate=false`**. En 0.42.3, le mode dev est **actif par défaut** et écrit chaque
    requête SQL, valeurs comprises (empreintes, `tokenKey`, bonnes réponses), dans les logs du
    conteneur. Sans `--automigrate=false`, une modification de collection dans le tableau de bord
    écrirait une migration hors dépôt. `deploy.sh` vérifie montages et options (§ 9).
- **Hors dépôt** (installation, à ne pas traiter ici) : Docker et son outil de gestion, proxy
  HTTPS, comptes superutilisateur et restriction de leur accès. Prérequis à la charge du
  mainteneur :
  - le port de PocketBase n'est joignable que par le proxy, sinon l'en-tête `X-Forwarded-For` peut
    être forgé et la limite de tentatives contournée (§ 4.3) ;
  - le proxy remplace le `X-Forwarded-For` reçu du client par l'IP réelle ;
  - HTTP est redirigé vers HTTPS ;
  - une copie de la clé de chiffrement des réglages (la variable nommée par `--encryptionEnv`) hors
    du VPS, dans son gestionnaire de mots de passe : sans elle, les archives de `deploy.sh` (D38)
    ne se restaurent plus si le VPS ou sa configuration disparaît ;
  - pour les avis (D88, D92) : la variable `ERUDITUS_AVIS_WEBHOOK` dans l'environnement du
    conteneur, réglée par lui seul (adresse du webhook Discord, jamais écrite dans le dépôt ni dans
    `deploy.env`), et le conteneur qui joint Discord en HTTPS. Sans elle, les avis sont refusés
    (`avis_indisponible`) et rien d'autre ne change.
- L'app n'emporte jamais d'identifiant de superutilisateur ; les hooks n'en ont pas besoin
  (`$app` écrit sans passer par les règles d'accès).

---

## 2. Contenu de `server/`

Prévu pour l'étape serveur (`SPEC.md` § 4.2) :

```
server/
  pocketbase.version          version épinglée, URL, SHA-256
  pb_migrations/              <horodatage>_eruditus_<sujet>.js
  pb_hooks/
    eruditus.pb.js            routes /api/eruditus/v1/…, commande d'import
    eruditus/                 modules chargés par require()
  tests/                      module JVM :server-tests (inclus dans le build Gradle d'android-app/)
    questions-exemple.json    questions factices pour les tests
    cas-avis.json             cas de la règle du texte des avis, joués aussi par les tests de l'app (D86)
  deploy.sh                   déploiement (§ 9)
  questions.sh                import de la banque (§ 8)
  commun.sh                   code commun aux deux scripts (deploy.env, SSH, commande du service)
  deploy.env.example          modèle ; deploy.env (non versionné) donne l'hôte SSH et les chemins
```

`.gitignore` : `server/deploy.env` et `server/questions/` (clone du dépôt privé de la banque,
§ 8).

---

## 3. Collections et règles d'accès

Règles PocketBase : `null` = superutilisateur seul, `""` = tout le monde. Les hooks lisent et
écrivent avec `$app`, sans passer par les règles. **Les cinq règles (list, view, create, update,
delete) de chaque collection sont `null`**, sauf mention contraire (D8).

### 3.1 `eruditus_joueurs` (auth)

| champ | type | contraintes |
|---|---|---|
| `pseudo` | text | requis, 3 – 20, motif `^[A-Za-z0-9_]{3,20}$` ; index unique `pseudo COLLATE NOCASE` |
| `password` | système | min 8, max 71 ; la route refuse aussi plus de 72 octets en UTF-8 (D10) |
| `email` | système | **non requis**, jamais rempli (l'index unique système ignore les valeurs vides) |
| `code_recuperation` | text | **caché** (`hidden`), requis, empreinte SHA-256 en hexadécimal (64 caractères) |
| `pseudo_modifie_le` | date | **caché**, facultatif ; date du dernier changement de pseudo réussi, vide avant le premier (D80, ajouté par la migration des Paramètres, D82) |
| `avis_envoye_le` | date | **caché**, facultatif ; date du dernier avis transmis, vide avant le premier (D90, ajouté par la migration des avis, D91) |
| `elo` | number | **caché**, entier, 100 au moins ; Elo du joueur (D103), 1000 à la création et pour les joueurs d'avant l'Elo (ajouté par la migration de l'Elo, D110) |
| `elo_reponses` | number | **caché**, entier ; réponses comptées (D105), dont dépendent K, le statut provisoire et le classement ; 0 à la création et pour les joueurs d'avant l'Elo (D110) |
| `created`, `updated` | autodate | |

Index : (`elo`), pour le classement par Elo (D107, D110).

Les autres champs système (`tokenKey`, `emailVisibility`, `verified`) restent inutilisés.

- Règles : list, view, create, update, delete = `null` ; `authRule = ""` (tout compte de la
  collection peut se connecter) ; `manageRule = null`.
- Options : connexion par mot de passe activée avec `identityFields = ["pseudo"]` (l'index
  `NOCASE` rend la recherche insensible à la casse) ; OAuth2, OTP et MFA désactivés ; alerte de
  connexion (`authAlert`) désactivée, faute d'e-mail ; durée du jeton d'authentification
  2 592 000 s (30 jours). Les jetons liés à l'e-mail (vérification, réinitialisation, changement
  d'e-mail) n'ont aucun usage.
- Un hook retire `email`, `emailVisibility` et `verified` du `record` renvoyé par
  `auth-with-password` et `auth-refresh` (D33).

### 3.2 `eruditus_questions`

| champ | type | contraintes |
|---|---|---|
| `ref` | text | requis, unique ; identifiant stable venu du fichier de la banque (« q-0001 ») |
| `enonce` | text | requis, 10 – 300 caractères |
| `choix` | json | requis : tableau de 4 textes non vides, différents, 120 caractères au plus chacun |
| `bonne_reponse` | number | **caché** ; entier 1 – 4 (voir la note ci-dessous) |
| `active` | bool | seules les questions actives sont servies |
| `explication` | text | **caché**, facultatif ; 300 caractères au plus ; vide = aucune (D96, ajouté par la migration du contenu sourcé, D99) |
| `source` | text | **caché**, facultatif ; 120 caractères au plus ; vide = aucune (D96, D99) |
| `niveau` | number | **caché**, facultatif ; entier 1 (facile) – 5 (difficile), 0 = aucun ; jamais dans une réponse (D98, D99) |
| `difficulte` | number | **caché**, entier ; difficulté de la question pour l'Elo (D107) : valeur de son niveau au départ (800 – 2400), 0 sans niveau ; remise à la valeur du niveau quand l'import le change (§ 8) ; ajouté par la migration de l'Elo (D110) |
| `difficulte_reponses` | number | **caché**, entier ; réponses données (un choix dans le délai) par des joueurs non provisoires (D107), remis à 0 avec la difficulté (D110) |
| `created`, `updated` | autodate | |

Une question n'est jamais supprimée (des réponses y renvoient) : on la désactive.

> **Indices de choix.** Ils vont de 0 à 3 dans l'API et dans le fichier de la banque. En base,
> PocketBase ne distingue pas 0 d'un nombre vide : `bonne_reponse` y est stocké de 1 à 4, et
> `choix` (§ 3.4) de 1 à 4, 0 voulant dire « sans réponse ». La conversion se fait à un seul
> endroit des hooks.

### 3.3 `eruditus_parties`

| champ | type | contraintes |
|---|---|---|
| `joueur` | relation → `eruditus_joueurs` | requis, suppression en cascade |
| `questions` | relation multiple → `eruditus_questions` | 5, dans l'ordre de la partie |
| `etat` | select | `en_cours`, `terminee`, `abandonnee` |
| `created`, `updated` | autodate | `updated` sert au délai d'inactivité de 30 min |

Index : (`joueur`, `etat`).

### 3.4 `eruditus_reponses`

Une ligne par question **servie**, complétée à la réponse.

| champ | type | contraintes |
|---|---|---|
| `partie` | relation → `eruditus_parties` | requis, suppression en cascade |
| `joueur` | relation → `eruditus_joueurs` | requis, suppression en cascade |
| `question` | relation → `eruditus_questions` | requis |
| `numero` | number | 1 – 5 |
| `servie_le` | date | heure du serveur, à la milliseconde |
| `repondue_le` | date | vide tant que pas répondu |
| `choix` | number | 0 = sans réponse, 1 – 4 = choix 0 – 3 de l'API |
| `correcte` | bool | |
| `hors_delai` | bool | |
| `points` | number | 0 ou 1 |
| `elo_avant` | number | **caché**, entier ; Elo du joueur quand la question lui a été servie ; vide = réponse non comptée (D105, D106 ; ajouté par la migration de l'Elo, D110) |
| `elo_apres` | number | **caché**, entier ; Elo après : la perte dès la question servie, remplacée par le gain si la réponse est correcte (D106) |
| `elo_difficulte` | number | **caché**, entier ; difficulté de la question utilisée pour l'attendu (D106) |
| `elo_k` | number | **caché**, entier ; K utilisé : 40 (joueur provisoire), 24 ou 12 (D104) |

Index : unique (`partie`, `numero`) ; (`joueur`, `question`) ; (`joueur`, `points`).

### 3.5 `users` (collection par défaut)

Inutilisée. Une migration met ses cinq règles à `null` (inscription comprise) si elle existe ; elle
n'est pas supprimée. Les routes Eruditus refusent ses jetons (§ 6.2).

---

## 4. Réglages

Réglés par migration, sans toucher aux autres réglages.

### 4.1 Limiteur (D23)

Activé (`rateLimits.enabled = true`, désactivé par défaut dans PocketBase). La migration ajoute ou
met à jour ces règles, par IP, **en tête de la liste** (D34), et garde après elles les règles par
défaut de PocketBase (`*:auth`, `*:create`, `/api/batch`, `/api/`) :

| label | max | période | protège |
|---|---|---|---|
| `eruditus_joueurs:auth` | 5 | 60 s | connexion (force brute du mot de passe) |
| `POST /api/eruditus/v1/comptes` | 5 | 3 600 s | création de comptes en masse |
| `POST /api/eruditus/v1/comptes/recuperation` | 5 | 3 600 s | force brute du code de récupération |
| `POST /api/eruditus/v1/comptes/suppression` | 5 | 600 s | essais de mot de passe |
| `POST /api/eruditus/v1/comptes/pseudo` | 10 | 3 600 s | pseudos essayés (« déjà pris » dit qu'un pseudo existe) ; D82 |
| `POST /api/eruditus/v1/comptes/mot-de-passe` | 5 | 600 s | essais du mot de passe actuel ; D82 |
| `POST /api/eruditus/v1/avis` | 5 | 3 600 s | avis en rafale, depuis plusieurs comptes ; D91 |
| `POST /api/eruditus/v1/parties` | 30 | 3 600 s | création de parties en rafale |
| `/api/eruditus/` | 120 | 60 s | tout le reste des routes Eruditus |

Les deux règles de D82 viennent d'une migration à part (celle des réglages, déployée, ne change
plus, D22), qui les place juste avant `/api/eruditus/` ; celle de D91 aussi, d'une troisième.

Choix de la règle par PocketBase 0.42.3 : pour une route standard, d'abord `<collection>:<action>`
puis `*:<action>` ; ensuite la règle exacte « méthode chemin », puis la première règle de préfixe
(label finissant par `/`) **dans l'ordre de la liste**. Placée après `/api/`, la règle
`/api/eruditus/` ne s'appliquerait jamais. Vérifié par les tests serveur (T10, T11, T29, et la
règle `/api/eruditus/`). Dépassement : statut 429.

### 4.2 Collection d'authentification

Voir § 3.1.

### 4.3 IP réelle

`trustedProxy.headers = ["X-Forwarded-For"]`, `useLeftmostIP = false` : le limiteur compte par
IP réelle du client et non par celle du proxy. Sûr seulement si PocketBase n'est joignable que
par le proxy (prérequis du § 1).

### 4.4 Divers

- API batch désactivée (`batch.enabled = false`) : rien ne s'en sert. C'est déjà la valeur par
  défaut de 0.42.3 ; la migration la fixe quand même.
- Aucun autre réglage modifié (journaux, sauvegardes, S3, SMTP restent ceux de l'installation).

---

## 5. Routes

Base : l'adresse du serveur (hors dépôt, D36). Corps en JSON UTF-8. Jeton dans l'en-tête
`Authorization: <jeton>`. Les routes `/api/eruditus/v1/` exigent un jeton de
`eruditus_joueurs`, sauf mention « public ».

**Routes publiques : jamais de jeton** (D30), `auth-with-password` compris. Depuis 0.42.0,
PocketBase répond 401 à tout jeton inconnu ou expiré, même sur une route publique : un vieux jeton
envoyé à la connexion la ferait échouer. Le jeton n'accompagne que `auth-refresh` et les routes
« jeton ». Succès : 200 avec un corps JSON, ou 204 là où c'est indiqué (D31).

### 5.1 Liste

| méthode et chemin | accès | entrée | sortie |
|---|---|---|---|
| `GET /api/eruditus/v1/info` | public | — | `{ versionMinimale }` |
| `POST /api/eruditus/v1/comptes` | public | `{ pseudo, motDePasse }` | `{ token, joueur, codeRecuperation }` |
| `POST /api/collections/eruditus_joueurs/auth-with-password` | public (API standard) | `{ identity: pseudo, password }` | `{ token, record }` |
| `POST /api/collections/eruditus_joueurs/auth-refresh` | jeton (API standard) | — | `{ token, record }` |
| `POST /api/eruditus/v1/comptes/recuperation` | public | `{ pseudo, code, nouveauMotDePasse }` | `{ token, joueur, codeRecuperation }` |
| `POST /api/eruditus/v1/comptes/deconnexion` | jeton | — | 204 |
| `POST /api/eruditus/v1/comptes/suppression` | jeton | `{ motDePasse }` | 204 |
| `POST /api/eruditus/v1/comptes/pseudo` | jeton | `{ pseudo }` | `{ joueur }` (D79) |
| `POST /api/eruditus/v1/comptes/mot-de-passe` | jeton | `{ motDePasse, nouveauMotDePasse }` | `{ token, joueur }` (D81) |
| `GET /api/eruditus/v1/moi` | jeton | — | `{ joueur, score, rang, classes, elo }` (`elo` : D109) |
| `POST /api/eruditus/v1/parties` | jeton | — | `{ partie: { id, total }, question }` |
| `POST /api/eruditus/v1/parties/{id}/question` | jeton | — | `{ question }` |
| `POST /api/eruditus/v1/parties/{id}/reponse` | jeton | `{ numero, choix }` | `{ correction, bilan }` |
| `GET /api/eruditus/v1/classement` | jeton | — | `{ top, moi, classes }` (par points, pour l'app précédente, D108) |
| `GET /api/eruditus/v1/classement/elo` | jeton | — | `{ top, moi, classes, restantes }` (D109) |
| `POST /api/eruditus/v1/avis` | jeton | `{ texte, versionApp, android }` (versions facultatives) | `{}` (D87) |

Des routes standard de PocketBase, l'app ne lit que `token` (et `record.id`) ; le reste vient de
`/moi`. Les routes Eruditus construisent leurs réponses champ par champ, jamais à partir d'un
enregistrement entier.

### 5.2 Objets

| objet | champs |
|---|---|
| `joueur` | `id`, `pseudo` |
| `question` | `numero` (1 – 5), `total` (5), `enonce`, `choix` (4 textes), `delaiMs` (20 000), `restantMs` |
| `correction` | `numero`, `choix` (0 – 3 ou `null`), `correcte`, `bonneReponse` (0 – 3), `horsDelai`, `point` (0 ou 1), `raison` (`gagne`, `fausse`, `sans_reponse`, `hors_delai`, `deja_reussie`), `score` (total après cette réponse), `explication` et `source` (texte, ou `null` quand la question n'en a pas, D97) |
| `bilan` | `null` avant la 5e réponse ; puis `bonnes` (0 – 5), `points` (0 – 5), `score`, `elo` : `{ valeur, variation, revision }` (Elo après la partie ; somme des écarts de ses réponses comptées, 0 si aucune ; `revision` vrai si ses 5 questions avaient toutes déjà été servies au joueur ; D105, D106, D109) |
| `codeRecuperation` | 16 caractères en 4 groupes séparés par des tirets : `7K3M-Q9XA-2HDE-F8RT` (D35) |
| `/info` | `versionMinimale` : versionCode, constante des hooks, 1000 au départ (D35) |
| ligne de classement | `rang`, `pseudo`, `score` |
| `/moi` | `rang` et ligne `moi` à `null` tant que le score est 0 ; `classes` = nombre de joueurs classés, par points ; `elo` (D109) : `{ valeur, provisoire, restantes, rang, classes, revision }` : Elo, statut provisoire (moins de 30 réponses comptées), réponses comptées qui manquent pour être classé (0 ensuite), rang par Elo (`null` tant que provisoire), nombre de joueurs classés par Elo, banque épuisée (toutes les questions actives lui ont déjà été servies, D105) |
| ligne du classement par Elo | `rang`, `pseudo`, `elo` |
| `/classement/elo` | `top` : 10 lignes au plus (`regles.md` § 4) ; `moi` : ma ligne, `null` tant que provisoire ; `classes`, `restantes` : comme dans `elo` de `/moi` |

`POST /parties/{id}/question` sert la question suivante, ou renvoie la question servie pas encore
répondue avec son temps restant (le chrono ne repart pas, T24).

Correction (D32) : `correcte` est vrai si le choix est la bonne réponse **et** arrive dans le délai
(hors délai = fausse, D15). Une seule `raison`, la première qui s'applique : `hors_delai`,
`sans_reponse`, `fausse`, `deja_reussie`, `gagne`. `bilan.bonnes` compte les réponses correctes de
la partie, `bilan.points` les points qu'elle a rapportés.

### 5.3 Erreurs

Format standard de PocketBase : `{ status, message, data }`. Les routes Eruditus mettent leur code
dans `data.code` (texte) ; l'app le traduit (`ecrans.md` § 15) et traite un code inconnu comme une
erreur générique. Les routes écrivent ces erreurs elles-mêmes : PocketBase réécrit le `data` d'une
erreur levée (D31).

Règle (D31) : champ absent ou de mauvais type JSON → `requete_invalide` ; bon type mais valeur
refusée → le code précis. Ordre des vérifications : jeton, corps, champs dans l'ordre du tableau
ci-dessous, puis partie, état, numéro.

| statut | `data.code` | quand |
|---|---|---|
| 400 | `requete_invalide` | JSON illisible, corps qui n'est pas un objet, champ manquant ou de mauvais type (`choix` : `"2"`, `true`, absent), `Content-Type` autre que `application/json`, corps de plus de 16 Ko. Les routes sans entrée ignorent le corps. |
| 400 | `pseudo_invalide` | motif non respecté (création de compte, changement de pseudo) |
| 400 | `pseudo_reserve` | nom réservé |
| 400 | `pseudo_pris` | déjà utilisé, casse ignorée ; au changement, par un autre joueur |
| 409 | `pseudo_trop_recent` | changement de pseudo moins de 24 heures après le précédent (D80) |
| 400 | `mot_de_passe_invalide` | moins de 8 ou plus de 71 caractères, ou plus de 72 octets en UTF-8 (D10) ; à la récupération et au changement de mot de passe, vérifié avant le code ou le mot de passe actuel |
| 400 | `code_incorrect` | pseudo inconnu, mal formé **ou** code faux ou mal formé (même réponse) |
| 400 | `mot_de_passe_incorrect` | suppression ou changement de mot de passe avec un mauvais mot de passe (actuel) |
| 400 | `choix_invalide` | `choix` est un nombre mais pas un entier de 0 à 3 (-1, 4, 1.5) |
| 400 | (standard, sans code) | connexion échouée (`auth-with-password`) |
| 401 | (standard) | jeton absent, invalide, expiré ou d'une autre collection (superutilisateur, `users`) ; tout jeton invalide ou expiré, même sur une route publique (D30) |
| 404 | `partie_introuvable` | identifiant inconnu **ou** partie d'un autre joueur (même réponse) |
| 409 | `partie_finie` | partie terminée ou abandonnée |
| 409 | `numero_invalide` | `numero` est un nombre mais pas celui de la question servie en attente (pas encore servi, hors de 1 – 5, non entier) |
| 409 | `deja_repondu` | réponse déjà enregistrée pour ce numéro |
| 400 | `avis_invalide` | texte hors de la règle après normalisation : vide, blancs seuls, plus de 1000 caractères (D86) |
| 409 | `avis_trop_recent` | avis moins de 10 minutes après le dernier avis transmis par ce compte (D90) |
| 429 | (standard) | limiteur (§ 4.1) |
| 503 | `banque_insuffisante` | moins de 5 questions actives |
| 503 | `avis_indisponible` | `ERUDITUS_AVIS_WEBHOOK` absente ou vide ; Discord injoignable, sans réponse en 5 s, ou statut autre que 2xx (D89). Le message ne dit rien de la cause ni de l'adresse |

Une route ne répond jamais 500 à une entrée mal formée (T32).

### 5.4 Avis (D87 – D90)

`POST /avis`, dans cet ordre :

1. jeton de `eruditus_joueurs` (401 sinon), corps (§ 5.3) ;
2. `texte` : absent ou pas une chaîne → `requete_invalide` ; passé par la règle de `regles.md`
   § 7.1, hors de 1 – 1000 caractères → `avis_invalide` ;
3. `versionApp` et `android` : une chaîne qui suit son motif est gardée, tout le reste (absent,
   autre type, autre forme) devient « inconnue », sans jamais refuser :
   `versionApp` `^[0-9]{1,4}(\.[0-9]{1,4}){1,3}(-[a-z]{1,16})?$` (« 1.0.6 », « 1.0.0-preview ») ;
   `android` `^[0-9]{1,3}(\.[0-9]{1,3}){0,2}$` (« 15 », « 8.1.0 ») ;
4. `ERUDITUS_AVIS_WEBHOOK` absente ou vide → `avis_indisponible` ;
5. dans une transaction : `avis_envoye_le` à moins de `ERUDITUS_DELAI_AVIS_MS` (600 000) de
   maintenant → `avis_trop_recent` ; sinon `avis_envoye_le` = maintenant, ce qui réserve le
   créneau ;
6. hors transaction (rien n'est bloqué pendant l'attente), le message à Discord, 5 s au plus ;
7. 2xx → 200 `{}`. Autre statut, erreur ou délai dépassé → dans une transaction,
   `avis_envoye_le` reprend sa valeur précédente s'il vaut encore celle du point 5 (sinon un autre
   envoi l'a changée, et on n'y touche pas), puis `avis_indisponible`.

Message envoyé (`Content-Type: application/json`), rien d'autre :

```json
{
  "username": "Eruditus",
  "allowed_mentions": { "parse": [] },
  "embeds": [{
    "title": "Avis de Hibou_42",
    "description": "<texte après la règle>",
    "footer": { "text": "Eruditus 1.0.6 · Android 15" },
    "timestamp": "2026-11-21T18:04:05.123Z"
  }]
}
```

- Pseudo : celui du compte du jeton au moment de l'envoi, jamais un champ du corps (T39).
- Pas de `content` : le texte n'est que dans l'embed ; `allowed_mentions` vide empêche toute
  notification, `@everyone`, `@here`, `<@id>` et `<@&id>` compris (T40).
- Ni identifiant du joueur, ni IP, ni jeton, ni rien de la requête de l'app.
- Le texte n'est écrit nulle part sur le serveur (base, journaux) ; seule la date du point 5
  l'est.

### 5.5 Elo (D103 – D111)

Règles : `regles.md` § 8. Constantes dans `pb_hooks/eruditus/config.js`, sans variable
d'environnement (D111) :

| constante | valeur |
|---|---|
| Elo de départ, plancher | 1000, 100 |
| difficulté des niveaux 1 – 5 | 800, 1200, 1600, 2000, 2400 |
| chance minimale du QCM | 0,25 |
| K du joueur | 40 sous 30 réponses comptées, 24 sous 200, 12 ensuite |
| seuil du statut provisoire et du classement | 30 réponses comptées |
| K des questions, réponses avant mouvement, écart maximal | 8, 30, 300 |

Tout se fait dans la transaction de la route. PocketBase sérialisant les écritures (§ 6.1,
point 5), deux réponses ne lisent jamais le même Elo ni la même difficulté.

**Question servie** (création de partie, ou `POST /parties/{id}/question` quand elle sert la
suivante) :

1. comptée si `difficulte` > 0 et qu'aucune autre ligne de `eruditus_reponses` de ce joueur ne
   porte sur cette question (index (`joueur`, `question`)) ; sinon, rien d'autre ;
2. P = `elo` du joueur, Q = `difficulte`, n = `elo_reponses`, K = 40 si n < 30, 24 si n < 200,
   12 sinon ; attendu E (`regles.md` § 8.2) ;
3. la ligne reçoit `elo_avant` = P, `elo_difficulte` = Q, `elo_k` = K et `elo_apres` = la perte,
   max(100, arrondi(P − K × E)) ;
4. le joueur reçoit `elo` = la perte et `elo_reponses` = n + 1.

Redemander la question en attente (`POST /parties/{id}/question` au retour au premier plan ou à la
recréation de l'écran, D66) ne passe pas par `servir` : ni perte, ni compteur, ni `elo_avant`,
`elo_difficulte`, `elo_k` ou `elo_apres` ne changent, une fois comme dix fois (T53).

**Réponse enregistrée** (`POST /parties/{id}/reponse`, après la correction), seulement si
`elo_avant` n'est pas vide :

5. E recalculé depuis `elo_avant` et `elo_difficulte` ; S = `correcte` ;
6. S = 1 : gain = max(100, arrondi(`elo_avant` + `elo_k` × (1 − E))) ; l'`elo` du joueur reçoit
   gain − `elo_apres`, puis `elo_apres` = gain. Le joueur n'a qu'une question comptée en attente
   à la fois (la suivante n'est servie qu'après la réponse, et créer une partie abandonne les
   autres, dont les questions ne peuvent plus être répondues) : son `elo` vaut alors la perte.
   S = 0 : rien, la perte reste ;
7. si `elo_k` ≠ 40 (joueur non provisoire quand la question a été servie) **et** que la réponse
   est donnée (`choix` non nul, pas hors délai) : si `difficulte_reponses` ≥ 30, `difficulte` =
   arrondi(Q − 8 × (S − E)), Q étant la difficulté actuelle, borné à la valeur du niveau ± 300 ;
   puis `difficulte_reponses` + 1. « Sans réponse » et hors délai : la difficulté et son compteur
   ne bougent pas (T52) ;
8. 5e réponse : `bilan.elo` = `{ valeur : elo du joueur, variation : somme des elo_apres −
   elo_avant des lignes comptées de la partie, revision : chacune des 5 questions avait déjà une
   ligne de ce joueur servie avant celle de la partie }`.

Une ligne servie jamais répondue (partie abandonnée) garde sa perte : abandonner compte comme
faux (T47), sans aucun traitement à l'abandon, et ne touche pas la difficulté.

**Lecture** : `elo` de `/moi` et `/classement/elo` ne lisent que `elo` et `elo_reponses` des
joueurs. Classés : `elo_reponses` ≥ 30 ; rang : 1 + classés à l'`elo` strictement supérieur ;
top 10 : `elo` décroissant puis `pseudo COLLATE NOCASE`, avec `RANK()` comme `/classement` ;
`restantes` : max(0, 30 − `elo_reponses`). Index (`elo`). `revision` de `/moi` : une seule
requête, « existe-t-il une question active sans aucune ligne de ce joueur ? », par l'index
(`joueur`, `question`), bornée par le nombre de questions. Rien n'agrège les réponses : la
lecture ne ralentit pas quand les réponses s'accumulent. `score`, `rang` et
`classes` par points sont calculés comme avant (`classement.js`), et `/classement` ne change pas.

---

## 6. Sécurité de l'API

### 6.1 Principes

1. **Le serveur décide de tout** (D8) : questions tirées, chrono, correction, points, score,
   classement. L'app n'envoie que des choix ; aucune valeur qu'elle envoie n'est crue sans
   vérification, et les champs inattendus sont ignorés (T2, T27).
2. **Surface minimale** : règles de toutes les collections fermées, API batch désactivée,
   collection `users` fermée. Seules routes publiques : `/info`, `/comptes`,
   `/comptes/recuperation` et `auth-with-password`. Seule requête sortante : le webhook des avis
   (D92, § 6.8).
3. **Défense en profondeur** : la bonne réponse, l'explication, la source, le niveau, l'Elo, la
   difficulté, leurs compteurs et l'empreinte du code sont cachés (`hidden`) en plus des règles
   fermées ; les réponses des routes sont construites champ par champ.
4. **Entrées validées strictement** : types, bornes, longueur ; corps limité à 16 Ko.
5. **Écritures atomiques** : chaque action (vérifications et écritures) se fait dans une
   transaction. PocketBase passe toutes les écritures par une seule connexion : deux transactions
   ne s'entremêlent jamais, et la seconde voit la réponse enregistrée par la première (T28).
   L'index unique (`partie`, `numero`) empêche en plus de servir deux fois le même numéro.
6. **Messages muets** : une erreur ne révèle ni une bonne réponse, ni l'existence d'un pseudo à la
   connexion ou à la récupération, ni l'existence de la partie d'un autre joueur. Seuls la
   création de compte et le changement de pseudo disent qu'un pseudo est pris, ce qui est
   inévitable (les pseudos sont publics au classement) ; tous deux sont limités par IP.
   `avis_indisponible` ne dit ni la cause ni l'adresse du webhook (§ 6.8).

### 6.2 Règles d'accès par collection

| collection | list | view | create | update | delete | autres |
|---|---|---|---|---|---|---|
| `eruditus_joueurs` | `null` | `null` | `null` | `null` | `null` | `authRule ""`, `manageRule null` |
| `eruditus_questions` | `null` | `null` | `null` | `null` | `null` | |
| `eruditus_parties` | `null` | `null` | `null` | `null` | `null` | |
| `eruditus_reponses` | `null` | `null` | `null` | `null` | `null` | |
| `users` | `null` | `null` | `null` | `null` | `null` | fermée, inutilisée |

Les routes protégées exigent `$apis.requireAuth("eruditus_joueurs")` : un jeton de superutilisateur
ou de toute autre collection est refusé (T14).

### 6.3 Score et bonne réponse

- **Score écrit par le serveur seul** : aucun champ de score n'existe ; le score se calcule à partir
  de `eruditus_reponses`, que seuls les hooks écrivent (D17).
- **Bonne réponse cachée** : `bonne_reponse` est `hidden` et sa collection fermée. Elle n'apparaît
  que dans la correction de la question à laquelle le joueur vient de répondre (D16, T18).
- **Explication et source, après le verdict seulement** (D97) : cachées comme la bonne réponse,
  qu'elles disent souvent. Elles n'apparaissent que dans la correction, construite une fois la
  réponse enregistrée (choix, sans réponse ou hors délai), et ce sont celles de la question
  corrigée ; jamais dans une question servie ou redemandée, ni dans une erreur (T45). `niveau`
  n'apparaît dans aucune réponse (T46).
- **Elo écrit par le serveur seul** (D106) : l'Elo, le compteur, la difficulté et les champs Elo
  des réponses sont cachés et écrits par les hooks seuls ; aucune route ne lit un Elo, une
  difficulté, un K ou une variation dans la requête (T50). L'Elo d'un joueur n'est renvoyé qu'à
  lui (`/moi`, `bilan`) et, une fois classé, dans les lignes du classement par Elo ; compteurs,
  K, difficulté, Elo avant et après, jamais (T51).

### 6.4 Comptes et jetons

- Limite de tentatives par IP : § 4.1.
- Mot de passe haché par PocketBase (bcrypt) ; code de récupération en SHA-256, comparé en temps
  constant (`regles.md` § 1.6).
- Jeton : JWT de PocketBase valable 30 jours, signé avec le secret de la collection et la
  `tokenKey` du compte. Changer le mot de passe (récupération, changement de mot de passe) ou la
  `tokenKey` (déconnexion) invalide tous les jetons du compte (T12, T13, T37) ; le changement de
  mot de passe renvoie un jeton neuf au téléphone qui l'a demandé (D81). Changer de pseudo ne
  touche pas aux jetons.
- Jamais de jeton sur une route publique, `auth-with-password` compris (D30) : un jeton expiré
  y donne 401.
- Dans l'app : **jeton stocké chiffré, jamais le mot de passe** ni le code de récupération
  (`donnees.md`).

### 6.5 HTTPS seulement (D24)

- Côté serveur : le proxy HTTPS (hors dépôt, prérequis du § 1).
- Côté app : `android:usesCleartextTraffic="false"` et une configuration de sécurité réseau sans
  aucune exception en clair ; l'URL du serveur est une constante de build, fournie hors dépôt
  (D36), et **le build échoue si elle ne commence pas par `https://`**.
- Pas d'épinglage de certificat : le proxy renouvelle ses certificats, et un épinglage
  mal tenu couperait l'app de tous ses joueurs.

### 6.6 Compatibilité avec la version précédente de l'app (D22)

- Les routes `/api/eruditus/v1/` ne changent **que par ajout** : nouveau champ de réponse,
  nouvelle route, nouveau champ de requête facultatif, nouveau code d'erreur. **Jamais** : retirer
  ou renommer un champ, changer son type ou son sens, rendre obligatoire un champ de requête,
  changer le statut ou le code d'une erreur existante.
- Un changement incompatible passe par de nouvelles routes `/v2/` à côté des `/v1/`. Celles-ci
  restent jusqu'à ce que `versionMinimale` dépasse toutes les versions de l'app qui les appellent.
- Migrations : jamais modifiées après déploiement ; un champ n'est retiré que lorsque plus aucune
  route ne le lit.
- Ordre : le serveur est déployé, tests passés, **avant** la publication de l'app qui s'en sert.
  Le merge publiant l'app aussitôt, un changement de `pb_migrations` ou `pb_hooks` se déploie
  depuis sa branche, avant le merge (D51, `publication.md` § 5).
- Les tests de contrat de la v1 (forme de chaque réponse) ne sont jamais modifiés, seulement
  complétés.

### 6.7 Limites connues, acceptées en v1

- **Comptes multiples** : un second compte peut récolter les bonnes réponses (montrées après
  chaque réponse) au profit d'un premier. Le limiteur de création de comptes le freine, rien ne
  l'empêche (`SPEC.md` § 5.2).
- **Comptes multiples et Elo** (D113) : un second compte qui a vu les questions peut souffler les
  réponses au premier ; un compte supprimé puis recréé (pseudo compris, D14) repart de 1000 avec
  des questions neuves pour lui, mais dont il connaît les réponses. L'Elo monte alors sans
  mérite ; comme pour les points, le limiteur de création le freine, rien ne l'empêche. Les
  comptes provisoires ne peuvent pas déplacer la difficulté des questions, et les autres de ± 300
  au plus (T52).
- **Voir puis abandonner** : une question servie puis abandonnée compte comme vue, pas comme
  réussie ; elle pourra rapporter plus tard. C'est le même effet que d'apprendre la réponse après
  une erreur. Pour l'Elo, elle compte comme fausse, et seulement à sa première tentative (T47).
- **Perte visible pendant une question** : pendant une question comptée (23 s au plus, délai et
  marge), l'Elo du joueur, au classement comme dans `/moi`, compte déjà la perte (§ 5.5) ; après
  un abandon, elle reste.
- **Difficulté lente à bouger** : chaque joueur ne tente une question qu'une fois ; il faut donc
  30 joueurs non provisoires différents sur une question avant qu'elle bouge. Avec peu de
  joueurs, elle reste celle de son niveau.
- **Elo figé sans nouvelles questions** : un joueur qui a vu toute la banque ne gagne ni ne perd
  plus rien, jusqu'aux questions suivantes ; ses parties sont des révisions, et l'app le dit
  (`revision`, D112).
- **Recherche pendant le chrono** : 20 s la limitent sans l'empêcher.
- **Force brute répartie sur beaucoup d'IP** : la limite est par IP, sans verrou par compte
  (`SPEC.md` § 5.2).
- **Déconnexion sans réseau** : le jeton oublié par le téléphone reste valable jusqu'à son
  expiration.
- **Avis mis en forme par Discord** : le texte est transmis tel quel ; Discord en interprète le
  markdown (gras, listes, liens masqués). Il n'est lu que par le mainteneur, et aucune mention ne
  notifie (D88).
- **Arrêt du serveur pendant l'envoi d'un avis** : le créneau réservé (§ 5.4, point 5) n'est pas
  rendu ; le compte attend 10 minutes, même si l'avis n'est pas parti.
- **Discord qui publie l'avis mais répond après 5 s** : pour le serveur, c'est un échec ; le délai
  est rendu et l'app affiche `avis_indisponible`. Si le joueur renvoie, l'avis arrive deux fois.
  Accepté.
- **Avis depuis beaucoup de comptes** : la limite est d'un avis par compte et de 5 par IP et par
  heure ; des comptes créés sur beaucoup d'IP pourraient remplir le salon Discord. Remède du
  mainteneur, sans aucun déploiement : vider `ERUDITUS_AVIS_WEBHOOK` sur le conteneur (la route
  répond alors `avis_indisponible`), ou régénérer le webhook dans Discord (l'ancienne adresse ne
  marche plus, la route répond `avis_indisponible` jusqu'à ce que la nouvelle soit réglée).

### 6.8 Webhook des avis (D89, D92)

- Seule requête sortante du serveur : `POST` HTTPS vers l'adresse de `ERUDITUS_AVIS_WEBHOOK`,
  pour chaque avis accepté par les points 1 – 5 du § 5.4, jamais autrement (aucune route ne prend
  une adresse en entrée).
- L'adresse contient le secret du webhook : elle n'est écrite que dans l'environnement du
  conteneur (§ 1). Jamais dans le dépôt, l'APK, `deploy.env`, une réponse, un message d'erreur
  ou un journal : les hooks attrapent toute erreur de l'envoi sans l'écrire ni la laisser sortir
  (PocketBase journaliserait une erreur sortie d'une route, avec l'adresse), et n'écrivent pas la
  réponse de Discord.
- Discord attendu 5 s au plus ; l'app attend 10 s (D52), le serveur répond donc toujours avant.

---

## 7. Tentatives de triche à faire échouer

Chacune est au moins un test serveur (§ 10). « Refusé » = statut d'erreur attendu (§ 5.3) **et**
aucune donnée modifiée.

**Comptes**

| n° | tentative | attendu |
|---|---|---|
| T1 | Créer un compte par l'API standard (`POST /api/collections/eruditus_joueurs/records`) | refusé |
| T2 | Créer un compte par la route avec des champs en plus (`email`, `verified`, `code_recuperation`, `score`, `id`, mot de passe déjà haché) | compte créé, ces champs ignorés |
| T3 | Pseudo invalide : 2 ou 21 caractères, espace, accent, tiret, emoji, caractère de contrôle, chaîne vide, nombre au lieu d'un texte | refusé |
| T4 | Pseudo réservé ou déjà pris en changeant la casse (« ADMIN », « Eruditus_1 », « HIBOU » quand « hibou » existe) | refusé |
| T5 | Mot de passe de 7 ou de 72 caractères | refusé |
| T6 | Créer un compte dans la collection `users` | refusé |
| T7 | Lister ou lire des joueurs par l'API standard, avec ou sans jeton ; chercher `password`, `tokenKey`, `code_recuperation`, `pseudo_modifie_le`, `avis_envoye_le` ou `email` dans toute réponse de toute route, connexion et renouvellement compris (D33) | refusé ; jamais présents |
| T8 | Modifier son propre compte par l'API standard (pseudo, mot de passe, code, champs système) | refusé |
| T9 | Supprimer un compte par l'API standard (le sien ou un autre) ; par la route avec un mauvais mot de passe | refusé, rien d'effacé |
| T10 | Force brute de la connexion : plus de 5 essais en une minute depuis une IP ; puis depuis une autre IP | 429 ; compteur distinct pour l'autre IP |
| T11 | Force brute du code de récupération au-delà de la limite ; pseudo inconnu et code faux comparés | 429 ; même erreur dans les deux cas |
| T12 | Réutiliser un code de récupération déjà utilisé ; réutiliser l'ancien jeton ou l'ancien mot de passe après une récupération | refusé |
| T13 | Jeton après déconnexion, après suppression du compte, expiré, altéré d'un caractère, signé par une autre clé | 401 |
| T14 | Jeton de superutilisateur ou de `users` sur une route Eruditus | refusé |
| T15 | Route protégée sans jeton | 401 |

**Partie**

| n° | tentative | attendu |
|---|---|---|
| T16 | Lister ou lire `eruditus_questions` par l'API standard, avec ou sans jeton | refusé |
| T17 | Lire, créer, modifier ou supprimer des parties ou des réponses par l'API standard | refusé |
| T18 | Chercher la bonne réponse dans la création de partie, une question servie, une question redemandée ou une erreur | jamais présente avant la réponse |
| T19 | Répondre à la partie d'un autre joueur, ou lui demander une question | 404, sa partie inchangée |
| T20 | Répondre deux fois au même numéro (même choix ou autre) | refusé, score inchangé |
| T21 | Répondre à un numéro pas encore servi, déjà répondu, ou hors de 1 – 5 | refusé |
| T22 | Choix invalide : -1, 4, `"2"`, 1.5, `true`, absent | refusé, rien d'enregistré |
| T23 | Répondre après le délai et la marge | enregistré hors délai, 0 point, correction renvoyée |
| T24 | Redemander la question en cours pour relancer le chrono | même question, temps restant qui continue de baisser |
| T25 | Réussir une question déjà réussie dans une partie précédente | 0 point, raison `deja_reussie` |
| T26 | Répondre à une partie abandonnée (une nouvelle a été créée, ou 30 min d'inactivité) ou terminée | refusé |
| T27 | Envoyer `point`, `score`, `correcte`, `horsDelai` ou `bonneReponse` dans la requête | ignorés |
| T28 | Envoyer la même bonne réponse 10 fois en parallèle | une seule enregistrée, score + 1 au plus |
| T29 | Créer des parties en rafale | 429 au-delà de la limite |

**Classement**

| n° | tentative | attendu |
|---|---|---|
| T30 | Lire `/classement` ou `/moi` sans jeton ; chercher dans les lignes autre chose que rang, pseudo et score | 401 ; rien d'autre |
| T31 | Supprimer un compte classé, puis recréer le même pseudo | l'ancien disparaît du classement ; le nouveau part de 0 sans rien hériter |

**Général**

| n° | tentative | attendu |
|---|---|---|
| T32 | Corps de plus de 16 Ko, JSON invalide, mauvais `Content-Type` | refusé, jamais de 500 |
| T33 | Appeler l'API batch (`POST /api/batch`) | refusé (désactivée) |

**Paramètres (D79 – D82)**

| n° | tentative | attendu |
|---|---|---|
| T34 | Prendre le pseudo d'un autre en changeant le sien (même casse ou autre casse) ; prendre un pseudo réservé ou invalide | refusé (`pseudo_pris`, `pseudo_reserve`, `pseudo_invalide`) ; aucun pseudo ne change |
| T35 | Enchaîner les changements de pseudo avant la fin du délai ; essayer des pseudos au-delà de la limite par IP | `pseudo_trop_recent`, pseudo inchangé ; 429 |
| T36 | Changer de mot de passe avec un mot de passe actuel faux, ou celui d'un autre joueur ; force brute au-delà de la limite | `mot_de_passe_incorrect`, puis 429 ; mot de passe et jetons inchangés |
| T37 | Après un changement de mot de passe, réutiliser l'ancien jeton (ce téléphone, un autre appareil) ou l'ancien mot de passe | 401 ; connexion refusée ; le jeton neuf et le nouveau mot de passe marchent |
| T38 | Changer le pseudo ou le mot de passe d'un autre joueur : jeton de A avec l'`id` ou le pseudo de B dans le corps ; jeton de superutilisateur ou de `users` ; sans jeton ; champs en plus (`id`, `tokenKey`, `password`, `code_recuperation`, `pseudo_modifie_le`, `email`, `verified`, `score`) | seul le compte du jeton change, champs en plus ignorés ; 401 sans jeton ou avec un jeton d'une autre collection |

**Avis (D85 – D92)**

Ici, « transmis » se lit sur le faux webhook des tests (§ 10).

| n° | tentative | attendu |
|---|---|---|
| T39 | Signer l'avis d'un autre : jeton de A avec `pseudo`, `joueur`, `id` de B dans le corps ; champs du message Discord dans le corps (`username`, `content`, `embeds`, `allowed_mentions`, `avatar_url`) ; `versionApp` et `android` forgés (texte long, markdown, `@everyone`, nombre, objet) | un seul message, titré « Avis de A », ces champs ignorés ; versions forgées → « inconnue », jamais un refus ; le délai de B intact |
| T40 | `@everyone`, `@here`, `<@123456789012345678>`, `<@&123456789012345678>` dans le texte | transmis tel quel dans `description`, `allowed_mentions.parse` vide, pas de `content` : personne n'est notifié |
| T41 | Deux avis d'affilée du même compte ; 10 en parallèle ; au-delà de 5 par heure depuis une IP | `avis_trop_recent` pour le second, un seul transmis sur les 10 ; 429 ; accepté de nouveau après le délai |
| T42 | Corps de plus de 16 Ko ; `texte` absent, nombre, tableau, objet, `null` ; texte vide, fait de blancs ou de contrôles, de 1001 caractères | `requete_invalide`, ou `avis_invalide` ; rien de transmis, délai intact |
| T43 | Avis sans jeton, avec un jeton expiré, de superutilisateur ou de `users` | 401 ; rien de transmis |
| T44 | Chercher `avis_envoye_le` dans toute réponse (`/moi`, connexion, renouvellement, `/avis`, erreurs) ; chercher l'adresse du webhook dans les réponses et le journal du serveur, Discord en panne compris | jamais présents |

**Contenu sourcé (D96 – D99)**

| n° | tentative | attendu |
|---|---|---|
| T45 | Lire l'explication ou la source avant d'avoir répondu : les chercher dans la création de partie, une question servie, une question redemandée (le chrono qui tourne), et dans les erreurs d'une réponse refusée (`deja_repondu`, `numero_invalide`, `choix_invalide`, `partie_finie`, partie d'un autre joueur) | jamais présentes ; seule la correction d'une réponse enregistrée (choix juste, faux, sans réponse, hors délai) les contient, et ce sont celles de la question corrigée, jamais de la suivante |
| T46 | Chercher `niveau` dans toute réponse de toute route, correction comprise | jamais présent |

**Elo (D102 – D113)**

Ici, « Elo inchangé » se lit par le superutilisateur : `elo` et `elo_reponses` du joueur,
`difficulte` et `difficulte_reponses` de la question, champs Elo de la ligne.

| n° | tentative | attendu |
|---|---|---|
| T47 | Abandonner pour éviter une perte : sur une question comptée, quitter sans répondre, laisser filer le chrono sans rien envoyer, créer une autre partie, rester inactif 30 min ; répondre à la partie abandonnée | la perte est comptée dès la question servie (`elo` de `/moi` déjà baissé avant toute réponse) et une seule fois ; aucun chemin ne la rend ; la réponse à la partie abandonnée est refusée (`partie_finie`), Elo inchangé ; difficulté de la question inchangée par l'abandon |
| T48 | Double envoi : la même bonne réponse 10 fois en parallèle ; deux réponses au même numéro ; 10 `POST /parties/{id}/question` en parallèle après une correction | une seule réponse enregistrée, un seul gain ; une seule question suivante servie, une seule perte ; Elo, compteur et difficulté changent une fois au plus |
| T49 | Rejouer une question vue : la question resservie quand toutes ont été vues ; une question vue avant l'Elo (ligne sans `elo_avant`) ; une question vue puis abandonnée ; répondre juste à chacune | Elo, compteur et difficulté inchangés ; le point suit D17 (1 si jamais réussie avant, `deja_reussie` sinon) ; banque épuisée : `elo.revision` vrai dans `/moi`, et `bilan.elo.revision` vrai avec `variation` 0 pour une partie de 5 questions déjà vues ; faux pour une partie qui en a au moins une neuve |
| T50 | Envoyer un Elo depuis l'app : `elo`, `eloAvant`, `eloApres`, `variation`, `difficulte`, `niveau`, `k`, `provisoire`, `restantes`, `reponsesComptees` dans le corps de `/parties`, `/question`, `/reponse`, `/comptes`, `/comptes/pseudo` ; modifier `elo`, `elo_reponses`, `difficulte` ou les champs Elo d'une réponse par l'API standard | ignorés : l'Elo est celui du calcul ; refusé, rien de modifié |
| T51 | Lire l'état interne : chercher `elo_reponses`, `elo_avant`, `elo_apres`, `elo_difficulte`, `elo_k`, `difficulte`, `difficulte_reponses` dans toute réponse de toute route ; chercher l'Elo d'un joueur provisoire, ou de quiconque, ailleurs que dans son propre `/moi`, son `bilan` et les lignes du classement par Elo | jamais présents |
| T52 | Comptes qui s'entraident : réponses de B qui changeraient l'Elo de A ; 40 comptes provisoires qui ratent exprès la même question, ou la réussissent ; comptes classés qui la laissent filer exprès (« sans réponse » à 0 s, ou réponse hors délai) pour la faire monter ; comptes classés qui la ratent tous, au-delà de 30 réponses | seul le compte du jeton change ; difficulté et compteur inchangés par les provisoires, par « sans réponse » et par le hors délai (l'Elo du joueur, lui, compte la perte) ; jamais au-delà de la valeur du niveau ± 300 |
| T53 | Redemander une question comptée pour effacer ou rejouer sa perte : `POST /parties/{id}/question` une fois, dix fois, dix fois en parallèle (retour au premier plan, recréation de l'écran, D66), puis répondre juste, faux ou laisser filer | une seule perte, comptée à la mise en service ; compteur, `elo_avant`, `elo_difficulte` et `elo_k` inchangés par chaque redemande ; même question, temps restant qui baisse (T24) ; le gain d'une bonne réponse part de l'Elo d'avant la mise en service |

---

## 8. Banque de questions (D7)

- Elle vit **hors dépôt** (D40) : `banque.json` dans un dépôt privé,
  cloné dans `server/questions/` (dossier ignoré par git). Elle n'est commitée et poussée que
  là-bas. Le dépôt public ne contient que `server/tests/questions-exemple.json` (12 questions
  factices : « Question d'exemple 1 »…, certaines avec une explication, une source et un niveau
  factices, d'autres sans, D99).
- Format (indices de 0 à 3 ; `explication`, `source` et `niveau` facultatifs, D96 – D99) :

  ```json
  [
    { "ref": "q-0001", "enonce": "…", "choix": ["…", "…", "…", "…"], "bonneReponse": 2, "active": true,
      "explication": "…", "source": "…", "niveau": 3 }
  ]
  ```

- Import (D40) : `server/questions.sh <fichier>`, lancé par le mainteneur depuis son PC :
  1. lit `server/deploy.env` (§ 9) et vérifie que le conteneur tourne ;
  2. lit dans la commande du service le binaire, `--dir`, `--hooksDir`, `--migrationsDir` et
     `--encryptionEnv` (ou leurs valeurs par défaut), pour travailler sur le même dossier de
     données que le service ;
  3. envoie le fichier par SSH sur l'entrée standard de `docker exec -i` : un petit script du
     conteneur l'écrit dans un fichier temporaire de `/tmp` (droits 600, utilisateur du
     conteneur), lance `eruditus-questions <fichier>` (commande ajoutée par les hooks) avec ces
     options plus
     `--dev=false` et `--hooksWatch=false`, puis efface le fichier, même en cas d'échec ;
     les copies laissées par un import interrompu sont effacées au suivant. Le fichier n'est
     jamais écrit sur l'hôte ;
  4. affiche le rapport de la commande et sort avec son code.
- La commande tourne sur une base déjà migrée, c'est-à-dire pendant que le service tourne (cas du
  VPS) : les commandes ajoutées par les hooks n'appliquent pas les migrations.
- Règles d'une entrée : `ref` selon `^[A-Za-z0-9_-]{1,50}$`, unique dans le fichier ; `enonce`
  de 10 à 300 caractères ; `choix` : 4 textes non vides de 120 caractères au plus, différents
  (espaces autour et casse ignorés) ; `bonneReponse` entier de 0 à 3 ; `active` booléen
  obligatoire. Facultatifs, chacun absent ou valide (`null` refusé) : `explication`, texte de 1 à
  300 caractères ; `source`, texte de 1 à 120 caractères ; tous deux comptés en points de code,
  sans caractère de contrôle (U+0000 – U+001F, retour à la ligne et tabulation compris, et
  U+007F – U+009F), sans blanc au début ni à la fin ; `niveau`, entier de 1 à 5. Aucun autre
  champ.
- La commande valide **tout** le fichier avant d'écrire quoi que ce soit, puis crée ou met à
  jour chaque question par sa `ref`, en une seule transaction. Une seule entrée invalide annule
  **tout le fichier**, jamais cette entrée seule (D99) : la base n'est jamais à moitié importée,
  et le rapport donne toutes les entrées refusées d'un coup (numéro, `ref`, raison), pour tout
  corriger en un passage. Une clé inconnue est refusée en la nommant (« champ inconnu
  « explicaton » ») : un champ absent étant vidé, une faute de frappe effacerait sinon
  l'explication sans rien dire. Elle ne
  supprime jamais : une question absente du fichier n'est pas touchée ; `active: false` la retire
  du jeu. Une entrée décrit toute la question : `explication`, `source` ou `niveau` absent est
  vidé à la mise à jour (D99). Rapport : créées, mises à jour, inchangées.
- Difficulté de l'Elo (D107) : ni `difficulte` ni son compteur ne sont dans le fichier. Une
  question créée, ou dont le niveau change réellement (ajouté, modifié, retiré : niveau du fichier
  différent de celui en base, 0 valant « aucun »), reçoit la difficulté de son niveau (0 sans
  niveau) et un compteur à 0. Niveau inchangé : ni l'une ni l'autre ne bouge, que la question
  soit « inchangée » au rapport (même fichier réimporté) ou « mise à jour » pour un autre champ
  (énoncé, choix, bonne réponse, explication, source, `active`).
- Changer la bonne réponse d'une question déjà jouée ne recalcule pas les réponses passées.
- Rédaction : culture générale variée (histoire, géographie, sciences, arts, littérature,
  sport…), en français ; faits stables, vérifiés sur une source sûre ; une seule bonne réponse
  défendable ; 4 choix plausibles et tous différents ; bonnes réponses réparties à peu près
  également entre A, B, C et D. Explication (D96) : une ou deux phrases de texte brut, sans mise
  en forme (ni markdown, ni HTML, ni retour à la ligne : l'app l'affiche telle quelle), qui disent
  pourquoi la bonne réponse est la bonne, compréhensibles sans relire l'énoncé. Source : la
  référence vérifiée, titre, auteur ou nom de site (« Encyclopædia Universalis », « larousse.fr »),
  jamais une adresse complète, l'app n'ouvrant aucun lien. Niveau : 1 (facile) à 5 (difficile),
  réparti sur toute la banque ; il fixe la difficulté de départ de l'Elo (800 à 2400, D103). Les `ref` suivent `q-0001`, `q-0002`… Aucune question de la
  banque ne reprend une question publique du dépôt (maquettes de `design/claude-design/`,
  questions d'exemple), dont la bonne réponse serait alors connue.
- Banque initiale : 30 questions (`q-0001` – `q-0030`), écrites à l'étape serveur, partie 2.

---

## 9. Mise à jour du serveur (D6)

`server/deploy.sh` est lancé par le mainteneur depuis son PC, jamais par GitHub ni par Claude
Code sans demande explicite. Il lui faut `ssh`, `rsync` et `curl` sur le PC, `rsync` sur le VPS.

`server/deploy.env` (non versionné, modèle `deploy.env.example`) : `HOTE` (alias SSH),
`CONTENEUR`, `DOSSIER_VPS` (dossier qui contient `pb_migrations/` et `pb_hooks/`),
`SAUVEGARDES` (dossier des archives), `URL` (adresse publique, en `https://`). Aucun
identifiant : l'alias SSH renvoie à la configuration SSH du
mainteneur. Le chemin de `pb_data` sur
l'hôte est lu dans les montages du conteneur.

Déroulé (D38, D39) :

1. lit `deploy.env` et vérifie les outils locaux ;
2. refuse si `server/pb_migrations` ou `server/pb_hooks` a des changements non commités : ce qui
   est déployé est toujours un commit (affiché) ;
3. vérifie, sans rien changer : `sudo -n` accepté, conteneur en marche, version épinglée
   (`pocketbase --version`), montages de `pb_migrations` et `pb_hooks` depuis `DOSSIER_VPS` en
   lecture seule, `--dev=false` et `--automigrate=false` dans la commande (§ 1), `rsync` présent,
   dossiers de `DOSSIER_VPS` accessibles en écriture, place libre pour l'archive ;
4. calcule ce que `rsync` copierait (comparaison par empreinte, sans rien copier) et l'affiche ;
   refuse si une migration déjà présente sur le VPS a changé (D22) ; s'il n'y a rien à copier,
   s'arrête sans redémarrer ;
5. arrête le conteneur ;
6. **sauvegarde à froid** : une archive `eruditus-<AAAAMMJJ-HHMMSS>.tar.gz` de `pb_data`,
   `pb_migrations` et `pb_hooks` dans `SAUVEGARDES` (root seul, dossier 700, archive 600) ;
7. copie par `rsync` les seuls fichiers Eruditus : `*_eruditus_*.js` de `pb_migrations`,
   `eruditus.pb.js` et `eruditus/` de `pb_hooks` (droits 644, dossiers 755). Seul
   `pb_hooks/eruditus/` est synchronisé avec suppression ; rien d'autre n'est supprimé ;
8. démarre le conteneur : les migrations pas encore appliquées s'exécutent au démarrage, chacune
   dans une transaction, et les hooks sont chargés ; une migration en échec empêche le démarrage ;
9. vérifie par l'URL publique, pendant 60 s au plus, `GET /api/health` puis
    `GET /api/eruditus/v1/info` (200 avec `versionMinimale`) ;
10. garde les 10 archives les plus récentes et supprime les autres.

- **`--essai`** : étapes 1, 2 (un avertissement au lieu d'un refus), 3 et 4, puis le nom de
  l'archive qui serait créée. Ni arrêt, ni copie.
- **Échec** : avant la copie (étapes 5 – 6), le conteneur est redémarré tel quel, rien n'a
  changé. Pendant la copie (étape 7), le script s'arrête en affichant les commandes exactes qui
  remettent `pb_migrations` et `pb_hooks` depuis l'archive puis redémarrent. Après le démarrage
  (étapes 8 – 9), il affiche les 40 dernières lignes du journal du conteneur et les commandes
  exactes qui remettent aussi `pb_data` (l'ancien est mis de côté en `_data.echec-<date>`, pas
  effacé). Le retour arrière n'est jamais automatique.
- **Tests serveur** : `deploy.sh` ne les lance pas (D114) ; ils passent en CI sur la PR, avant le
  déploiement depuis sa branche (D51). À la main, au besoin : `./gradlew :server-tests:test` (§ 10).
- Restaurer une archive suppose la même clé de chiffrement des réglages (§ 1).
- Migrations : un fichier par changement, nommé `<horodatage>_eruditus_<sujet>.js`, avec sa
  fonction de retour ; jamais modifié une fois déployé (D22).
- Ordre avec l'app : § 6.6 ; `deploy.sh` se lance depuis la branche de la PR, avant le merge
  (D51).
- Avis (D91) : le mainteneur règle `ERUDITUS_AVIS_WEBHOOK` sur le conteneur, hors dépôt (§ 1),
  avant ou après le déploiement ; `deploy.sh` n'y touche pas et ne la lit pas. Sans elle, la route
  répond `avis_indisponible`.

---

## 10. Tests serveur (D25)

- Module JVM `:server-tests` (dossier `server/tests/`, inclus dans le build Gradle
  d'`android-app/`) : `./gradlew :server-tests:test`.
- Avant les tests : télécharge le binaire de `server/pocketbase.version` (cache
  `~/.cache/eruditus/pocketbase/<version>/`), vérifie son SHA-256 (différent : arrêt), le lance
  sur un dossier de données temporaire vide avec `--migrationsDir server/pb_migrations` et
  `--hooksDir server/pb_hooks`, sur `127.0.0.1` et un port libre, puis importe
  `questions-exemple.json` avec la commande d'import.
- Sept serveurs, chacun sur sa base : le principal (délais par défaut, partagé par la plupart
  des tests, sans `ERUDITUS_AVIS_WEBHOOK` : `avis_indisponible`) ; un « chrono court » (délai
  1 000 ms, marge 500 ms, inactivité 3 000 ms, délai du pseudo et des avis 1 000 ms, avis vers
  le faux webhook) pour le hors délai, l'inactivité, le pseudo et l'avis permis de nouveau après
  leur délai ; un pour le classement, dont les tests maîtrisent tous les scores ; un pour les
  avis, vers le faux webhook, avec le délai des avis par défaut (10 minutes) ; un dont le webhook
  est sur un port fermé (connexion refusée) ; deux sans questions, l'un pour les migrations, le
  redémarrage et `banque_insuffisante`, l'autre pour l'import.
- **Faux webhook** (D91) : petit serveur HTTP local lancé par les tests, sur `127.0.0.1`, jamais
  Discord. Son adresse porte un faux secret, cherché ensuite dans toutes les réponses et dans le
  journal du serveur (T44). Il garde chaque message reçu (corps et en-têtes) et répond selon le
  test : 204, 200, 500, 429, ou rien avant 8 s (le serveur doit répondre `avis_indisponible` en
  moins de 8 s). Les cas de `cas-avis.json` (D86) y passent un à un : le texte attendu doit être la
  `description` reçue.
- Sur chaque base temporaire, un superutilisateur aux identifiants tirés au hasard, jamais écrits,
  sert à inspecter la base, à obtenir un jeton de superutilisateur ou de `users` (T14) et un jeton
  qui expire au bout d'une seconde (T13, connexion avec un vieux jeton).
- Les tests passent par HTTP, comme l'app. Ils posent `X-Forwarded-For` pour simuler des IP
  différentes, puisque le limiteur compte par IP.
- Délai : les hooks lisent le délai, la marge et l'inactivité dans `ERUDITUS_DELAI_MS`,
  `ERUDITUS_MARGE_MS` et `ERUDITUS_INACTIVITE_MS` (20 000, 3 000 et 1 800 000 par défaut), et le
  délai entre deux changements de pseudo dans `ERUDITUS_DELAI_PSEUDO_MS` (86 400 000, D80), et celui
  entre deux avis dans `ERUDITUS_DELAI_AVIS_MS` (600 000, D90) ; les tests les raccourcissent pour
  vérifier le hors délai, l'abandon et ces délais sans attendre. Sur le VPS, ces variables ne sont
  pas définies (D35). `ERUDITUS_AVIS_WEBHOOK` y est définie par le mainteneur (§ 1).
- Ils couvrent : chaque route (cas nominal et chaque erreur du § 5.3), la forme de chaque réponse
  (tests de contrat v1, D22), chaque tentative T1 – T53, la connexion avec un jeton expiré
  (réussie sans l'en-tête, 401 avec, D30), l'import des questions, et les migrations appliquées
  sur une base vide puis au redémarrage suivant (rien ne change).
- Elo (D111, à l'étape serveur) : les constantes de `config.js` sont celles du VPS, sans
  variable d'environnement. Pour K = 24 et 12, le seuil du classement et le mouvement des
  difficultés, les tests règlent `elo`, `elo_reponses`, `difficulte` et `difficulte_reponses` par
  le superutilisateur avant de jouer, plutôt que des centaines de réponses ; ils vérifient chaque
  pas contre la formule (attendu, arrondi, plancher de 100, bornes ± 300, seuils 30 et 200) et
  le contrat des ajouts (`elo` de `/moi` et du `bilan`, `revision` compris, `/classement/elo`),
  ceux de la v1 inchangés. La migration de l'Elo est vérifiée sur une base créée sans elle, avec
  joueurs, parties et réponses : 1000, 0 réponse comptée, rien de rejoué, et une question déjà
  vue ne compte pas. Import (§ 8) : une question dont la difficulté et le compteur ont bougé
  (réglés par le superutilisateur) les garde quand on réimporte le même fichier, puis quand on
  change seulement son énoncé, son explication ou sa source ; changer son niveau, puis le
  retirer, les remet à la valeur du niveau (0 sans niveau) et à 0.
- La tâche n'est jamais considérée « à jour » par Gradle : chaque lancement rejoue tout.
- Le client HTTP des tests échoue de lui-même sur toute réponse 500 (T32) et sur tout champ
  sensible (`password`, `tokenKey`, `code_recuperation`, `pseudo_modifie_le`, `avis_envoye_le`,
  `email`, `emailVisibility`, `verified`, `bonne_reponse`, `niveau`, `elo_reponses`,
  `elo_avant`, `elo_apres`, `elo_difficulte`, `elo_k`, `difficulte`, `difficulte_reponses`) dans
  une réponse faite à un client autre que le superutilisateur (T7, T18, T44, T46, T51).
  `explication` et `source`, permises dans la correction, sont cherchées partout ailleurs par T45 ;
  `elo`, nom de l'objet de `/moi` et du `bilan` et champ des lignes du classement par Elo, est
  cherché par T51 là où il ne doit pas être.
- Le binaire épinglé est celui de Linux amd64 : les tests tournent sur le PC du mainteneur et sur la
  CI
  (Ubuntu), pas ailleurs.
- Lancés par la CI sur chaque PR qui touche `server/` ou
  `android-app/` (job « Tests serveur », D47), binaire gardé en cache.

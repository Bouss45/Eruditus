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
| `created`, `updated` | autodate | |

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
| `GET /api/eruditus/v1/moi` | jeton | — | `{ joueur, score, rang, classes }` |
| `POST /api/eruditus/v1/parties` | jeton | — | `{ partie: { id, total }, question }` |
| `POST /api/eruditus/v1/parties/{id}/question` | jeton | — | `{ question }` |
| `POST /api/eruditus/v1/parties/{id}/reponse` | jeton | `{ numero, choix }` | `{ correction, bilan }` |
| `GET /api/eruditus/v1/classement` | jeton | — | `{ top, moi, classes }` |
| `POST /api/eruditus/v1/avis` | jeton | `{ texte, versionApp, android }` (versions facultatives) | `{}` (D87) |

Des routes standard de PocketBase, l'app ne lit que `token` (et `record.id`) ; le reste vient de
`/moi`. Les routes Eruditus construisent leurs réponses champ par champ, jamais à partir d'un
enregistrement entier.

### 5.2 Objets

| objet | champs |
|---|---|
| `joueur` | `id`, `pseudo` |
| `question` | `numero` (1 – 5), `total` (5), `enonce`, `choix` (4 textes), `delaiMs` (20 000), `restantMs` |
| `correction` | `numero`, `choix` (0 – 3 ou `null`), `correcte`, `bonneReponse` (0 – 3), `horsDelai`, `point` (0 ou 1), `raison` (`gagne`, `fausse`, `sans_reponse`, `hors_delai`, `deja_reussie`), `score` (total après cette réponse) |
| `bilan` | `null` avant la 5e réponse ; puis `bonnes` (0 – 5), `points` (0 – 5), `score` |
| `codeRecuperation` | 16 caractères en 4 groupes séparés par des tirets : `7K3M-Q9XA-2HDE-F8RT` (D35) |
| `/info` | `versionMinimale` : versionCode, constante des hooks, 1000 au départ (D35) |
| ligne de classement | `rang`, `pseudo`, `score` |
| `/moi` | `rang` et ligne `moi` à `null` tant que le score est 0 ; `classes` = nombre de joueurs classés |

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
3. **Défense en profondeur** : la bonne réponse et l'empreinte du code sont cachées (`hidden`) en
   plus des règles fermées ; les réponses des routes sont construites champ par champ.
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
- **Voir puis abandonner** : une question servie puis abandonnée compte comme vue, pas comme
  réussie ; elle pourra rapporter plus tard. C'est le même effet que d'apprendre la réponse après
  une erreur.
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

---

## 8. Banque de questions (D7)

- Elle vit **hors dépôt** (D40) : `banque.json` dans un dépôt privé,
  cloné dans `server/questions/` (dossier ignoré par git). Elle n'est commitée et poussée que
  là-bas. Le dépôt public ne contient que `server/tests/questions-exemple.json` (12 questions
  factices : « Question d'exemple 1 »…).
- Format (indices de 0 à 3) :

  ```json
  [
    { "ref": "q-0001", "enonce": "…", "choix": ["…", "…", "…", "…"], "bonneReponse": 2, "active": true }
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
  obligatoire ; aucun autre champ.
- La commande valide **tout** le fichier avant d'écrire quoi que ce soit (une seule entrée
  invalide et rien n'est importé), puis crée ou met à jour chaque question par sa `ref`. Elle ne
  supprime jamais : une question absente du fichier n'est pas touchée ; `active: false` la retire
  du jeu. Rapport : créées, mises à jour, inchangées.
- Changer la bonne réponse d'une question déjà jouée ne recalcule pas les réponses passées.
- Rédaction : culture générale variée (histoire, géographie, sciences, arts, littérature,
  sport…), en français ; faits stables, vérifiés sur une source sûre ; une seule bonne réponse
  défendable ; 4 choix plausibles et tous différents ; bonnes réponses réparties à peu près
  également entre A, B, C et D. Les `ref` suivent `q-0001`, `q-0002`… Aucune question de la
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
3. lance les tests serveur (§ 10) et s'arrête au premier échec ;
4. vérifie, sans rien changer : `sudo -n` accepté, conteneur en marche, version épinglée
   (`pocketbase --version`), montages de `pb_migrations` et `pb_hooks` depuis `DOSSIER_VPS` en
   lecture seule, `--dev=false` et `--automigrate=false` dans la commande (§ 1), `rsync` présent,
   dossiers de `DOSSIER_VPS` accessibles en écriture, place libre pour l'archive ;
5. calcule ce que `rsync` copierait (comparaison par empreinte, sans rien copier) et l'affiche ;
   refuse si une migration déjà présente sur le VPS a changé (D22) ; s'il n'y a rien à copier,
   s'arrête sans redémarrer ;
6. arrête le conteneur ;
7. **sauvegarde à froid** : une archive `eruditus-<AAAAMMJJ-HHMMSS>.tar.gz` de `pb_data`,
   `pb_migrations` et `pb_hooks` dans `SAUVEGARDES` (root seul, dossier 700, archive 600) ;
8. copie par `rsync` les seuls fichiers Eruditus : `*_eruditus_*.js` de `pb_migrations`,
   `eruditus.pb.js` et `eruditus/` de `pb_hooks` (droits 644, dossiers 755). Seul
   `pb_hooks/eruditus/` est synchronisé avec suppression ; rien d'autre n'est supprimé ;
9. démarre le conteneur : les migrations pas encore appliquées s'exécutent au démarrage, chacune
   dans une transaction, et les hooks sont chargés ; une migration en échec empêche le démarrage ;
10. vérifie par l'URL publique, pendant 60 s au plus, `GET /api/health` puis
    `GET /api/eruditus/v1/info` (200 avec `versionMinimale`) ;
11. garde les 10 archives les plus récentes et supprime les autres.

- **`--essai`** : étapes 1, 2 (un avertissement au lieu d'un refus), 4 et 5, puis le nom de
  l'archive qui serait créée. Ni tests, ni arrêt, ni copie.
- **Échec** : avant la copie (étapes 6 – 7), le conteneur est redémarré tel quel, rien n'a
  changé. Pendant la copie (étape 8), le script s'arrête en affichant les commandes exactes qui
  remettent `pb_migrations` et `pb_hooks` depuis l'archive puis redémarrent. Après le démarrage
  (étapes 9 – 10), il affiche les 40 dernières lignes du journal du conteneur et les commandes
  exactes qui remettent aussi `pb_data` (l'ancien est mis de côté en `_data.echec-<date>`, pas
  effacé). Le retour arrière n'est jamais automatique.
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
  (tests de contrat v1, D22), chaque tentative T1 – T44, la connexion avec un jeton expiré
  (réussie sans l'en-tête, 401 avec, D30), l'import des questions, et les migrations appliquées
  sur une base vide puis au redémarrage suivant (rien ne change).
- La tâche n'est jamais considérée « à jour » par Gradle : chaque lancement rejoue tout.
- Le client HTTP des tests échoue de lui-même sur toute réponse 500 (T32) et sur tout champ
  sensible (`password`, `tokenKey`, `code_recuperation`, `pseudo_modifie_le`, `avis_envoye_le`,
  `email`, `emailVisibility`, `verified`, `bonne_reponse`) dans une réponse faite à un client autre
  que le superutilisateur (T7, T18, T44).
- Le binaire épinglé est celui de Linux amd64 : les tests tournent sur le PC du mainteneur et sur la
  CI
  (Ubuntu), pas ailleurs.
- Lancés par `deploy.sh` avant toute copie, et par la CI sur chaque PR qui touche `server/` ou
  `android-app/` (job « Tests serveur », D47), binaire gardé en cache.

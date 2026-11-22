# Règles : compte, partie, score, classement, nouvelles versions, avis, Elo

> Partie de la spec (`SPEC.md` § 2). **Dn** renvoie aux Décisions de `SPEC.md` § 3. Les routes
> citées sont détaillées dans `serveur.md` § 5.
>
> Toutes ces règles sont appliquées **par le serveur** (D8). L'app peut les pré-vérifier pour
> guider la saisie, jamais à la place du serveur ; en cas de désaccord, le serveur a raison.

---

## 1. Compte

### 1.1 Pseudo (D9)

- 3 à 20 caractères parmi `A–Z`, `a–z`, `0–9` et `_` (motif `^[A-Za-z0-9_]{3,20}$`) : ni espace,
  ni accent, ni tiret, ni emoji.
- L'app retire les espaces au début et à la fin de la saisie. Le serveur ne corrige rien : il
  refuse tout ce qui ne respecte pas le motif.
- **Casse** : le pseudo s'affiche tel qu'il a été créé (« Hibou_42 ») ; l'unicité et la connexion
  ignorent la casse (« hibou_42 » est déjà pris et permet de se connecter).
- **Noms réservés**, refusés quelle que soit la casse : `admin`, `administrateur`,
  `administrator`, `moderateur`, `moderator`, `modo`, `root`, `superuser`, `system`, `systeme`,
  `support`, `staff`, `officiel`, `official`, `null`, `undefined`, `anonyme`, `anonymous`,
  `joueur`, `invite`, `guest`, `test`, ainsi que tout pseudo qui commence par `eruditus`. La liste
  vit dans les hooks ; l'allonger ne touche pas les comptes existants.
- Modifiable depuis Paramètres (§ 1.8). Libéré à la suppression du compte et au changement de
  pseudo : quelqu'un d'autre peut le reprendre aussitôt, sans rien hériter.

### 1.2 Mot de passe (D10)

- 8 à 71 caractères **et** 72 octets au plus une fois encodé en UTF-8 : bcrypt ne prend que
  72 octets, et un accent en compte 2, un emoji 4. Avec des accents, la longueur maximale est donc
  plus courte. Tous caractères permis, aucune règle de composition.
- L'app applique **exactement** la même règle avant l'envoi (caractères et octets) ; le serveur
  la vérifie quand même (`mot_de_passe_invalide`).
- Les tests serveur couvrent 7, 8, 71 et 72 caractères, et 36 « é » (72 octets, accepté) contre
  37 « é » (74 octets, refusé).
- Haché par PocketBase (bcrypt). Jamais stocké par l'app ni écrit dans un log (`donnees.md`).

### 1.3 Création

- Entrées : pseudo et mot de passe (`POST /comptes`). Le serveur crée le compte, génère le code de
  récupération (§ 1.6) et renvoie le jeton, le joueur et le code. Le joueur est connecté
  aussitôt ; l'app affiche le code (`ecrans.md` § 5).
- Refus : pseudo invalide, réservé ou déjà pris ; mot de passe invalide ; trop de créations depuis
  la même IP (D23).
- Tout autre champ envoyé est ignoré (e-mail, score, code, champs système).

### 1.4 Connexion et session (D12)

- Connexion : pseudo (casse libre) et mot de passe, par l'API standard de PocketBase
  (`auth-with-password`). Une seule erreur, « Pseudo ou mot de passe incorrect » : on ne dit pas
  lequel des deux est faux.
- Le jeton est valable **30 jours**. À chaque démarrage de l'app, `auth-refresh` renvoie un jeton
  neuf de 30 jours qui remplace l'ancien : on ne se reconnecte qu'après 30 jours sans ouvrir
  l'app.
- Jeton refusé (401), au renouvellement ou sur n'importe quelle route : l'app efface la session et
  revient à Bienvenue avec « Ta session a expiré, reconnecte-toi. »
- Plusieurs téléphones peuvent être connectés au même compte, chacun avec son jeton.

### 1.5 Déconnexion (D13)

- « Se déconnecter » appelle `POST /comptes/deconnexion` : le serveur change la clé des jetons du
  compte, ce qui invalide **tous** ses jetons (ce téléphone et les autres).
- Puis l'app efface la session locale (`donnees.md` § 5), même si l'appel a échoué. Sans réseau,
  le jeton est donc oublié par le téléphone mais reste valable côté serveur jusqu'à son
  expiration (`serveur.md` § 6.7).

### 1.6 Code de récupération (D11)

- 16 caractères tirés au hasard par le générateur cryptographique du serveur, dans l'alphabet de
  Crockford : `0–9` et `A–Z` sans `I`, `L`, `O`, `U` (32 symboles, 80 bits). Affiché en 4 groupes
  de 4 : `7K3M-Q9XA-2HDE-F8RT`.
- Saisie tolérante : minuscules acceptées, espaces et tirets ignorés, `O` lu comme `0`, `I` et `L`
  lus comme `1` (règle de Crockford). La normalisation est la même côté app et côté serveur.
- Format, après normalisation : 16 symboles de l'alphabet, et 64 caractères saisis au plus.
  L'app juge le format avant l'envoi avec exactement cette règle (D55) : un test JVM relit
  l'alphabet, le motif, la limite et la normalisation dans `server/pb_hooks/eruditus/comptes.js`.
- Le serveur ne garde que l'empreinte SHA-256 du code normalisé (champ caché). Un code de 80 bits
  tiré au hasard n'a pas besoin d'un hachage lent. La comparaison se fait en temps constant.
- Affiché **une seule fois** : à la création du compte et après chaque récupération. Personne, pas
  même le serveur, ne peut le réafficher. Le serveur le renvoie déjà en 4 groupes :
  `7K3M-Q9XA-2HDE-F8RT`.
- **Récupération** (`POST /comptes/recuperation`) : pseudo + code + nouveau mot de passe. Le serveur
  vérifie, change le mot de passe (ce qui invalide tous les jetons existants), génère un nouveau
  code (l'ancien ne marche plus) et renvoie le jeton, le joueur et le nouveau code, que l'app
  affiche.
- Le nouveau mot de passe est vérifié d'abord (`mot_de_passe_invalide`), ce qui ne révèle rien.
  Ensuite une seule erreur, « Pseudo ou code incorrect », que le pseudo existe ou non, soit mal
  formé ou que le code soit faux ou mal formé ; essais limités par IP (D23).
- **Sans le code, le compte est perdu.** Il reste au classement : aucun compte n'expire en v1.

### 1.7 Suppression (D14)

- Depuis Paramètres : « Supprimer mon compte », mot de passe redemandé, confirmation, puis
  `POST /comptes/suppression`. Le serveur vérifie le mot de passe, puis efface en une transaction
  le compte, ses parties et ses réponses. Le joueur disparaît du classement à la requête
  suivante ; le pseudo est libre.
- Mot de passe faux : refus, rien n'est effacé ; essais limités par IP (D23).
- Sans réseau : impossible, rien n'est effacé.
- Après succès, l'app efface la session locale et revient à Bienvenue.

### 1.8 Changement de pseudo (D79, D80)

- Depuis Paramètres, sans mot de passe : `POST /comptes/pseudo` avec le nouveau pseudo. Le serveur
  le juge avec **la même fonction** qu'à la création (§ 1.1) : motif, noms réservés, déjà pris
  (casse ignorée). Le pseudo du joueur lui-même ne compte pas comme pris : changer seulement la
  casse (« hibou_42 » → « Hibou_42 ») est permis. Un pseudo identique à l'actuel ne change rien
  (succès).
- **Une fois par 24 heures** par compte, comptées depuis le dernier changement réussi :
  avant, `pseudo_trop_recent`. Un refus ne compte pas ; le premier changement est libre, même
  juste après la création du compte (D80).
- Après succès : l'ancien pseudo est libre aussitôt (§ 1.1) ; le score, les parties, les
  réponses, le rang, le code de récupération et les jetons ne changent pas. Le classement et
  `/moi` montrent le nouveau dès la requête suivante ; connexion et récupération se font avec le
  nouveau seulement. Les autres appareils du compte restent connectés et reçoivent le nouveau
  pseudo à leur prochain `/moi`.
- Essais limités par IP (D23, D82).

### 1.9 Changement de mot de passe (D81)

- Depuis Paramètres : `POST /comptes/mot-de-passe` avec le mot de passe actuel et le nouveau. Le
  nouveau est vérifié d'abord (§ 1.2, `mot_de_passe_invalide`), puis l'actuel
  (`mot_de_passe_incorrect`) ; rien ne change après un refus. Essais limités par IP (D23, D82).
- Après succès, en une transaction : le nouveau mot de passe remplace l'ancien et la clé des
  jetons change, ce qui ferme **toutes** les sessions du compte, celle de ce téléphone comprise
  (comme la déconnexion, § 1.5). La réponse porte un jeton neuf de 30 jours, que l'app enregistre
  aussitôt à la place de l'ancien : ce téléphone reste connecté, les autres devront se reconnecter
  avec le nouveau mot de passe.
- Le code de récupération ne change pas. Le nouveau mot de passe peut être égal à l'actuel :
  seules les autres sessions tombent.

---

## 2. Partie (D15)

### 2.1 Création

« Jouer » appelle `POST /parties`. Le serveur :

1. abandonne la partie en cours du joueur, s'il y en a une (§ 2.5) ;
2. tire 5 questions actives différentes (D105) : d'abord celles qui n'ont jamais été servies à ce
   joueur, au hasard ; puis, quand il les a toutes vues, celles qui lui ont été servies il y a le
   plus longtemps (date de leur dernier service). Une question resservie ne change pas son Elo
   (§ 8.4) ;
3. crée la partie et sert la question 1, dont le chrono démarre à cet instant (et, si elle est
   comptée pour l'Elo, compte sa perte, § 8.5).

Moins de 5 questions actives dans la banque : refus (`banque_insuffisante`), cas d'un serveur mal
rempli.

### 2.2 Question servie et chrono

- Une question servie contient : son numéro (1 à 5), le total (5), l'énoncé, les 4 choix dans
  l'ordre de la banque, le délai (20 s) et le temps restant. **Jamais la bonne réponse** (D16),
  ni l'explication, ni la source, ni le niveau (D97, D98).
- Le chrono est celui du serveur : il note l'heure à laquelle il sert la question. Une réponse
  reçue plus de **23 s** après (20 s + 3 s de marge réseau) est hors délai.
- L'app affiche un compte à rebours de 20 s calé sur le temps restant envoyé par le serveur. À 0,
  elle envoie « sans réponse » pour recevoir la correction.
- Redemander la question en cours (`POST /parties/{id}/question`, après une rotation ou une brève
  coupure) renvoie la même question avec le temps restant : le chrono ne repart pas de zéro.
- App en arrière-plan : le chrono continue côté serveur. L'app n'envoie rien tant qu'elle est en
  arrière-plan ; à son retour (ou quand l'écran est recréé), une question qui attend encore sa
  réponse est redemandée et le compte à rebours recalé sur le temps restant ; à 0, « sans
  réponse » part aussitôt (D66). Une correction déjà affichée n'est jamais redemandée : ce serait
  servir la question suivante.

### 2.3 Réponse et correction

- Le joueur touche un choix ; les choix se verrouillent (un seul envoi) et l'app appelle
  `POST /parties/{id}/reponse` avec le numéro et le choix (0 à 3, ou `null` = sans réponse).
- Le serveur vérifie que la partie est à ce joueur et en cours, que le numéro est celui de la
  question servie et pas encore répondue, et que le choix est valide. Il enregistre la réponse et
  renvoie la correction : correcte ou non, **bonne réponse**, hors délai ou non, point gagné (0 ou
  1) avec sa raison, score total du joueur, et l'explication et la source de la question, s'il y
  en a (D96, D97). Une réponse refusée ne renvoie ni correction, ni explication, ni source.
- Correction (D32) : la réponse est correcte si le choix est la bonne réponse **et** arrive dans
  le délai (hors délai = fausse). Une seule raison, la première qui s'applique : hors délai, sans
  réponse, fausse, déjà réussie, gagné.
- Une réponse enregistrée est définitive : une seconde réponse au même numéro est refusée.
- Après la correction, « Question suivante » appelle `POST /parties/{id}/question`, qui sert la
  question suivante et démarre son chrono : le temps passé à lire la correction ne compte pas.
- Après la 5e réponse, la partie est terminée ; la correction contient aussi le bilan : bonnes
  réponses sur 5, points gagnés dans la partie, score total.

### 2.4 Points (D17)

- **1 point** si la réponse est correcte, dans le délai, et que le joueur n'a jamais réussi cette
  question auparavant.
- Sinon **0**, avec sa raison : fausse, sans réponse, hors délai, ou déjà réussie (« Bonne
  réponse, déjà réussie : pas de point cette fois »).

### 2.5 Abandon et coupure

- Une partie est abandonnée quand le joueur en commence une autre, ou quand elle n'a eu aucune
  activité (question servie ou réponse) depuis 30 minutes, constaté à l'accès suivant à cette
  partie. « Abandonner » dans l'app ne fait
  qu'en sortir : il n'y a pas de route d'abandon.
- Les réponses déjà envoyées restent acquises, points compris. Une question servie sans réponse
  compte comme vue, pas comme réussie : elle pourra rapporter plus tard (`serveur.md` § 6.7).
  Pour l'Elo, elle compte comme fausse (§ 8.5).
- Coupure réseau pendant une partie : écran « Pas de connexion » ; la partie est perdue pour
  l'app (pas de reprise en v1, `SPEC.md` § 5.2) et « Réessayer » ramène à l'Accueil. De même si
  Android tue l'app pendant une partie : elle repart de l'aiguillage (D65).

---

## 3. Score (D17)

- Score = nombre de questions **différentes** que le joueur a réussies (correctes, dans le
  délai), toutes parties confondues. Points d'une partie = questions qui ont rapporté dans cette
  partie.
- Calculé par le serveur à chaque demande à partir des réponses enregistrées. Aucun champ de score
  n'existe : il n'y a rien à écrire ni à falsifier.
- Jamais remis à zéro en v1 ; supprimer le compte le supprime.
- Plafond : le nombre de questions de la banque, qui monte quand on en ajoute. Une question
  désactivée garde les réussites qu'elle a déjà comptées.
- Depuis l'Elo (D108), le score ne classe plus : l'app l'affiche comme « bonnes réponses ». Il
  reste calculé de même, et sert encore le classement par points de l'app précédente (§ 4).

---

## 4. Classement (D18, D108)

- Joueurs classés : au moins **30 réponses comptées** (§ 8.4), c'est-à-dire sortis du statut
  provisoire (§ 8.3). Ordre : Elo décroissant, puis pseudo croissant (sans tenir compte de la
  casse).
- Rang de compétition : 1 + nombre de joueurs classés à l'Elo strictement supérieur (1, 2, 2, 4).
- **Top 10** : les 10 premières lignes dans cet ordre. Si des ex æquo débordent, la 10e ligne
  coupe ; chacun garde son rang.
- **Ma ligne** : mon rang, mon pseudo, mon Elo, et le nombre de joueurs classés (« 12e sur
  48 »). Dans le top 10, ma ligne y est mise en avant ; plus loin, elle s'affiche sous le top 10.
  Provisoire : pas de ligne ; je vois mon Elo (Accueil, Résultat) et le nombre de réponses
  comptées qui me manquent.
- Une ligne ne contient que rang, pseudo et Elo : aucun identifiant ni autre donnée d'un joueur.
  L'Elo d'un joueur provisoire n'est montré qu'à lui.
- Calculé par le serveur à chaque demande (`GET /classement/elo`), à partir de l'Elo et du
  compteur gardés sur chaque joueur (§ 8.5) ; aucun cache dans l'app (D20).
- **Dans l'app (D72)** : rangs, ordre et Elo sont ceux du serveur, jamais recalculés. Ma ligne
  est dans le top 10 si l'une de ses lignes porte le pseudo de `moi` (casse ignorée : un pseudo
  est unique, D9) ; sinon, et si `moi` n'est pas nul, elle s'affiche sous le top 10, même quand
  son rang est 10 (ex æquo coupé par la 10e ligne).
- **Classement par points, pour l'app précédente (D22)** : `GET /classement`, et le rang et le
  nombre de classés de `/moi`, restent calculés comme avant l'Elo (score d'au moins 1 point,
  score décroissant, mêmes ex æquo, mêmes lignes), pour qu'une app pas encore mise à jour
  marche. L'app de l'Elo ne les affiche plus.

---

## 5. Version minimale (D21, D22)

- Au démarrage, avant tout, l'app lit `GET /info` (public, sans jeton, D30) : `versionMinimale`,
  un versionCode, constante des hooks qui vaut 1000 au départ (D35). Si le versionCode de l'app
  est inférieur : écran « Mise à jour nécessaire », rien d'autre n'est accessible.
- `/info` injoignable : « Pas de connexion ».
- L'app ignore les champs JSON inconnus et traite un code d'erreur inconnu comme une erreur
  générique : un serveur plus récent ne la casse pas.
- Remonter `versionMinimale` est un dernier recours, quand un changement incompatible ne peut pas
  être évité. D22 demande d'abord de garder les routes `/v1` (`serveur.md` § 6.6).

---

## 6. Nouvelles versions (D61 – D64)

Règles de l'app seule, reprises de MuscleNote (`UpdateRules`), sans notification. Le serveur
Eruditus n'y prend aucune part ; la version minimale (§ 5) reste la seule obligation.

### 6.1 Quand vérifier

- Build release seulement. Debug et preview ne vérifient rien : leur versionCode (1000) serait
  toujours dépassé.
- Au chargement de l'Accueil, si elle est due, comme MuscleNote : **6 h** après la dernière
  vérification réussie, et **1 h** après le dernier échec. Les deux délais s'appliquent : un échec
  récent retarde l'essai même si la dernière réussite date de plus de 6 h.
- Jamais vérifié, ou date dans le futur (horloge du téléphone reculée), comptent comme dus.
- Une réussite efface l'échec précédent. Une seule vérification à la fois.

### 6.2 Lire la dernière Release

- `GET https://api.github.com/repos/Bouss45/Eruditus/releases/latest` : GitHub y renvoie la
  dernière Release qui n'est ni un brouillon ni une préversion ; l'app vérifie quand même
  `draft` et `prerelease`.
- Version : le tag sans son `v` (`v1.0.13` → `1.0.13`). Un tag sans aucun nombre (`latest`) est
  ignoré.
- APK : le premier fichier dont le nom finit par `.apk` (`Eruditus-v1.0.13.apk`,
  `spec/publication.md` § 2). Sans APK, la page de la Release (`html_url`).
- Réponse illisible, sans tag ou sans page, statut autre que 200 (dont 403 et 429 de la limite de
  60 requêtes par heure), plus de 1 Mo : échec. Champs inconnus ignorés.

### 6.3 Plus récente que l'app installée

- Tag `v1.0.n` (forme de `release.yml`) : versionCode 1000 + n, comparé au versionCode installé.
- Autre forme : nombres un à un contre le versionName installé, un nombre manquant valant 0
  (`1.0.10` après `1.0.9`, `1.1` après `1.0.42`, `1.0` égale `1.0.0`).
- Égale ou plus ancienne : rien n'est annoncé.

### 6.4 Annonce et « Plus tard »

- Annoncée : la dernière Release connue, plus récente que l'app installée, différente de la
  version ignorée.
- « Plus tard » ignore cette version : elle n'est plus jamais annoncée ; une plus récente l'est.
- Une fois l'app mise à jour à cette version ou plus, l'annonce disparaît d'elle-même.
- Un échec de vérification ne change rien : ni annonce effacée, ni « Pas de connexion ».

### 6.5 Télécharger et Mettre à jour (D63)

- Obtainium a installé l'app si l'installateur ou l'initiateur de l'installation est
  `dev.imranr.obtainium`, `dev.imranr.obtainium.fdroid` ou `com.android.vending` : c'est ainsi
  qu'Obtainium se présente quand il installe par Shizuku, et Eruditus n'est pas sur le Play
  Store.
- Alors : Obtainium, sa variante d'abord puis l'autre. Sinon, ou si aucun ne s'ouvre : l'APK de la
  dernière Release connue, à défaut sa page, à défaut la page de la dernière Release du dépôt.
- « Télécharger » prend la Release annoncée. « Mettre à jour » prend la dernière Release connue si
  elle est plus récente que l'app installée, même ignorée par « Plus tard » ; sinon, la page de la
  dernière Release du dépôt.

---

## 7. Avis (D85 – D94)

Un joueur connecté envoie une idée, un bug ou une question à revoir. Le serveur ne garde pas
l'avis : il le transmet aussitôt au mainteneur, sur Discord (`serveur.md` § 5.4).

### 7.1 Texte (D86)

Une seule règle, appliquée par le serveur, qui fait foi, et par l'app avant l'envoi, dans cet
ordre :

1. `\r\n`, puis `\r` seul, deviennent `\n` ;
2. les autres caractères de contrôle sont retirés : U+0000 – U+001F sauf `\n` (la tabulation est
   donc retirée), et U+007F – U+009F ;
3. les blancs du début et de la fin sont retirés : ceux que retire `trim()` en JavaScript (espace,
   `\n`, U+00A0, U+1680, U+2000 – U+200A, U+2028, U+2029, U+202F, U+205F, U+3000, U+FEFF).

Le reste est gardé tel quel : lignes vides, espaces intérieurs, émojis, caractères invisibles.
Le texte obtenu doit faire **1 à 1000 caractères**, comptés en points de code, sinon
`avis_invalide` : un émoji simple compte 1 ; une séquence (drapeau, teinte de peau, famille) compte
chacun de ses points de code, comme pour le mot de passe (§ 1.2).

- L'app compte et vérifie avec exactement cette règle (compteur, bouton) et envoie le texte déjà
  passé par elle ; le serveur la rejoue sur ce qu'il reçoit.
- Les mêmes cas (`server/tests/cas-avis.json` : retours à la ligne, contrôles, blancs, émojis,
  1000 et 1001 caractères) sont joués par les tests serveur, à travers la route, et par les tests
  JVM de l'app.

### 7.2 Envoi (D87, D88)

- `POST /avis` avec le texte, la version de l'app et celle d'Android. Le pseudo est celui du
  jeton, au moment de l'envoi (après un changement de pseudo, le nouveau) ; un pseudo ou un autre
  joueur écrit dans le corps est ignoré.
- Versions : informatives, jamais crues et jamais cause d'un refus. Chacune n'est gardée que si
  elle suit son motif : version de l'app comme « 1.0.6 » ou « 1.0.0-preview », version d'Android
  comme « 15 » ou « 8.1.0 » ; sinon « inconnue ».
- Le message reçu par le mainteneur : « Avis de Hibou_42 », le texte, puis « Eruditus 1.0.6 ·
  Android 15 » et l'heure. Aucune mention n'y notifie personne (`@everyone`, `@here` et les
  autres restent du texte).

### 7.3 Fréquence (D90)

- **Un avis réussi par compte et par 10 minutes glissantes**, comptées depuis le dernier avis
  transmis : avant, `avis_trop_recent`. Un refus (texte invalide, trop tôt) ou un échec de
  Discord ne compte pas ; le premier avis est libre.
- Deux avis du même compte envoyés en même temps : un seul part, l'autre est `avis_trop_recent`.
- En plus, 5 avis par heure et par IP (D23, D91).

### 7.4 Conservation (D88)

- Le serveur ne garde que la date du dernier avis réussi du compte (champ caché), effacée avec le
  compte. Le texte n'est écrit ni dans la base ni dans un journal.
- Sur le téléphone, le texte ne vit que dans la mémoire de l'écran (`donnees.md` § 3).
- Les avis déjà transmis restent sur Discord après la suppression du compte.

---

## 8. Elo (D102 – D113)

Chaque joueur a un Elo, chaque question une difficulté. Le serveur seul les calcule et les écrit
(D8), à partir des réponses ; l'app n'envoie que ses choix. L'Elo classe les joueurs (§ 4) ; les
points (§ 2.4, § 3) restent, comme « bonnes réponses ». Le tirage ne tient pas compte de l'Elo.
Hors périmètre : thèmes, Elo par thème, vitesse, saisons, mode en ligne (`SPEC.md` § 5).
Constantes : `serveur.md` § 5.5 (D111). Affichage : `ecrans.md` § 8, § 10, § 11.

### 8.1 Échelle (D103)

- Elo entier ; départ **1000** (à la création du compte), plancher **100** : il ne descend jamais
  plus bas.
- Difficulté de départ d'une question, selon son niveau (D98) :

  | niveau | 1 | 2 | 3 | 4 | 5 | 0 (aucun) |
  |---|---|---|---|---|---|---|
  | difficulté | 800 | 1200 | 1600 | 2000 | 2400 | aucune |

  Une question sans niveau ne change aucun Elo, ni le sien, et n'est pas une réponse comptée.

### 8.2 Attendu et résultat (D103)

- Attendu d'un joueur d'Elo P contre une question de difficulté Q :

  E = 0,25 + 0,75 × 1 / (1 + 10^((Q − P) / 400))

  Un QCM à 4 choix donne au moins 25 % de chances : E va de 0,25 (question bien trop difficile)
  à 1 (bien trop facile) ; à P = Q, E = 0,625.
- Résultat S : **1** pour une réponse correcte (juste et dans le délai, D32) ; **0** pour une
  réponse fausse, sans réponse, hors délai ou abandonnée (§ 8.5). La vitesse n'entre pas dans le
  calcul : répondre en 2 s ou en 19 s donne le même Elo.
- Nouvel Elo : max(100, arrondi(P + K × (S − E))), à l'entier le plus proche, ,5 vers le haut.
- Exemples : à 1000 contre une question à 1600 (K = 40), 1029 si juste, 989 si faux. À niveau
  égal (K = 24), +9 si juste, −15 si faux : il faut en réussir plus de 62,5 % pour monter. À 2300
  contre une question à 800 (K = 24), une erreur coûte 24 (2276), une bonne réponse ne rapporte
  rien.

### 8.3 K et statut provisoire (D104)

| réponses comptées avant celle-ci | K | statut |
|---|---|---|
| 0 – 29 | 40 | provisoire |
| 30 – 199 | 24 | classé |
| 200 et plus | 12 | classé |

- K élevé au début : l'Elo trouve vite le niveau du joueur ; plus bas ensuite : il se stabilise.
- **Provisoire** : moins de 30 réponses comptées. Le joueur voit son Elo (Accueil, Résultat), mais
  n'apparaît pas au classement (§ 4), et ses réponses ne font pas bouger la difficulté des
  questions (§ 8.6). Sa 30e réponse comptée, encore à K = 40, le fait classer.

### 8.4 Réponses comptées (D105)

- Une question ne compte qu'à la **première tentative** du joueur : la première fois qu'elle lui
  est servie, et seulement si elle a un niveau. C'est décidé quand elle est servie : une autre
  ligne de réponse de ce joueur sur cette question, même d'avant l'Elo, l'en empêche.
- Une question resservie (§ 2.1) ne change ni l'Elo, ni le compteur, ni la difficulté. Ses points
  suivent toujours D17 (§ 2.4) : une question vue, ratée, puis réussie plus tard rapporte encore
  son point aux « bonnes réponses ».
- Les réponses d'avant l'Elo ne sont jamais rejouées : les joueurs existants partent de 1000 avec
  0 réponse comptée (D110), et les questions qu'ils ont déjà vues ne compteront pas pour eux.
- **Banque épuisée** : quand le joueur a vu toutes les questions actives, le tirage lui ressert
  les plus anciennes (§ 2.1), sans aucun effet sur son Elo : ses parties sont des révisions,
  jusqu'à l'arrivée de nouvelles questions. Le Résultat d'une partie dont les 5 questions avaient
  toutes déjà été servies dit « Révision : sans effet sur l'Elo » au lieu d'une variation de 0,
  et l'Accueil l'explique en une phrase tant que la banque est épuisée (`ecrans.md` § 8, § 10).

### 8.5 Mise à jour (D106)

Par le serveur seul, dans la transaction de la route qui sert ou corrige la question, jamais
ailleurs (`serveur.md` § 5.5).

1. **Question servie et comptée** (création de partie, question suivante) : le serveur fixe E et
   K, puis compte aussitôt la perte (S = 0) : l'Elo du joueur devient max(100, arrondi(P − K ×
   E)), et son compteur de réponses comptées avance de 1.
2. **Réponse correcte enregistrée** : la perte est remplacée par le gain, max(100, arrondi(P + K ×
   (1 − E))), calculé depuis le même Elo de départ P, la même difficulté et le même K. Fausse,
   sans réponse ou hors délai : la perte reste.
3. **Abandon** (quitter l'app, laisser filer le chrono sans rien envoyer, nouvelle partie, 30 min
   d'inactivité) : rien à faire, la perte est déjà comptée. Abandonner ne protège jamais l'Elo, et
   l'Accueil le montre dès le retour.

- **Question redemandée** (retour au premier plan, recréation de l'écran, D66 ; § 2.2) : ce n'est
  pas une nouvelle mise en service. La perte déjà comptée ne l'est pas une seconde fois, le
  compteur n'avance pas, et la question garde son Elo d'avant, celui de sa mise en service : le
  gain d'une bonne réponse se calcule depuis lui, quel que soit le nombre de fois où elle a été
  redemandée.
- Une réponse envoyée deux fois n'est enregistrée qu'une fois (§ 2.3), son gain aussi.
- Pendant une question comptée, l'Elo du joueur, au classement comme dans `/moi`, compte déjà la
  perte, jusqu'à la réponse (20 s au plus, sauf abandon).
- **Variation d'une partie** (Résultat) : somme des écarts (après − avant) de ses réponses
  comptées ; 0 si aucune. Partie de révision (§ 8.4) : pas de variation, « Révision : sans effet
  sur l'Elo ».
- Chaque réponse comptée garde l'Elo avant et après, la difficulté et le K utilisés : chaque pas
  se contrôle, et tout se rejoue dans l'ordre des réponses.

### 8.6 Difficulté des questions (D107)

- Part de la valeur de son niveau (§ 8.1).
- Ne bouge qu'avec les **réponses données** : un choix reçu dans le délai, juste ou faux, d'un
  joueur **non provisoire** au moment où la question lui a été servie. Jamais avec « sans
  réponse » (délai écoulé), une réponse hors délai ou un abandon : seules les réponses données
  disent quelque chose de la question (un délai écoulé peut venir d'une distraction, d'une app
  tuée ou du réseau).
- Les 30 premières de ces réponses ne font que compter. À partir de la 31e : Q' = arrondi(Q − 8 ×
  (S − E)), avec l'attendu E du joueur : une question ratée monte, une question réussie descend,
  d'autant plus que c'était inattendu.
- Bornée à **± 300** autour de la valeur de son niveau : un niveau 3 reste entre 1300 et 1900.
- Gardée sur la question et mise à jour dans la transaction de la réponse : rien n'est recalculé
  quand on lit l'Accueil ou le classement.
- Niveau **réellement** changé à l'import de la banque (ajouté, modifié, retiré) : la difficulté
  reprend la valeur du nouveau niveau, et son compteur repart de 0 ; niveau retiré : plus de
  difficulté, la question ne compte plus. Réimporter le même fichier, ou changer seulement
  l'énoncé, les choix, la bonne réponse, l'explication ou la source, ne remet rien à zéro.
- La suppression d'un compte efface ses réponses, pas ce qu'elles ont fait aux difficultés.

### 8.7 Plafond naturel (D104)

- Plus l'Elo dépasse la difficulté, plus la bonne réponse est attendue : elle rapporte de moins
  en moins, et une erreur coûte presque K. C'est voulu, et la formule le fait seule.
- Arrondie, une bonne réponse ne rapporte plus rien dès que l'Elo dépasse la difficulté de plus
  de 618 (K = 24) ou de 492 (K = 12), alors qu'une erreur coûte encore presque K.
- Le plafond est donc la question la plus difficile de la banque : 2400 au départ, 2700 au plus si
  elle monte (§ 8.6). Pour tenir 3000 à K = 12, il faudrait ne presque jamais se tromper, et
  seulement sur des questions montées au-delà de 2500 parce que les joueurs classés les ratent :
  3000 est presque hors d'atteinte.
- Avec la banque de 150 questions, nul ne dépasse 150 réponses comptées (K reste à 24) : sans
  aucune erreur, on finit vers 2250 ; avec 9 bonnes réponses sur 10, vers 2050.

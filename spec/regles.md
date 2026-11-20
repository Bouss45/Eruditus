# Règles : compte, partie, score, classement, nouvelles versions

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
- Non modifiable en v1 (`SPEC.md` § 5.2). Libéré à la suppression du compte : quelqu'un d'autre
  peut le reprendre aussitôt, sans rien hériter.

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

- Depuis Compte : « Supprimer mon compte », mot de passe redemandé, confirmation, puis
  `POST /comptes/suppression`. Le serveur vérifie le mot de passe, puis efface en une transaction
  le compte, ses parties et ses réponses. Le joueur disparaît du classement à la requête
  suivante ; le pseudo est libre.
- Mot de passe faux : refus, rien n'est effacé ; essais limités par IP (D23).
- Sans réseau : impossible, rien n'est effacé.
- Après succès, l'app efface la session locale et revient à Bienvenue.

---

## 2. Partie (D15)

### 2.1 Création

« Jouer » appelle `POST /parties`. Le serveur :

1. abandonne la partie en cours du joueur, s'il y en a une (§ 2.5) ;
2. tire 5 questions actives différentes, dans cet ordre de priorité : jamais servies à ce joueur,
   puis servies mais jamais réussies, puis déjà réussies ; au hasard dans chaque groupe ;
3. crée la partie et sert la question 1, dont le chrono démarre à cet instant.

Moins de 5 questions actives dans la banque : refus (`banque_insuffisante`), cas d'un serveur mal
rempli.

### 2.2 Question servie et chrono

- Une question servie contient : son numéro (1 à 5), le total (5), l'énoncé, les 4 choix dans
  l'ordre de la banque, le délai (20 s) et le temps restant. **Jamais la bonne réponse** (D16).
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
  1) avec sa raison, score total du joueur.
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

---

## 4. Classement (D18)

- Joueurs classés : score d'au moins 1 point. Ordre : score décroissant, puis pseudo croissant
  (sans tenir compte de la casse).
- Rang de compétition : 1 + nombre de joueurs au score strictement supérieur (1, 2, 2, 4).
- **Top 10** : les 10 premières lignes dans cet ordre. Si des ex æquo débordent, la 10e ligne
  coupe ; chacun garde son rang.
- **Ma ligne** : mon rang, mon pseudo, mon score, et le nombre de joueurs classés (« 12e sur
  48 »). Dans le top 10, ma ligne y est mise en avant ; plus loin, elle s'affiche sous le top 10 ;
  à 0 point : « Pas encore classé ».
- Une ligne ne contient que rang, pseudo et score : aucun identifiant ni autre donnée d'un joueur.
- Calculé par le serveur à chaque demande (`GET /classement`) ; aucun cache dans l'app (D20).

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

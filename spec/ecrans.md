# Écrans

> Partie de la spec (`SPEC.md` § 2). **Dn** renvoie aux Décisions de `SPEC.md` § 3. Ce fichier
> dit ce que montre et fait chaque écran ; l'apparence (couleurs, tailles, composants, gabarits)
> vient de `DESIGN.md`, qui fait foi. Les règles appliquées sont dans `regles.md`.

---

## Sommaire

1. Conventions et navigation
2. Démarrage
3. Bienvenue
4. Créer un compte
5. Code de récupération
6. Se connecter
7. Mot de passe oublié
8. Accueil
9. Partie
10. Résultat
11. Classement
12. Compte
13. Pas de connexion
14. Mise à jour nécessaire
15. Messages d'erreur

---

## 1. Conventions et navigation

- Textes en français, tutoiement, dans les ressources (`values/strings.xml`), jamais en dur dans le
  code.
- Une pile d'écrans, sans barre de navigation : l'Accueil mène à tout. Le retour système revient à
  l'écran précédent, sauf mention contraire.
- Pendant un appel réseau : indicateur de chargement dans le bouton concerné, boutons désactivés
  (jamais de double envoi). Délai d'attente réseau : 10 s pour se connecter, 10 s pour la
  réponse (D52).
- Pile d'écrans en mémoire (D56) : Bienvenue, Code de récupération, Accueil, Pas de connexion et
  Mise à jour nécessaire remplacent la pile ; Créer un compte, Se connecter, Mot de passe oublié
  et Compte s'y empilent. Rien n'est sauvegardé : si Android tue l'app, elle repart de
  l'aiguillage (§ 1.2).
- Données d'un écran (Accueil, Classement) : au tout premier chargement de l'écran, un indicateur
  centré remplace le contenu (TalkBack : « Chargement »). Ensuite (retour sur l'Accueil après une
  partie, Classement tiré vers le bas), les valeurs déjà affichées restent à l'écran, en mémoire
  seulement (rien sur le disque), et se mettent à jour à l'arrivée de la réponse.
- Message en haut d'écran : il reste jusqu'à ce qu'on quitte l'écran ou qu'on relance une
  action ; ni minuterie, ni bouton fermer.
- Couleurs sémantiques seulement (vert = bonne réponse, rouge = mauvaise), jamais seules : une
  icône ✓ ou ✗ et un texte les doublent (TalkBack, daltonisme).
- Accessibilité : cibles de 48 dp, TalkBack, texte à 200 % sans coupure.

### 1.1 Arborescence

```
Démarrage ─┬─ Mise à jour nécessaire
           ├─ Pas de connexion
           ├─ Bienvenue ─┬─ Créer un compte ── Code de récupération ──┐
           │             └─ Se connecter ── Mot de passe oublié ── Code de récupération ──┤
           └─ Accueil ◄───────────────────────────────────────────────────────────────────┘
                ├─ Partie ── Résultat
                ├─ Classement
                └─ Compte
```

### 1.2 Aiguillage au démarrage

1. `GET /info`, sans jeton (D30) : erreur réseau → Pas de connexion ; versionCode inférieur à
   `versionMinimale` → Mise à jour nécessaire (D21).
2. Pas de session locale → Bienvenue (D19).
3. Session locale → `auth-refresh` : succès → Accueil (le nouveau jeton remplace l'ancien) ;
   401 → session effacée, Bienvenue avec « Ta session a expiré, reconnecte-toi. » ; erreur
   réseau → Pas de connexion.

Un 401 reçu plus tard sur n'importe quelle route a le même effet que le 401 du point 3.

Tout autre échec de `/info` ou d'`auth-refresh` (5xx, pas de réponse, réponse illisible, 429, code
inattendu) → Pas de connexion, texte serveur (D53) : la session est gardée. Session illisible
(fichier tronqué, clé perdue, version inconnue) → effacée, Bienvenue sans message (D54).

---

## 2. Démarrage

Écran de lancement Android (logo) le temps de l'aiguillage du § 1.2. Rien à toucher. Il n'est
montré qu'au lancement : « Réessayer » relance l'aiguillage avec l'indicateur dans son bouton
(§ 13).

---

## 3. Bienvenue (D19)

- Le nom « Eruditus » et une phrase : « La culture générale, une question à la fois. »
- « Créer un compte » (action principale) et « Se connecter ».
- Message éventuel en haut, selon l'arrivée : « Ta session a expiré, reconnecte-toi. » ou « Ton
  compte a été supprimé. »
- Retour système : quitte l'app.

---

## 4. Créer un compte

- **Pseudo**, avec l'aide « 3 à 20 caractères : lettres, chiffres et _ ».
- **Mot de passe**, avec l'aide « 8 caractères minimum » et un bouton afficher / masquer.
- « Créer mon compte » : actif quand les deux champs respectent les règles (`regles.md` § 1.1,
  § 1.2), exactement celles du serveur : pour le mot de passe, 8 à 71 caractères et 72 octets au
  plus (D10). Les erreurs du serveur s'affichent sous le champ concerné (§ 15).
- Pendant la saisie (D55) : tant que le champ est juste trop court, l'aide seule ; dès qu'il ne
  peut plus devenir valide (caractère refusé, plus de 20 caractères pour le pseudo, plus de 71
  caractères ou de 72 octets pour le mot de passe), le texte de `pseudo_invalide` ou de
  `mot_de_passe_invalide` remplace l'aide. Modifier un champ efface l'erreur du serveur qui le
  concernait. Les espaces au début et à la fin du pseudo sont retirés avant la vérification.
- Remplissage automatique : champs marqués nouveau pseudo et nouveau mot de passe, pour que le
  gestionnaire de mots de passe du téléphone propose de les enregistrer.
- Succès → Code de récupération.

---

## 5. Code de récupération (D11)

Après la création du compte et après chaque récupération.

- Titre « Note ton code de récupération ».
- Le code en grand, en 4 groupes de 4, lu caractère par caractère par TalkBack.
- « Sans e-mail, c'est le seul moyen de retrouver ton compte si tu oublies ton mot de passe. Il ne
  sera plus jamais affiché. »
- « Copier » : copie le code au format `7K3M-Q9XA-2HDE-F8RT` dans le presse-papiers, marqué comme
  sensible (Android 13 et plus : pas d'aperçu à l'écran). Android 12 et moins n'affichent pas de
  confirmation : message en haut « Code copié. ».
- « J'ai noté mon code » (action principale) → Accueil.
- Retour système : « As-tu bien noté ton code ? Il ne sera plus jamais affiché. » [Oui] → Accueil,
  [Pas encore] → reste.
- Le code n'existe que dans la mémoire de l'écran : jamais enregistré (`donnees.md` § 3). Les
  captures d'écran restent permises : c'est une façon de le noter.

---

## 6. Se connecter

- **Pseudo** et **Mot de passe** (afficher / masquer), marqués pour le remplissage automatique.
- « Se connecter » : actif quand les deux champs sont remplis, sans autre vérification (D55) ;
  lien « Mot de passe oublié ? » → Mot de passe oublié.
- Erreurs : « Pseudo ou mot de passe incorrect. », « Trop de tentatives. Réessaie dans quelques
  minutes. »
- Succès → Accueil.

---

## 7. Mot de passe oublié

- « Sans code de récupération, le compte ne peut pas être retrouvé. »
- **Pseudo**, **Code de récupération** (saisie tolérante, `regles.md` § 1.6), **Nouveau mot de
  passe**, avec l'aide « 8 caractères minimum » et afficher / masquer.
- « Changer mon mot de passe » : actif quand les trois champs sont remplis. Le nouveau mot de
  passe est vérifié avant l'envoi, avec la règle exacte du serveur (D10), et pendant la saisie
  comme à la création (§ 4). Erreurs : « Pseudo ou code incorrect. », et sous le nouveau mot de
  passe le texte de `mot_de_passe_invalide` (§ 15).
- Un pseudo hors du motif ou un code mal formé donne « Pseudo ou code incorrect. » sans appel au
  serveur (D55) : aucun essai perdu. Le code est jugé après la saisie tolérante (espaces,
  minuscules, tirets, O, I, L), avec exactement la règle de format du serveur (`regles.md` § 1.6).
- Succès → Code de récupération (le nouveau), puis Accueil.

---

## 8. Accueil

Données : `GET /moi`, rechargées à chaque retour sur l'écran (après une partie, notamment).

- Le pseudo.
- Le score, sous le libellé « Score » : « 12 points » (« 1 point », « 0 point »).
- Le rang, sous le libellé « Rang » : « 3e sur 48 » (« 1er sur 48 ») ou « Pas encore classé ».
- « Jouer » (action principale) → Partie.
- « Classement » → Classement ; « Compte » → Compte.
- Retour système : quitte l'app.

**Avant la partie (D57)** : jusqu'aux sujets Partie et Classement, ni « Jouer » ni « Classement »
(jamais un bouton qui ne fait rien dans une Release) ; seul le petit bouton « Compte », en bas.

- Pseudo : celui de `/moi` (casse de création), qui remplace celui de la session (D54).
- Premier chargement : indicateur centré à la place du contenu (§ 1) ; ensuite, les valeurs
  restent affichées pendant le rechargement.
- Erreurs de `/moi` : 401 → Bienvenue avec « Ta session a expiré, reconnecte-toi. » ; réseau,
  5xx → Pas de connexion ; 429 et tout autre cas → message en haut (§ 15), valeurs déjà
  affichées gardées.

---

## 9. Partie (D15, D16)

- « Jouer » crée la partie (`POST /parties`) et affiche la question 1.
- En tête : « Question 2/5 » et le chrono : compte à rebours de 20 s calé sur `restantMs`, barre et
  secondes, annoncé par TalkBack à 10 s et à 5 s.
- L'énoncé, puis les 4 choix (A, B, C, D) en boutons pleine largeur.
- Toucher un choix : envoi, les choix se verrouillent. Chrono à 0 : l'app envoie « sans réponse ».
- **Correction** : la bonne réponse marquée ✓ en vert, avec « Bonne réponse » ; le choix du
  joueur, s'il est faux, marqué ✗ en rouge, avec « Ta réponse » ; un message selon `raison`, avec
  son détail :

  | raison | message | détail |
  |---|---|---|
  | `gagne` | « Bonne réponse ! +1 point » | |
  | `deja_reussie` | « Bonne réponse, déjà réussie : pas de point cette fois. » | |
  | `fausse` | « Mauvaise réponse. » | « La bonne réponse était C. » |
  | `sans_reponse`, `hors_delai` | « Temps écoulé. » | « La bonne réponse était C. » |

  C est la lettre de la bonne réponse. TalkBack annonce le message et le détail avec le texte du
  choix : « Mauvaise réponse. La bonne réponse était C, Salvador Dalí. »

- Puis « Question suivante » (`POST /parties/{id}/question`) ou, après la 5e, « Voir le résultat »
  → Résultat.
- Retour système ou bouton fermer : « Abandonner la partie ? Les réponses déjà données
  comptent. » [Abandonner] → Accueil, [Continuer] ; le chrono continue pendant la question.
- Rotation ou retour depuis l'arrière-plan : la question en cours est redemandée au serveur, avec
  son temps restant.
- Erreur réseau → Pas de connexion (la partie est perdue). `partie_finie`, `numero_invalide`,
  `deja_repondu`, `partie_introuvable` → « Cette partie n'est plus valable. » puis Accueil.

---

## 10. Résultat

Données : le `bilan` de la dernière correction.

- « 3 bonnes réponses sur 5 ».
- Points gagnés : « +2 points » (« +0 point » : « Aucun point cette fois »).
- Score total : « Score : 14 points ».
- « Rejouer » (action principale) → nouvelle Partie ; « Accueil ».
- Retour système : Accueil.

---

## 11. Classement (D18)

Données : `GET /classement`, rechargées à l'ouverture et en tirant la liste vers le bas.

- Le top 10, sous l'en-tête « Top 10 » et « Points » : rang, pseudo, points (« 1er · Hypatie · 19
  points »).
- Ma ligne mise en avant et marquée « Toi » si j'y suis ; sinon, un séparateur puis ma ligne (« 27e · Hibou_42 · 4
  points ») sous le top 10.
- Pas encore classé : « Réussis une question pour entrer au classement. »
- Personne de classé : « Personne n'est encore classé. Lance une partie ! »
- Sous la liste : « 48 joueurs classés ».

---

## 12. Compte (D13, D14)

- Le pseudo, sous le libellé « Pseudo », et la version de l'app (« Eruditus 1.0.12 » ; en preview
  « Eruditus 1.0.0-preview »).
- « Se déconnecter » → « Se déconnecter de tous tes appareils ? » [Se déconnecter] → Bienvenue,
  [Annuler]. Indicateur dans le bouton de la boîte pendant l'appel ; puis Bienvenue sans message,
  que l'appel ait réussi ou non (réseau coupé, 401, autre erreur : D13).
- « Supprimer mon compte » (action destructrice) → boîte avec le champ **Mot de passe** et « Ton
  compte, tes parties et ton score seront effacés définitivement. Ton pseudo deviendra libre. »
  [Supprimer définitivement] (actif une fois le mot de passe saisi), [Annuler].
  - Succès → Bienvenue avec « Ton compte a été supprimé. »
  - Erreurs, sous le champ de la boîte (un message en haut serait caché par la boîte) : « Mot de
    passe incorrect. », « Trop de tentatives. Réessaie dans quelques minutes. », « Une erreur est
    survenue. Réessaie. ». Erreur réseau : Pas de connexion (rien n'est effacé).
  - 401 : session effacée, Bienvenue avec « Ta session a expiré, reconnecte-toi. ».
- **Comptes d'essai (D59)** : debug et preview parlent au vrai serveur ; un compte créé pour
  essayer apparaît au classement dès son premier point. Le mainteneur le supprime ici.

---

## 13. Pas de connexion (D20)

- « Pas de connexion » et « Vérifie ta connexion internet, puis réessaie. » ; pour une erreur 5xx
  ou un serveur qui ne répond pas : « Le serveur ne répond pas. Réessaie dans un moment. »
- Réseau ou serveur (D53) : échec avant que la connexion soit établie (pas de réseau, nom
  introuvable, connexion refusée ou trop longue, TLS) → icône réseau et premier texte ; connexion
  établie puis pas de réponse dans le délai, 5xx ou réponse illisible → icône serveur et second
  texte.
- « Réessayer » relance l'aiguillage du démarrage (§ 1.2), avec l'indicateur dans le bouton.
- Aucune donnée affichée : rien n'est gardé en cache.
- Retour système : quitte l'app (l'écran remplace la pile).

---

## 14. Mise à jour nécessaire (D21)

- « Mise à jour nécessaire » et « Cette version d'Eruditus ne fonctionne plus avec le serveur. »
- « Mettre à jour » : ouvre Obtainium s'il est installé, sinon la page des Releases
  `https://github.com/Bouss45/Eruditus/releases` dans le navigateur (D60 : intent de lancement
  d'Obtainium, sans `<queries>` ni permission).
- Rien d'autre n'est accessible. Retour système : quitte l'app.

---

## 15. Messages d'erreur

Codes renvoyés par le serveur (`serveur.md` § 5.3) et texte affiché. Trois places : sous le
champ, au-dessus de l'action (erreur de formulaire), en haut de l'écran (message en haut) ;
leur apparence est dans `DESIGN.md`.

| cas | texte | où |
|---|---|---|
| `pseudo_invalide` | « 3 à 20 caractères : lettres, chiffres et _ uniquement. » | sous le pseudo |
| `pseudo_reserve` | « Ce pseudo est réservé. » | sous le pseudo |
| `pseudo_pris` | « Ce pseudo est déjà pris. » | sous le pseudo |
| `mot_de_passe_invalide` | « 8 à 71 caractères (moins avec des accents ou des emojis). » | sous le mot de passe ; l'app affiche le même texte quand sa propre vérification échoue (D10) |
| connexion échouée (400 sur `auth-with-password`) | « Pseudo ou mot de passe incorrect. » | Se connecter, au-dessus de l'action |
| `code_incorrect` | « Pseudo ou code incorrect. » | Mot de passe oublié, au-dessus de l'action |
| `mot_de_passe_incorrect` | « Mot de passe incorrect. » | sous le champ de la boîte de suppression |
| 401 | « Ta session a expiré, reconnecte-toi. » | en haut de Bienvenue |
| 429 | « Trop de tentatives. Réessaie dans quelques minutes. » | en haut de l'écran en cours ; sous le champ dans la boîte de suppression |
| `partie_introuvable`, `partie_finie`, `numero_invalide`, `deja_repondu` | « Cette partie n'est plus valable. » | en haut de l'Accueil |
| `banque_insuffisante` | « Aucune partie disponible pour le moment. » | en haut de l'Accueil |
| erreur réseau, délai dépassé | Pas de connexion (§ 13) | |
| 5xx | Pas de connexion, texte « Le serveur ne répond pas » | |
| `requete_invalide`, `choix_invalide`, tout autre cas | « Une erreur est survenue. Réessaie. » | en haut de l'écran en cours ; sous le champ dans la boîte de suppression |

Précisions (D53, D55) :

- « Délai dépassé » : avant la connexion établie, texte réseau ; après, texte serveur. Une réponse
  qui n'est pas le JSON attendu compte comme un 5xx.
- Au démarrage, tout cas sans écran prévu donne Pas de connexion, texte serveur (§ 1.2).
- Un 401 n'est attendu que sur une route à jeton. Sur une route publique, qui n'en reçoit jamais
  (D30), il compte comme « tout autre cas ».
- Déconnexion : aucun message, Bienvenue quoi qu'il arrive (§ 12).
- Un message en haut de genre alerte pour 429 et « Une erreur est survenue », info sinon
  (`DESIGN.md` § 8). Les codes de la partie (`partie_*`, `numero_invalide`, `deja_repondu`,
  `banque_insuffisante`, `choix_invalide`) arrivent avec l'écran Partie.

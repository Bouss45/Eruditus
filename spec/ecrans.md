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
12. Paramètres
13. Pas de connexion
14. Mise à jour nécessaire
15. Messages d'erreur
16. Historique (D131 – D136)
17. Détail d'une partie (D132, D133)

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
  et Paramètres s'y empilent, comme le Classement (D71) et l'Historique (D136), qui mène au Détail d'une partie ; Changer de pseudo, Changer de mot de
  passe (D76) et Envoyer un avis (D85) s'empilent sur Paramètres. La Partie s'empile sur l'Accueil, le Résultat
  la remplace, « Rejouer » remplace le Résultat par une Partie (D65). Rien n'est sauvegardé : si Android tue l'app, elle
  repart de l'aiguillage (§ 1.2), et une partie en cours est perdue.
- Données d'un écran (Accueil, Classement) : au premier chargement de l'écran (Accueil : à la
  première arrivée ; Classement : à chaque ouverture, D73), un indicateur centré remplace le
  contenu (TalkBack : « Chargement »). Ensuite (retour sur l'Accueil après une partie, Classement
  tiré vers le bas ou revenu au premier plan), les valeurs déjà affichées restent à l'écran, en mémoire seulement (rien sur
  le disque), et se mettent à jour à l'arrivée de la réponse.
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
                ├─ Historique ── Détail d'une partie
                └─ Paramètres ─┬─ Changer de pseudo
                               ├─ Changer de mot de passe
                               └─ Envoyer un avis
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
- L'Elo, sous le libellé « Elo » : « 1247 », entier, sans espace des milliers (`elo.valeur`,
  D112).
- Le rang par Elo, sous le libellé « Rang » : « 3e sur 18 » (« 1er sur 18 ») (`elo.rang`,
  `elo.classes`). Provisoire (`elo.provisoire`) : pas de rang, mais une phrase : « Elo
  provisoire : encore 12 réponses avant d'entrer au classement. » (« encore 1 réponse »,
  d'après `elo.restantes`).
- Les bonnes réponses, sous le libellé « Bonnes réponses » : « 42 » (`score`, D17) : questions
  différentes réussies, qui ne classent plus (D108). `rang` et `classes` par points ne sont plus
  affichés.
- Banque épuisée (`elo.revision`, D105) : une phrase courte, « Tu as vu toutes les questions :
  tes parties sont des révisions, sans effet sur l'Elo. ». Elle disparaît d'elle-même quand de
  nouvelles questions arrivent ; un joueur encore provisoire qui a tout vu voit les deux
  phrases.
- Mise en page (D117) : `Statistiques` dont l'Elo est la première valeur, en grand (`chiffre`)
  sur sa ligne ; « Rang » et « Bonnes réponses » en dessous, côte à côte, en `choix` 600 (le
  rang est absent quand provisoire). Les phrases d'état (provisoire, banque épuisée) sont du
  texte `corps` `texte-secondaire`, sans surface, sous les `Statistiques` ; l'`Annonce` reste la
  seule surface teintée. TalkBack : chaque statistique d'un bloc, comme aujourd'hui.
- Lecture (D112) : `elo` et ses champs sont obligatoires pour l'app de l'Elo (serveur déployé
  avant elle, D51), `elo.rang` peut être `null` ; manquant ou de mauvais type → réponse illisible
  (D53).
- Un `Lien` « Historique » (D136), sous les `Statistiques` et leurs phrases d'état (12) et avant l'`Annonce` : → Historique (§ 16). Rien d'autre n'est ajouté à l'Accueil.
- « Jouer » (action principale) → Partie.
- « Classement » → Classement ; « Paramètres » → Paramètres (D76).
- Retour système : quitte l'app.

**En bas (D71, D76)** : les petits boutons « Classement » (à gauche) et « Paramètres », côte à
côte, puis « Jouer ».

- Pseudo : celui de `/moi` (casse de création), qui remplace celui de la session (D54).
- Premier chargement : indicateur centré à la place du contenu (§ 1) ; ensuite, les valeurs
  restent affichées pendant le rechargement.
- Erreurs de `/moi` : 401 → Bienvenue avec « Ta session a expiré, reconnecte-toi. » ; réseau,
  5xx → Pas de connexion ; 429 et tout autre cas → message en haut (§ 15), valeurs déjà
  affichées gardées.

**Jouer (D65, D68)** :

- « Jouer » crée la partie (`POST /parties`) : indicateur dans le bouton, « Classement » et
  « Paramètres » désactivés ; succès → Partie, sur la question 1.
- Erreurs : `banque_insuffisante` → « Aucune partie disponible pour le moment. » en haut ; 429 et
  tout autre refus → message en haut (§ 15) ; réseau, 5xx → Pas de connexion ; 401 → Bienvenue
  avec « Ta session a expiré, reconnecte-toi. ».
- Retour d'une partie qui n'est plus valable (§ 9), ou de « Rejouer » sans partie disponible
  (§ 10) : le message en haut de l'Accueil. Un message d'arrivée reste pendant le rechargement
  de `/moi`, jusqu'à ce qu'on quitte l'écran ou qu'on relance une action (§ 1).

**Annonce de mise à jour (D61, D62)**, en release seulement :

- Au chargement de l'Accueil, vérification des nouvelles versions si elle est due
  (`regles.md` § 6.1), en même temps que `/moi` et sans l'attendre.
- Une version plus récente est connue et n'est pas ignorée : sous les statistiques, une
  `Annonce` « Mise à jour disponible », « Eruditus 1.0.13 est disponible. Version installée :
  1.0.12. », avec « Plus tard » (action sûre, à gauche) et « Télécharger ».
- « Télécharger » : Obtainium s'il a installé l'app, sinon l'APK dans le navigateur (D63) ;
  l'annonce reste.
- « Plus tard » : l'annonce disparaît, et cette version n'est plus jamais annoncée.
- Un échec de la vérification n'affiche rien et n'ouvre jamais « Pas de connexion ».
- Pas d'annonce pendant le premier chargement (indicateur centré à la place du contenu).

---

## 9. Partie (D15, D16, D65 – D68, D70)

- La Partie s'ouvre sur la question 1 une fois la partie créée par « Jouer » (§ 8) ou
  « Rejouer » (§ 10).
- En tête : « Question 2/5 » et le chrono : compte à rebours qui part de `restantMs` (20 s pour
  une question neuve, moins pour une question redemandée), barre et secondes, annoncé par
  TalkBack à 10 s et à 5 s (D66).
- **Ligne d'enjeu** (D125 – D129), sous le `Chrono`, hors de la zone qui défile : une ligne de
  texte, sans nouveau composant, en `libelle` `texte` (monochrome : jamais de vert ni de rouge,
  D115), à la ligne plutôt que coupée, sur sa place réservée (celle de la plus longue ligne
  possible, rien ne saute). Données : `question.enjeu` pendant la question, `correction.elo` à la
  correction. Le niveau, en mots (« Très facile », « Facile », « Moyen », « Difficile », « Expert »),
  précède toujours quand il est connu ; signes + et − (U+2212), « 0 » jamais « +0 ».

  | moment | cas | ligne |
  |---|---|---|
  | question | comptée | « Difficile · Réussir +12 · Rater −28 » ; gain ou perte nul : « Réussir 0 », « Rater 0 » |
  | question | déjà vue, révision | « Difficile · Déjà vue : sans effet sur l'Elo » |
  | question | sans niveau | « Sans effet sur l'Elo » |
  | correction | comptée | « Difficile · +12 » ; « Difficile · −28 » ; « Difficile · 0 » (`elo.variation`) |
  | correction | déjà vue, révision | « Difficile · Déjà vue : sans effet sur l'Elo » |
  | correction | sans niveau | « Sans effet sur l'Elo » |

  La ligne reste la même du début à la fin de la question : à la correction seul son texte
  change, sans mouvement ni vibration. Pendant le chrono, la perte est déjà comptée dans l'Elo
  (§ 8) : « Rater −28 » est ce qui reste si la réponse n'est pas correcte. TalkBack : « Difficile.
  Réussir : plus 12. Rater : moins 28. » ; à la correction, après le détail du verdict et avant
  l'explication : « Difficile. Elo, plus 12. » (« moins 28 », « 0 »). Lecture tolérante (D22) :
  `enjeu` ou `elo` absent, `null` ou d'un autre type → pas de ligne, sans erreur ; `niveau` hors
  de 1 à 5 → la ligne sans niveau ; `gain`, `perte` ou `variation` non entiers → la ligne sans
  chiffres. Jamais « Pas de connexion » à cause d'elle (D67). À 200 % (D70), elle fait partie de la
  zone fixe sous le `Chrono` : les 4 choix restent visibles pendant le chrono.
- L'énoncé, puis les 4 choix (A, B, C, D) en boutons pleine largeur.
- Toucher un choix : envoi (`POST /parties/{id}/reponse`), indicateur dans le choix touché, les
  autres verrouillés, chrono arrêté. Un seul envoi par question : tout autre appui est ignoré
  jusqu'à la question suivante (D67). Chrono à 0 : l'app envoie « sans réponse », une fois.
- **Correction** : la bonne réponse marquée ✓ en vert, avec « Bonne réponse » ; le choix du
  joueur, s'il est faux, marqué ✗ en rouge, avec « Ta réponse » ; un message selon `raison`, avec
  son détail :

  | raison | message | détail |
  |---|---|---|
  | `gagne`, `deja_reussie` | « Bonne réponse ! » | |
  | `fausse` | « Mauvaise réponse. » | « La bonne réponse était C. » |
  | `sans_reponse`, `hors_delai` | « Temps écoulé. » | « La bonne réponse était C. » |
  | autre (serveur plus récent, D22), `correcte` vrai | « Bonne réponse ! » | |
  | autre, `correcte` faux | « Mauvaise réponse. » | « La bonne réponse était C. » |

  Plus aucun point n'est affiché (D116) : `point` est lu avec tolérance (D22) mais ignoré. La
  variation de l'Elo de la question, elle, s'affiche à la correction, dans la ligne d'enjeu ci-dessous
  (D129, qui amende D116).

  C est la lettre de la bonne réponse. TalkBack annonce le message et le détail avec le texte du
  choix : « Mauvaise réponse. La bonne réponse était C, Salvador Dalí. »

- **Explication et source** (D96 – D101), quand la correction en a : sous le message et son
  détail, l'explication (« Magritte a peint La Trahison des images en 1929… »), puis la source
  en plus petit et en gris : « Source : Encyclopædia Universalis ». L'une sans l'autre est
  possible ; sans aucune des deux, rien de plus ne s'affiche. Elles apparaissent avec le message,
  jamais avant la réponse. Choix et message remontent alors d'un seul mouvement dans l'espace
  libre sous l'énoncé ; si tout ne tient pas, l'écran défile jusqu'au bas de la source, sans que
  le haut du message sorte de l'écran, et le reste se lit en faisant défiler. TalkBack : même
  annonce qu'avant (message et détail), puis l'explication, la source et le bouton, dans cet
  ordre de lecture.

  Mouvement et vibration (D67, `DESIGN.md` § 6) :

  | raison | bonne réponse | choix du joueur, s'il est autre | vibration |
  |---|---|---|---|
  | `gagne`, `deja_reussie` | choisie : remplissage, rebond, ✓ | — | Confirm |
  | `fausse` | révélée après le « non » | ✗, « Ta réponse », « non » | Reject |
  | `sans_reponse` | révélée en fondu | — | Reject |
  | `hors_delai` | révélée en fondu ; après le « non » s'il y a un autre choix | ✗, « Ta réponse », « non » | Reject |

- **Signaler** (D118, D119) : à la correction, après l'explication et la source, un `Lien`
  « Signaler cette question » ; sans explication ni source, il suit le message et son détail.
  Il ouvre la boîte « Signaler cette question » : texte « Pourquoi ? », quatre `LigneOption` à
  choix unique (« Réponse fausse », « Énoncé ambigu », « Explication fausse », « Autre »), puis le
  `Champ` « Commentaire (facultatif) » de plusieurs lignes, compteur « 0 / 200 », texte passé par
  la règle de D86 ; [Annuler] et [Envoyer], actif dès qu'un motif est choisi ; la boîte défile à
  200 % (`DESIGN.md` § 7). `POST /signalement` `{ partie, numero, motif, commentaire }` ; envoi
  en cours : tout désactivé sauf l'indicateur. Succès : la boîte se ferme et le lien devient le
  texte « Question signalée. Merci. » (`petit`, `texte-secondaire`, annoncé par TalkBack), sans
  action. Erreurs dans la boîte, sous le champ : `signalement_trop_frequent` « Tu as signalé
  plusieurs questions. Réessaie plus tard. » ; réseau « Vérifie ta connexion internet, puis
  réessaie. » ; 5xx ou réponse illisible « Le serveur ne répond pas. Réessaie dans un moment. » ;
  429 et tout autre cas, `partie_introuvable` et `numero_invalide` compris : « Une erreur est survenue. Réessaie. » ; 401 : Bienvenue (§ 15). Le
  motif et le commentaire restent. L'état « signalé » vit dans la mémoire de la correction ; la
  question suivante repart sans. Retour système avec la boîte ouverte : elle se ferme.
- Puis « Question suivante » (`POST /parties/{id}/question`, indicateur dans le bouton) ou, après
  la 5e, « Voir le résultat » → Résultat, qui remplace la Partie.
- Texte à 200 % (D70) : pendant le chrono, les 4 choix restent visibles (c'est le haut de
  l'énoncé qui sort de l'écran) ; à la correction, l'écran défile jusqu'au verdict, visible
  au-dessus du bouton, ou jusqu'au bas de la source s'il y a une explication, sans que le haut du
  verdict sorte de l'écran (D101).
- Retour système ou bouton fermer : « Abandonner la partie ? Les réponses déjà données
  comptent. » [Abandonner] → Accueil, sans appel au serveur ; [Continuer]. Le chrono continue
  pendant la question, et son envoi automatique à 0 aussi.
- Arrière-plan et recréation de l'écran (D66) : au retour, une question qui attend encore sa
  réponse est redemandée au serveur, avec son temps restant ; le chrono reprend là, jamais à
  20 s ; s'il est à 0, « sans réponse » part aussitôt. Correction affichée : rien n'est
  redemandé. Aucun envoi automatique tant que l'écran est en arrière-plan.
- Erreurs (D68) : réseau, 5xx → Pas de connexion (la partie est perdue ; « Réessayer » mène à
  l'Accueil). 401 → Bienvenue avec « Ta session a expiré, reconnecte-toi. ». `partie_finie`,
  `numero_invalide`, `deja_repondu`, `partie_introuvable` → Accueil avec « Cette partie n'est
  plus valable. ». 429 et tout autre refus → message en haut de la Partie (§ 15) ; « Question
  suivante » redevient active, et les choix aussi tant que le chrono tourne.

---

## 10. Résultat (D69, D112)

Données : le `bilan` de la dernière correction, en mémoire seulement.

- « 3 bonnes réponses sur 5 » (« 1 bonne réponse sur 5 », « 0 bonne réponse sur 5 »).
- La variation de l'Elo sur la partie, en grand, à la place des points gagnés : « +14 », « −8 »
  (signe moins U+2212) ; 0 : « Elo inchangé » (`bilan.elo.variation`). TalkBack : « Elo, plus
  14 » (« Elo, moins 8 »).
- Partie de révision (`bilan.elo.revision` : ses 5 questions avaient toutes déjà été servies,
  D105) : « Révision : sans effet sur l'Elo » à la place de la variation, jamais « +0 » ni « Elo
  inchangé ». « Elo inchangé » reste pour une partie qui avait des questions neuves sans que
  l'Elo bouge (questions sans niveau, arrondi, plancher).
- L'Elo après la partie, à la place du score : « Elo : 1261 » (`bilan.elo.valeur`).
- Les points de la partie (« +2 points ») et le score ne sont plus affichés : ils ne classent
  plus (D108).
- Mise en page (D115, D117) : gabarit Information ; sous le titre, la variation seule en grand
  (`Statistiques` sans libellé, `chiffre`, `texte`, jamais en couleur), puis « Elo : 1261 » en
  `corps` `texte-secondaire`. « Elo inchangé » et « Révision : sans effet sur l'Elo » prennent
  la place de la variation, dans le même style.
- Lecture (D112) : `bilan.elo`, `valeur` et `variation` (entiers) et `revision` (booléen)
  obligatoires pour l'app de l'Elo, comme le reste du `bilan` (D67).
- « Rejouer » (action principale) → nouvelle Partie, indicateur dans le bouton, « Accueil »
  désactivé ; « Accueil » → Accueil.
- Erreurs de « Rejouer » (D68) : `banque_insuffisante` → Accueil avec « Aucune partie disponible
  pour le moment. » ; 429 et tout autre refus → message en haut du Résultat ; réseau, 5xx → Pas
  de connexion ; 401 → Bienvenue.
- Retour système : Accueil.

---

## 11. Classement (D18, D72, D73, D108, D112)

Données : `GET /classement/elo` (D109), rechargées à l'ouverture, en tirant la liste vers le bas
et au retour au premier plan. Titre de la barre : « Classement ». L'app précédente garde
`GET /classement`, par points (D108) ; l'app de l'Elo ne l'appelle plus.

- Le top 10, sous l'en-tête « Top 10 » et « Elo » : rang, pseudo, Elo (« 1er · Hypatie ·
  2140 »), dans l'ordre et avec les rangs du serveur : des ex æquo ont le même rang (« 2e »,
  « 2e », « 4e »).
- Ma ligne, reconnue à mon pseudo (casse ignorée) : dans le top 10, mise en avant et marquée
  « Toi » ; sinon, un séparateur puis ma ligne seule (« 27e · Hibou_42 · 1180 »), teintée,
  sous le top 10. Un ex æquo de la 10e ligne coupé par le top 10 est sous le séparateur, avec
  son rang (« 10e »).
- Provisoire (`moi` nul) : l'`Encadre` « Elo provisoire : encore 12 réponses avant d'entrer au
  classement. » (« encore 1 réponse », d'après `restantes`) sous le top 10.
- Personne de classé : « Personne n'est encore classé. Lance une partie ! » seul, centré, sans
  liste ni pied.
- Sous la liste : « 48 joueurs classés » (« 1 joueur classé »).
- Accords : « 1er », « 2e » ; l'Elo, sans unité ni espace des milliers ; « 1 réponse »,
  « 2 réponses » (0 et 1 au singulier, règle CLDR du français qu'Android applique, D72).
- Plus de 10 lignes reçues : les 10 premières, le reste ignoré (D72).
- TalkBack lit chaque ligne d'un seul bloc : « rang 27, Hibou_42, Elo 1180, toi » (« rang 1,
  Hypatie, Elo 2140 »).
- Lecture (D72) : mêmes règles, `elo` à la place de `score` dans les lignes, et `restantes`
  entier obligatoire.
- Mise en page (D117) : `ListeGroupee` « Top 10 » / « Elo » ; `LigneClassement` sans unité (le
  nombre seul) ; phrase provisoire dans l'`Encadre`, entre la liste et le pied.
- Chargement (D73) : à chaque ouverture, indicateur centré à la place du contenu jusqu'à la
  première réponse. Tirer vers le bas recharge : indicateur de tirage en haut, les lignes restent
  affichées jusqu'à la nouvelle réponse. Retour au premier plan (ou recréation de l'activité)
  avec le Classement ouvert : même rechargement que le tirage. Un seul chargement à la fois :
  une seule requête à l'ouverture, même si le premier plan arrive en même temps, et aucune
  autre tant qu'une est en cours. Rien n'est gardé en quittant l'écran.
- Erreurs : 401 → Bienvenue avec « Ta session a expiré, reconnecte-toi. » ; réseau → Pas de
  connexion ; 5xx, pas de réponse ou réponse illisible → Pas de connexion, texte serveur ; 429
  et tout autre cas → message en haut (§ 15), lignes déjà affichées gardées (au premier
  chargement, le message seul : tirer vers le bas réessaie). Le message s'efface au
  rechargement suivant (tirage ou premier plan).
- Retour système ou flèche : Accueil, qui se recharge.

---

## 12. Paramètres (D13, D14, D76, D77, D85, D95)

Remplace l'écran Compte, dont il reprend tout. Titre de la barre : « Paramètres ». Tout en listes,
sans aucun bouton (D95) : chaque action est une ligne, touchable sur toute sa largeur, lue comme
un bouton par TalkBack. Trois sections, chacune sous son en-tête, annoncé comme titre par
TalkBack, puis une dernière liste sans en-tête :

- **Compte** : le pseudo, sous le libellé « Pseudo » (celui de la session, D54), puis les lignes
  « Changer de pseudo » (§ 12.1), « Changer de mot de passe » (§ 12.2) et « Se déconnecter ».
- **Apparence** : le thème, une option à la fois : « Système » (par défaut), « Sombre »,
  « Clair ». Toucher une option l'applique tout de suite à toute l'app et l'enregistre sur le
  téléphone (D77, D78) ; l'écran est redessiné aux nouvelles couleurs, au même endroit. Le choix
  reste à la déconnexion et à la suppression du compte.
- **À propos** : la version, sous le libellé « Version » (« 1.0.12 » ; en preview
  « 1.0.0-preview »), puis la ligne « Envoyer un avis » (§ 12.3).
- Tout en bas, seule dans sa liste : la ligne « Supprimer mon compte » (action destructrice,
  libellé en rouge).

Après un changement de pseudo ou de mot de passe, ou un avis envoyé, message info en haut
(§ 12.1, § 12.2, § 12.3) ; il s'efface en quittant l'écran ou à l'action suivante (§ 1).

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
- Retour système ou flèche : Accueil, qui se recharge (nouveau pseudo compris).

### 12.1 Changer de pseudo (D79, D80, D83)

Gabarit Formulaire, titre de la barre « Changer de pseudo ».

- « Ton ancien pseudo deviendra libre : quelqu'un d'autre pourra le prendre. Tu ne pourras plus
  en changer pendant 24 heures. »
- **Nouveau pseudo**, prérempli avec le pseudo actuel, avec l'aide « 3 à 20 caractères : lettres,
  chiffres et _ » ; marqué nouveau pseudo pour le remplissage automatique.
- « Changer mon pseudo » : actif quand le pseudo respecte les règles (`regles.md` § 1.1) et
  diffère du pseudo actuel, casse comprise (« hibou_42 » → « Hibou_42 » est un changement).
  Vérification pendant la saisie comme à la création (§ 4, D55) : mêmes règles, mêmes textes.
- Erreurs sous le champ : `pseudo_invalide`, `pseudo_reserve`, `pseudo_pris`,
  `pseudo_trop_recent` (§ 15) ; modifier le champ efface l'erreur du serveur. 429 et tout autre
  refus : message en haut. 401 → Bienvenue avec « Ta session a expiré, reconnecte-toi. ». Réseau,
  5xx → Pas de connexion (la saisie est perdue).
- Succès → Paramètres, avec « Ton pseudo est maintenant Hibou_43. » (info) ; le pseudo affiché
  et celui de la session sont le nouveau.
- Retour système ou flèche : Paramètres, saisie oubliée.

### 12.2 Changer de mot de passe (D81, D83)

Gabarit Formulaire, titre de la barre « Changer de mot de passe ».

- « Tes autres appareils seront déconnectés. »
- **Mot de passe actuel** (afficher / masquer), marqué mot de passe pour le remplissage
  automatique.
- **Nouveau mot de passe**, avec l'aide « 8 caractères minimum » et afficher / masquer, marqué
  nouveau mot de passe ; vérifié pendant la saisie comme à la création (§ 4, D55).
- « Changer mon mot de passe » : actif quand le mot de passe actuel est rempli et que le nouveau
  respecte la règle exacte du serveur (D10).
- Erreurs : « Mot de passe incorrect. » sous le mot de passe actuel ; le texte de
  `mot_de_passe_invalide` sous le nouveau (§ 15). 429 et tout autre refus : message en haut.
  401 → Bienvenue avec « Ta session a expiré, reconnecte-toi. ». Réseau, 5xx → Pas de connexion
  (la saisie est perdue).
- Succès : le jeton neuf remplace l'ancien sur ce téléphone, qui reste connecté ; les autres
  appareils sont déconnectés. → Paramètres, avec « Mot de passe changé. Tes autres appareils sont
  déconnectés. » (info).
- Retour système ou flèche : Paramètres, saisie oubliée. Les deux mots de passe ne vivent que
  dans la mémoire de l'écran (`donnees.md` § 3).

### 12.3 Envoyer un avis (D85 – D94)

Gabarit Formulaire, titre de la barre « Envoyer un avis ».

- « Une idée, un bug, une question à revoir ? Ton avis est envoyé avec ton pseudo, la version de
  l'app et celle d'Android. »
- **Ton avis** : champ sur plusieurs lignes (D94), vide à l'ouverture, sans aide, avec le
  compteur « 0 / 1000 » à droite sous le champ. Le compteur donne la longueur du texte tel qu'il
  partira : après la règle de `regles.md` § 7.1 (retours à la ligne ramenés à `\n`, caractères de
  contrôle et blancs autour retirés), en caractères (points de code). La saisie n'est jamais
  bloquée ni coupée : un collage trop long reste entier, et le compteur passe en rouge au-delà de
  1000 (« 1042 / 1000 »). TalkBack lit « 12 sur 1000 caractères », et au-delà « 1042 sur 1000
  caractères, trop long », ce qui dit pourquoi « Envoyer » est inactif ; le compteur n'est pas
  annoncé à chaque frappe.
- « Envoyer » : actif quand le texte fait 1 à 1000 caractères après la règle. Pendant l'envoi,
  indicateur dans le bouton, champ désactivé (§ 1). L'app envoie le texte passé par la règle, la
  version de l'app (`1.0.12`, `1.0.0-preview`) et celle d'Android (`15`, `8.1.0`) (`regles.md`
  § 7.2).
- Succès → Paramètres, avec « Merci, ton avis a été envoyé. » (info).
- Erreurs (D93), le texte toujours gardé dans le champ :
  - sous le champ : `avis_invalide`, `avis_trop_recent` (§ 15) ; modifier le texte efface
    l'erreur du serveur ;
  - en haut (`MessageHaut`) : `avis_indisponible` (info), 429 et tout autre refus (alerte) ;
  - en haut aussi, au lieu de « Pas de connexion » (écart voulu au § 15, D93) : erreur réseau,
    texte réseau ; 5xx, pas de réponse ou réponse illisible, texte serveur (D53) ; tous deux en
    alerte ;
  - 401 → Bienvenue avec « Ta session a expiré, reconnecte-toi. ».
- Retour système ou flèche :
  - champ vide → Paramètres ;
  - champ non vide, envoi en cours compris → boîte « Abandonner ton avis ? », « Ce que tu as
    écrit sera perdu. » [Continuer] (action sûre, à gauche) → la boîte se ferme, texte intact ;
    [Abandonner] → Paramètres, texte oublié. Abandonné pendant l'envoi, l'avis peut quand même
    partir : un succès arrivé ensuite met « Merci, ton avis a été envoyé. » sur Paramètres si l'on
    y est encore ; un échec est ignoré, sauf 401 : la session est déjà effacée, donc Bienvenue.
- Le texte ne vit que dans la mémoire de l'écran (`donnees.md` § 3) : si Android tue l'app, il est
  perdu.

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
- « Mettre à jour » : même fonction que « Télécharger » de l'Accueil (D63) : Obtainium s'il a
  installé l'app, sinon l'APK de la dernière Release connue dans le navigateur, à défaut la page
  `https://github.com/Bouss45/Eruditus/releases/latest`.
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
| `pseudo_trop_recent` | « Tu as déjà changé de pseudo il y a moins de 24 heures. » | sous le pseudo (Changer de pseudo) |
| `mot_de_passe_invalide` | « 8 à 71 caractères (moins avec des accents ou des emojis). » | sous le mot de passe (le nouveau, au changement) ; l'app affiche le même texte quand sa propre vérification échoue (D10) |
| connexion échouée (400 sur `auth-with-password`) | « Pseudo ou mot de passe incorrect. » | Se connecter, au-dessus de l'action |
| `code_incorrect` | « Pseudo ou code incorrect. » | Mot de passe oublié, au-dessus de l'action |
| `mot_de_passe_incorrect` | « Mot de passe incorrect. » | sous le champ de la boîte de suppression ; sous le mot de passe actuel (Changer de mot de passe) |
| `avis_invalide` | « Ton avis doit faire 1 à 1000 caractères. » | sous le champ (Envoyer un avis) |
| `avis_trop_recent` | « Tu as déjà envoyé un avis il y a peu. Réessaie dans 10 minutes. » | sous le champ (Envoyer un avis) |
| `avis_indisponible` | « L'envoi d'avis ne marche pas pour le moment. Réessaie plus tard. » | en haut d'Envoyer un avis |
| 401 | « Ta session a expiré, reconnecte-toi. » | en haut de Bienvenue |
| 429 | « Trop de tentatives. Réessaie dans quelques minutes. » | en haut de l'écran en cours ; sous le champ dans la boîte de suppression |
| `partie_introuvable`, `partie_finie`, `numero_invalide`, `deja_repondu` | « Cette partie n'est plus valable. » | en haut de l'Accueil |
| `banque_insuffisante` | « Aucune partie disponible pour le moment. » | en haut de l'Accueil |
| `partie_introuvable` sur le détail d'une partie (D134) | « Cette partie n'est plus disponible. » (info) | en haut de l'Historique, au retour du détail |
| `requete_invalide` (curseur illisible) sur l'Historique | « Une erreur est survenue. Réessaie. » | en haut de l'Historique |
| `signalement_trop_frequent` (D118) | « Tu as signalé plusieurs questions. Réessaie plus tard. » | sous le champ de la boîte « Signaler cette question » |
| réseau, 5xx, 429 et tout autre cas dans la boîte « Signaler cette question » (D118) | les textes de D93 (réseau, serveur) ou « Une erreur est survenue. Réessaie. » (429, `signalement_invalide`, `partie_introuvable`, `numero_invalide`, `requete_invalide`) | sous le champ de la boîte, sans quitter la partie |
| erreur réseau, délai dépassé | Pas de connexion (§ 13) | |
| 5xx | Pas de connexion, texte « Le serveur ne répond pas » | |
| erreur réseau sur Envoyer un avis (D93) | « Vérifie ta connexion internet, puis réessaie. » | en haut d'Envoyer un avis, au lieu de Pas de connexion |
| 5xx, pas de réponse ou réponse illisible sur Envoyer un avis (D93) | « Le serveur ne répond pas. Réessaie dans un moment. » | en haut d'Envoyer un avis, au lieu de Pas de connexion |
| `requete_invalide`, `choix_invalide`, tout autre cas | « Une erreur est survenue. Réessaie. » | en haut de l'écran en cours ; sous le champ dans la boîte de suppression |

Précisions (D53, D55) :

- « Délai dépassé » : avant la connexion établie, texte réseau ; après, texte serveur. Une réponse
  qui n'est pas le JSON attendu compte comme un 5xx.
- Au démarrage, tout cas sans écran prévu donne Pas de connexion, texte serveur (§ 1.2).
- Un 401 n'est attendu que sur une route à jeton. Sur une route publique, qui n'en reçoit jamais
  (D30), il compte comme « tout autre cas ».
- Déconnexion : aucun message, Bienvenue quoi qu'il arrive (§ 12).
- Vérification des nouvelles versions (API GitHub, § 8) : aucune erreur affichée, jamais (D61).
- Un message en haut de genre alerte pour 429 et « Une erreur est survenue », info sinon
  (`DESIGN.md` § 8) : « Cette partie n'est plus valable. » et « Aucune partie disponible pour le
  moment. » sont des infos, comme « L'envoi d'avis ne marche pas pour le moment. ». Les textes
  réseau et serveur d'Envoyer un avis sont des alertes (D93).
- `banque_insuffisante` et `avis_indisponible` arrivent avec le statut 503 : un 5xx qui porte un
  code Eruditus est un refus, pas une panne (D68, D89). Pendant la partie, 429 et « Une erreur est survenue » (dont
  `choix_invalide`) s'affichent en haut de la Partie.

---

## 16. Historique (D131 – D136)

Données : `GET /elo/courbe` (D135) et `GET /historique` (D131), au premier affichage de l'écran,
rechargées à chaque ouverture (comme le Classement, D73) et en tirant vers le bas ; en mémoire
seulement (§ 1). Gabarit Liste (`DESIGN.md` § 8). On y arrive par le `Lien` « Historique » de
l'Accueil (§ 8), et le retour système revient à l'Accueil.

- Barre « Historique » avec retour.
- **Courbe** en tête (`CourbeElo`, `DESIGN.md` § 7), puis son résumé en `corps` `texte-secondaire` :
  « Elo 1043 après 12 parties. Plus haut 1061, plus bas 987. » (le nombre de parties des `points`,
  le plus haut et le plus bas de `depart` et des `points`) ; TalkBack lit ce résumé à la place du
  dessin, et la liste en dessous est le tableau équivalent. Provisoire (`provisoire`) : « Elo
  provisoire. » sous le résumé. Une seule partie (un point) : le dessin avec `depart` et le point,
  et le résumé. Aucun point : ni dessin ni résumé, la phrase « Pas encore d'Elo à tracer. ». Plus
  de 50 parties : les 50 dernières, et « sur les 50 dernières parties » dans le résumé.
- **Liste** : une `ListeGroupee` « Mes parties », une `LigneAction` par partie, avec à droite sa
  variation (`elo.variation`, « +14 », « −8 », « 0 » ; `null` : « Sans effet sur l'Elo » à la
  place) ; libellé : la date (« 22 nov. 2026 »), puis « 3 bonnes réponses sur 5 » (`bonnes`,
  `servies`) ; une partie abandonnée porte la mention « Abandonnée » (« 2 bonnes réponses sur 3
  questions · Abandonnée ») ; la date s'écrit dans la langue et le fuseau du téléphone. Toucher une
  ligne ouvre le Détail (§ 17). Les parties du serveur arrivent dans l'ordre : l'app ne trie rien.
- **Page suivante** : quand `suivant` n'est pas `null`, un `Lien` « Voir plus » sous la liste,
  indicateur dedans pendant la demande ; les nouvelles parties s'ajoutent, rien ne saute. Tirer
  vers le bas recharge la première page.
- **Aucune partie** : la courbe absente et la phrase « Tu n'as pas encore terminé de partie. », avec
  le texte « Elles apparaîtront ici. » ; pas de liste.
- **Premier chargement** : `ChargementEcran` (§ 1) ; ensuite, les valeurs restent pendant le
  rechargement.
- **Erreurs** : 401 → Bienvenue avec « Ta session a expiré, reconnecte-toi. » ; réseau, 5xx →
  Pas de connexion ; 429 et tout autre cas → message en haut (§ 15), valeurs déjà affichées
  gardées ; échec seul de la courbe : la liste s'affiche sans elle, et un message en haut.
- **Lecture** (D22) : `parties`, et pour chacune `id`, `date`, `etat`, `bonnes`, `servies`, `elo`
  sont obligatoires, comme les champs de la courbe ; un champ manquant ou de mauvais type rend la
  réponse illisible (D53). Un `etat` inconnu est traité comme une partie terminée ; tout champ
  inconnu est ignoré.
- **200 %** : rien n'est coupé ; la courbe garde sa hauteur, le résumé passe à la ligne, les
  lignes de la liste grandissent, la variation passe sous le libellé quand elle ne tient pas.
- TalkBack : chaque ligne est un bouton lu « 22 novembre 2026, 3 bonnes réponses sur 5, Elo, plus
  14 ».

## 17. Détail d'une partie (D132, D133)

Données : `GET /historique/{id}`, au toucher d'une ligne. Gabarit Liste, barre « Partie du 22 nov. »
avec retour ; retour système et retour de la barre : l'Historique.

- **En-tête** : « 3 bonnes réponses sur 5 » (« Abandonnée · 2 bonnes réponses sur 3 questions » si
  elle l'est), puis la variation de la partie en grand (`Statistiques` sans libellé, `chiffre`,
  `texte`, D115) et « Elo : 1261 » en `corps` `texte-secondaire` ; `elo.variation` `null` : « Sans
  effet sur l'Elo ». La variation de la partie est la somme de celles des questions (D133).
- **Les questions**, dans l'ordre, toutes dépliées, une `ListeGroupee` par question dont l'en-tête
  est « Question 2 » : la ligne d'enjeu du § 9, version correction (« Difficile · +12 », « Déjà vue :
  sans effet sur l'Elo », etc.), puis, avec `detail` `ok`, l'énoncé et les 4 choix en lecture seule
  (`ChoixReponse` sans appui : ✓ vert « Bonne réponse » sur `bonneReponse`, ✗ rouge « Ta réponse »
  sur `choixJoueur` s'il est faux), le verdict (`LigneResultat` : « Bonne réponse ! », « Mauvaise
  réponse. », « Temps écoulé. ») et, s'il y en a, l'explication et la source comme à la correction
  (D101).

  | `statut` | `detail` | ce qui s'affiche |
  |---|---|---|
  | `juste`, `fausse`, `sans_reponse`, `hors_delai` | `ok` | l'énoncé, les choix, ✓ et ✗, le verdict, l'explication et la source |
  | `non_repondue` | `ok` | l'énoncé et les choix seulement, neutres, et « Question non répondue » : ni ✓, ni explication, ni source |
  | tout | `modifiee` | le verdict seulement (« Bonne réponse », « Mauvaise réponse », « Temps écoulé », « Question non répondue ») et « Cette question a été modifiée depuis : son détail n'est plus affiché. » |
  | tout | `retiree` | le verdict seulement et « Cette question a été retirée du jeu : son détail n'est plus affiché. » |

- **Erreurs** : `partie_introuvable` → retour à l'Historique avec « Cette partie n'est plus
  disponible. » (§ 15) ; 401 → Bienvenue ; réseau, 5xx → Pas de connexion ; 429 et tout autre
  cas : message en haut, la barre de retour reste.
- **Lecture** (D22) : `partie`, `elo`, `questions` et, par question, `numero`, `statut`, `detail`
  obligatoires ; `enonce`, `choix`, `choixJoueur`, `bonneReponse`, `explication`, `source`, `niveau`
  et `variation` facultatifs, ignorés s'ils sont absents ou d'un autre type ; un `statut` ou un
  `detail` inconnu : seulement le numéro et « Détail indisponible. ».
- **200 %** : rien n'est coupé ; la zone défile, la barre reste.
- TalkBack : chaque question est un groupe : « Question 2. Difficile, Elo plus 12. Bonne réponse.
  [énoncé]. Choix A, … ».

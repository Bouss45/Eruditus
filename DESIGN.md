# DESIGN.md — Eruditus

> Fait foi sur l'apparence ; comportement et textes : `spec/ecrans.md`. Direction **B · Glacier**,
> export Claude Design dans `design/claude-design/` (§ 11). Un écran = un gabarit (§ 8) + des
> composants (§ 7) de `ui/designsystem/` : jamais de couleur ni de taille en dur dans un écran.
> Nouveau composant : `@Preview` sombre et clair, et sa ligne ici, dans le même changement.

## 1. Principes

- **Calme** : seuls bougent le chrono, la correction et le passage à la question suivante.
- **Monochrome + accent bleu**, réservé à l'action principale, au chrono qui tourne, aux liens et
  à « Toi ». Vert = bonne réponse, rouge = mauvaise réponse ou saisie refusée, jamais seuls
  (✓ ou ✗ + libellé, et une clarté différente).
- **Une surface mise en avant** par écran ; listes groupées, pas de cartes ; une seule ombre (boîte).
- **Action principale en bas**, une par écran. **200 %** : aucune hauteur fixe sur un texte, le
  contenu défile, rien n'est coupé.
- Pas de son, pas de couleurs dynamiques (Material You), pas de police téléchargée (D26).

## 2. Thème

Sombre par défaut (référence des maquettes), clair quand le téléphone est en clair
(`isSystemInDarkTheme()`) ; aucun réglage dans l'app. Material 3 : `ColorScheme` rempli par les
tokens du § 3 (colonne M3), les autres dans une extension (`EruditusColors`). Edge-to-edge.

## 3. Couleurs

| token | rôle | sombre | clair | M3 |
|---|---|---|---|---|
| `fond` | Fond des écrans | `#080A0C` | `#F3F6F8` | background, surface |
| `surface` | Choix, champs, listes, code | `#121518` | `#FFFFFF` | surfaceContainer |
| `surface-haute` | Boîte, message en haut | `#1B1F22` | `#FFFFFF` | surfaceContainerHigh |
| `contour` | Choix, bouton secondaire, message | `#44484C` | `#C0C5C9` | outline |
| `contour-champ` | Champs | `#646A6E` | `#8D9398` | |
| `separateur` | Lignes de liste, choix verrouillé, code | `#23272A` | `#DEE2E5` | outlineVariant |
| `texte` | Texte, énoncé, contour du choix en envoi | `#E7ECF0` | `#14191C` | onSurface |
| `texte-secondaire` | Aides, libellés, rangs, secondes arrêtées | `#A5ACB1` | `#585F64` | onSurfaceVariant |
| `neutre` | Chrono arrêté, points de séparation (jamais du texte) | `#7B8186` | `#81878C` | |
| `piste` | Piste du chrono, pastille de lettre | `#25292C` | `#DBDFE2` | |
| `accent` | Bouton principal, chrono qui tourne, liens, « Toi » | `#7DD0FA` | `#1364B0` | primary |
| `sur-accent` | Sur l'accent | `#080A0C` | `#FFFFFF` | onPrimary |
| `accent-teinte` | Ma ligne au classement, encadré | `#18262D` | `#E7F0F7` | primaryContainer |
| `vert` | Case, ✓ et verdict de la bonne réponse | `#62D286` | `#21763C` | |
| `sur-vert` | Sur le vert | `#061B0D` | `#FFFFFF` | |
| `pastille-vert` | Pastille de lettre sur le vert | noir 16 % | noir 12 % ¹ | |
| `rouge` | Contour et ✗ de la mauvaise réponse, erreur de saisie | `#F2716A` | `#BA2B28` | error |
| `sur-rouge` | Lettre sur la pastille rouge | `#361717` | `#FFFFFF` | onError |
| `rouge-conteneur` | Fond de la mauvaise réponse, bouton destructeur | `#361717` | `#FFEAE9` | errorContainer |
| `sur-rouge-conteneur` | Sur rouge-conteneur | `#FBDCDA` | `#75201C` | onErrorContainer |
| `desactive-fond` / `-texte` | Bouton ou champ désactivé | `#171A1C` / `#6E7276` | `#E3E7E9` / `#7E8489` | |
| `voile` | Derrière une boîte | noir 64 % | `#14191C` 45 % | scrim |
| `pression` | Appui | `texte` 10 % | `texte` 8 % | |
| `pression-accent` | Appui sur le bouton principal | `#080A0C` 12 % | blanc 14 % | |
| `ombre-boite` | Ombre de la boîte | noir 50 %, flou 32, 12 vers le bas | `#14191C` 16 %, idem | |

¹ L'export donne blanc 18 % (3,9:1, sous 4,5:1) : remplacé par noir 12 % (6,8:1).

| contraste (WCAG) | sombre | clair | seuil |
|---|---|---|---|
| `texte` sur `fond` / `surface` / `surface-haute` | 16,7 / 15,4 / 13,9 | 16,3 / 17,7 / 17,7 | 4,5 |
| `texte-secondaire` sur `fond` / `surface` / `piste` | 8,6 / 8,0 / 6,4 | 6,0 / 6,5 / 4,8 | 4,5 |
| `accent` sur `fond` / `accent-teinte` ; `sur-accent` sur `accent` | 11,6 / 9,1 ; 11,6 | 5,6 / 5,2 ; 6,0 | 4,5 |
| `vert` sur `fond` ; `sur-vert` sur `vert` ; lettre sur `pastille-vert` | 10,5 ; 9,5 ; 6,7 | 5,2 ; 5,6 ; 6,8 | 4,5 |
| `rouge` sur `fond` / `rouge-conteneur` ; `sur-rouge-conteneur` dessus | 6,9 / 5,7 ; 12,7 | 5,6 / 5,3 ; 9,2 | 4,5 |
| `texte` sur `piste` (lettre) ; `sur-rouge` sur `rouge` (lettre) | 12,3 ; 5,7 | 13,2 ; 6,1 | 4,5 |
| `texte` / `texte-secondaire` sur `accent-teinte` | 13,0 / 6,7 | 15,4 / 5,6 | 4,5 |
| `texte-secondaire` / `rouge` sur `surface-haute` (boîte) | 7,2 / 5,8 | 6,5 / 6,1 | 4,5 |
| `contour-champ` sur `surface` ; `accent` sur `piste` | 3,3 ; 8,6 | 3,1 ; 4,5 | 3 |
| `accent` / `rouge` sur `surface` (contour 2 du champ) | 10,7 / 6,4 | 6,0 / 6,1 | 3 |
| `contour` sur `surface` ² | 2,0 | 1,7 | — |
| `neutre` sur `piste` (chrono arrêté) ³ | 3,7 | 2,7 | — |

² Accepté : le choix est identifié par sa pastille et son texte, le bouton par son libellé.
³ Accepté : les secondes affichées donnent la même information, et la barre arrêtée ne compte plus.
Les tests JVM recalculent chaque valeur depuis les tokens (`ContrastesTest`) ; désactivé : exempté.

## 4. Typographie et polices

Tailles et interlignes en **sp** (échelle non linéaire d'Android 14 : à 200 %, 13 sp → 26,
28 sp → 44) ; espacement des lettres en em.

| token | police | sp | graisse | détail | usage |
|---|---|---|---|---|---|
| `enonce` | Newsreader | 28 / 36 | 400 | | Énoncé |
| `marque` | Newsreader | 52 / 52 | 500 | −0,02 em | « Eruditus » |
| `titre-1` | Inter | 26 / 32 | 600 | −0,015 em | Titre sans barre, pseudo de l'Accueil |
| `titre-barre` | Inter | 22 / 28 | 600 | −0,015 em | Titre de la barre |
| `chiffre` | Inter | 32 / 37 | 600 | −0,02 em, `tnum` | Score, rang, points gagnés |
| `code` | Inter | 30 / 36 | 600 | +0,12 em, `tnum`, `ss02`, `zero` | Code de récupération |
| `titre-boite` | Inter | 20 / 26 | 600 | −0,01 em | Titre de boîte |
| `choix` | Inter | 17 / 23 | 500 (saisie 400) | | Choix, pseudo de ligne, champ |
| `corps` | Inter | 16 / 24 | 400 (message, encadré 500) | | Textes |
| `verdict` | Inter | 16 / 22 | 700 (détail 400) | | Ligne de résultat |
| `bouton` | Inter | 16 / 21 | 600 | | Bouton 56, lien |
| `bouton-grand` | Inter | 18 / 23 | 700 | | « Jouer » |
| `libelle` | Inter | 15 / 20 | 600 (erreur 500) | `tnum` (secondes, rangs) | Barre de partie, libellé, rang, petit bouton |
| `lettre` | Inter | 15 / 20 | 700 | | A à D |
| `petit` | Inter | 13 / 18 | 400 à 700 | | Aide, en-tête, étiquette d'un choix, pied |
| `etiquette` | Inter | 12 / 12 | 700 | | « Toi » |

| police | graisses | fichiers | licence |
|---|---|---|---|
| Newsreader | 400, 500 | variable `wght` + `opsz` 6–72, `res/font/` | SIL OFL 1.1 |
| Inter | 400, 500, 600, 700 | variable `wght` + `opsz` 14–32, `res/font/` | SIL OFL 1.1 |

`opsz` suit la taille du texte. Le texte de l'OFL accompagne chaque police (dépôt et APK,
`assets/licences/`).

## 5. Espacements, rayons, tailles (dp)

| espacement | usage | rayon | usage | taille | usage |
|---|---|---|---|---|---|
| 4 | Décalage d'une erreur, d'un lien, du pied | 10 | Pastille de lettre | ≥ 48 | Toute cible tactile |
| 8 | Icône et texte ; haut d'un champ | 12 | Champ | 56 | Bouton, champ, barre du haut |
| 12 | Entre blocs, choix, boutons | 16 | Choix, message | 64 | « Jouer », choix (min.) |
| 16 | Marge interne des groupes | 20 | Liste groupée, code, encadré | 52 | Ligne de classement (min.) |
| 20 | Marge latérale des écrans | 28 | Boîte | 4 | Barre du chrono |
| 24 | Marge des boîtes ; haut sans barre | pilule | Boutons, « Toi » | 1 | Contour (2 au focus, en erreur) |
| 32 | Entre sections | | | | |

Écran : 20 sur les côtés ; contenu à 12 sous la barre, 24 sans barre ; action du bas à 16
au-dessus de la barre de navigation système.

## 6. Mouvement et vibrations

Hors correction et question suivante, aucun ressort (jamais `dampingRatio` < 1).

| token | valeur Compose | usage |
|---|---|---|
| `ressort.rebond` | `spring(0.45f, 600f)` | Retour de la bonne réponse à 1 après 1,03 |
| `ressort.non` | `spring(0.25f, 1200f)`, départ 390 dp/s | « Non » horizontal, 8 dp au plus |
| `ressort.arrivee` | `spring(0.72f, 380f)` | Question suivante depuis le bord droit (dépasse ≈ 3,8 %) |
| `duree.couleur` | `tween(120, LinearEasing)` | Couleur d'un choix, d'un bouton |
| `duree.remplissage` | `tween(280, EmphasizedDecelerate)` (0.05, 0.7, 0.1, 1) | Le vert part du centre de la lettre et couvre la case |
| `duree.trait` | `tween(220, CubicBezier(0.2, 0, 0, 1))` ; ✗ `tween(160, EmphasizedDecelerate)` | ✓ dessiné (PathMeasure) ; ✗ fondu et échelle 0,6 → 1 |
| `duree.revelation` | `tween(220, delayMillis = 320)` | Bonne réponse allumée après une erreur |
| `duree.resultat` | `fadeIn(tween(200, delayMillis = 160))` + 6 dp vers le haut | Ligne de résultat et bouton |
| `duree.sortie` | `tween(130, EmphasizedAccelerate)` (0.3, 0, 0.8, 0.15) | Question qui part : fondu, 24 dp à gauche |
| `duree.chrono` | `tween(300, EmphasizedDecelerate)` | Barre qui se remplit |
| `duree.ecran` | entrée `tween(200, EmphasizedDecelerate)`, sortie `tween(100)` | Pile : fondu et 30 dp |
| `duree.boite` | `tween(150)` | Fondu et échelle 0,95 → 1 ; sortie à l'envers, même durée |
| `duree.message` | `tween(200)` | `expandVertically` + `fadeIn` ; sortie `shrinkVertically` + `fadeOut` |

| moment | mouvement | vibration | animations désactivées |
|---|---|---|---|
| Attente | Chrono linéaire sur 20 s, piloté par l'horloge de la partie ; accent jusqu'au bout. | — | Paliers d'une seconde |
| Appui | `pression` bornée à la case, sans échelle ni onde qui déborde. | — | Identique |
| Envoi | Contour `texte` 2 + indicateur ; autres choix verrouillés ; chrono gris. | — | Indicateur fixe |
| Bonne réponse (`gagne`, `deja_reussie`) | Remplissage ; à +120 ms échelle 1,03 (`tween(80, FastOutSlowIn)`) puis `ressort.rebond` ; ✓ à +140 ms ; `duree.resultat`. | Confirm, à la correction | État final direct |
| Mauvaise réponse | Rouge, ✗, « Ta réponse », « non » (≈ 420 ms) ; bonne réponse en `duree.revelation`, ✓ à +440 ms. | Reject, au début du « non » | Rouge et vert ensemble |
| Temps écoulé | Bonne réponse en fondu 220 ms, ✓ à +140 ms ; chrono gris. | Reject | État final direct |
| Question suivante | `duree.sortie`, puis `ressort.arrivee` et `duree.chrono` ; compteur sans animation. | — | Remplacement direct |
| Ailleurs | `duree.ecran`, `duree.boite`, `duree.message` ; seuls les indicateurs bouclent. | — | Coupures nettes |

- **Animations désactivées** : `ValueAnimator.areAnimatorsEnabled()` faux (relu à chaque retour
  dans l'app) → chaque token devient `snap()` ; couleur, icône, libellé et vibration restent.
- **Seuls les changements d'état s'animent** : un élément qui apparaît directement dans un état
  (aperçu, catalogue, écran recréé) montre l'état final, sans rejouer le mouvement.
- **Vibrations** : `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.Confirm /
  Reject)` (Compose UI ≥ 1.8) ; aucune permission ; suit « Vibreur au toucher ». Avant Android 11,
  androidx.core joue `VIRTUAL_KEY` pour Confirm et `LONG_PRESS` pour Reject (D41).

## 7. Composants

Dimensions internes en dp. Icônes au trait : tracés de `Ecran.dc.html` (retour, fermer, ✓, ✗,
horloge, info, alerte, erreur, œil, œil barré, copier, réseau, serveur, mise à jour) en
`ImageVector`, couleur du texte.

| composant | anatomie | états |
|---|---|---|
| `BarreHaut` | ≥ 56 ; marges 4 à gauche, 20 à droite ; bouton icône 48. Retour : flèche 24 `texte` + `titre-barre`. Partie : fermer 22 `texte-secondaire`, « Question n/5 » et secondes en `libelle`. | Secondes `texte`, puis `texte-secondaire` à l'arrêt |
| `Chrono` | Sous la barre, marges 2 en haut, 20 sur les côtés ; barre 4, rayon 2, sur `piste` ; largeur = restant / 20 s (`restantMs`). Barre de progression pour TalkBack. | En cours `accent` ; arrêté `neutre` |
| `ChoixReponse` | Pleine largeur, ≥ 64, rayon 16, contour 1 ; marges 11 / 14 / 11 / 12, écart 14 ; pastille 36 (≥ 2,4 × la lettre), rayon 10, `lettre` ; texte `choix` sur plusieurs lignes, étiquette `petit` 700 dessous ; à droite ✓, ✗ ou indicateur, 24. TalkBack : « A, René Magritte » + état. | Neutre (`surface`, `contour`, pastille `piste`) · Pressé · Envoi (contour `texte` 2, indicateur) · Verrouillé (contour `separateur`, `texte-secondaire`) · Bonne, choisie ou révélée (`vert`, `sur-vert`, `pastille-vert`, « Bonne réponse », ✓) · Mauvaise (`rouge-conteneur`, contour `rouge`, `sur-rouge-conteneur`, pastille `rouge`, « Ta réponse » en `rouge`, ✗) |
| `LigneResultat` | Icône 20 + verdict (`verdict` 700) seul sur sa ligne ; détail (`verdict` 400, `texte-secondaire`) dessous, pleine largeur ; écart 2 ; zone annoncée. Puis `BoutonSecondaire`. | `gagne`, `deja_reussie` : ✓ `vert` · `fausse` : ✗ `rouge` · `sans_reponse`, `hors_delai` : horloge `texte` |
| `Champ` | Libellé `libelle` au-dessus, écart 6 ; champ ≥ 56, rayon 12, contour `contour-champ`, `surface`, saisie `choix` 400, marge 16 (56 avec l'œil) ; œil : bouton 48, icône 22, « Afficher / Masquer le mot de passe » ; aide `petit` `texte-secondaire`, écart 6. | Vide · Saisie (contour `accent` 2) · Erreur (contour `rouge` 2 ; icône 16 + `petit` 500 `rouge` à la place de l'aide) · Masqué / affiché · Désactivé (`separateur`, `desactive-*`, libellé `texte-secondaire`) |
| `BoutonPrincipal` | Pilule, contour 1, marges 8 × 24, écart 10 ; 56 en `bouton`, grand 64 en `bouton-grand` ; `accent` / `sur-accent`. | Normal · Pressé (`pression-accent`) · Chargement (indicateur 20 + libellé ; boutons et champs de l'écran désactivés) · Désactivé (`desactive-*`) |
| `BoutonSecondaire` | Idem sans fond, contour `contour`, `texte`. Petit 48 en `libelle` (« Classement », « Compte », « Copier » + icône 20) ; en rangée à parts égales, passe à la ligne. | Normal · Pressé (`pression`) · Chargement · Désactivé (contour `separateur`, `desactive-texte`) |
| `BoutonDestructeur` | `rouge-conteneur`, contour `rouge`, `sur-rouge-conteneur` : ce qui efface ou interrompt. | Comme le principal |
| `RangeeBoutons` | Deux boutons ou plus à parts égales, écart 12 ; l'un sous l'autre dès qu'un libellé ne tient plus dans sa part sans couper un mot. Petits boutons de l'Accueil, actions d'une `Boite`. | — |
| `Lien` | `bouton` en `accent`, cible ≥ 48, marges 0 × 4. | Normal · Pressé (`pression` dans une pilule) |
| `MessageHaut` | `surface-haute`, contour `contour`, rayon 16, marges 14 × 16, écart 12 ; icône 20 `texte-secondaire` ; `corps` 500 ; monochrome. Sous la barre (8 en haut, 20 sur les côtés) ou en haut du contenu sans barre ; **pousse le contenu, ne recouvre jamais rien**, même à 200 %. Zone annoncée. | Info (cercle i) · Alerte (triangle !) |
| `ErreurFormulaire` | Icône 18 + `libelle` 500 `rouge`, annoncée ; au-dessus du lien ou de l'action. | — |
| `CodeRecuperation` | Grille 2 × 2 à toutes les tailles, écarts 14 × 12, marges 22 × 16, rayon 20, `surface`, contour `separateur` ; groupes `code` centrés, sans tiret ; une seule description TalkBack pour la grille. | — |
| `Statistiques` | Libellé `petit` 600 `texte-secondaire` + valeur `chiffre`, écart 4 ; 40 entre paires (16 à la ligne). | — |
| `Marque` | « Eruditus » en `marque`, phrase en `corps` `texte-secondaire`, écart 14. | — |
| `ListeGroupee` | En-tête facultatif (`petit` 600 `texte-secondaire`, marges 16, écart 8) ; groupe rayon 20, `surface` ; séparateur 1 `separateur` entre lignes. | Normal · Teintée (`accent-teinte`) |
| `LigneClassement` | ≥ 52 ; rang en colonne ≥ 52, `libelle` `tnum` `texte-secondaire` ; pseudo `choix` 500 à la ligne plutôt que coupé ; nombre `choix` 600 `tnum` + unité `texte-secondaire` ; marges 12 × 16. | Normal · Moi (`accent-teinte`, rang `texte`, pseudo 700, « Toi » : `etiquette`, contour et texte `accent`, marges 4 × 8) |
| `SeparateurPoints` | Trois points `neutre` sur 28, décoratifs. | — |
| `Encadre` | `accent-teinte`, rayon 20, marge 16, `corps` 500. | — |
| `LigneInfo` | ≥ 56, marges 14 × 16 ; clé `corps` `texte-secondaire`, valeur `corps` 600. | — |
| `Boite` | `voile` ; 340 au plus, marge 24, rayon 28, `surface-haute`, `ombre-boite`, écart 16 ; titre `titre-boite` ; texte `corps` (`texte-secondaire` sous un titre) ; `Champ` facultatif ; actions côte à côte (marges 8 × 20, écart 12), l'une sous l'autre quand le texte grandit ; action sûre à gauche. | Confirmation · Avec champ (erreur sous le champ ; envoi : tout désactivé sauf l'indicateur) |
| `IconeEtat` | 48, trait 1,5, `texte-secondaire` : réseau, serveur, mise à jour. | — |
| `Indicateur` | Quart de cercle, trait 2,4, sur une piste du même ton à 25 % (choix) ou 30 % (bouton) ; 24 (choix), 20 (bouton). Un tour par seconde, linéaire. | Tourne · Fixe |
| `ChargementEcran` | `Indicateur` 40, trait 4, `texte-secondaire`, centré dans la zone de contenu, sous la barre du haut. Tirage (Classement) : indicateur de tirage Material 3, fond `surface-haute`, arc `texte-secondaire`. | Centré · Tirage |

## 8. Gabarits

À 200 %, seul le contenu défile : la barre du haut et la zone du bas restent visibles.

| gabarit | écrans | de haut en bas | règles |
|---|---|---|---|
| Formulaire | Créer un compte, Se connecter, Mot de passe oublié | `BarreHaut` retour ; texte facultatif ; `Champ`s (8 en haut) ; `ErreurFormulaire`, `Lien` ; espace ; `BoutonPrincipal` | Clavier ouvert : la zone du bas monte au-dessus, le contenu défile. |
| Information | Bienvenue, Code de récupération, Résultat, Pas de connexion, Mise à jour nécessaire | Sans barre ; `Marque` (à 150) ou `IconeEtat` (à 96) facultatifs ; `titre-1` ; texte ; contenu facultatif ; espace ; secondaire facultatif ; principal | En haut à gauche, jamais centré : se lit comme une page. |
| Accueil | Accueil | `titre-1` pseudo (à 24) ; `Statistiques` (8) ; espace ; deux petits `BoutonSecondaire` ; « Jouer » grand | Rien d'autre. |
| Partie | Partie | `BarreHaut` partie ; `Chrono` ; énoncé (8) ; espace ; 4 `ChoixReponse` ; `LigneResultat` juste sous le choix D ; bouton | Verdict et bouton : places réservées dès la question, rien ne saute. La place du verdict est celle de la plus longue ligne de résultat possible, mesurée sans être affichée. |
| Liste | Classement, Compte | `BarreHaut` retour ; `ListeGroupee`s ; pied `petit` centré | Tirage : `ChargementEcran`. |

**Partie à 200 %** : l'énoncé, les choix, puis le verdict et le détail juste sous le choix D
défilent ensemble ; seul le bouton « Question suivante » ou « Voir le résultat » reste fixé en
bas. Pendant le chrono, les 4 choix sont visibles sans faire défiler : si l'énoncé et les choix
ne tiennent pas, la zone qui défile est calée sur le choix D et c'est le haut de l'énoncé qui
sort de l'écran.

| placement (`spec/ecrans.md` § 15) | composant |
|---|---|
| Sous le champ | `Champ`, état erreur |
| Au-dessus de l'action | `ErreurFormulaire` |
| En haut de l'écran | `MessageHaut` : alerte pour 429 et « Une erreur est survenue », info sinon |
| Pas de connexion | Gabarit Information, `IconeEtat` réseau ou serveur (5xx) |

## 9. Navigation

- Pile et retours : `spec/ecrans.md` (§ 1.1 et chaque écran) ; aucune barre de navigation.
- Transitions `duree.ecran` ; retour prédictif activé (`enableOnBackInvokedCallback`) ; un
  retour intercepté ouvre une `Boite`.

## 10. Icône et démarrage

Piste 3 · **Le E chronométré** : la barre du milieu du E est le chrono, aux deux tiers. Icône
adaptative, calque de 108, tout dans la zone sûre (cercle de 66).

| calque | contenu (unités du calque) | fichier |
|---|---|---|
| Fond | Plein `#080A0C` | `design/icone/fond.svg` |
| Premier plan | Fût x 34 y 30, 10 × 48 ; barres de 40 × 9 en y 30 et y 69 ; rayons 2,5 ; `#E7ECF0`. Chrono y 49,5–58,5 : piste x 44–70 `#E7ECF0` 30 %, remplissage x 44–61 `#7DD0FA`, bout droit arrondi 2,5. | `design/icone/premier-plan.svg` |
| Monochrome | Même tracé, une couleur, piste à 30 % (icônes à thème, Android 13+). | `design/icone/monochrome.svg` |

- Même icône dans les deux thèmes (le lanceur ne suit pas l'app).
- **Démarrage** : écran de lancement d'Android (`core-splashscreen` jusqu'à Android 11), fond
  `fond`, E en `texte`, piste en `texte` 30 % et chrono en `accent` du thème (dessin à part : le E
  clair de l'icône disparaîtrait sur fond clair). Le E a la taille de la maquette (grille de 108
  sur 144) : Android affiche l'icône sur 288, le dessin a donc une grille de 216.

## 11. Export

`design/claude-design/` : le canevas Claude Design (`canvas.json`, une planche `.dc.html` par
cadre). Entre deux planches, `Ecran.dc.html` (composant partagé de toutes les maquettes)
l'emporte ; ce fichier tranche.

| planche | statut |
|---|---|
| `Tokens`, `Gabarits`, `Composants`, `Ecran`, `Mouvement` | Référence |
| `Ecrans-Acces`, `-Jeu`, `-Classement-Compte`, `-Systeme` | Référence : écrans et états, sombre, clair, 200 % |
| `Icone` | Piste 3 retenue ; pistes 1 et 2 écartées |
| `Variante-B` | Historique (direction retenue) |
| `Main` (A · Projecteur), `Variante-C` (C · Minuit) | Écartées ; leurs données (scores à 1 284, pseudos accentués) ne suivent pas D9 |
| `Prototype` | Démonstration ; ses vibrations (`navigator.vibrate`) et polices ne valent pas |

## 12. Dans le code

`android-app/app/src/main/java/com/eruditus/android/ui/designsystem/` :

| dossier | contenu |
|---|---|
| `theme/` | `EruditusTheme` et l'accès `Eruditus.couleurs`, `.typo`, `.espacements`, `.rayons`, `.tailles`, `.mouvement` ; `Couleurs.kt` (§ 3, noms en camelCase : `surfaceHaute`, `texteSecondaire`…), `Typographie.kt` (§ 4), `Dimensions.kt` (§ 5), `Mouvement.kt` et `Vibrations.kt` (§ 6), `Icones.kt`, `Pression.kt` (appui) |
| `components/` | Les composants du § 7, sous les mêmes noms ; les cotes propres à un seul composant y sont des constantes privées |
| `templates/` | `GabaritFormulaire`, `GabaritInformation`, `GabaritAccueil`, `GabaritPartie`, `GabaritListe` (§ 8) |

Aperçus et catalogue (D42) : `app/src/catalogue/`, compilé dans les seuls builds debug et
`preview`. Annotations `@ApercusComposant` (sombre, clair), `@ApercusTexte` (sombre, clair,
sombre à 200 %) et `@ApercusGabarit` (écran de 390 × 844 comme `Ecran.dc.html`, sombre, clair,
200 %) ; un aperçu par composant et par gabarit. Les builds debug et `preview` s'ouvrent sur le
catalogue : tous les états, sombre et clair, vibrations, ressorts et gabarits à essayer.

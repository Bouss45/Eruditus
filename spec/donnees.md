# Données sur le téléphone

> Partie de la spec (`SPEC.md` § 2). **Dn** renvoie aux Décisions de `SPEC.md` § 3. Tout ce qui
> compte (comptes, parties, réponses, scores) vit sur le serveur ; le téléphone ne garde que de
> quoi rester connecté, le thème choisi et, en release, de quoi annoncer les nouvelles versions.

---

## 1. Ce qui est stocké

| donnée | pourquoi | où |
|---|---|---|
| jeton | rester connecté (D12) | fichier de session chiffré |
| identifiant du joueur | savoir à qui appartient le jeton | même fichier |
| pseudo | l'afficher dès le démarrage | même fichier |
| dates de la dernière vérification réussie et du dernier échec, dernière Release vue (version, APK, page), version ignorée | annoncer une nouvelle version sans interroger GitHub plus d'une fois toutes les 6 h (1 h après un échec), sans revenir sur « Plus tard » (D61 – D64) | `mises-a-jour.json`, release seulement |
| thème : Système, Sombre ou Clair | l'appliquer dès le lancement, écran de démarrage compris (D77, D78) | `preferences.json` ; à partir d'Android 12, aussi le mode nuit de l'app gardé par Android |

- Un seul fichier, `session`, dans le stockage privé de l'app hors sauvegarde
  (`noBackupFilesDir`) : le JSON `{ version: 1, jeton, idJoueur, pseudo }`, chiffré (§ 2), écrit
  de façon atomique (D54). Un fichier illisible ou d'une version inconnue est effacé : il ne
  contient qu'une session, rien de précieux, et l'app revient à Bienvenue, sans message.
- Le pseudo vient de la création ou de la récupération (`joueur.pseudo`). Après une connexion,
  c'est le pseudo saisi, sans espaces autour, jusqu'au premier `/moi`, qui le remplace par la
  casse de création : des routes standard de PocketBase, l'app ne lit que `token` et `record.id`.
- `mises-a-jour.json` (D64), dans `noBackupFilesDir`, en clair : rien de secret ni rien sur le
  joueur. `{ version: 1, reussieLe, echecLe, derniereVersion, apk, page, versionIgnoree }` : les
  deux dates en millisecondes depuis 1970 (0 = jamais), les autres champs facultatifs. Écrit de façon atomique ; illisible ou d'une
  autre version, il est effacé, comme si rien n'avait été vérifié. Debug et preview n'en ont pas.
- `preferences.json` (D78), dans `noBackupFilesDir`, en clair : rien de secret ni rien sur le
  joueur. `{ version: 1, theme }`, `theme` valant `systeme`, `sombre` ou `clair` ; absent :
  Système. Écrit de façon atomique ; une valeur inconnue se lit Système ; illisible ou d'une autre
  version, il est effacé (Système). Lu avant le premier affichage. Dans les trois builds ; la
  preview a le sien. À partir d'Android 12, le même choix est confié à Android
  (`UiModeManager.setApplicationNightMode`), qui le garde pour l'app et l'applique à l'écran de
  démarrage ; il est redonné à chaque lancement d'après le fichier, qui fait foi (D77).
- **Le jeton ne part que là où il sert** (D30) : `auth-refresh` et les routes protégées. Jamais
  sur une route publique (`/info`, `/comptes`, `/comptes/recuperation`, `auth-with-password`) :
  depuis PocketBase 0.42.0, un jeton expiré y donne 401, et une reconnexion échouerait à cause
  d'un vieux jeton encore en mémoire. Le client déclare chaque route publique ou à jeton, et
  n'ajoute l'en-tête `Authorization` qu'aux routes à jeton, même quand une session existe (D52).

## 2. Chiffrement

- Le fichier de session est chiffré en AES-256-GCM avec une clé générée dans l'Android Keystore
  (non exportable, propre à l'appareil). Pas d'`androidx.security:security-crypto`, déprécié :
  Keystore directement.
- Clé `eruditus_session`, sans authentification de l'utilisateur (l'app s'ouvre sans demander
  le code du téléphone). Chaque écriture tire un IV de 12 octets, écrit en tête du fichier
  (D54).
- Clé perdue ou illisible (restauration, changement du verrouillage de l'écran, Keystore
  réinitialisé) : le fichier et la clé sont effacés et l'app revient à Bienvenue ; une nouvelle
  clé est créée à la prochaine session.

## 3. Ce qui n'est jamais stocké

- **Le mot de passe** : il ne vit que le temps de la saisie et de l'appel.
- **Le code de récupération** : il ne vit que dans la mémoire de l'écran qui l'affiche
  (`ecrans.md` § 5), sans état sauvegardé. Si Android tue l'app à ce moment, il est perdu.
- **Le texte d'un avis** (D85) : il ne vit que dans la mémoire de l'écran « Envoyer un avis »,
  sans état sauvegardé, gardé après un échec tant qu'on reste sur l'écran, oublié en le quittant.
  Si Android tue l'app, il est perdu.
- Questions, réponses, score, classement : aucun cache (D20).
- Ce qui est envoyé à GitHub (D61) : rien. Ni jeton, ni cookie, ni identifiant, ni version de
  l'app ; seul `User-Agent: Eruditus-Android`, exigé par GitHub.
- Ce qui part avec un avis (D87, D92), au serveur seulement : le texte, la version de l'app et
  celle d'Android. Ni modèle du téléphone, ni langue, ni identifiant de l'appareil ; l'app ne
  parle jamais à Discord, c'est le serveur qui transmet.
- Les logs : ni jeton, ni en-tête `Authorization`, ni corps de requête, ni mot de passe, ni code
  (D26). Aucune journalisation HTTP, debug et preview compris (D52).
- L'état sauvegardé d'Android (rotation, app tuée en arrière-plan) : rien, ni pile d'écrans ni
  saisie (D56), ni partie en cours (D65). Pseudo, mot de passe, code, question, correction,
  bilan et texte d'un avis vivent dans la mémoire de l'app seulement.

## 4. Sauvegarde Android et transfert

- `android:allowBackup="false"` et des règles d'extraction de données qui excluent tout
  (sauvegarde cloud et transfert d'appareil). Un nouveau téléphone se reconnecte avec pseudo et
  mot de passe.
- La variante `.preview` a ses propres données, séparées de l'app, mais parle au même serveur :
  ses comptes sont de vrais comptes (D59).

## 5. Effacement

La session locale (fichier et contenu en mémoire) est effacée :

- à la déconnexion, que l'appel au serveur ait réussi ou non (réseau coupé, 401, autre erreur,
  D13) ;
- après la suppression du compte (D14), jamais après un refus (mot de passe faux, 429, réseau) ;
- à tout 401 : jeton expiré, invalidé ou refusé (`ecrans.md` § 1.2) ;
- quand le fichier ou sa clé est illisible (§ 1, § 2).

`mises-a-jour.json` et `preferences.json` ne sont pas liés au compte : ils restent à la
déconnexion et à la suppression du compte, et ne sont effacés que s'ils sont illisibles (§ 1). Un
changement de pseudo ou de mot de passe réécrit la session (pseudo, ou jeton neuf, D79, D81) sans
rien effacer.

Les avis déjà envoyés ne sont pas sur le téléphone : ils restent sur Discord, même après la
suppression du compte (D88).

La désinstallation efface tout, mode nuit gardé par Android compris. Rien d'autre n'est à
effacer : le téléphone ne garde rien d'autre.

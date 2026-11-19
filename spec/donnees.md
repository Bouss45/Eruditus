# Données sur le téléphone

> Partie de la spec (`SPEC.md` § 2). **Dn** renvoie aux Décisions de `SPEC.md` § 3. Tout ce qui
> compte (comptes, parties, réponses, scores) vit sur le serveur ; le téléphone ne garde que de
> quoi rester connecté.

---

## 1. Ce qui est stocké

| donnée | pourquoi | où |
|---|---|---|
| jeton | rester connecté (D12) | fichier de session chiffré |
| identifiant du joueur | savoir à qui appartient le jeton | même fichier |
| pseudo | l'afficher dès le démarrage | même fichier |

- Un seul fichier JSON, `session.json`, dans le stockage privé de l'app, avec un champ
  `version: 1`. Un fichier illisible ou d'une version inconnue est effacé : il ne contient qu'une
  session, rien de précieux, et l'app revient à Bienvenue.
- Aucune préférence en v1.
- **Le jeton ne part que là où il sert** (D30) : `auth-refresh` et les routes protégées. Jamais
  sur une route publique (`/info`, `/comptes`, `/comptes/recuperation`, `auth-with-password`) :
  depuis PocketBase 0.42.0, un jeton expiré y donne 401, et une reconnexion échouerait à cause
  d'un vieux jeton encore en mémoire.

## 2. Chiffrement

- Le fichier de session est chiffré en AES-256-GCM avec une clé générée dans l'Android Keystore
  (non exportable, propre à l'appareil). Pas d'`androidx.security:security-crypto`, déprécié :
  Keystore directement.
- Clé perdue ou illisible (restauration, changement du verrouillage de l'écran, Keystore
  réinitialisé) : le fichier est effacé et l'app revient à Bienvenue.

## 3. Ce qui n'est jamais stocké

- **Le mot de passe** : il ne vit que le temps de la saisie et de l'appel.
- **Le code de récupération** : il ne vit que dans la mémoire de l'écran qui l'affiche
  (`ecrans.md` § 5), sans état sauvegardé. Si Android tue l'app à ce moment, il est perdu.
- Questions, réponses, score, classement : aucun cache (D20).
- Les logs : ni jeton, ni en-tête `Authorization`, ni corps de requête, ni mot de passe, ni code
  (D26). Pas de journalisation HTTP dans l'APK de release ; en debug, les en-têtes sensibles sont
  masqués.

## 4. Sauvegarde Android et transfert

- `android:allowBackup="false"` et des règles d'extraction de données qui excluent tout
  (sauvegarde cloud et transfert d'appareil). Un nouveau téléphone se reconnecte avec pseudo et
  mot de passe.
- La variante `.preview` a ses propres données, séparées de l'app.

## 5. Effacement

La session locale (fichier et contenu en mémoire) est effacée :

- à la déconnexion, que l'appel au serveur ait réussi ou non (D13) ;
- après la suppression du compte (D14) ;
- à tout 401 : jeton expiré, invalidé ou refusé (`ecrans.md` § 1.2) ;
- quand le fichier ou sa clé est illisible (§ 1, § 2).

La désinstallation efface tout. Rien d'autre n'est à effacer : le téléphone ne garde rien d'autre.

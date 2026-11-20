# Publication : CI et Releases

> Vérification des pull requests, publication de l'app, notes de version, secrets, ordre serveur
> puis app (D47 – D51). Fichiers : `.github/workflows/checks.yml`, `.github/workflows/release.yml`
> et le module `android-app/release-notes/`.

## Sommaire

1. [Vérification des PR (D47)](#1-vérification-des-pr-d47)
2. [Publication de l'app (D48)](#2-publication-de-lapp-d48)
3. [Ce qui touche l'app, notes de version (D49)](#3-ce-qui-touche-lapp-notes-de-version-d49)
4. [Secrets et sécurité (D50)](#4-secrets-et-sécurité-d50)
5. [Ordre serveur puis app, protection de `main` (D51)](#5-ordre-serveur-puis-app-protection-de-main-d51)
6. [Limites connues](#6-limites-connues)

---

## 1. Vérification des PR (D47)

`checks.yml`, sur chaque pull request vers `main` (ouverture et chaque push). Un nouveau push
annule le run en cours de la même PR.

| job | quand | quoi |
|---|---|---|
| Changements | toujours | liste les fichiers modifiés par la PR (`git diff` entre sa base et sa tête) et décide des deux jobs suivants |
| Tests et build | `android-app/`, `DESIGN.md` (lu par les tests de l'app) ou `.github/workflows/` change | tests JVM de l'app et de `release-notes`, puis `assembleDebug`, `assemblePreview` et `assembleRelease` (R8 compris, non signé) |
| Tests serveur | `server/`, `android-app/` (même build Gradle, même catalogue de versions) ou `.github/workflows/` change | `./gradlew :server-tests:test` (serveur § 10) |

- Un job sauté compte comme réussi : les deux checks peuvent être requis par la protection de
  `main` (D51) sans bloquer une PR qui ne touche que la spec ou le design.
- Aucun secret : l'app est construite avec l'adresse factice `https://serveur.invalid` (domaine
  réservé, jamais joignable), qui satisfait D43. Les APK d'une PR ne sont ni signés ni publiés ;
  debug et preview s'installent depuis un build local.
- Caches : dépendances Gradle (clé : catalogue de versions, fichiers `*.gradle.kts`, wrapper) ;
  zip PocketBase dans `~/.cache/eruditus/pocketbase/` (clé : empreinte de
  `server/pocketbase.version`). Le SHA-256 du zip reste vérifié à chaque lancement : un cache
  altéré fait échouer les tests, il ne les contourne pas.
- Accès réseau de la CI (pas de l'app, D26 inchangée) : dépôts Maven pour Gradle, et l'URL de
  `server/pocketbase.version` quand le cache est vide.
- Ubuntu, Java 21 (Temurin).

---

## 2. Publication de l'app (D48)

`release.yml`, à chaque push sur `main` dont au moins un fichier touche l'app (§ 3). Avec le merge
en squash, un push = une PR. Un seul run à la fois, jamais annulé.

1. Récupère tout l'historique et les tags (notes depuis la Release précédente).
2. Tests JVM de l'app et de `release-notes`, avec l'adresse factice ; un échec arrête tout.
3. Notes de version (§ 3) ; si le module échoue, en-tête seul, avec un avertissement.
4. Décode le keystore de `ANDROID_KEYSTORE_BASE64` dans le dossier temporaire du runner.
5. `assembleRelease -Pnumero=<n>` (D45), `n` = numéro du run de `release.yml` : versionCode
   1000 + n, versionName `1.0.<n>` ; adresse du serveur lue dans le secret `ERUDITUS_URL` (D43).
6. Vérifie la signature avec `apksigner`, sans afficher le certificat.
7. Crée la Release `v1.0.<n>`, titre « Eruditus v1.0.<n> », marquée Latest, sur le commit
   construit, avec deux fichiers identiques : `Eruditus-v1.0.<n>.apk` et `Eruditus.apk`.
8. Efface le keystore, même après un échec.

- Lien fixe vers la dernière version :
  `https://github.com/Bouss45/Eruditus/releases/latest/download/Eruditus.apk`.
- Obtainium voit deux APK identiques par Release : son filtre d'APK réglé sur `^Eruditus\.apk$`
  évite qu'il demande lequel installer.
- Relance d'un run (même numéro) : les deux APK de la Release existante sont remplacés, texte
  inchangé.
- Relance d'un ancien run alors qu'une Release plus récente contient déjà son commit : rien n'est
  publié, sinon cette version plus ancienne passerait Latest devant elle.
- Un numéro de run sans Release (échec) laisse un trou dans les versions : sans effet, le
  versionCode ne fait que monter.
- La CI ne déploie jamais `server/` (D6) : `deploy.sh` reste lancé par le mainteneur (§ 5).

---

## 3. Ce qui touche l'app, notes de version (D49)

Un fichier touche l'app s'il entre dans le build de l'APK publié : tout fichier sous
`android-app/`, sauf

- la documentation (`*.md`) ;
- le module `release-notes`, outil de la CI ;
- les tests : `src/test*/` et `src/androidTest*/` de chaque module ;
- ce qui ne sert qu'aux builds debug et preview (D42) : `src/catalogue/`, `src/debug/`,
  `src/preview/`.

Motifs, dans la syntaxe de `paths` de GitHub Actions (un motif `!` exclut ; pour chaque fichier,
le dernier motif qui correspond l'emporte ; `**` traverse les dossiers, `*` non) :

```
android-app/**
!android-app/**/*.md
!android-app/release-notes/**
!android-app/**/src/test*/**
!android-app/**/src/androidTest*/**
!android-app/**/src/catalogue/**
!android-app/**/src/debug/**
!android-app/**/src/preview/**
```

La liste est écrite deux fois : dans `paths` de `release.yml` (déclenchement) et dans le module
`release-notes` (notes). Un test JVM vérifie qu'elles sont identiques.

Texte de la Release :

- en-tête : `Eruditus 1.0.<n> (versionCode <1000 + n>), commit <7 premiers caractères>.` ;
- `## Changements` : le titre de chaque commit qui touche l'app depuis le tag de la Release
  précédente (Latest), du plus ancien au plus récent ; fusions (plus d'un parent) et commits sans
  fichier exclus ; au-delà de 50, une dernière ligne « … et N autres changements » ;
- aucun commit retenu : « Aucun changement dans l'app : nouvelle compilation. » ;
- pas de Release précédente (première Release) ou son tag absent de l'historique : « Liste des
  changements indisponible. » ;
- enfin, le lien de comparaison des deux tags.

Les titres de commits sont écrits pour l'utilisateur (`CLAUDE.md`) : ce sont les notes. Avec le
squash, un titre = le titre de la PR.

Module `android-app/release-notes/` (repris de MuscleNote) : JVM sans Android, fonctions pures
testées par `./gradlew :release-notes:test`, lancé par `./gradlew :release-notes:run`.

---

## 4. Secrets et sécurité (D50)

| secret | sert à |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | keystore de signature, en base64 |
| `ANDROID_KEYSTORE_PASSWORD` | mot de passe du keystore |
| `ANDROID_KEY_ALIAS` | alias de la clé |
| `ANDROID_KEY_PASSWORD` | mot de passe de la clé |
| `ERUDITUS_URL` | adresse du serveur (D43), écrite dans `BuildConfig` de l'APK publié |

- Secrets du dépôt, lus par `release.yml` seul, chacun passé en variable d'environnement à la
  seule étape qui en a besoin, jamais dans une ligne de commande.
- Jamais affichés : aucun `echo` d'un secret, pas de `set -x`, le build n'écrit jamais l'adresse
  (D43) ; GitHub masque en plus leur valeur dans les logs. Un secret absent fait échouer le run
  avec son nom, jamais sa valeur.
- Keystore : dans le dossier temporaire du runner seulement, effacé en fin de run ; jamais dans le
  dépôt (`*.jks` et `*.keystore` ignorés). La clé de signature est sauvegardée hors du dépôt par
  le mainteneur : la perdre, c'est ne plus pouvoir mettre à jour l'app installée (Android refuse
  une mise à jour signée par une autre clé).
- Jeton GitHub de la CI au minimum : `contents: read`, `contents: write` pour le seul job de
  Release. Ni secret ni droit d'écriture pour les PR.
- Actions tierces épinglées par empreinte de commit (version en commentaire) ;
  `persist-credentials: false` au checkout.

---

## 5. Ordre serveur puis app, protection de `main` (D51)

- Le merge d'une PR qui touche l'app publie aussitôt une Release, que les joueurs reçoivent par
  Obtainium. Le serveur doit donc être à jour avant (D22, serveur § 6.6).
- Une PR qui change `server/pb_migrations` ou `server/pb_hooks` : checks passés, le mainteneur
  lance `server/deploy.sh` depuis la branche de la PR, **avant** le merge. Une fois déployées, ses
  migrations ne changent plus (D39) : une correction = une nouvelle migration sur la même branche.
- Ce qui est déployé depuis la branche sert aussi la version de l'app déjà publiée : il reste
  compatible avec elle (D22).
- `main` est protégée : PR obligatoire, checks « Tests et build » et « Tests serveur » requis,
  merge en squash par le mainteneur. Plus de commit direct sur `main`.

---

## 6. Limites connues

- L'adresse du serveur n'est pas dans le dépôt (D36), mais elle se lit dans l'APK publié
  (`BuildConfig`), comme pour toute app en ligne. La sécurité repose sur le serveur (D8, D23,
  serveur § 6), pas sur le secret de l'adresse.

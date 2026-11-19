#!/usr/bin/env bash
# Déploie les migrations et les hooks d'Eruditus sur le VPS (spec/serveur.md § 9, D6, D36 – D39).
# Lancé par le mainteneur seul, depuis son PC :
#   server/deploy.sh           tests, vérifications, sauvegarde à froid, copie, redémarrage
#   server/deploy.sh --essai   vérifications et liste de ce qui serait copié, sans rien changer
set -euo pipefail
shopt -s inherit_errexit

# shellcheck source=commun.sh
source "$(dirname -- "${BASH_SOURCE[0]}")/commun.sh"

usage() {
  sed -n '3,5p' "${BASH_SOURCE[0]}" | sed 's/^# //'
}

ESSAI=false
case $# in
  0) ;;
  1)
    case $1 in
      --essai) ESSAI=true ;;
      -h | --aide | --help) usage; exit 0 ;;
      *) usage >&2; exit 2 ;;
    esac
    ;;
  *) usage >&2; exit 2 ;;
esac

# Où en est le déploiement, pour savoir quoi faire en cas d'échec :
# avant (rien n'a changé), arrete (conteneur arrêté, rien copié), copie (archive faite, copie en
# cours), demarre (copie faite, conteneur redémarré), fini.
PHASE=avant
HORODATAGE=$(date +%Y%m%d-%H%M%S)
ARCHIVE=eruditus-$HORODATAGE.tar.gz
DONNEES_HOTE=""

fin() {
  local code=$?
  trap - EXIT
  if ((code != 0)); then
    case $PHASE in
      arrete) redemarrer_tel_quel ;;
      copie | demarre) afficher_retour_arriere ;;
    esac
  fi
  fermer_ssh
  exit "$code"
}
trap fin EXIT
trap 'exit 130' INT TERM HUP

redemarrer_tel_quel() {
  printf "\nRien n'a été copié : redémarrage du conteneur tel quel.\n" >&2
  if distant sudo -n docker start "$CONTENEUR" >/dev/null; then
    printf "Conteneur %s redémarré, rien n'a changé.\n" "$CONTENEUR" >&2
  else
    printf "Le redémarrage a échoué. Sur le VPS : sudo docker start %s\n" "$CONTENEUR" >&2
  fi
}

# Commandes exactes du retour arrière ; jamais lancées automatiquement (D39).
afficher_retour_arriere() {
  local archive=$SAUVEGARDES/$ARCHIVE
  if [[ $PHASE == demarre ]]; then
    printf '\n== Journal du conteneur (40 dernières lignes)\n' >&2
    distant sudo -n docker logs --tail 40 "$CONTENEUR" >&2 || true
  fi
  {
    printf "\nÉchec du déploiement. Rien n'est annulé automatiquement.\n"
    printf "Pour revenir à l'état d'avant (archive %s) :\n\n" "$archive"
    printf '  ssh %s\n' "$HOTE"
    printf '  sudo docker stop %s\n' "$CONTENEUR"
    if [[ $PHASE == demarre ]]; then
      printf '  sudo mv %s %s\n' "$DONNEES_HOTE" "$DONNEES_HOTE.echec-$HORODATAGE"
      printf '  sudo tar --numeric-owner -xzpf %s -C %s %s\n' \
        "$archive" "$(dirname -- "$DONNEES_HOTE")" "$(basename -- "$DONNEES_HOTE")"
    fi
    printf '  sudo rm -rf %s/pb_migrations %s/pb_hooks\n' "$DOSSIER_VPS" "$DOSSIER_VPS"
    printf '  sudo tar --numeric-owner -xzpf %s -C %s pb_migrations pb_hooks\n' "$archive" "$DOSSIER_VPS"
    printf '  sudo docker start %s\n' "$CONTENEUR"
    printf '  exit\n'
    printf '  curl -fsS %s/api/health\n' "$URL"
    if [[ $PHASE == demarre ]]; then
      printf "\nLa base en échec reste dans %s.echec-%s pour comprendre ; à supprimer ensuite.\n" \
        "$DONNEES_HOTE" "$HORODATAGE"
    fi
  } >&2
}

# --- 1. Réglages et outils locaux -------------------------------------------------------------

charger_reglages
for outil in ssh rsync curl git; do
  command -v "$outil" >/dev/null || erreur "$outil manque sur ce PC."
done
VERSION_EPINGLEE=$(sed -n 's/^version=//p' "$DOSSIER_SERVEUR/pocketbase.version")
[[ -n $VERSION_EPINGLEE ]] || erreur "version introuvable dans server/pocketbase.version."

# --- 2. Ce qui est déployé est un commit ------------------------------------------------------

COMMIT=$(git -C "$DEPOT" rev-parse --short HEAD)
BRANCHE=$(git -C "$DEPOT" rev-parse --abbrev-ref HEAD)
MODE=""
if $ESSAI; then MODE=" : essai, rien ne sera changé"; fi
etape "Déploiement de $COMMIT ($BRANCHE) vers $HOTE, conteneur $CONTENEUR$MODE"
SALE=$(git -C "$DEPOT" status --porcelain -- server/pb_migrations server/pb_hooks)
if [[ -n $SALE ]]; then
  if $ESSAI; then
    avertir "changements non commités dans pb_migrations ou pb_hooks ; le déploiement réel refusera :"
    printf '%s\n' "$SALE" >&2
  else
    printf '%s\n' "$SALE" >&2
    erreur "changements non commités dans server/pb_migrations ou server/pb_hooks : commite-les d'abord."
  fi
fi

# --- 3. Tests serveur -------------------------------------------------------------------------

if $ESSAI; then
  etape "Tests serveur : sautés (essai)"
else
  etape "Tests serveur"
  (cd "$DEPOT/android-app" && ./gradlew --console=plain :server-tests:test) ||
    erreur "tests serveur en échec : rien n'est copié."
fi

# --- 4. Vérifications sur le VPS, sans rien changer -------------------------------------------

etape "Vérifications sur $HOTE"
ouvrir_ssh
lire_service
[[ $SERVICE_ETAT == running ]] || erreur "le conteneur $CONTENEUR ne tourne pas (état : ${SERVICE_ETAT:-inconnu})."

VERSION_VPS=$(distant sudo -n docker exec "$CONTENEUR" "$SERVICE_BINAIRE" --version)
[[ $VERSION_VPS == "pocketbase version $VERSION_EPINGLEE" ]] ||
  erreur "le conteneur tourne « $VERSION_VPS », la version épinglée est $VERSION_EPINGLEE (D3)."
echo "PocketBase $VERSION_EPINGLEE : conforme."

MONTAGE=$(montage_de "$SERVICE_DONNEES") || erreur "$SERVICE_DONNEES (pb_data) n'est pas monté dans le conteneur."
DONNEES_HOTE=${MONTAGE%%$'\t'*}

verifier_montage() {
  local destination=$1 attendu=$2 montage source ecriture
  montage=$(montage_de "$destination") ||
    erreur "$destination n'est pas monté dans le conteneur : à ajouter à la configuration du conteneur ($attendu, en lecture seule ; spec/serveur.md § 1)."
  source=${montage%%$'\t'*}
  ecriture=${montage#*$'\t'}
  [[ ${source%/} == "$attendu" ]] || erreur "$destination est monté depuis $source, $attendu attendu (DOSSIER_VPS)."
  [[ $ecriture == false ]] || erreur "$destination doit être monté en lecture seule (:ro) : à corriger dans la configuration du conteneur (D37)."
}
verifier_montage "$SERVICE_MIGRATIONS" "$DOSSIER_VPS/pb_migrations"
verifier_montage "$SERVICE_HOOKS" "$DOSSIER_VPS/pb_hooks"
echo "Montages : pb_data depuis $DONNEES_HOTE ; pb_migrations et pb_hooks depuis $DOSSIER_VPS, en lecture seule."

[[ $SERVICE_DEV == false ]] ||
  erreur "la commande du conteneur n'a pas --dev=false : le mode dev écrit chaque requête SQL dans les logs. À ajouter à la commande du conteneur (D37)."
[[ $SERVICE_AUTOMIGRATE == false ]] ||
  erreur "la commande du conteneur n'a pas --automigrate=false : à ajouter à la commande du conteneur (D37)."
echo "Options : --dev=false et --automigrate=false."

SORTIE_ETAT=$(distant bash -s -- "$DONNEES_HOTE" "$DOSSIER_VPS" "$SAUVEGARDES" <<'SCRIPT'
set -u
donnees=$1 dossier=$2 sauvegardes=$3
if command -v rsync >/dev/null 2>&1; then echo "rsync=oui"; else echo "rsync=non"; fi
for d in pb_migrations pb_hooks; do
  if [ -d "$dossier/$d" ] && [ -w "$dossier/$d" ]; then echo "$d=oui"; else echo "$d=non"; fi
done
echo "taille=$(sudo -n du -sbc "$donnees" "$dossier/pb_migrations" "$dossier/pb_hooks" | tail -n 1 | cut -f1)"
parent=$sauvegardes
while [ ! -d "$parent" ]; do parent=$(dirname "$parent"); done
echo "libre=$(df -B1 --output=avail "$parent" | tail -n 1 | tr -d ' ')"
if sudo -n test -d "$sauvegardes"; then
  echo "archives=$(sudo -n find "$sauvegardes" -maxdepth 1 -type f -name 'eruditus-*.tar.gz' | wc -l)"
else
  echo "archives=absent"
fi
SCRIPT
) || erreur "vérifications sur $HOTE interrompues."
declare -A ETAT=()
while IFS='=' read -r cle valeur; do
  if [[ -n $cle ]]; then ETAT[$cle]=$valeur; fi
done <<<"$SORTIE_ETAT"
[[ ${ETAT[rsync]:-} == oui ]] || erreur "rsync manque sur le VPS : installe-le."
for d in pb_migrations pb_hooks; do
  [[ ${ETAT[$d]:-} == oui ]] || erreur "$DOSSIER_VPS/$d n'existe pas ou n'est pas accessible en écriture par l'utilisateur SSH."
done
[[ ${ETAT[taille]:-} =~ ^[0-9]+$ && ${ETAT[libre]:-} =~ ^[0-9]+$ ]] ||
  erreur "impossible de mesurer pb_data ou la place libre sur le VPS."
((ETAT[libre] > ETAT[taille])) ||
  erreur "place insuffisante pour l'archive : $(en_mo "${ETAT[libre]}") libres, $(en_mo "${ETAT[taille]}") à archiver."
if [[ ${ETAT[archives]:-absent} == absent ]]; then
  ARCHIVES_TEXTE="dossier à créer"
else
  ARCHIVES_TEXTE="${ETAT[archives]} archive(s) déjà là"
fi
echo "Sauvegarde : $(en_mo "${ETAT[taille]}") à archiver, $(en_mo "${ETAT[libre]}") libres ($SAUVEGARDES : $ARCHIVES_TEXTE)."

# --- 5. Ce qui serait copié --------------------------------------------------------------------

# shellcheck disable=SC2054 # la virgule fait partie de l'option de rsync
RSYNC_COMMUN=(-rcp --chmod=D755,F644 --itemize-changes)

rsync_migrations() {
  rsync "${RSYNC_COMMUN[@]}" -e "$(ssh_pour_rsync)" "$@" \
    --include='*_eruditus_*.js' --exclude='*' \
    "$DOSSIER_SERVEUR/pb_migrations/" "$HOTE:$DOSSIER_VPS/pb_migrations/"
}

# Seul pb_hooks/eruditus/ est synchronisé avec suppression ; les fichiers exclus sont protégés.
rsync_hooks() {
  rsync "${RSYNC_COMMUN[@]}" -e "$(ssh_pour_rsync)" --delete "$@" \
    --include='/eruditus.pb.js' --include='/eruditus/' --include='/eruditus/**' --exclude='*' \
    "$DOSSIER_SERVEUR/pb_hooks/" "$HOTE:$DOSSIER_VPS/pb_hooks/"
}

# Lignes de rsync --itemize-changes, en clair : + nouveau, ~ modifié, - supprimé.
afficher_copie() {
  local dossier=$1 sortie=$2 ligne code chemin
  while IFS= read -r ligne; do
    [[ -n $ligne ]] || continue
    code=${ligne%% *}
    chemin=${ligne#* }
    chemin=${chemin#"${chemin%%[! ]*}"}
    case $code in
      \*deleting) printf '  - %s/%s\n' "$dossier" "$chemin" ;;
      ?f+++++++++ | cd+++++++++) printf '  + %s/%s\n' "$dossier" "$chemin" ;;
      *) printf '  ~ %s/%s  (%s)\n' "$dossier" "$chemin" "$code" ;;
    esac
  done <<<"$sortie"
}

etape "Ce qui serait copié vers $HOTE:$DOSSIER_VPS"
COPIE_MIGRATIONS=$(rsync_migrations --dry-run)
COPIE_HOOKS=$(rsync_hooks --dry-run)
afficher_copie pb_migrations "$COPIE_MIGRATIONS"
afficher_copie pb_hooks "$COPIE_HOOKS"

# rsync note « <f… » un fichier envoyé au VPS (« >f… » pour une copie locale) ; « f+++++++++ »
# est un fichier nouveau, tout autre « f… » un fichier qui change.
MODIFIEES=$(grep -E '^[<>]f' <<<"$COPIE_MIGRATIONS" | grep -vE '^[<>]f[+]{9}' || true)
if [[ -n $MODIFIEES ]]; then
  afficher_copie pb_migrations "$MODIFIEES" >&2
  erreur "une migration déjà déployée a changé : une migration déployée n'est jamais modifiée, il en faut une nouvelle (D22)."
fi

if [[ -z ${COPIE_MIGRATIONS//[[:space:]]/} && -z ${COPIE_HOOKS//[[:space:]]/} ]]; then
  echo "Rien à copier : le VPS a déjà ces fichiers. Pas de redémarrage."
  exit 0
fi
NOMBRE=$(printf '%s\n%s\n' "$COPIE_MIGRATIONS" "$COPIE_HOOKS" | grep -cE '^([<>]f|\*deleting)' || true)

if $ESSAI; then
  etape "Essai terminé, rien n'a changé"
  echo "Le déploiement réel lancerait les tests serveur, arrêterait $CONTENEUR, créerait"
  echo "$SAUVEGARDES/$ARCHIVE, copierait ce qui est listé ci-dessus et redémarrerait."
  exit 0
fi

# --- 6. Arrêt du conteneur ---------------------------------------------------------------------

etape "Arrêt de $CONTENEUR"
PHASE=arrete
distant sudo -n docker stop -t 30 "$CONTENEUR" >/dev/null
echo "Arrêté."

# --- 7. Sauvegarde à froid (D38) ---------------------------------------------------------------

etape "Sauvegarde à froid : $SAUVEGARDES/$ARCHIVE"
TAILLE_ARCHIVE=$(distant bash -s -- "$DONNEES_HOTE" "$DOSSIER_VPS" "$SAUVEGARDES" "$ARCHIVE" <<'SCRIPT'
set -euo pipefail
donnees=$1 dossier=$2 sauvegardes=$3 archive=$4
sudo -n install -d -m 700 -o root -g root "$sauvegardes"
sudo -n tar --numeric-owner -czf "$sauvegardes/$archive.partiel" \
  -C "$(dirname "$donnees")" "$(basename "$donnees")" \
  -C "$dossier" pb_migrations pb_hooks
sudo -n chmod 600 "$sauvegardes/$archive.partiel"
sudo -n mv "$sauvegardes/$archive.partiel" "$sauvegardes/$archive"
sudo -n du -h "$sauvegardes/$archive" | cut -f1
SCRIPT
)
echo "Archive créée ($TAILLE_ARCHIVE)."

# --- 8. Copie ----------------------------------------------------------------------------------

etape "Copie des fichiers Eruditus"
PHASE=copie
rsync_migrations >/dev/null
rsync_hooks >/dev/null
echo "$NOMBRE fichier(s) copié(s) ou supprimé(s)."

# --- 9. Démarrage : les migrations s'appliquent ------------------------------------------------

etape "Démarrage de $CONTENEUR"
PHASE=demarre
distant sudo -n docker start "$CONTENEUR" >/dev/null
echo "Démarré."

# --- 10. Vérification par l'URL publique -------------------------------------------------------

verifier_en_ligne() {
  local limite=$((SECONDS + 60)) info etat
  until curl -fs --max-time 5 -o /dev/null "$URL/api/health"; do
    if ((SECONDS >= limite)); then
      printf '%s/api/health ne répond pas après 60 s.\n' "$URL" >&2
      return 1
    fi
    sleep 2
  done
  echo "$URL/api/health : en ligne."
  info=$(curl -fsS --max-time 5 "$URL/api/eruditus/v1/info") || return 1
  if [[ $info != *'"versionMinimale"'* ]]; then
    printf '/api/eruditus/v1/info : réponse inattendue : %s\n' "$info" >&2
    return 1
  fi
  echo "/api/eruditus/v1/info : $info"
  etat=$(distant sudo -n docker inspect --format '{{.State.Status}}' "$CONTENEUR")
  if [[ $etat != running ]]; then
    printf 'Le conteneur est « %s » au lieu de « running ».\n' "$etat" >&2
    return 1
  fi
}

etape "Vérification"
verifier_en_ligne || erreur "le serveur ne répond pas comme prévu après le déploiement."
PHASE=fini

# --- 11. Les 10 archives les plus récentes sont gardées ----------------------------------------

etape "Archives"
distant bash -s -- "$SAUVEGARDES" <<'SCRIPT'
set -euo pipefail
sauvegardes=$1
sudo -n find "$sauvegardes" -maxdepth 1 -type f -name 'eruditus-*.tar.gz.partiel' -delete
sudo -n find "$sauvegardes" -maxdepth 1 -type f -name 'eruditus-*.tar.gz' -printf '%f\n' |
  sort -r | tail -n +11 | while IFS= read -r nom; do
    sudo -n rm -f -- "$sauvegardes/$nom"
    echo "Supprimée : $nom"
  done
echo "Gardées : $(sudo -n find "$sauvegardes" -maxdepth 1 -type f -name 'eruditus-*.tar.gz' | wc -l)."
SCRIPT

etape "Déploiement de $COMMIT terminé"
echo "Archive d'avant ce déploiement : $SAUVEGARDES/$ARCHIVE"

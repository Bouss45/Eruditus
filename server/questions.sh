#!/usr/bin/env bash
# Importe la banque de questions dans le PocketBase du VPS (spec/serveur.md § 8, D7, D40).
# Lancé par le mainteneur seul, depuis son PC, une fois le serveur déployé :
#   server/questions.sh <fichier.json>     par exemple server/questions/banque.json
set -euo pipefail
shopt -s inherit_errexit

# shellcheck source=commun.sh
source "$(dirname -- "${BASH_SOURCE[0]}")/commun.sh"

usage() {
  sed -n '3,4p' "${BASH_SOURCE[0]}" | sed 's/^# //'
}

case ${1:-} in
  -h | --aide | --help) usage; exit 0 ;;
esac
[[ $# -eq 1 ]] || { usage >&2; exit 2; }
FICHIER=$1
[[ -f $FICHIER && -r $FICHIER ]] || erreur "fichier illisible : $FICHIER"
[[ -s $FICHIER ]] || erreur "fichier vide : $FICHIER"
(($(stat -c %s -- "$FICHIER") <= 5 * 1024 * 1024)) || erreur "fichier de plus de 5 Mo : $FICHIER"

charger_reglages
command -v ssh >/dev/null || erreur "ssh manque sur ce PC."

trap fermer_ssh EXIT
trap 'exit 130' INT TERM HUP
ouvrir_ssh
lire_service
# La commande d'import n'applique pas les migrations : elle travaille sur la base du service en
# marche, déjà migrée (§ 8).
[[ $SERVICE_ETAT == running ]] ||
  erreur "le conteneur $CONTENEUR ne tourne pas (état : ${SERVICE_ETAT:-inconnu}) : l'import se fait service en marche."

# Mêmes dossiers et même clé de chiffrement que le service ; ni mode dev (il écrirait chaque
# requête SQL, bonnes réponses comprises), ni surveillance des hooks.
OPTIONS=(
  "--dir=$SERVICE_DONNEES"
  "--hooksDir=$SERVICE_HOOKS"
  "--migrationsDir=$SERVICE_MIGRATIONS"
  --dev=false
  --hooksWatch=false
  --automigrate=false
)
if [[ -n $SERVICE_CHIFFREMENT ]]; then
  OPTIONS+=("--encryptionEnv=$SERVICE_CHIFFREMENT")
fi

# Script lancé par le sh du conteneur (son utilisateur) : efface les copies d'un import
# interrompu, écrit l'entrée standard dans un fichier temporaire (droits 600), lance l'import,
# puis efface le fichier, même en cas d'échec. Arguments : le binaire, puis les options.
# shellcheck disable=SC2016 # les $ sont développés par le sh du conteneur, pas ici
SCRIPT_CONTENEUR='rm -f /tmp/eruditus-questions.*; f=$(mktemp /tmp/eruditus-questions.XXXXXX) || exit 1; trap '\''rm -f "$f"'\'' EXIT; trap '\''exit 1'\'' HUP INT TERM; bin=$1; shift; cat > "$f" && "$bin" eruditus-questions "$f" "$@"'

etape "Import de $FICHIER dans $CONTENEUR sur $HOTE"
if distant sudo -n docker exec -i "$CONTENEUR" sh -c "$SCRIPT_CONTENEUR" sh "$SERVICE_BINAIRE" "${OPTIONS[@]}" <"$FICHIER"; then
  echo "Import terminé."
else
  erreur "import refusé ou interrompu (messages ci-dessus) ; la commande valide tout le fichier avant d'écrire, rien n'est importé à moitié."
fi

# shellcheck shell=bash
# Code commun à deploy.sh et questions.sh (spec/serveur.md § 8, § 9) : réglages de deploy.env,
# connexion SSH au VPS et lecture de la commande du service PocketBase. Chargé par « source »,
# jamais lancé seul.
# shellcheck disable=SC2034 # DEPOT et SERVICE_* sont lues par les scripts qui chargent ce fichier

DOSSIER_SERVEUR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
DEPOT=$(dirname -- "$DOSSIER_SERVEUR")

erreur() {
  printf '\nErreur : %s\n' "$*" >&2
  exit 1
}

avertir() {
  printf '\nAttention : %s\n' "$*" >&2
}

etape() {
  printf '\n== %s\n' "$*"
}

en_mo() {
  awk -v octets="$1" 'BEGIN { printf "%.1f Mo", octets / 1048576 }'
}

# Lit server/deploy.env et vérifie chaque valeur. Aucun identifiant : l'alias SSH renvoie à la
# configuration SSH du mainteneur.
charger_reglages() {
  local fichier=$DOSSIER_SERVEUR/deploy.env
  [[ -f $fichier ]] || erreur "$fichier introuvable : copie server/deploy.env.example en server/deploy.env et remplis-le."
  # shellcheck source=deploy.env.example
  source "$fichier"
  [[ ${HOTE:-} =~ ^[A-Za-z0-9][A-Za-z0-9._@-]*$ ]] || erreur "HOTE (alias SSH) manquant ou invalide dans $fichier."
  [[ ${CONTENEUR:-} =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]] || erreur "CONTENEUR manquant ou invalide dans $fichier."
  [[ ${DOSSIER_VPS:-} =~ ^/[A-Za-z0-9._/-]+$ ]] || erreur "DOSSIER_VPS : chemin absolu attendu dans $fichier."
  [[ ${SAUVEGARDES:-} =~ ^/[A-Za-z0-9._/-]+$ ]] || erreur "SAUVEGARDES : chemin absolu attendu dans $fichier."
  [[ ${URL:-} =~ ^https://[A-Za-z0-9.-]+(:[0-9]+)?/?$ ]] || erreur "URL : adresse en https:// attendue dans $fichier."
  DOSSIER_VPS=${DOSSIER_VPS%/}
  SAUVEGARDES=${SAUVEGARDES%/}
  URL=${URL%/}
}

# Une seule connexion SSH, partagée par tous les appels du script : une seule authentification.
SSH_CONTROLE=""
SSH_OPTIONS=()

ouvrir_ssh() {
  SSH_CONTROLE=$(mktemp -d "${TMPDIR:-/tmp}/eruditus-ssh.XXXXXX")
  SSH_OPTIONS=(-o ControlMaster=auto -o "ControlPath=$SSH_CONTROLE/%C" -o ControlPersist=300 -o ConnectTimeout=15)
  ssh "${SSH_OPTIONS[@]}" "$HOTE" true || erreur "connexion SSH à $HOTE impossible."
  distant sudo -n true 2>/dev/null || erreur "sudo -n refusé sur $HOTE : l'utilisateur SSH doit pouvoir lancer sudo sans saisie (D36)."
}

fermer_ssh() {
  if [[ -n $SSH_CONTROLE ]]; then
    ssh "${SSH_OPTIONS[@]}" -O exit "$HOTE" 2>/dev/null || true
    rm -rf -- "$SSH_CONTROLE"
    SSH_CONTROLE=""
  fi
}

# Lance une commande sur le VPS ; chaque argument y arrive tel quel (citation par printf %q).
distant() {
  # shellcheck disable=SC2029 # voulu : la citation est faite ici, le VPS ne fait que la défaire
  ssh "${SSH_OPTIONS[@]}" "$HOTE" "$(printf '%q ' "$@")"
}

# Commande ssh à passer à rsync (-e), sur la même connexion.
ssh_pour_rsync() {
  printf 'ssh'
  printf ' %s' "${SSH_OPTIONS[@]}"
}

# Dernière valeur de l'option --nom dans les arguments qui suivent (--nom=valeur ou --nom valeur,
# comme les lit PocketBase) ; code 1 si l'option est absente.
valeur_option() {
  local nom=$1 precedent="" arg valeur="" trouvee=false
  shift
  for arg in "$@"; do
    if [[ $arg == "--$nom="* ]]; then
      valeur=${arg#*=}
      trouvee=true
    elif [[ $precedent == "--$nom" ]]; then
      valeur=$arg
      trouvee=true
    fi
    precedent=$arg
  done
  $trouvee || return 1
  printf '%s\n' "$valeur"
}

# Valeur finale d'une option booléenne (--nom, --nom=false…) : « true » ou « false ».
valeur_booleenne() {
  local nom=$1 defaut=$2 arg valeur
  shift 2
  valeur=$defaut
  for arg in "$@"; do
    case $arg in
      "--$nom") valeur=true ;;
      "--$nom="*)
        case ${arg#*=} in
          1 | t | T | true | TRUE | True) valeur=true ;;
          0 | f | F | false | FALSE | False) valeur=false ;;
          *) valeur=invalide ;;
        esac
        ;;
    esac
  done
  printf '%s\n' "$valeur"
}

# Lit l'état, la commande et les montages du conteneur, et en déduit ce que PocketBase utilise :
# binaire, dossier de données, hooks, migrations, clé de chiffrement, mode dev, automigrate.
lire_service() {
  local sortie ligne format
  format='{{.State.Status}}{{println}}'
  format+='{{range .Config.Cmd}}arg{{"\t"}}{{.}}{{println}}{{end}}'
  format+='{{range .Mounts}}montage{{"\t"}}{{.Destination}}{{"\t"}}{{.Source}}{{"\t"}}{{.RW}}{{println}}{{end}}'
  sortie=$(distant sudo -n docker inspect --format "$format" "$CONTENEUR") ||
    erreur "docker inspect $CONTENEUR a échoué sur $HOTE (conteneur absent ?)."

  SERVICE_ETAT=""
  SERVICE_ARGS=()
  SERVICE_MONTAGES=()
  while IFS= read -r ligne; do
    case $ligne in
      arg$'\t'*) SERVICE_ARGS+=("${ligne#arg$'\t'}") ;;
      montage$'\t'*) SERVICE_MONTAGES+=("${ligne#montage$'\t'}") ;;
      "") ;;
      *) [[ -n $SERVICE_ETAT ]] || SERVICE_ETAT=$ligne ;;
    esac
  done <<<"$sortie"

  [[ ${#SERVICE_ARGS[@]} -ge 2 && ${SERVICE_ARGS[0]} == /*pocketbase && ${SERVICE_ARGS[1]} == serve ]] ||
    erreur "commande du conteneur inattendue (« ${SERVICE_ARGS[*]} ») : « <chemin>/pocketbase serve … » attendu."
  SERVICE_BINAIRE=${SERVICE_ARGS[0]}
  local args=("${SERVICE_ARGS[@]:2}")
  SERVICE_DONNEES=$(valeur_option dir "${args[@]}") || SERVICE_DONNEES=$(dirname -- "$SERVICE_BINAIRE")/pb_data
  SERVICE_DONNEES=${SERVICE_DONNEES%/}
  [[ $SERVICE_DONNEES == /* ]] || erreur "--dir du conteneur doit être un chemin absolu ($SERVICE_DONNEES)."
  # Par défaut, PocketBase prend pb_hooks et pb_migrations à côté du dossier de données.
  SERVICE_HOOKS=$(valeur_option hooksDir "${args[@]}") || SERVICE_HOOKS=$(dirname -- "$SERVICE_DONNEES")/pb_hooks
  SERVICE_MIGRATIONS=$(valeur_option migrationsDir "${args[@]}") || SERVICE_MIGRATIONS=$(dirname -- "$SERVICE_DONNEES")/pb_migrations
  SERVICE_HOOKS=${SERVICE_HOOKS%/}
  SERVICE_MIGRATIONS=${SERVICE_MIGRATIONS%/}
  SERVICE_CHIFFREMENT=$(valeur_option encryptionEnv "${args[@]}") || SERVICE_CHIFFREMENT=""
  SERVICE_DEV=$(valeur_booleenne dev true "${args[@]}")
  SERVICE_AUTOMIGRATE=$(valeur_booleenne automigrate true "${args[@]}")
}

# Montage du conteneur dont la destination est $1 : « source<TAB>true|false » (true = écriture).
montage_de() {
  local montage destination reste
  for montage in "${SERVICE_MONTAGES[@]}"; do
    destination=${montage%%$'\t'*}
    reste=${montage#*$'\t'}
    if [[ ${destination%/} == "$1" ]]; then
      printf '%s\n' "$reste"
      return 0
    fi
  done
  return 1
}

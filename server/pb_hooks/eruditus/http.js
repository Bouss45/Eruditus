// Outils communs des routes : erreurs au format { status, message, data: { code } }, lecture
// stricte du corps, jeton exigé, transactions (spec/serveur.md § 5.3, § 6, D31).

const config = require(`${__hooks}/eruditus/config.js`)

const ERREURS = {
  requete_invalide: [400, "Requête invalide."],
  pseudo_invalide: [400, "3 à 20 caractères : lettres, chiffres et _ uniquement."],
  pseudo_reserve: [400, "Ce pseudo est réservé."],
  pseudo_pris: [400, "Ce pseudo est déjà pris."],
  pseudo_trop_recent: [409, "Tu as déjà changé de pseudo il y a moins de 24 heures."],
  mot_de_passe_invalide: [400, "8 à 71 caractères (moins avec des accents ou des emojis)."],
  code_incorrect: [400, "Pseudo ou code incorrect."],
  mot_de_passe_incorrect: [400, "Mot de passe incorrect."],
  choix_invalide: [400, "Choix invalide."],
  partie_introuvable: [404, "Partie introuvable."],
  partie_finie: [409, "Cette partie n'est plus en cours."],
  numero_invalide: [409, "Ce numéro n'est pas celui de la question en attente."],
  deja_repondu: [409, "Réponse déjà enregistrée pour ce numéro."],
  banque_insuffisante: [503, "Pas assez de questions actives."],
}

/** Erreur Eruditus, écrite par `traiter` (PocketBase réécrirait le `data` d'une erreur levée). */
function erreur(code) {
  const [status, message] = ERREURS[code]
  return { estErreurEruditus: true, status, code, message }
}

function nonAutorise() {
  return { estErreurEruditus: true, status: 401, code: null, message: "The request requires valid record authorization token." }
}

/** Exécute le gestionnaire et écrit ses erreurs Eruditus ; toute autre erreur suit son cours. */
function traiter(e, gestionnaire) {
  try {
    return gestionnaire()
  } catch (err) {
    if (err && err.estErreurEruditus) {
      return e.json(err.status, { status: err.status, message: err.message, data: err.code ? { code: err.code } : {} })
    }
    throw err
  }
}

/** Jeton de `eruditus_joueurs` exigé : absent, invalide ou d'une autre collection → 401 (D31). */
function joueur(e) {
  const auth = e.auth
  if (!auth || auth.collection().name !== "eruditus_joueurs") throw nonAutorise()
  return auth
}

/**
 * Corps JSON d'une route qui a une entrée : Content-Type JSON, 16 Ko au plus, un objet.
 * Sinon `requete_invalide`.
 */
function corps(e) {
  const type = String(e.request.header.get("Content-Type") || "").split(";")[0].trim().toLowerCase()
  if (type !== "application/json") throw erreur("requete_invalide")
  let texte
  try {
    const octets = toBytes(e.request.body, config.CORPS_MAX_OCTETS + 1)
    if (octets.length > config.CORPS_MAX_OCTETS) throw erreur("requete_invalide")
    texte = toString(octets)
  } catch (err) {
    throw erreur("requete_invalide")
  }
  let valeur
  try {
    valeur = JSON.parse(texte)
  } catch (_) {
    throw erreur("requete_invalide")
  }
  if (valeur === null || typeof valeur !== "object" || Array.isArray(valeur)) throw erreur("requete_invalide")
  return valeur
}

function present(objet, nom) {
  return Object.prototype.hasOwnProperty.call(objet, nom)
}

/** Champ texte obligatoire ; absent ou d'un autre type → `requete_invalide`. */
function texte(objet, nom) {
  if (!present(objet, nom) || typeof objet[nom] !== "string") throw erreur("requete_invalide")
  return objet[nom]
}

/**
 * Transaction : les vérifications et écritures de l'action d'un bloc (§ 6.1). Une erreur
 * Eruditus levée dedans annule tout et ressort telle quelle.
 */
function transaction(action) {
  let resultat
  let erreurEruditus = null
  try {
    $app.runInTransaction((app) => {
      try {
        resultat = action(app)
      } catch (err) {
        if (err && err.estErreurEruditus) erreurEruditus = err
        throw err
      }
    })
  } catch (err) {
    if (erreurEruditus) throw erreurEruditus
    throw err
  }
  return resultat
}

/** Nombre de caractères (points de code), comme PocketBase compte les longueurs. */
function nombreDeCaracteres(s) {
  let n = 0
  for (let i = 0; i < s.length; i++) {
    const c = s.charCodeAt(i)
    if (c >= 0xd800 && c <= 0xdbff && i + 1 < s.length) {
      const d = s.charCodeAt(i + 1)
      if (d >= 0xdc00 && d <= 0xdfff) i++
    }
    n++
  }
  return n
}

module.exports = { erreur, traiter, joueur, corps, present, texte, transaction, nombreDeCaracteres }

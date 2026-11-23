// Historique des parties et courbe de l'Elo (spec/serveur.md § 5.6, D131 – D135) : trois routes en
// lecture seule. Aucune écriture, aucune partie abandonnée au passage, aucune question servie
// (D134). Les réponses sont construites champ par champ : jamais un enregistrement entier.

const http = require(`${__hooks}/eruditus/http.js`)
const config = require(`${__hooks}/eruditus/config.js`)
const elo = require(`${__hooks}/eruditus/elo.js`)
const parties = require(`${__hooks}/eruditus/parties.js`)

const MOTIF_CURSEUR = /^([0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3}Z)\|([a-z0-9]{15})$/

/** `created` de la base (« 2026-11-23 10:00:00.123Z ») en ISO 8601 UTC. */
function iso(created) {
  return created.replace(" ", "T")
}

/** Curseur opaque : `created` et `id` de la dernière partie, en hexadécimal. */
function encoder(created, id) {
  const texte = created + "|" + id
  let hex = ""
  for (let i = 0; i < texte.length; i++) hex += texte.charCodeAt(i).toString(16).padStart(2, "0")
  return hex
}

/** { created, id } d'un curseur, ou `requete_invalide` : illisible, vide, ou pas de la forme attendue. */
function decoder(hex) {
  if (!/^(?:[0-9a-f]{2}){1,100}$/.test(hex)) throw http.erreur("requete_invalide")
  let texte = ""
  for (let i = 0; i < hex.length; i += 2) texte += String.fromCharCode(parseInt(hex.slice(i, i + 2), 16))
  const m = MOTIF_CURSEUR.exec(texte)
  if (!m) throw http.erreur("requete_invalide")
  return { created: m[1], id: m[2] }
}

/** Le curseur `avant` de la requête : null s'il est absent ; répété, vide ou illisible → `requete_invalide`. */
function curseurDemande(e) {
  const valeurs = []
  for (const morceau of String(e.request.url.rawQuery || "").split("&")) {
    const i = morceau.indexOf("=")
    if ((i < 0 ? morceau : morceau.slice(0, i)) === "avant") valeurs.push(i < 0 ? "" : morceau.slice(i + 1))
  }
  if (valeurs.length === 0) return null
  if (valeurs.length > 1) throw http.erreur("requete_invalide")
  return decoder(valeurs[0])
}

/** Lignes de `eruditus_reponses` des parties données, par partie puis `numero` (index unique). */
function lignesDes(ids, seulementComptees) {
  const parPartie = {}
  if (ids.length === 0) return parPartie
  const liaisons = {}
  const marques = ids.map((id, i) => {
    liaisons["p" + i] = id
    return "{:p" + i + "}"
  })
  const lignes = arrayOf(new DynamicModel({ partie: "", numero: 0, correcte: 0, elo_avant: 0, elo_apres: 0 }))
  $app.db()
    .newQuery(
      "SELECT partie, numero, CASE WHEN correcte THEN 1 ELSE 0 END AS correcte, elo_avant, elo_apres " +
      "FROM eruditus_reponses WHERE partie IN (" + marques.join(",") + ")" +
      (seulementComptees ? " AND elo_avant > 0" : "") + " ORDER BY partie, numero",
    )
    .bind(liaisons)
    .all(lignes)
  for (const l of lignes) (parPartie[l.partie] = parPartie[l.partie] || []).push(l)
  return parPartie
}

/**
 * `elo` d'une partie (D131, D133) : somme des écarts de ses lignes comptées (`elo_avant` non vide),
 * `elo_apres` de la dernière ; `null` et `null` sans ligne comptée. Les écarts sont ceux de la base,
 * aucune formule n'est rejouée.
 */
function eloDe(lignes) {
  let variation = null
  let valeur = null
  let dernier = 0
  for (const l of lignes) {
    if (l.elo_avant <= 0) continue
    variation = (variation === null ? 0 : variation) + l.elo_apres - l.elo_avant
    if (l.numero > dernier) {
      dernier = l.numero
      valeur = l.elo_apres
    }
  }
  return { variation: variation, valeur: valeur }
}

function bonnes(lignes) {
  return lignes.filter((l) => l.correcte === 1).length
}

/** GET /historique?avant=<curseur> : { parties, suivant }, 20 parties au plus, de la plus récente à la plus ancienne. */
function liste(e) {
  return http.traiter(e, () => {
    const joueur = http.joueur(e)
    const curseur = curseurDemande(e)
    const taille = config.HISTORIQUE_PAGE
    const trouvees = arrayOf(new DynamicModel({ id: "", created: "", etat: "" }))
    $app.db()
      .newQuery(
        "SELECT id, created, etat FROM eruditus_parties WHERE joueur = {:joueur} AND etat != 'en_cours'" +
        (curseur ? " AND (created < {:created} OR (created = {:created} AND id < {:id}))" : "") +
        " ORDER BY created DESC, id DESC LIMIT " + (taille + 1),
      )
      .bind({ joueur: joueur.id, created: curseur ? curseur.created : "", id: curseur ? curseur.id : "" })
      .all(trouvees)
    const page = []
    for (let i = 0; i < trouvees.length && i < taille; i++) page.push(trouvees[i])
    const lignes = lignesDes(page.map((p) => p.id), false)
    return e.json(200, {
      parties: page.map((p) => {
        const l = lignes[p.id] || []
        return { id: p.id, date: iso(p.created), etat: p.etat, bonnes: bonnes(l), servies: l.length, elo: eloDe(l) }
      }),
      suivant: trouvees.length > taille ? encoder(page[taille - 1].created, page[taille - 1].id) : null,
    })
  })
}

/**
 * Une question servie, pour le joueur (D132). Retirée (désactivée ou en observation, sans les
 * distinguer) ou modifiée (empreinte vide ou différente d'aujourd'hui) : verdict, niveau et
 * variation seulement. Sinon l'énoncé et les choix ; et, la question étant répondue, ce que le
 * joueur a vu à la correction. Une question non répondue n'en donne jamais la réponse (D134).
 */
function entree(ligne) {
  const repondue = ligne.getString("repondue_le") !== ""
  let statut = "non_repondue"
  if (repondue) {
    if (ligne.getBool("hors_delai")) statut = "hors_delai"
    else if (ligne.getBool("correcte")) statut = "juste"
    else if (ligne.getInt("choix") === 0) statut = "sans_reponse"
    else statut = "fausse"
  }
  const avant = ligne.getInt("elo_avant")
  const sortie = {
    numero: ligne.getInt("numero"),
    statut: statut,
    niveau: elo.niveau(ligne.getInt("elo_difficulte")),
    variation: avant > 0 ? ligne.getInt("elo_apres") - avant : null,
    detail: "ok",
  }
  let question = null
  try {
    question = $app.findRecordById("eruditus_questions", ligne.getString("question"))
  } catch (_) {
    question = null
  }
  if (!question || !question.getBool("active") || question.getBool("observation")) {
    sortie.detail = "retiree"
    return sortie
  }
  const empreinte = ligne.getString("question_empreinte")
  if (empreinte === "" || empreinte !== parties.empreinte(question)) {
    sortie.detail = "modifiee"
    return sortie
  }
  sortie.enonce = question.getString("enonce")
  sortie.choix = JSON.parse(question.getString("choix"))
  if (repondue) {
    const choix = ligne.getInt("choix") // 0 = sans réponse en base, 1 – 4 sinon
    sortie.choixJoueur = choix === 0 ? null : choix - 1
    sortie.bonneReponse = question.getInt("bonne_reponse") - 1
    sortie.explication = question.getString("explication") || null
    sortie.source = question.getString("source") || null
  }
  return sortie
}

/** GET /historique/{id} : { partie, elo, questions } ; la partie en cours, celle d'un autre ou inconnue : `partie_introuvable`. */
function detail(e) {
  return http.traiter(e, () => {
    const joueur = http.joueur(e)
    const partie = parties.partieDuJoueur($app, e.request.pathValue("id"), joueur.id)
    if (partie.getString("etat") === "en_cours") throw http.erreur("partie_introuvable")
    const lignes = $app.findRecordsByFilter("eruditus_reponses", "partie = {:partie}", "numero", 0, 0, { partie: partie.id })
    const resume = (lignesDes([partie.id], false)[partie.id] || [])
    return e.json(200, {
      partie: {
        id: partie.id,
        date: iso(partie.getString("created")),
        etat: partie.getString("etat"),
        bonnes: bonnes(resume),
        servies: resume.length,
        total: config.QUESTIONS_PAR_PARTIE,
      },
      elo: eloDe(resume),
      questions: lignes.map(entree),
    })
  })
}

/**
 * GET /elo/courbe : { depart, points, provisoire }. Un point par partie non en cours qui a au moins
 * une ligne comptée, les 50 plus récentes, du plus ancien au plus récent (D135).
 */
function courbe(e) {
  return http.traiter(e, () => {
    const joueur = http.joueur(e)
    const trouvees = arrayOf(new DynamicModel({ id: "", created: "" }))
    $app.db()
      .newQuery(
        "SELECT p.id AS id, p.created AS created FROM eruditus_parties p WHERE p.joueur = {:joueur} AND p.etat != 'en_cours' " +
        "AND EXISTS (SELECT 1 FROM eruditus_reponses r WHERE r.partie = p.id AND r.elo_avant > 0) " +
        "ORDER BY p.created DESC, p.id DESC LIMIT " + config.COURBE_POINTS,
      )
      .bind({ joueur: joueur.id })
      .all(trouvees)
    // Du plus ancien au plus récent, dans un tableau JS (une tranche de Go ne se retourne pas).
    const ordre = []
    for (let i = trouvees.length - 1; i >= 0; i--) ordre.push(trouvees[i])
    const lignes = lignesDes(ordre.map((p) => p.id), true)
    return e.json(200, {
      depart: ordre.length > 0 ? lignes[ordre[0].id][0].elo_avant : null,
      points: ordre.map((p) => ({ date: iso(p.created), elo: eloDe(lignes[p.id]).valeur })),
      provisoire: joueur.getInt("elo_reponses") < config.ELO.SEUIL_CLASSE,
    })
  })
}

module.exports = { liste, detail, courbe }

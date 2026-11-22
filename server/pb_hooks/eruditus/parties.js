// Partie (spec/regles.md § 2) : création, question servie, réponse et correction. Le serveur
// tire les questions, mesure le chrono, corrige et compte les points (D8, D15 – D17).

const http = require(`${__hooks}/eruditus/http.js`)
const config = require(`${__hooks}/eruditus/config.js`)
const scores = require(`${__hooks}/eruditus/classement.js`)

const TOTAL = config.QUESTIONS_PAR_PARTIE

function millisecondes(enregistrement, champ) {
  return enregistrement.getDateTime(champ).time().unixMilli()
}

/** Question servie, sans la bonne réponse (D16), avec son temps restant (le chrono ne repart pas). */
function sortieQuestion(app, ligne, maintenant) {
  const question = app.findRecordById("eruditus_questions", ligne.getString("question"))
  const delai = config.delaiMs()
  const ecoule = maintenant - millisecondes(ligne, "servie_le")
  return {
    numero: ligne.getInt("numero"),
    total: TOTAL,
    enonce: question.getString("enonce"),
    choix: JSON.parse(question.getString("choix")),
    delaiMs: delai,
    restantMs: Math.max(0, Math.min(delai, delai - ecoule)),
  }
}

function servir(app, partie, questionId, numero) {
  const ligne = new Record(app.findCollectionByNameOrId("eruditus_reponses"))
  ligne.set("partie", partie.id)
  ligne.set("joueur", partie.getString("joueur"))
  ligne.set("question", questionId)
  ligne.set("numero", numero)
  ligne.set("servie_le", new DateTime())
  ligne.set("choix", 0)
  ligne.set("correcte", false)
  ligne.set("hors_delai", false)
  ligne.set("points", 0)
  app.save(ligne)
  return ligne
}

/**
 * 5 questions actives différentes : jamais servies à ce joueur, puis servies jamais réussies,
 * puis réussies ; au hasard dans chaque groupe (spec/regles.md § 2.1).
 */
function tirer(app, joueurId) {
  const lignes = arrayOf(new DynamicModel({ id: "" }))
  app.db()
    .newQuery(
      "SELECT q.id AS id FROM eruditus_questions q WHERE q.active = TRUE ORDER BY " +
      "CASE " +
      "WHEN NOT EXISTS (SELECT 1 FROM eruditus_reponses r WHERE r.joueur = {:joueur} AND r.question = q.id) THEN 0 " +
      "WHEN NOT EXISTS (SELECT 1 FROM eruditus_reponses r WHERE r.joueur = {:joueur} AND r.question = q.id AND r.correcte = TRUE) THEN 1 " +
      "ELSE 2 END, RANDOM() LIMIT " + TOTAL,
    )
    .bind({ joueur: joueurId })
    .all(lignes)
  return lignes.map((l) => l.id)
}

/** Partie du joueur ; inconnue ou à un autre joueur : même réponse (§ 6.1). */
function partieDuJoueur(app, id, joueurId) {
  let partie = null
  try {
    partie = app.findRecordById("eruditus_parties", id)
  } catch (_) {
    partie = null
  }
  if (!partie || partie.getString("joueur") !== joueurId) throw http.erreur("partie_introuvable")
  return partie
}

/**
 * Null si la partie est en cours ; sinon l'erreur `partie_finie`. Une partie sans activité depuis
 * l'inactivité permise est marquée abandonnée ici, à l'accès suivant (spec/regles.md § 2.5).
 */
function finie(app, partie, maintenant) {
  if (partie.getString("etat") !== "en_cours") return http.erreur("partie_finie")
  if (maintenant - millisecondes(partie, "updated") > config.inactiviteMs()) {
    partie.set("etat", "abandonnee")
    app.save(partie)
    return http.erreur("partie_finie")
  }
  return null
}

function lignesDeLaPartie(app, partie) {
  return app.findRecordsByFilter("eruditus_reponses", "partie = {:partie}", "numero", 0, 0, { partie: partie.id })
}

/** POST /parties : abandonne la partie en cours, tire 5 questions, sert la première. */
function creer(e) {
  return http.traiter(e, () => {
    const joueur = http.joueur(e)
    const maintenant = Date.now()
    const sortie = http.transaction((app) => {
      const enCours = app.findRecordsByFilter(
        "eruditus_parties", "joueur = {:joueur} && etat = 'en_cours'", "", 0, 0, { joueur: joueur.id },
      )
      for (const ancienne of enCours) {
        ancienne.set("etat", "abandonnee")
        app.save(ancienne)
      }
      const questions = tirer(app, joueur.id)
      if (questions.length < TOTAL) throw http.erreur("banque_insuffisante")
      const partie = new Record(app.findCollectionByNameOrId("eruditus_parties"))
      partie.set("joueur", joueur.id)
      partie.set("questions", questions)
      partie.set("etat", "en_cours")
      app.save(partie)
      const premiere = servir(app, partie, questions[0], 1)
      return { partie: { id: partie.id, total: TOTAL }, question: sortieQuestion(app, premiere, maintenant) }
    })
    return e.json(200, sortie)
  })
}

/**
 * POST /parties/{id}/question : la question servie pas encore répondue avec son temps restant,
 * sinon la suivante, dont le chrono démarre maintenant.
 */
function question(e) {
  return http.traiter(e, () => {
    const joueur = http.joueur(e)
    const id = e.request.pathValue("id")
    const maintenant = Date.now()
    const resultat = http.transaction((app) => {
      const partie = partieDuJoueur(app, id, joueur.id)
      const fin = finie(app, partie, maintenant)
      if (fin) return { erreur: fin }
      const lignes = lignesDeLaPartie(app, partie)
      const enAttente = lignes.filter((l) => l.getString("repondue_le") === "")
      if (enAttente.length > 0) return { sortie: { question: sortieQuestion(app, enAttente[0], maintenant) } }
      const numero = lignes.length + 1
      if (numero > TOTAL) return { erreur: http.erreur("partie_finie") }
      const ligne = servir(app, partie, partie.getStringSlice("questions")[numero - 1], numero)
      app.save(partie) // activité : repousse l'abandon
      return { sortie: { question: sortieQuestion(app, ligne, maintenant) } }
    })
    if (resultat.erreur) throw resultat.erreur
    return e.json(200, resultat.sortie)
  })
}

/**
 * POST /parties/{id}/reponse : { numero, choix } (choix 0 – 3 ou null = sans réponse). Tout le
 * reste est calculé ici ; les autres champs envoyés sont ignorés (T27).
 */
function reponse(e) {
  return http.traiter(e, () => {
    const joueur = http.joueur(e)
    const maintenant = Date.now()
    const corps = http.corps(e)
    if (!http.present(corps, "numero") || typeof corps.numero !== "number") throw http.erreur("requete_invalide")
    if (!http.present(corps, "choix") || (corps.choix !== null && typeof corps.choix !== "number")) {
      throw http.erreur("requete_invalide")
    }
    const numero = corps.numero
    const choix = corps.choix
    if (choix !== null && !(Number.isInteger(choix) && choix >= 0 && choix <= 3)) throw http.erreur("choix_invalide")
    const id = e.request.pathValue("id")

    const resultat = http.transaction((app) => {
      const partie = partieDuJoueur(app, id, joueur.id)
      const fin = finie(app, partie, maintenant)
      if (fin) return { erreur: fin }
      if (!Number.isInteger(numero) || numero < 1 || numero > TOTAL) throw http.erreur("numero_invalide")
      const lignes = app.findRecordsByFilter(
        "eruditus_reponses", "partie = {:partie} && numero = {:numero}", "", 1, 0, { partie: partie.id, numero: numero },
      )
      if (lignes.length === 0) throw http.erreur("numero_invalide")
      const ligne = lignes[0]
      if (ligne.getString("repondue_le") !== "") throw http.erreur("deja_repondu")

      const question = app.findRecordById("eruditus_questions", ligne.getString("question"))
      const bonne = question.getInt("bonne_reponse") - 1 // 1 – 4 en base, 0 – 3 dans l'API
      const horsDelai = maintenant - millisecondes(ligne, "servie_le") > config.delaiMs() + config.margeMs()
      const juste = choix !== null && choix === bonne
      const correcte = juste && !horsDelai // hors délai = fausse (D15, D32)
      let dejaReussie = false
      if (correcte) {
        const deja = new DynamicModel({ n: 0 })
        app.db()
          .newQuery(
            "SELECT COUNT(*) AS n FROM eruditus_reponses " +
            "WHERE joueur = {:joueur} AND question = {:question} AND correcte = TRUE AND id != {:id}",
          )
          .bind({ joueur: joueur.id, question: question.id, id: ligne.id })
          .one(deja)
        dejaReussie = deja.n > 0
      }
      const point = correcte && !dejaReussie ? 1 : 0
      let raison = "gagne"
      if (horsDelai) raison = "hors_delai"
      else if (choix === null) raison = "sans_reponse"
      else if (!juste) raison = "fausse"
      else if (dejaReussie) raison = "deja_reussie"

      ligne.set("repondue_le", new DateTime())
      ligne.set("choix", choix === null ? 0 : choix + 1) // 0 = sans réponse en base
      ligne.set("correcte", correcte)
      ligne.set("hors_delai", horsDelai)
      ligne.set("points", point)
      app.save(ligne)
      if (numero === TOTAL) partie.set("etat", "terminee")
      app.save(partie) // activité, ou fin de la partie

      const score = scores.score(app, joueur.id)
      let bilan = null
      if (numero === TOTAL) {
        const totaux = new DynamicModel({ bonnes: 0, points: 0 })
        app.db()
          .newQuery(
            "SELECT COALESCE(SUM(correcte), 0) AS bonnes, COALESCE(SUM(points), 0) AS points " +
            "FROM eruditus_reponses WHERE partie = {:partie}",
          )
          .bind({ partie: partie.id })
          .one(totaux)
        bilan = { bonnes: totaux.bonnes, points: totaux.points, score: score }
      }
      return {
        sortie: {
          correction: {
            numero: numero,
            choix: choix,
            correcte: correcte,
            bonneReponse: bonne,
            horsDelai: horsDelai,
            point: point,
            raison: raison,
            score: score,
            // Seulement ici, la réponse enregistrée (D97) ; vide en base = aucune.
            explication: question.getString("explication") || null,
            source: question.getString("source") || null,
          },
          bilan: bilan,
        },
      }
    })
    if (resultat.erreur) throw resultat.erreur
    return e.json(200, resultat.sortie)
  })
}

module.exports = { creer, question, reponse }

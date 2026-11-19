// Score et classement (spec/regles.md § 3, § 4), calculés à chaque demande à partir des réponses
// enregistrées : aucun champ de score n'existe (D17).

const http = require(`${__hooks}/eruditus/http.js`)

// Score de chaque joueur classé : questions différentes qui lui ont rapporté un point.
const SCORES =
  "SELECT r.joueur AS joueur, COUNT(DISTINCT r.question) AS score " +
  "FROM eruditus_reponses r WHERE r.points = 1 GROUP BY r.joueur"

function score(app, joueurId) {
  const resultat = new DynamicModel({ n: 0 })
  app.db()
    .newQuery("SELECT COUNT(DISTINCT question) AS n FROM eruditus_reponses WHERE joueur = {:joueur} AND points = 1")
    .bind({ joueur: joueurId })
    .one(resultat)
  return resultat.n
}

function classes(app) {
  const resultat = new DynamicModel({ n: 0 })
  app.db().newQuery("SELECT COUNT(*) AS n FROM (" + SCORES + ")").one(resultat)
  return resultat.n
}

/** Rang de compétition : 1 + joueurs au score strictement supérieur (1, 2, 2, 4). Null à 0 point. */
function rang(app, monScore) {
  if (monScore < 1) return null
  const resultat = new DynamicModel({ n: 0 })
  app.db()
    .newQuery("SELECT COUNT(*) AS n FROM (" + SCORES + ") s WHERE s.score > {:score}")
    .bind({ score: monScore })
    .one(resultat)
  return resultat.n + 1
}

/** Les 10 premières lignes : score décroissant, puis pseudo sans tenir compte de la casse. */
function top(app) {
  const lignes = arrayOf(new DynamicModel({ rang: 0, pseudo: "", score: 0 }))
  app.db()
    .newQuery(
      "WITH s AS (" + SCORES + ") " +
      "SELECT RANK() OVER (ORDER BY s.score DESC) AS rang, j.pseudo AS pseudo, s.score AS score " +
      "FROM s JOIN eruditus_joueurs j ON j.id = s.joueur " +
      "ORDER BY s.score DESC, j.pseudo COLLATE NOCASE ASC LIMIT 10",
    )
    .all(lignes)
  return lignes.map((l) => ({ rang: l.rang, pseudo: l.pseudo, score: l.score }))
}

/** GET /moi : { joueur, score, rang, classes }. */
function moi(e) {
  return http.traiter(e, () => {
    const joueur = http.joueur(e)
    const monScore = score($app, joueur.id)
    return e.json(200, {
      joueur: { id: joueur.id, pseudo: joueur.getString("pseudo") },
      score: monScore,
      rang: rang($app, monScore),
      classes: classes($app),
    })
  })
}

/** GET /classement : { top, moi, classes } ; une ligne = rang, pseudo, score, rien d'autre. */
function classement(e) {
  return http.traiter(e, () => {
    const joueur = http.joueur(e)
    const monScore = score($app, joueur.id)
    const monRang = rang($app, monScore)
    return e.json(200, {
      top: top($app),
      moi: monRang === null ? null : { rang: monRang, pseudo: joueur.getString("pseudo"), score: monScore },
      classes: classes($app),
    })
  })
}

module.exports = { score, moi, classement }

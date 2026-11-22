// Elo du solo (spec/regles.md § 8, spec/serveur.md § 5.5) : calculé et écrit ici seulement, dans
// la transaction de la route qui sert ou corrige la question (D102, D106). L'app n'envoie que ses
// choix ; aucun champ interne ne sort jamais (T51).

const http = require(`${__hooks}/eruditus/http.js`)
const config = require(`${__hooks}/eruditus/config.js`)

const C = config.ELO

/** Attendu du joueur P contre la question Q, 25 % au moins (D103). */
function attendu(p, q) {
  return C.CHANCE + (1 - C.CHANCE) / (1 + Math.pow(10, (q - p) / 400))
}

/** K selon les réponses comptées avant celle-ci (D104). */
function k(n) {
  if (n < C.SEUIL_CLASSE) return C.K_PROVISOIRE
  return n < C.SEUIL_ANCIEN ? C.K_CLASSE : C.K_ANCIEN
}

/** Nouvel Elo, à l'entier le plus proche (,5 vers le haut, comme Math.round), plancher compris. */
function apres(p, kJoueur, s, e) {
  return Math.max(C.PLANCHER, Math.round(p + kJoueur * (s - e)))
}

/**
 * Question servie, avant l'enregistrement de sa ligne : si elle compte (à niveau, jamais servie
 * à ce joueur, réponses d'avant l'Elo comprises, D105), la ligne reçoit l'Elo de départ, la
 * difficulté et K, et la perte est comptée aussitôt, compteur compris (D106).
 */
function servir(app, ligne) {
  const joueurId = ligne.getString("joueur")
  const question = app.findRecordById("eruditus_questions", ligne.getString("question"))
  const q = question.getInt("difficulte")
  if (q <= 0) return
  const vue = new DynamicModel({ n: 0 })
  app.db()
    .newQuery("SELECT COUNT(*) AS n FROM eruditus_reponses WHERE joueur = {:joueur} AND question = {:question}")
    .bind({ joueur: joueurId, question: question.id })
    .one(vue)
  if (vue.n > 0) return

  const joueur = app.findRecordById("eruditus_joueurs", joueurId)
  const p = joueur.getInt("elo")
  const n = joueur.getInt("elo_reponses")
  const kJoueur = k(n)
  const perte = apres(p, kJoueur, 0, attendu(p, q))
  ligne.set("elo_avant", p)
  ligne.set("elo_difficulte", q)
  ligne.set("elo_k", kJoueur)
  ligne.set("elo_apres", perte)
  joueur.set("elo", perte)
  joueur.set("elo_reponses", n + 1)
  app.save(joueur)
}

/**
 * Réponse enregistrée sur une ligne comptée : une réponse correcte remplace la perte par le gain,
 * depuis le même Elo de départ, la même difficulté et le même K. Une réponse donnée (un choix
 * dans le délai) d'un joueur classé à la mise en service fait ensuite bouger la question (D107).
 * La ligne est enregistrée par l'appelant.
 */
function repondre(app, ligne, question, donnee, correcte) {
  const p = ligne.getInt("elo_avant")
  if (p <= 0) return
  const e = attendu(p, ligne.getInt("elo_difficulte"))
  const s = correcte ? 1 : 0
  if (correcte) {
    const gain = apres(p, ligne.getInt("elo_k"), 1, e)
    const joueur = app.findRecordById("eruditus_joueurs", ligne.getString("joueur"))
    // Une seule question comptée en attente à la fois : son Elo vaut la perte de cette ligne.
    joueur.set("elo", joueur.getInt("elo") + gain - ligne.getInt("elo_apres"))
    app.save(joueur)
    ligne.set("elo_apres", gain)
  }

  const q = question.getInt("difficulte")
  // Niveau retiré depuis la mise en service : la question ne compte plus (D107).
  // Question en observation : sa difficulté et son compteur ne bougent plus (D122).
  if (!donnee || ligne.getInt("elo_k") === C.K_PROVISOIRE || q <= 0 || question.getBool("observation")) return
  const n = question.getInt("difficulte_reponses")
  if (n >= C.REPONSES_AVANT_MOUVEMENT) {
    const base = C.DIFFICULTES[question.getInt("niveau")]
    const bougee = Math.round(q - C.K_QUESTION * (s - e))
    question.set("difficulte", Math.min(base + C.ECART_MAX, Math.max(base - C.ECART_MAX, bougee)))
  }
  question.set("difficulte_reponses", n + 1)
  app.save(question)
}

/**
 * `elo` du bilan : Elo après la partie, somme des écarts de ses lignes comptées, et révision si
 * ses 5 questions avaient toutes déjà été servies au joueur avant (D105, D109).
 */
function bilan(app, partie, joueurId) {
  const totaux = new DynamicModel({ variation: 0, neuves: 0 })
  app.db()
    .newQuery(
      "SELECT COALESCE(SUM(CASE WHEN r.elo_avant > 0 THEN r.elo_apres - r.elo_avant ELSE 0 END), 0) AS variation, " +
      "COALESCE(SUM(CASE WHEN EXISTS (SELECT 1 FROM eruditus_reponses a WHERE a.joueur = r.joueur " +
      "AND a.question = r.question AND a.servie_le < r.servie_le) THEN 0 ELSE 1 END), 0) AS neuves " +
      "FROM eruditus_reponses r WHERE r.partie = {:partie}",
    )
    .bind({ partie: partie.id })
    .one(totaux)
  const joueur = app.findRecordById("eruditus_joueurs", joueurId)
  return { valeur: joueur.getInt("elo"), variation: totaux.variation, revision: totaux.neuves === 0 }
}

function classes(app) {
  const r = new DynamicModel({ n: 0 })
  app.db()
    .newQuery("SELECT COUNT(*) AS n FROM eruditus_joueurs WHERE elo_reponses >= {:seuil}")
    .bind({ seuil: C.SEUIL_CLASSE })
    .one(r)
  return r.n
}

/** Rang par Elo : 1 + classés à l'Elo strictement supérieur ; null tant que provisoire. */
function rang(app, joueur) {
  if (joueur.getInt("elo_reponses") < C.SEUIL_CLASSE) return null
  const r = new DynamicModel({ n: 0 })
  app.db()
    .newQuery("SELECT COUNT(*) AS n FROM eruditus_joueurs WHERE elo_reponses >= {:seuil} AND elo > {:elo}")
    .bind({ seuil: C.SEUIL_CLASSE, elo: joueur.getInt("elo") })
    .one(r)
  return r.n + 1
}

function restantes(joueur) {
  return Math.max(0, C.SEUIL_CLASSE - joueur.getInt("elo_reponses"))
}

/** `elo` de /moi (D109) ; `revision` : plus aucune question active jamais servie au joueur. */
function moi(app, joueur) {
  const neuve = new DynamicModel({ n: 0 })
  app.db()
    .newQuery(
      "SELECT EXISTS (SELECT 1 FROM eruditus_questions q WHERE q.active = TRUE AND q.observation = FALSE AND NOT EXISTS " +
      "(SELECT 1 FROM eruditus_reponses r WHERE r.joueur = {:joueur} AND r.question = q.id)) AS n",
    )
    .bind({ joueur: joueur.id })
    .one(neuve)
  const reste = restantes(joueur)
  return {
    valeur: joueur.getInt("elo"),
    provisoire: reste > 0,
    restantes: reste,
    rang: rang(app, joueur),
    classes: classes(app),
    revision: neuve.n === 0,
  }
}

/** GET /classement/elo : { top, moi, classes, restantes } ; une ligne = rang, pseudo, elo. */
function classement(e) {
  return http.traiter(e, () => {
    const joueur = http.joueur(e)
    const lignes = arrayOf(new DynamicModel({ rang: 0, pseudo: "", elo: 0 }))
    $app.db()
      .newQuery(
        "SELECT RANK() OVER (ORDER BY elo DESC) AS rang, pseudo, elo FROM eruditus_joueurs " +
        "WHERE elo_reponses >= {:seuil} ORDER BY elo DESC, pseudo COLLATE NOCASE ASC LIMIT 10",
      )
      .bind({ seuil: C.SEUIL_CLASSE })
      .all(lignes)
    const monRang = rang($app, joueur)
    return e.json(200, {
      top: lignes.map((l) => ({ rang: l.rang, pseudo: l.pseudo, elo: l.elo })),
      moi: monRang === null ? null : { rang: monRang, pseudo: joueur.getString("pseudo"), elo: joueur.getInt("elo") },
      classes: classes($app),
      restantes: restantes(joueur),
    })
  })
}

module.exports = { servir, repondre, bilan, moi, classement }

// Signalement d'une question (spec/serveur.md § 5.4, D118 – D122) : écrit d'abord, puis annoncé
// sur Discord sans que son échec change quoi que ce soit. À 3 comptes distincts, la question passe
// en observation et n'est plus tirée.

const http = require(`${__hooks}/eruditus/http.js`)
const config = require(`${__hooks}/eruditus/config.js`)
const avis = require(`${__hooks}/eruditus/avis.js`)
const parties = require(`${__hooks}/eruditus/parties.js`)
const discord = require(`${__hooks}/eruditus/discord.js`)

const MOTIFS = {
  reponse_fausse: "Réponse fausse",
  enonce_ambigu: "Énoncé ambigu",
  explication_fausse: "Explication fausse",
  autre: "Autre",
}
// Limites de Discord (D120) : titre 256, description et valeur de champ 1 024 ; on reste dessous.
const LONGUEUR_DISCORD = 1000

/** Coupe en points de code, « … » à la fin si besoin. */
function tronquer(texte, max) {
  const points = Array.from(texte)
  return points.length <= max ? texte : points.slice(0, max - 1).join("") + "…"
}


/** Écrit le signalement ; renvoie null s'il existait déjà, sinon ce qu'il faut pour le message. */
function ecrire(app, auth, idPartie, numero, motif, commentaire) {
  const partie = parties.partieDuJoueur(app, idPartie, auth.id)
  const lignes = app.findRecordsByFilter(
    "eruditus_reponses", "partie = {:partie} && numero = {:numero}", "", 1, 0, { partie: partie.id, numero: numero },
  )
  if (lignes.length === 0 || lignes[0].getDateTime("repondue_le").isZero()) throw http.erreur("numero_invalide")
  const idQuestion = lignes[0].getString("question")

  const deja = app.findRecordsByFilter(
    "eruditus_signalements", "joueur = {:joueur} && question = {:question}", "", 1, 0, { joueur: auth.id, question: idQuestion },
  )
  if (deja.length > 0) return null

  const depuis = new Date(Date.now() - 3600000).toISOString().replace("T", " ")
  const recents = app.findRecordsByFilter(
    "eruditus_signalements", "joueur = {:joueur} && created > {:depuis}", "", 0, 0, { joueur: auth.id, depuis: depuis },
  )
  if (recents.length >= config.SIGNALEMENTS_PAR_HEURE) throw http.erreur("signalement_trop_frequent")

  const ligne = new Record(app.findCollectionByNameOrId("eruditus_signalements"))
  ligne.set("joueur", auth.id)
  ligne.set("question", idQuestion)
  ligne.set("motif", motif)
  ligne.set("commentaire", commentaire)
  app.save(ligne)

  const question = app.findRecordById("eruditus_questions", idQuestion)
  const nombre = app.findRecordsByFilter("eruditus_signalements", "question = {:question}", "", 0, 0, { question: idQuestion }).length
  let etat = ""
  if (nombre >= config.SEUIL_OBSERVATION && !question.getBool("observation")) {
    const servables = new DynamicModel({ n: 0 })
    app.db()
      .newQuery("SELECT COUNT(*) AS n FROM eruditus_questions WHERE active = TRUE AND observation = FALSE")
      .one(servables)
    // Le retrait d'une question servable en laisserait une de moins (D121).
    const apres = servables.n - (question.getBool("active") ? 1 : 0)
    if (apres >= config.PLANCHER_SERVABLES) {
      question.set("observation", true)
      app.save(question)
      etat = "observation"
    } else {
      etat = "gardee"
    }
  }
  const choix = JSON.parse(question.getString("choix"))
  return {
    ref: question.getString("ref"),
    enonce: question.getString("enonce"),
    bonne: choix[question.getInt("bonne_reponse") - 1],
    lettre: "ABCD"[question.getInt("bonne_reponse") - 1],
    nombre: nombre,
    etat: etat,
  }
}

/** Le message du webhook (D120) : rien du joueur. */
function message(s, motif, commentaire) {
  let compteur = `${s.nombre} / ${config.SEUIL_OBSERVATION}`
  if (s.etat === "observation") compteur += " · mise en observation"
  if (s.etat === "gardee") compteur += " · gardée en jeu : banque trop petite"
  const champs = [
    { name: "Bonne réponse", value: tronquer(`${s.lettre}. ${s.bonne}`, LONGUEUR_DISCORD) },
    { name: "Motif", value: MOTIFS[motif] },
    { name: "Signalements", value: compteur },
  ]
  if (commentaire) champs.push({ name: "Commentaire", value: tronquer(commentaire, LONGUEUR_DISCORD) })
  return {
    username: "Eruditus",
    allowed_mentions: { parse: [] },
    embeds: [{
      title: tronquer(`Question signalée · ${s.ref}`, 250),
      description: tronquer(s.enonce, LONGUEUR_DISCORD),
      fields: champs,
      footer: { text: "Eruditus" },
      timestamp: new Date().toISOString(),
    }],
  }
}

/** POST /signalement : { partie, numero, motif, commentaire? } → {} ; la question vient de la partie (D119). */
function signaler(e) {
  return http.traiter(e, () => {
    const auth = http.joueur(e)
    const corps = http.corps(e)
    const idPartie = http.texte(corps, "partie")
    const motif = http.texte(corps, "motif")
    if (!http.present(corps, "numero") || typeof corps.numero !== "number") throw http.erreur("requete_invalide")
    const brut = http.present(corps, "commentaire") ? http.texte(corps, "commentaire") : ""
    const commentaire = avis.normaliser(brut)
    if (!Object.prototype.hasOwnProperty.call(MOTIFS, motif) || http.nombreDeCaracteres(commentaire) > config.COMMENTAIRE_MAX) {
      throw http.erreur("signalement_invalide")
    }

    const ecrit = http.transaction((app) => ecrire(app, auth, idPartie, corps.numero, motif, commentaire))
    const adresse = config.webhookAvis()
    if (ecrit && adresse) discord.transmettre(adresse, message(ecrit, motif, commentaire))
    return e.json(200, {})
  })
}

module.exports = { signaler }

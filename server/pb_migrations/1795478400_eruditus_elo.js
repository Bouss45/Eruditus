/// <reference path="../pb_data/types.d.ts" />

// Elo du solo (spec/serveur.md § 3, D110) : Elo et réponses comptées des joueurs, difficulté des
// questions, Elo avant et après de chaque réponse comptée. Tous entiers et cachés ; ajouts
// seulement. Joueurs existants : 1000 et 0 réponse comptée, rien n'est rejoué ; leurs réponses
// d'avant restent sans Elo. Questions existantes : difficulté de leur niveau. Les valeurs sont
// écrites ici, pas lues dans config.js : une migration déployée ne change jamais (D22).
const DIFFICULTES = "CASE niveau WHEN 1 THEN 800 WHEN 2 THEN 1200 WHEN 3 THEN 1600 WHEN 4 THEN 2000 WHEN 5 THEN 2400 ELSE 0 END"
const CHAMPS = {
  eruditus_joueurs: ["elo", "elo_reponses"],
  eruditus_questions: ["difficulte", "difficulte_reponses"],
  eruditus_reponses: ["elo_avant", "elo_apres", "elo_difficulte", "elo_k"],
}

function entier(nom, min) {
  return new NumberField({ name: nom, hidden: true, onlyInt: true, min: min })
}

migrate((app) => {
  const joueurs = app.findCollectionByNameOrId("eruditus_joueurs")
  joueurs.fields.add(entier("elo", 100))
  joueurs.fields.add(entier("elo_reponses", 0))
  joueurs.addIndex("idx_eruditus_joueurs_elo", false, "elo", "")
  app.save(joueurs)
  app.db().newQuery("UPDATE eruditus_joueurs SET elo = 1000, elo_reponses = 0").execute()

  const questions = app.findCollectionByNameOrId("eruditus_questions")
  questions.fields.add(entier("difficulte", 0))
  questions.fields.add(entier("difficulte_reponses", 0))
  app.save(questions)
  app.db().newQuery(`UPDATE eruditus_questions SET difficulte = ${DIFFICULTES}, difficulte_reponses = 0`).execute()

  const reponses = app.findCollectionByNameOrId("eruditus_reponses")
  for (const nom of CHAMPS.eruditus_reponses) reponses.fields.add(entier(nom, 0))
  app.save(reponses)
}, (app) => {
  for (const collection of Object.keys(CHAMPS)) {
    const c = app.findCollectionByNameOrId(collection)
    for (const nom of CHAMPS[collection]) c.fields.removeByName(nom)
    if (collection === "eruditus_joueurs") c.removeIndex("idx_eruditus_joueurs_elo")
    app.save(c)
  }
})

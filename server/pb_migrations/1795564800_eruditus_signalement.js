/// <reference path="../pb_data/types.d.ts" />

// Signalement d'une question (spec/serveur.md § 3.2, § 3.6, § 4.1, D119, D121, D124) : collection
// fermée des signalements, champ caché `observation` des questions, et limite par IP de la route,
// juste avant la règle de préfixe /api/eruditus/ (D34). Ajouts seulement : les migrations déjà
// déployées ne changent jamais (D22).
const REGLES = [
  { label: "POST /api/eruditus/v1/signalement", audience: "", maxRequests: 20, duration: 3600 },
]
const PREFIXE_ERUDITUS = "/api/eruditus/"

function copie(r) {
  return { label: r.label, audience: r.audience, maxRequests: r.maxRequests, duration: r.duration }
}

migrate((app) => {
  const questions = app.findCollectionByNameOrId("eruditus_questions")
  questions.fields.add(new BoolField({ name: "observation", hidden: true }))
  app.save(questions)
  const joueurs = app.findCollectionByNameOrId("eruditus_joueurs")

  app.save(new Collection({
    type: "base",
    name: "eruditus_signalements",
    listRule: null, viewRule: null, createRule: null, updateRule: null, deleteRule: null,
    fields: [
      { type: "relation", name: "joueur", required: true, collectionId: joueurs.id, cascadeDelete: true, maxSelect: 1 },
      { type: "relation", name: "question", required: true, collectionId: questions.id, cascadeDelete: false, maxSelect: 1 },
      { type: "select", name: "motif", required: true, values: ["reponse_fausse", "enonce_ambigu", "explication_fausse", "autre"], maxSelect: 1 },
      { type: "text", name: "commentaire", hidden: true, max: 200 },
      { type: "autodate", name: "created", onCreate: true, onUpdate: false },
      { type: "autodate", name: "updated", onCreate: true, onUpdate: true },
    ],
    indexes: [
      "CREATE UNIQUE INDEX idx_eruditus_signalements_joueur_question ON eruditus_signalements (joueur, question)",
      "CREATE INDEX idx_eruditus_signalements_question ON eruditus_signalements (question)",
    ],
  }))

  const reglages = app.settings()
  const labels = REGLES.map((r) => r.label)
  let regles = []
  let placees = false
  for (const r of reglages.rateLimits.rules) {
    if (labels.indexOf(r.label) !== -1) continue
    if (r.label === PREFIXE_ERUDITUS && !placees) {
      regles = regles.concat(REGLES)
      placees = true
    }
    regles.push(copie(r))
  }
  if (!placees) regles = REGLES.concat(regles)
  unmarshal({ rateLimits: { rules: regles } }, reglages)
  app.save(reglages)
}, (app) => {
  app.delete(app.findCollectionByNameOrId("eruditus_signalements"))
  const questions = app.findCollectionByNameOrId("eruditus_questions")
  questions.fields.removeByName("observation")
  app.save(questions)

  const reglages = app.settings()
  const labels = REGLES.map((r) => r.label)
  const restantes = []
  for (const r of reglages.rateLimits.rules) {
    if (labels.indexOf(r.label) === -1) restantes.push(copie(r))
  }
  unmarshal({ rateLimits: { rules: restantes } }, reglages)
  app.save(reglages)
})

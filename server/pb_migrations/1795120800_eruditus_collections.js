/// <reference path="../pb_data/types.d.ts" />

// Collections d'Eruditus (spec/serveur.md § 3) : joueurs, questions, parties, réponses.
// Règles list, view, create, update et delete toutes à null (superutilisateur seul, D8) :
// les hooks lisent et écrivent avec $app, sans passer par les règles.
migrate((app) => {
  const fermee = { listRule: null, viewRule: null, createRule: null, updateRule: null, deleteRule: null }
  const dates = [
    { type: "autodate", name: "created", onCreate: true, onUpdate: false },
    { type: "autodate", name: "updated", onCreate: true, onUpdate: true },
  ]

  // eruditus_joueurs (auth) : pseudo et empreinte du code de récupération.
  app.save(new Collection(Object.assign({}, fermee, {
    type: "auth",
    name: "eruditus_joueurs",
    authRule: "",
    manageRule: null,
    fields: [
      { type: "text", name: "pseudo", required: true, min: 3, max: 20, pattern: "^[A-Za-z0-9_]{3,20}$", presentable: true },
      { type: "text", name: "code_recuperation", required: true, hidden: true, min: 64, max: 64, pattern: "^[0-9a-f]{64}$" },
    ].concat(dates),
    indexes: ["CREATE UNIQUE INDEX idx_eruditus_joueurs_pseudo ON eruditus_joueurs (pseudo COLLATE NOCASE)"],
  })))
  // Les champs système (mot de passe, e-mail) n'existent qu'une fois la collection créée.
  const joueurs = app.findCollectionByNameOrId("eruditus_joueurs")
  const motDePasse = joueurs.fields.getByName("password")
  motDePasse.min = 8
  motDePasse.max = 71
  joueurs.fields.getByName("email").required = false
  joueurs.passwordAuth.enabled = true
  joueurs.passwordAuth.identityFields = ["pseudo"]
  joueurs.oauth2.enabled = false
  joueurs.otp.enabled = false
  joueurs.mfa.enabled = false
  joueurs.authAlert.enabled = false
  joueurs.authToken.duration = 2592000
  app.save(joueurs)

  // eruditus_questions : bonne_reponse de 1 à 4 en base (0 – 3 dans l'API), cachée.
  app.save(new Collection(Object.assign({}, fermee, {
    type: "base",
    name: "eruditus_questions",
    fields: [
      { type: "text", name: "ref", required: true, min: 1, max: 50, pattern: "^[A-Za-z0-9_-]{1,50}$", presentable: true },
      { type: "text", name: "enonce", required: true, min: 10, max: 300 },
      { type: "json", name: "choix", required: true, maxSize: 4000 },
      { type: "number", name: "bonne_reponse", required: true, hidden: true, min: 1, max: 4, onlyInt: true },
      { type: "bool", name: "active" },
    ].concat(dates),
    indexes: ["CREATE UNIQUE INDEX idx_eruditus_questions_ref ON eruditus_questions (ref)"],
  })))
  const questions = app.findCollectionByNameOrId("eruditus_questions")

  // eruditus_parties : 5 questions dans l'ordre de la partie ; `updated` mesure l'inactivité.
  app.save(new Collection(Object.assign({}, fermee, {
    type: "base",
    name: "eruditus_parties",
    fields: [
      { type: "relation", name: "joueur", required: true, collectionId: joueurs.id, cascadeDelete: true, maxSelect: 1 },
      { type: "relation", name: "questions", required: true, collectionId: questions.id, cascadeDelete: false, minSelect: 5, maxSelect: 5 },
      { type: "select", name: "etat", required: true, values: ["en_cours", "terminee", "abandonnee"], maxSelect: 1 },
    ].concat(dates),
    indexes: ["CREATE INDEX idx_eruditus_parties_joueur_etat ON eruditus_parties (joueur, etat)"],
  })))
  const parties = app.findCollectionByNameOrId("eruditus_parties")

  // eruditus_reponses : une ligne par question servie, complétée à la réponse.
  app.save(new Collection(Object.assign({}, fermee, {
    type: "base",
    name: "eruditus_reponses",
    fields: [
      { type: "relation", name: "partie", required: true, collectionId: parties.id, cascadeDelete: true, maxSelect: 1 },
      { type: "relation", name: "joueur", required: true, collectionId: joueurs.id, cascadeDelete: true, maxSelect: 1 },
      { type: "relation", name: "question", required: true, collectionId: questions.id, cascadeDelete: false, maxSelect: 1 },
      { type: "number", name: "numero", required: true, min: 1, max: 5, onlyInt: true },
      { type: "date", name: "servie_le", required: true },
      { type: "date", name: "repondue_le" },
      { type: "number", name: "choix", min: 0, max: 4, onlyInt: true },
      { type: "bool", name: "correcte" },
      { type: "bool", name: "hors_delai" },
      { type: "number", name: "points", min: 0, max: 1, onlyInt: true },
    ].concat(dates),
    indexes: [
      "CREATE UNIQUE INDEX idx_eruditus_reponses_partie_numero ON eruditus_reponses (partie, numero)",
      "CREATE INDEX idx_eruditus_reponses_joueur_question ON eruditus_reponses (joueur, question)",
      "CREATE INDEX idx_eruditus_reponses_joueur_points ON eruditus_reponses (joueur, points)",
    ],
  })))
}, (app) => {
  for (const nom of ["eruditus_reponses", "eruditus_parties", "eruditus_questions", "eruditus_joueurs"]) {
    app.delete(app.findCollectionByNameOrId(nom))
  }
})

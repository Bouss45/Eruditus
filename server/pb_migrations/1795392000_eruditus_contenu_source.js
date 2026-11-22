/// <reference path="../pb_data/types.d.ts" />

// Contenu sourcé (spec/serveur.md § 3.2, D96 – D99) : explication, source et niveau de chaque
// question, facultatifs et cachés (ils disent souvent la réponse). Ajouts seulement : les
// questions déjà en base restent telles quelles, champs vides. Les migrations déjà déployées ne
// changent jamais (D22) : tout passe par celle-ci.
const CHAMPS = ["explication", "source", "niveau"]

migrate((app) => {
  const questions = app.findCollectionByNameOrId("eruditus_questions")
  questions.fields.add(new TextField({ name: "explication", hidden: true, max: 300 }))
  questions.fields.add(new TextField({ name: "source", hidden: true, max: 120 }))
  questions.fields.add(new NumberField({ name: "niveau", hidden: true, min: 1, max: 5, onlyInt: true }))
  app.save(questions)
}, (app) => {
  const questions = app.findCollectionByNameOrId("eruditus_questions")
  for (const nom of CHAMPS) questions.fields.removeByName(nom)
  app.save(questions)
})

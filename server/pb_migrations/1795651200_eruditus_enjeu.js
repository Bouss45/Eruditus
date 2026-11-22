/// <reference path="../pb_data/types.d.ts" />

// Enjeu de difficulté (spec/serveur.md § 3.3, § 3.4, D130) : empreinte de la question au service
// (SHA-256 hexadécimal de l'énoncé, des choix et de la bonne réponse, écrite par les hooks) et
// index de la liste des parties d'un joueur. Ajouts seulement : aucune ligne d'avant n'est
// modifiée (empreinte vide), rien n'est rejoué.
const INDEX = "idx_eruditus_parties_joueur_created"

migrate((app) => {
  const reponses = app.findCollectionByNameOrId("eruditus_reponses")
  reponses.fields.add(new TextField({ name: "question_empreinte", hidden: true, max: 64 }))
  app.save(reponses)

  const parties = app.findCollectionByNameOrId("eruditus_parties")
  parties.addIndex(INDEX, false, "joueur, created", "")
  app.save(parties)
}, (app) => {
  const parties = app.findCollectionByNameOrId("eruditus_parties")
  parties.removeIndex(INDEX)
  app.save(parties)

  const reponses = app.findCollectionByNameOrId("eruditus_reponses")
  reponses.fields.removeByName("question_empreinte")
  app.save(reponses)
})

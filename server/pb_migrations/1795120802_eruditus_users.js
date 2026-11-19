/// <reference path="../pb_data/types.d.ts" />

// Collection `users` par défaut, inutilisée (spec/serveur.md § 3.5) : ses cinq règles à null,
// inscription comprise. Elle n'est pas supprimée.
migrate((app) => {
  let users
  try {
    users = app.findCollectionByNameOrId("users")
  } catch (_) {
    return // absente : rien à fermer
  }
  users.listRule = null
  users.viewRule = null
  users.createRule = null
  users.updateRule = null
  users.deleteRule = null
  app.save(users)
}, (app) => {
  let users
  try {
    users = app.findCollectionByNameOrId("users")
  } catch (_) {
    return
  }
  // Règles par défaut de PocketBase.
  users.listRule = "id = @request.auth.id"
  users.viewRule = "id = @request.auth.id"
  users.createRule = ""
  users.updateRule = "id = @request.auth.id"
  users.deleteRule = "id = @request.auth.id"
  app.save(users)
})

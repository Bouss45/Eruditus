/// <reference path="../pb_data/types.d.ts" />

// Avis (spec/serveur.md § 3.1, § 4.1, D91) : date du dernier avis transmis, cachée, et limite par
// IP de la route, juste avant la règle de préfixe /api/eruditus/ (D34). Les migrations déjà
// déployées ne changent jamais (D22) : tout passe par celle-ci.
const REGLES_AVIS = [
  { label: "POST /api/eruditus/v1/avis", audience: "", maxRequests: 5, duration: 3600 },
]
const PREFIXE_ERUDITUS = "/api/eruditus/"

function copie(r) {
  return { label: r.label, audience: r.audience, maxRequests: r.maxRequests, duration: r.duration }
}

migrate((app) => {
  const joueurs = app.findCollectionByNameOrId("eruditus_joueurs")
  joueurs.fields.add(new DateField({ name: "avis_envoye_le", hidden: true }))
  app.save(joueurs)

  const reglages = app.settings()
  const labels = REGLES_AVIS.map((r) => r.label)
  let regles = []
  let placees = false
  for (const r of reglages.rateLimits.rules) {
    if (labels.indexOf(r.label) !== -1) continue
    if (r.label === PREFIXE_ERUDITUS && !placees) {
      regles = regles.concat(REGLES_AVIS)
      placees = true
    }
    regles.push(copie(r))
  }
  if (!placees) regles = REGLES_AVIS.concat(regles)
  unmarshal({ rateLimits: { rules: regles } }, reglages)
  app.save(reglages)
}, (app) => {
  const joueurs = app.findCollectionByNameOrId("eruditus_joueurs")
  joueurs.fields.removeByName("avis_envoye_le")
  app.save(joueurs)

  const reglages = app.settings()
  const labels = REGLES_AVIS.map((r) => r.label)
  const restantes = []
  for (const r of reglages.rateLimits.rules) {
    if (labels.indexOf(r.label) === -1) restantes.push(copie(r))
  }
  unmarshal({ rateLimits: { rules: restantes } }, reglages)
  app.save(reglages)
})

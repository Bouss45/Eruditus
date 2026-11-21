/// <reference path="../pb_data/types.d.ts" />

// Paramètres (spec/serveur.md § 3.1, § 4.1, D82) : date du dernier changement de pseudo, cachée,
// et limites par IP des deux routes, juste avant la règle de préfixe /api/eruditus/ (D34). Les
// migrations déjà déployées ne changent jamais (D22) : tout passe par celle-ci.
const REGLES_PARAMETRES = [
  { label: "POST /api/eruditus/v1/comptes/pseudo", audience: "", maxRequests: 10, duration: 3600 },
  { label: "POST /api/eruditus/v1/comptes/mot-de-passe", audience: "", maxRequests: 5, duration: 600 },
]
const PREFIXE_ERUDITUS = "/api/eruditus/"

function copie(r) {
  return { label: r.label, audience: r.audience, maxRequests: r.maxRequests, duration: r.duration }
}

migrate((app) => {
  const joueurs = app.findCollectionByNameOrId("eruditus_joueurs")
  joueurs.fields.add(new DateField({ name: "pseudo_modifie_le", hidden: true }))
  app.save(joueurs)

  const reglages = app.settings()
  const labels = REGLES_PARAMETRES.map((r) => r.label)
  let regles = []
  let placees = false
  for (const r of reglages.rateLimits.rules) {
    if (labels.indexOf(r.label) !== -1) continue
    if (r.label === PREFIXE_ERUDITUS && !placees) {
      regles = regles.concat(REGLES_PARAMETRES)
      placees = true
    }
    regles.push(copie(r))
  }
  if (!placees) regles = REGLES_PARAMETRES.concat(regles)
  unmarshal({ rateLimits: { rules: regles } }, reglages)
  app.save(reglages)
}, (app) => {
  const joueurs = app.findCollectionByNameOrId("eruditus_joueurs")
  joueurs.fields.removeByName("pseudo_modifie_le")
  app.save(joueurs)

  const reglages = app.settings()
  const labels = REGLES_PARAMETRES.map((r) => r.label)
  const restantes = []
  for (const r of reglages.rateLimits.rules) {
    if (labels.indexOf(r.label) === -1) restantes.push(copie(r))
  }
  unmarshal({ rateLimits: { rules: restantes } }, reglages)
  app.save(reglages)
})

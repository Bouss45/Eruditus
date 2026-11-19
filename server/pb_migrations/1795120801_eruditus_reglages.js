/// <reference path="../pb_data/types.d.ts" />

// Réglages (spec/serveur.md § 4) : limiteur par IP (D23), règles Eruditus en tête (D34), IP réelle
// lue dans X-Forwarded-For posé par le proxy HTTPS, API batch désactivée. Rien d'autre n'est touché.
const REGLES_ERUDITUS = [
  { label: "eruditus_joueurs:auth", audience: "", maxRequests: 5, duration: 60 },
  { label: "POST /api/eruditus/v1/comptes", audience: "", maxRequests: 5, duration: 3600 },
  { label: "POST /api/eruditus/v1/comptes/recuperation", audience: "", maxRequests: 5, duration: 3600 },
  { label: "POST /api/eruditus/v1/comptes/suppression", audience: "", maxRequests: 5, duration: 600 },
  { label: "POST /api/eruditus/v1/parties", audience: "", maxRequests: 30, duration: 3600 },
  { label: "/api/eruditus/", audience: "", maxRequests: 120, duration: 60 },
]

migrate((app) => {
  const reglages = app.settings()
  const labels = REGLES_ERUDITUS.map((r) => r.label)
  // Les règles existantes (celles de PocketBase par défaut) restent, après les nôtres.
  const autres = []
  for (const r of reglages.rateLimits.rules) {
    if (labels.indexOf(r.label) === -1) {
      autres.push({ label: r.label, audience: r.audience, maxRequests: r.maxRequests, duration: r.duration })
    }
  }
  unmarshal({
    rateLimits: { enabled: true, rules: REGLES_ERUDITUS.concat(autres) },
    trustedProxy: { headers: ["X-Forwarded-For"], useLeftmostIP: false },
    batch: { enabled: false },
  }, reglages)
  app.save(reglages)
}, (app) => {
  const reglages = app.settings()
  const labels = REGLES_ERUDITUS.map((r) => r.label)
  const restantes = []
  for (const r of reglages.rateLimits.rules) {
    if (labels.indexOf(r.label) === -1) {
      restantes.push({ label: r.label, audience: r.audience, maxRequests: r.maxRequests, duration: r.duration })
    }
  }
  unmarshal({
    rateLimits: { enabled: false, rules: restantes },
    trustedProxy: { headers: [], useLeftmostIP: false },
  }, reglages)
  app.save(reglages)
})

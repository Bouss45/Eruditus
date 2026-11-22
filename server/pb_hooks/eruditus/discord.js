// Envoi au webhook Discord (avis D88, signalements D120) : l'adresse contient le secret, elle ne
// sort jamais (D89).

const config = require(`${__hooks}/eruditus/config.js`)

/**
 * Envoie au webhook, 5 s au plus ; vrai si Discord répond 2xx. Aucune erreur ne sort et rien
 * n'est écrit : l'erreur de l'envoi et la réponse contiennent ou suivent l'adresse (D89).
 */
function transmettre(adresse, contenu) {
  try {
    const reponse = $http.send({
      url: adresse,
      method: "POST",
      body: JSON.stringify(contenu),
      headers: { "Content-Type": "application/json" },
      timeout: config.ATTENTE_WEBHOOK_S,
    })
    return reponse.statusCode >= 200 && reponse.statusCode < 300
  } catch (_) {
    return false
  }
}

module.exports = { transmettre }

// Avis (spec/regles.md § 7, spec/serveur.md § 5.4, D85 – D92) : le texte passé par la règle, puis
// transmis aussitôt au webhook Discord de ERUDITUS_AVIS_WEBHOOK, sans être gardé. Seule la date du
// dernier avis transmis est écrite.

const http = require(`${__hooks}/eruditus/http.js`)
const config = require(`${__hooks}/eruditus/config.js`)

// Règle du texte (D86), la même dans l'app (ReglesAvis), confrontée par ReglesAvisTest et par les
// cas de server/tests/cas-avis.json.
const LONGUEUR_MAX = 1000
const CONTROLES = /[\u0000-\u0009\u000B-\u001F\u007F-\u009F]/g
// Blancs retirés au début et à la fin : ceux de trim() en JavaScript, hors contrôles déjà retirés.
const BLANCS = "[ \\n\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]"
const EST_BLANC = new RegExp(`^${BLANCS}$`)

// Versions informatives (D87) : gardées seulement si elles suivent leur motif, sinon « inconnue ».
const MOTIF_VERSION_APP = /^[0-9]{1,4}(\.[0-9]{1,4}){1,3}(-[a-z]{1,16})?$/
const MOTIF_ANDROID = /^[0-9]{1,3}(\.[0-9]{1,3}){0,2}$/
const INCONNUE = "inconnue"

/**
 * `\r\n`, puis `\r`, deviennent `\n` ; autres contrôles retirés ; blancs retirés autour. En une
 * passe par étape, jamais d'expression qui reviendrait en arrière sur un long texte.
 */
function normaliser(saisie) {
  const texte = saisie.replace(/\r\n/g, "\n").replace(/\r/g, "\n").replace(CONTROLES, "")
  let debut = 0
  let fin = texte.length
  while (debut < fin && EST_BLANC.test(texte[debut])) debut++
  while (fin > debut && EST_BLANC.test(texte[fin - 1])) fin--
  return texte.substring(debut, fin)
}

function version(corps, nom, motif) {
  const valeur = corps[nom]
  return typeof valeur === "string" && motif.test(valeur) ? valeur : INCONNUE
}

/** Le message du webhook (D88) : rien d'autre que ces champs. */
function message(pseudo, texte, versionApp, android) {
  return {
    username: "Eruditus",
    allowed_mentions: { parse: [] },
    embeds: [{
      title: `Avis de ${pseudo}`,
      description: texte,
      footer: { text: `Eruditus ${versionApp} · Android ${android}` },
      timestamp: new Date().toISOString(),
    }],
  }
}

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

/** Rend le créneau après un échec : la date d'avant, seulement si personne ne l'a changée. */
function rendre(id, reservation) {
  try {
    http.transaction((app) => {
      const joueur = app.findRecordById("eruditus_joueurs", id)
      if (joueur.getDateTime("avis_envoye_le").string() !== reservation.posee) return
      joueur.set("avis_envoye_le", reservation.avant)
      app.save(joueur)
    })
  } catch (_) {
    // Compte supprimé pendant l'envoi : plus rien à rendre.
  }
}

/** POST /avis : { texte, versionApp, android } → {} ; le pseudo vient du jeton (D87). */
function envoyer(e) {
  return http.traiter(e, () => {
    const auth = http.joueur(e)
    const corps = http.corps(e)
    const texte = normaliser(http.texte(corps, "texte"))
    const longueur = http.nombreDeCaracteres(texte)
    if (longueur < 1 || longueur > LONGUEUR_MAX) throw http.erreur("avis_invalide")
    const versionApp = version(corps, "versionApp", MOTIF_VERSION_APP)
    const android = version(corps, "android", MOTIF_ANDROID)
    const adresse = config.webhookAvis()
    if (!adresse) throw http.erreur("avis_indisponible")

    // Le créneau est réservé avant l'envoi, sans tenir la transaction pendant l'attente (D90) :
    // deux avis du même compte en même temps, un seul part.
    const reservation = http.transaction((app) => {
      const joueur = app.findRecordById("eruditus_joueurs", auth.id)
      const dernier = joueur.getDateTime("avis_envoye_le")
      if (!dernier.isZero() && Date.now() - dernier.time().unixMilli() < config.delaiAvisMs()) {
        throw http.erreur("avis_trop_recent")
      }
      joueur.set("avis_envoye_le", new DateTime())
      app.save(joueur)
      return {
        pseudo: joueur.getString("pseudo"),
        avant: dernier.isZero() ? "" : dernier.string(),
        posee: joueur.getDateTime("avis_envoye_le").string(),
      }
    })

    if (!transmettre(adresse, message(reservation.pseudo, texte, versionApp, android))) {
      rendre(auth.id, reservation)
      throw http.erreur("avis_indisponible")
    }
    return e.json(200, {})
  })
}

module.exports = { envoyer }

// Réglages d'Eruditus. Délai, marge, inactivité, délais du pseudo et des avis : variables
// d'environnement, absentes sur le VPS, raccourcies par les tests serveur (D35, D80, D90).
// L'adresse du webhook des avis : variable du conteneur, réglée hors dépôt (D88).

function lireEntier(nom, defaut, minimum) {
  const brut = $os.getenv(nom)
  if (!brut || !/^[0-9]{1,9}$/.test(brut)) return defaut
  const valeur = parseInt(brut, 10)
  return valeur >= minimum ? valeur : defaut
}

module.exports = {
  // versionCode minimal de l'app (D21) ; le remonter est un dernier recours (D22).
  VERSION_MINIMALE: 1000,

  QUESTIONS_PAR_PARTIE: 5,
  CORPS_MAX_OCTETS: 16 * 1024,

  delaiMs: () => lireEntier("ERUDITUS_DELAI_MS", 20000, 1),
  margeMs: () => lireEntier("ERUDITUS_MARGE_MS", 3000, 0),
  inactiviteMs: () => lireEntier("ERUDITUS_INACTIVITE_MS", 1800000, 1),
  // Délai entre deux changements de pseudo d'un même compte : 24 heures (D80).
  delaiPseudoMs: () => lireEntier("ERUDITUS_DELAI_PSEUDO_MS", 86400000, 0),
  // Délai entre deux avis transmis d'un même compte : 10 minutes (D90).
  delaiAvisMs: () => lireEntier("ERUDITUS_DELAI_AVIS_MS", 600000, 0),

  // Adresse du webhook Discord des avis, vide si absente : jamais écrite ni renvoyée (D89).
  webhookAvis: () => String($os.getenv("ERUDITUS_AVIS_WEBHOOK") || "").trim(),
  // Attente de Discord, en secondes ; l'app attend 10 s (D52, D89).
  ATTENTE_WEBHOOK_S: 5,

  // Noms réservés, quelle que soit la casse, plus tout pseudo qui commence par « eruditus »
  // (spec/regles.md § 1.1). L'allonger ne touche pas les comptes existants.
  NOMS_RESERVES: [
    "admin", "administrateur", "administrator", "moderateur", "moderator", "modo", "root",
    "superuser", "system", "systeme", "support", "staff", "officiel", "official", "null",
    "undefined", "anonyme", "anonymous", "joueur", "invite", "guest", "test",
  ],
}

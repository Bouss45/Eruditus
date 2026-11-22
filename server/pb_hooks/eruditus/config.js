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

  // Elo du solo (spec/serveur.md § 5.5, D111) : constantes du VPS, sans variable
  // d'environnement ; les tests serveur les gardent telles quelles.
  ELO: {
    DEPART: 1000,
    PLANCHER: 100,
    // Difficulté de départ selon le niveau (indice) ; 0 = sans niveau, ne compte pas.
    DIFFICULTES: [0, 800, 1200, 1600, 2000, 2400],
    // Chances minimales du QCM à 4 choix.
    CHANCE: 0.25,
    // K du joueur : 40 sous 30 réponses comptées (provisoire), 24 sous 200, 12 ensuite.
    K_PROVISOIRE: 40,
    K_CLASSE: 24,
    K_ANCIEN: 12,
    // Seuil du statut provisoire, qui est aussi celui du classement par Elo (D108).
    SEUIL_CLASSE: 30,
    SEUIL_ANCIEN: 200,
    // Questions : K, réponses données avant le premier mouvement, écart maximal au niveau.
    K_QUESTION: 8,
    REPONSES_AVANT_MOUVEMENT: 30,
    ECART_MAX: 300,
  },

  // Signalement d'une question (D119, D121) : comptes distincts qui la mettent en observation,
  // questions servables à garder, signalements par compte et par heure, commentaire.
  SEUIL_OBSERVATION: 3,
  PLANCHER_SERVABLES: 50,
  SIGNALEMENTS_PAR_HEURE: 5,
  COMMENTAIRE_MAX: 200,

  // Noms réservés, quelle que soit la casse, plus tout pseudo qui commence par « eruditus »
  // (spec/regles.md § 1.1). L'allonger ne touche pas les comptes existants.
  NOMS_RESERVES: [
    "admin", "administrateur", "administrator", "moderateur", "moderator", "modo", "root",
    "superuser", "system", "systeme", "support", "staff", "officiel", "official", "null",
    "undefined", "anonyme", "anonymous", "joueur", "invite", "guest", "test",
  ],
}

// Comptes (spec/regles.md § 1) : création, récupération, déconnexion, suppression, changement de
// pseudo et de mot de passe (Paramètres), et /info.

const http = require(`${__hooks}/eruditus/http.js`)
const config = require(`${__hooks}/eruditus/config.js`)

const MOTIF_PSEUDO = /^[A-Za-z0-9_]{3,20}$/
// Alphabet de Crockford : 0–9 et A–Z sans I, L, O, U (32 symboles, 16 caractères = 80 bits).
const ALPHABET_CODE = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
const MOTIF_CODE = /^[0-9ABCDEFGHJKMNPQRSTVWXYZ]{16}$/
// Comparée quand le pseudo n'existe pas, pour que la réponse prenne le même chemin (§ 6.1).
const EMPREINTE_FACTICE = "0000000000000000000000000000000000000000000000000000000000000000"

function info(e) {
  return e.json(200, { versionMinimale: config.VERSION_MINIMALE })
}

function pseudoReserve(pseudo) {
  const minuscule = pseudo.toLowerCase()
  return config.NOMS_RESERVES.indexOf(minuscule) !== -1 || minuscule.indexOf("eruditus") === 0
}

/** 8 à 71 caractères et 72 octets au plus en UTF-8, la limite de bcrypt (D10). */
function motDePasseValide(motDePasse) {
  const caracteres = http.nombreDeCaracteres(motDePasse)
  return caracteres >= 8 && caracteres <= 71 && toBytes(motDePasse).length <= 72
}

/**
 * Saisie tolérante (spec/regles.md § 1.6) : casse, espaces et tirets ignorés, O lu 0, I et L
 * lus 1. Renvoie les 16 caractères normalisés, ou null si ce n'est pas un code.
 */
function normaliserCode(saisie) {
  if (saisie.length > 64) return null
  const code = saisie.toUpperCase().replace(/[\s-]/g, "").replace(/O/g, "0").replace(/[IL]/g, "1")
  return MOTIF_CODE.test(code) ? code : null
}

function nouveauCode() {
  return $security.randomStringWithAlphabet(16, ALPHABET_CODE)
}

function afficherCode(code) {
  return code.match(/.{4}/g).join("-")
}

/** Joueur dont le pseudo correspond sans tenir compte de la casse, ou null. */
function joueurParPseudo(app, pseudo) {
  const lignes = arrayOf(new DynamicModel({ id: "" }))
  app.db()
    .newQuery("SELECT id FROM eruditus_joueurs WHERE pseudo = {:pseudo} COLLATE NOCASE LIMIT 1")
    .bind({ pseudo: pseudo })
    .all(lignes)
  return lignes.length === 0 ? null : app.findRecordById("eruditus_joueurs", lignes[0].id)
}

/**
 * Les règles du pseudo (D9), les mêmes à la création et au changement (D79) : motif, noms
 * réservés, déjà pris casse ignorée. [idSoi] : le joueur qui change de pseudo, dont le pseudo
 * actuel ne compte pas comme pris (changer seulement la casse est permis) ; null à la création.
 */
function verifierPseudo(app, pseudo, idSoi) {
  if (!MOTIF_PSEUDO.test(pseudo)) throw http.erreur("pseudo_invalide")
  if (pseudoReserve(pseudo)) throw http.erreur("pseudo_reserve")
  const titulaire = joueurParPseudo(app, pseudo)
  if (titulaire && titulaire.id !== idSoi) throw http.erreur("pseudo_pris")
}

function sortieJoueur(joueur) {
  return { id: joueur.id, pseudo: joueur.getString("pseudo") }
}

/** POST /comptes : { pseudo, motDePasse } → { token, joueur, codeRecuperation }. */
function creer(e) {
  return http.traiter(e, () => {
    const corps = http.corps(e)
    const pseudo = http.texte(corps, "pseudo")
    const motDePasse = http.texte(corps, "motDePasse")
    const code = nouveauCode()
    const joueur = http.transaction((app) => {
      verifierPseudo(app, pseudo, null)
      if (!motDePasseValide(motDePasse)) throw http.erreur("mot_de_passe_invalide")
      const nouveau = new Record(app.findCollectionByNameOrId("eruditus_joueurs"))
      nouveau.set("pseudo", pseudo)
      nouveau.setPassword(motDePasse)
      nouveau.set("code_recuperation", $security.sha256(code))
      nouveau.set("elo", config.ELO.DEPART) // D110
      nouveau.set("elo_reponses", 0)
      app.save(nouveau)
      return nouveau
    })
    return e.json(200, {
      token: joueur.newAuthToken(),
      joueur: sortieJoueur(joueur),
      codeRecuperation: afficherCode(code),
    })
  })
}

/**
 * POST /comptes/recuperation : { pseudo, code, nouveauMotDePasse }. Nouveau mot de passe vérifié
 * d'abord ; ensuite une seule erreur, que le pseudo existe ou non (§ 6.1).
 */
function recuperer(e) {
  return http.traiter(e, () => {
    const corps = http.corps(e)
    const pseudo = http.texte(corps, "pseudo")
    const saisie = http.texte(corps, "code")
    const motDePasse = http.texte(corps, "nouveauMotDePasse")
    if (!motDePasseValide(motDePasse)) throw http.erreur("mot_de_passe_invalide")
    const code = normaliserCode(saisie)
    const resultat = http.transaction((app) => {
      const joueur = MOTIF_PSEUDO.test(pseudo) ? joueurParPseudo(app, pseudo) : null
      const attendue = joueur ? joueur.getString("code_recuperation") : EMPREINTE_FACTICE
      const recue = $security.sha256(code || saisie)
      const egal = $security.equal(recue, attendue)
      if (!joueur || !code || !egal) throw http.erreur("code_incorrect")
      const suivant = nouveauCode()
      joueur.setPassword(motDePasse)
      joueur.refreshTokenKey()
      joueur.set("code_recuperation", $security.sha256(suivant))
      app.save(joueur)
      return { joueur: joueur, code: suivant }
    })
    return e.json(200, {
      token: resultat.joueur.newAuthToken(),
      joueur: sortieJoueur(resultat.joueur),
      codeRecuperation: afficherCode(resultat.code),
    })
  })
}

/** POST /comptes/deconnexion : nouvelle clé des jetons, tous les jetons du compte tombent. */
function deconnecter(e) {
  return http.traiter(e, () => {
    const auth = http.joueur(e)
    http.transaction((app) => {
      const joueur = app.findRecordById("eruditus_joueurs", auth.id)
      joueur.refreshTokenKey()
      app.save(joueur)
    })
    return e.noContent(204)
  })
}

/** POST /comptes/suppression : { motDePasse } ; le compte, ses parties et ses réponses. */
function supprimer(e) {
  return http.traiter(e, () => {
    const auth = http.joueur(e)
    const corps = http.corps(e)
    const motDePasse = http.texte(corps, "motDePasse")
    http.transaction((app) => {
      const joueur = app.findRecordById("eruditus_joueurs", auth.id)
      if (!joueur.validatePassword(motDePasse)) throw http.erreur("mot_de_passe_incorrect")
      app.delete(joueur) // parties et réponses suivent (suppression en cascade)
    })
    return e.noContent(204)
  })
}

/**
 * POST /comptes/pseudo : { pseudo } → { joueur }, sans mot de passe (D79). Pseudo identique :
 * rien ne change. Sinon les règles de la création, puis une fois par délai (D80) ; l'ancien
 * pseudo est libre aussitôt, jetons, score et code inchangés.
 */
function changerPseudo(e) {
  return http.traiter(e, () => {
    const auth = http.joueur(e)
    const corps = http.corps(e)
    const pseudo = http.texte(corps, "pseudo")
    const joueur = http.transaction((app) => {
      const joueur = app.findRecordById("eruditus_joueurs", auth.id)
      if (joueur.getString("pseudo") === pseudo) return joueur
      verifierPseudo(app, pseudo, joueur.id)
      const dernier = joueur.getDateTime("pseudo_modifie_le")
      if (!dernier.isZero() && Date.now() - dernier.time().unixMilli() < config.delaiPseudoMs()) {
        throw http.erreur("pseudo_trop_recent")
      }
      joueur.set("pseudo", pseudo)
      joueur.set("pseudo_modifie_le", new DateTime())
      app.save(joueur)
      return joueur
    })
    return e.json(200, { joueur: sortieJoueur(joueur) })
  })
}

/**
 * POST /comptes/mot-de-passe : { motDePasse, nouveauMotDePasse } → { token, joueur } (D81). Le
 * nouveau d'abord, puis l'actuel ; nouvelle clé des jetons (toutes les sessions tombent) et un
 * jeton neuf pour ce téléphone. Code de récupération inchangé.
 */
function changerMotDePasse(e) {
  return http.traiter(e, () => {
    const auth = http.joueur(e)
    const corps = http.corps(e)
    const actuel = http.texte(corps, "motDePasse")
    const nouveau = http.texte(corps, "nouveauMotDePasse")
    if (!motDePasseValide(nouveau)) throw http.erreur("mot_de_passe_invalide")
    const joueur = http.transaction((app) => {
      const joueur = app.findRecordById("eruditus_joueurs", auth.id)
      if (!joueur.validatePassword(actuel)) throw http.erreur("mot_de_passe_incorrect")
      joueur.setPassword(nouveau)
      joueur.refreshTokenKey()
      app.save(joueur)
      return joueur
    })
    return e.json(200, { token: joueur.newAuthToken(), joueur: sortieJoueur(joueur) })
  })
}

module.exports = { info, creer, recuperer, deconnecter, supprimer, changerPseudo, changerMotDePasse }

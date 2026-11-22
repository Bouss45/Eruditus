/// <reference path="../pb_data/types.d.ts" />

// Routes d'Eruditus (spec/serveur.md § 5) et commande d'import des questions (§ 8).
// Chaque gestionnaire tourne isolé : le code commun vit dans eruditus/, chargé par require().

routerAdd("GET", "/api/eruditus/v1/info", (e) => require(`${__hooks}/eruditus/comptes.js`).info(e))

routerAdd("POST", "/api/eruditus/v1/comptes", (e) => require(`${__hooks}/eruditus/comptes.js`).creer(e))
routerAdd("POST", "/api/eruditus/v1/comptes/recuperation", (e) => require(`${__hooks}/eruditus/comptes.js`).recuperer(e))
routerAdd("POST", "/api/eruditus/v1/comptes/deconnexion", (e) => require(`${__hooks}/eruditus/comptes.js`).deconnecter(e))
routerAdd("POST", "/api/eruditus/v1/comptes/suppression", (e) => require(`${__hooks}/eruditus/comptes.js`).supprimer(e))
routerAdd("POST", "/api/eruditus/v1/comptes/pseudo", (e) => require(`${__hooks}/eruditus/comptes.js`).changerPseudo(e))
routerAdd("POST", "/api/eruditus/v1/comptes/mot-de-passe", (e) => require(`${__hooks}/eruditus/comptes.js`).changerMotDePasse(e))

routerAdd("GET", "/api/eruditus/v1/moi", (e) => require(`${__hooks}/eruditus/classement.js`).moi(e))
routerAdd("GET", "/api/eruditus/v1/classement", (e) => require(`${__hooks}/eruditus/classement.js`).classement(e))
routerAdd("GET", "/api/eruditus/v1/classement/elo", (e) => require(`${__hooks}/eruditus/elo.js`).classement(e))

routerAdd("POST", "/api/eruditus/v1/parties", (e) => require(`${__hooks}/eruditus/parties.js`).creer(e))
routerAdd("POST", "/api/eruditus/v1/parties/{id}/question", (e) => require(`${__hooks}/eruditus/parties.js`).question(e))
routerAdd("POST", "/api/eruditus/v1/parties/{id}/reponse", (e) => require(`${__hooks}/eruditus/parties.js`).reponse(e))

routerAdd("POST", "/api/eruditus/v1/avis", (e) => require(`${__hooks}/eruditus/avis.js`).envoyer(e))

// Connexion et renouvellement (API standard) : ni e-mail, ni visibilité, ni vérification dans le
// record renvoyé (D33, T7).
onRecordAuthRequest((e) => {
  e.record.hide("email", "emailVisibility", "verified")
  e.next()
}, "eruditus_joueurs")

// `pocketbase eruditus-questions <fichier>` : import de la banque, lancé par server/questions.sh
// sur la base du service en marche (les commandes n'appliquent pas les migrations).
$app.rootCmd.addCommand(new Command({
  use: "eruditus-questions <fichier>",
  short: "Importe ou met à jour la banque de questions d'Eruditus",
  run: (cmd, args) => {
    require(`${__hooks}/eruditus/questions.js`).commande(args)
  },
}))

// Commande `eruditus-questions <fichier>` (spec/serveur.md § 8) : valide tout le fichier avant
// d'écrire quoi que ce soit, puis crée ou met à jour chaque question par sa `ref`. Une entrée
// invalide annule tout le fichier (D99). Ne supprime jamais ; `active: false` retire une question
// du jeu. Une entrée décrit toute la question : explication, source ou niveau absent est vidé.

const http = require(`${__hooks}/eruditus/http.js`)
const config = require(`${__hooks}/eruditus/config.js`)

const CHAMPS = ["ref", "enonce", "choix", "bonneReponse", "active", "explication", "source", "niveau"]
const MOTIF_REF = /^[A-Za-z0-9_-]{1,50}$/
const CONTROLE = /[\u0000-\u001f\u007f-\u009f]/

/** Erreur d'un texte facultatif (explication, source, D96), ou null s'il est absent ou valide. */
function erreurTexte(q, nom, max) {
  if (!(nom in q)) return null
  const t = q[nom]
  if (typeof t !== "string") return `${nom} : un texte`
  if (CONTROLE.test(t)) return `${nom} : sans retour à la ligne ni caractère de contrôle`
  if (t.trim() !== t) return `${nom} : sans blanc au début ni à la fin`
  const n = http.nombreDeCaracteres(t)
  if (n < 1 || n > max) return `${nom} : 1 à ${max} caractères`
  return null
}

/** Message d'erreur de l'entrée, ou null si elle est valide. */
function erreurEntree(q) {
  if (q === null || typeof q !== "object" || Array.isArray(q)) return "ce n'est pas un objet"
  for (const cle of Object.keys(q)) {
    if (CHAMPS.indexOf(cle) === -1) return `champ inconnu « ${cle} »`
  }
  if (typeof q.ref !== "string" || !MOTIF_REF.test(q.ref)) return "ref : 1 à 50 caractères parmi A–Z a–z 0–9 _ -"
  if (typeof q.enonce !== "string") return "enonce : un texte"
  const longueur = http.nombreDeCaracteres(q.enonce)
  if (http.nombreDeCaracteres(q.enonce.trim()) < 10 || longueur > 300) return "enonce : 10 à 300 caractères"
  if (!Array.isArray(q.choix) || q.choix.length !== 4) return "choix : un tableau de 4 textes"
  const vus = []
  for (const choix of q.choix) {
    if (typeof choix !== "string" || choix.trim() === "") return "choix : 4 textes non vides"
    if (http.nombreDeCaracteres(choix) > 120) return "choix : 120 caractères au plus"
    const cle = choix.trim().toLowerCase()
    if (vus.indexOf(cle) !== -1) return "choix : 4 textes différents"
    vus.push(cle)
  }
  if (typeof q.bonneReponse !== "number" || !Number.isInteger(q.bonneReponse) || q.bonneReponse < 0 || q.bonneReponse > 3) {
    return "bonneReponse : un entier de 0 à 3"
  }
  if (typeof q.active !== "boolean") return "active : true ou false"
  const texte = erreurTexte(q, "explication", 300) || erreurTexte(q, "source", 120)
  if (texte) return texte
  if ("niveau" in q && !(typeof q.niveau === "number" && Number.isInteger(q.niveau) && q.niveau >= 1 && q.niveau <= 5)) {
    return "niveau : un entier de 1 à 5"
  }
  return null
}

function memesChoix(a, b) {
  return a.length === b.length && a.every((c, i) => c === b[i])
}

function importer(chemin) {
  let liste
  try {
    liste = JSON.parse(toString($os.readFile(chemin)))
  } catch (err) {
    return { erreurs: [`fichier illisible ou JSON invalide : ${chemin}`] }
  }
  if (!Array.isArray(liste)) return { erreurs: ["le fichier doit contenir un tableau de questions"] }

  const erreurs = []
  const refs = []
  liste.forEach((q, i) => {
    const probleme = erreurEntree(q)
    const nom = q && typeof q.ref === "string" ? ` (${q.ref})` : ""
    if (probleme) erreurs.push(`entrée ${i + 1}${nom} : ${probleme}`)
    else if (refs.indexOf(q.ref) !== -1) erreurs.push(`entrée ${i + 1}${nom} : ref en double`)
    else refs.push(q.ref)
  })
  if (erreurs.length > 0) return { erreurs: erreurs }

  const bilan = { creees: 0, misesAJour: 0, inchangees: 0 }
  $app.runInTransaction((app) => {
    const collection = app.findCollectionByNameOrId("eruditus_questions")
    for (const q of liste) {
      let existante = null
      try {
        existante = app.findFirstRecordByFilter("eruditus_questions", "ref = {:ref}", { ref: q.ref })
      } catch (_) {
        existante = null
      }
      const bonne = q.bonneReponse + 1 // 1 – 4 en base
      const explication = q.explication || "" // absent : vidé (D99)
      const source = q.source || ""
      const niveau = q.niveau || 0
      if (existante
        && existante.getString("enonce") === q.enonce
        && memesChoix(JSON.parse(existante.getString("choix")), q.choix)
        && existante.getInt("bonne_reponse") === bonne
        && existante.getBool("active") === q.active
        && existante.getString("explication") === explication
        && existante.getString("source") === source
        && existante.getInt("niveau") === niveau) {
        bilan.inchangees++
        continue
      }
      // Difficulté de l'Elo (D107) : remise à la valeur du niveau seulement s'il change vraiment.
      const niveauChange = !existante || existante.getInt("niveau") !== niveau
      const question = existante || new Record(collection)
      question.set("ref", q.ref)
      question.set("enonce", q.enonce)
      question.set("choix", q.choix)
      question.set("bonne_reponse", bonne)
      question.set("active", q.active)
      question.set("explication", explication)
      question.set("source", source)
      question.set("niveau", niveau)
      if (niveauChange) {
        question.set("difficulte", config.ELO.DIFFICULTES[niveau])
        question.set("difficulte_reponses", 0)
      }
      app.save(question)
      if (existante) bilan.misesAJour++
      else bilan.creees++
    }
  })
  return { bilan: bilan }
}

/** Point d'entrée de la commande ; code de sortie 1 au moindre problème. */
function commande(args) {
  if (args.length !== 1) {
    console.error("Usage : pocketbase eruditus-questions <fichier.json>")
    $os.exit(1)
  }
  let resultat
  try {
    resultat = importer(args[0])
  } catch (err) {
    console.error(`Import interrompu, rien n'est importé : ${err}`)
    $os.exit(1)
  }
  if (resultat.erreurs) {
    resultat.erreurs.forEach((m) => console.error(m))
    console.error(`${resultat.erreurs.length} problème(s), rien n'est importé.`)
    $os.exit(1)
  }
  const b = resultat.bilan
  console.log(`Questions : ${b.creees} créées, ${b.misesAJour} mises à jour, ${b.inchangees} inchangées.`)
}

module.exports = { commande }

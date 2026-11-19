// Commande `eruditus-questions <fichier>` (spec/serveur.md § 8) : valide tout le fichier avant
// d'écrire quoi que ce soit, puis crée ou met à jour chaque question par sa `ref`. Ne supprime
// jamais ; `active: false` retire une question du jeu.

const http = require(`${__hooks}/eruditus/http.js`)

const CHAMPS = ["ref", "enonce", "choix", "bonneReponse", "active"]
const MOTIF_REF = /^[A-Za-z0-9_-]{1,50}$/

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
      if (existante
        && existante.getString("enonce") === q.enonce
        && memesChoix(JSON.parse(existante.getString("choix")), q.choix)
        && existante.getInt("bonne_reponse") === bonne
        && existante.getBool("active") === q.active) {
        bilan.inchangees++
        continue
      }
      const question = existante || new Record(collection)
      question.set("ref", q.ref)
      question.set("enonce", q.enonce)
      question.set("choix", q.choix)
      question.set("bonne_reponse", bonne)
      question.set("active", q.active)
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

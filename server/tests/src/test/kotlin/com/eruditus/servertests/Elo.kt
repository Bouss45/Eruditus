package com.eruditus.servertests

import java.nio.file.Files
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * La formule de l'Elo écrite d'après la spec (spec/regles.md § 8), indépendamment des hooks : les
 * tests comparent chaque pas du serveur à elle. Constantes de D111, recopiées ici exprès : si
 * `config.js` dérive, les tests le voient.
 */
object Elo {
    const val DEPART = 1000
    const val PLANCHER = 100
    const val SEUIL = 30
    val DIFFICULTES = listOf(0, 800, 1200, 1600, 2000, 2400)

    fun attendu(p: Int, q: Int): Double = 0.25 + 0.75 / (1 + 10.0.pow((q - p) / 400.0))

    fun k(n: Int): Int = when {
        n < 30 -> 40
        n < 200 -> 24
        else -> 12
    }

    /** À l'entier le plus proche, ,5 vers le haut (D103). */
    fun arrondi(x: Double): Int = floor(x + 0.5).toInt()

    fun apres(p: Int, k: Int, s: Int, e: Double): Int = max(PLANCHER, arrondi(p + k * (s - e)))

    /** Difficulté après une réponse donnée par un joueur classé, bornée à ± 300 (D107). */
    fun difficulte(q: Int, niveau: Int, s: Int, e: Double): Int {
        val base = DIFFICULTES[niveau]
        return arrondi(q - 8 * (s - e)).coerceIn(base - 300, base + 300)
    }
}

/** Importe ces questions (format du fichier de la banque) ; l'import doit réussir. */
fun Serveur.importerListe(questions: List<JsonObject>) {
    val fichier = Files.createTempFile(dossier, "banque", ".json")
    Files.writeString(fichier, buildJsonArray { questions.forEach { add(it) } }.toString())
    val r = importer(fichier)
    assertEquals(r.sortie, 0, r.code)
}

/** Un enregistrement, lu par le superutilisateur (champs cachés compris). */
fun Serveur.fiche(collection: String, id: String): JsonObject {
    val r = su.get("/api/collections/$collection/records/$id")
    assertEquals("Lecture de $collection/$id : $r", 200, r.status)
    return r.obj
}

/** Règle des champs par le superutilisateur, comme le permet D111. */
fun Serveur.regler(collection: String, id: String, vararg champs: Pair<String, Any>) {
    val corps = JsonObject(champs.associate { (cle, valeur) ->
        cle to when (valeur) {
            is Number -> JsonPrimitive(valeur)
            is Boolean -> JsonPrimitive(valeur)
            else -> JsonPrimitive(valeur.toString())
        }
    })
    val r = su.patch("/api/collections/$collection/records/$id", corps)
    assertEquals("Réglage de $collection/$id : $r", 200, r.status)
}

fun Serveur.reglerJoueur(compte: Compte, elo: Int, reponses: Int) =
    regler("eruditus_joueurs", compte.id, "elo" to elo, "elo_reponses" to reponses)

fun Serveur.joueurBase(compte: Compte): JsonObject = fiche("eruditus_joueurs", compte.id)

/** Questions en base, par énoncé. */
fun Serveur.questionsParEnonce(): Map<String, JsonObject> = lister("eruditus_questions").associateBy { it.str("enonce") }

fun Serveur.questionParEnonce(enonce: String): JsonObject =
    questionsParEnonce()[enonce] ?: throw AssertionError("Question absente : $enonce")

/** Les questions actives reprennent la difficulté de leur niveau et un compteur à 0. */
fun Serveur.remettreQuestions() {
    for (q in lister("eruditus_questions")) {
        regler("eruditus_questions", q.str("id"), "difficulte" to Elo.DIFFICULTES[q.int("niveau")], "difficulte_reponses" to 0)
    }
}

fun Serveur.lignes(partie: String): List<JsonObject> = lister("eruditus_reponses", "partie = '$partie'", "numero")

fun Serveur.ligne(partie: String, numero: Int): JsonObject = lignes(partie).single { it.int("numero") == numero }

/**
 * Toutes les lignes du joueur, dans l'ordre où les questions lui ont été servies (`created` :
 * `servie_le` peut avoir été reculé par [vieillir]).
 */
fun Serveur.lignesDuJoueur(compte: Compte): List<JsonObject> =
    lister("eruditus_reponses", "joueur = '${compte.id}'", "created,numero")

/** Fait passer une question servie hors délai : son heure de service recule d'une minute. */
fun Serveur.vieillir(ligne: JsonObject) {
    val avant = java.time.Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
    regler("eruditus_reponses", ligne.str("id"), "servie_le" to avant.toString())
}

/** L'objet `elo` de `/moi`. */
fun Compte.elo(): JsonObject {
    val r = moi()
    assertEquals("GET /moi : $r", 200, r.status)
    return r.obj.objet("elo")
}

/**
 * Rejoue dans l'ordre toutes les lignes du joueur contre la formule (D106) : chaque ligne comptée
 * part de l'Elo laissé par la précédente, avec le K de son compteur ; une ligne non comptée n'a
 * aucun champ Elo et ne change rien. Comptée = première fois que la question est servie au joueur
 * et question à niveau. [depart] et [reponses] : l'état du joueur avant sa première ligne ;
 * [avantElo] : ses lignes d'avant la migration, vues mais jamais comptées (D110). Vérifie enfin
 * l'Elo et le compteur en base et dans `/moi`. Renvoie l'Elo final.
 */
fun Serveur.verifierChaine(compte: Compte, depart: Int = Elo.DEPART, reponses: Int = 0, avantElo: Set<String> = emptySet()): Int {
    var p = depart
    var n = reponses
    val vues = mutableSetOf<String>()
    val niveaux = lister("eruditus_questions").associate { it.str("id") to it.int("niveau") }
    for (l in lignesDuJoueur(compte)) {
        val question = l.str("question")
        val premiere = vues.add(question)
        val comptee = premiere && l.str("id") !in avantElo && niveaux.getValue(question) > 0
        if (!comptee) {
            for (champ in listOf("elo_avant", "elo_apres", "elo_difficulte", "elo_k")) {
                assertEquals("$champ d'une ligne non comptée : $l", 0, l.int(champ))
            }
            continue
        }
        assertEquals("elo_avant : $l", p, l.int("elo_avant"))
        assertEquals("elo_k : $l", Elo.k(n), l.int("elo_k"))
        val q = l.int("elo_difficulte")
        assertTrue("elo_difficulte : $l", q > 0)
        val s = if (l.bool("correcte")) 1 else 0
        p = Elo.apres(p, Elo.k(n), s, Elo.attendu(l.int("elo_avant"), q))
        assertEquals("elo_apres : $l", p, l.int("elo_apres"))
        n++
    }
    val base = joueurBase(compte)
    assertEquals("elo en base", p, base.int("elo"))
    assertEquals("elo_reponses en base", n, base.int("elo_reponses"))
    val elo = compte.elo()
    assertEquals(p, elo.int("valeur"))
    assertEquals(n < Elo.SEUIL, elo.bool("provisoire"))
    assertEquals(max(0, Elo.SEUIL - n), elo.int("restantes"))
    return p
}

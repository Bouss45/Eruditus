package com.eruditus.servertests

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Empreinte de la question au service (D130, T72) : SHA-256 de l'énoncé, des choix et de la bonne
 * réponse, écrite à chaque service. L'explication, la source, le niveau et la difficulté n'y
 * entrent pas. Elle se prouve par le service : un compte neuf joue la banque, la ligne de la
 * question visée garde son empreinte.
 */
class EmpreinteTest {
    private val base: JsonObject = Banque.questions[0]
    private val ref = base.str("ref")

    private fun importer(question: JsonObject) {
        serveur.importerListe(listOf(question) + Banque.questions.subList(1, 5))
    }

    private fun avec(vararg changements: Pair<String, Any>): JsonObject = JsonObject(
        base + changements.associate { (cle, valeur) ->
            cle to when (valeur) {
                is JsonObject, is JsonArray -> valeur
                is Number -> JsonPrimitive(valeur)
                else -> JsonPrimitive(valeur.toString())
            }
        },
    )

    /** L'empreinte qu'un compte neuf obtient pour la question visée : tout est servi, comptée ou non. */
    private fun empreinteAuService(): String {
        val compte = Compte.creer(serveur)
        compte.jouer { _, _ -> null }
        val id = serveur.lister("eruditus_questions", "ref = '$ref'").single().str("id")
        return serveur.lignesDuJoueur(compte).single { it.str("question") == id }.str("question_empreinte")
    }

    @Test
    fun `T72 l'empreinte suit l'enonce, les choix et la bonne reponse, rien d'autre`() {
        importer(base)
        val q = serveur.lister("eruditus_questions", "ref = '$ref'").single()
        val e0 = empreinteAuService()
        // La formule de la spec : énoncé, choix (JSON), bonne réponse (1 – 4 en base), séparés par \n.
        val attendue = Binaire.sha256(q.str("enonce") + "\n" + q.tableau("choix").toString() + "\n" + q.int("bonne_reponse"))
        assertEquals(attendue, e0)
        assertEquals(64, e0.length)

        val choix = base.tableau("choix").map { (it as JsonPrimitive).content }
        for ((nom, variante) in listOf(
            "énoncé" to avec("enonce" to base.str("enonce") + " (reformulé)"),
            "un choix" to avec("choix" to buildJsonArray { (choix.take(1) + "Autre choix" + choix.drop(2)).forEach { add(JsonPrimitive(it)) } }),
            "la bonne réponse" to avec("bonneReponse" to (base.int("bonneReponse") + 1) % 4),
        )) {
            importer(variante)
            assertNotEquals("Changer $nom doit changer l'empreinte", e0, empreinteAuService())
        }

        for ((nom, variante) in listOf(
            "l'explication" to avec("explication" to "Une autre explication, tout aussi factice."),
            "la source" to avec("source" to "Une autre source factice"),
            "le niveau" to avec("niveau" to 4),
        )) {
            importer(variante)
            assertEquals("Changer $nom ne doit pas toucher l'empreinte", e0, empreinteAuService())
        }
        importer(base)
        val id = serveur.lister("eruditus_questions", "ref = '$ref'").single().str("id")
        serveur.regler("eruditus_questions", id, "difficulte" to 1234, "difficulte_reponses" to 7)
        assertEquals("La difficulté n'est pas dans l'empreinte", e0, empreinteAuService())
    }

    companion object {
        private val serveur: Serveur by lazy {
            Serveur("empreinte", questions = false).demarrer()
        }

        @AfterClass
        @JvmStatic
        fun arreter() {
            serveur.arreter()
        }
    }
}

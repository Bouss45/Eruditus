package com.eruditus.servertests

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tenue du plafond naturel (spec/regles.md § 8.7, D104) : un joueur parfait sur une banque de 150
 * questions, 30 par niveau, finit vers 2250 (2200 – 2320 selon l'ordre du tirage). La marge est
 * large exprès : ce test ne vise que la dérive des constantes de `config.js` (D111).
 */
class EloPlafondTest {

    @Test
    fun `joueur parfait sur 150 questions, vers 2250, jamais 3000`() {
        val compte = Compte.creer(serveur)
        repeat(30) { compte.jouer { enonce, _ -> bonnes.getValue(enonce) } }
        val elo = serveur.verifierChaine(compte)
        assertEquals(150, serveur.joueurBase(compte).int("elo_reponses"))
        assertTrue("Elo d'un joueur parfait sur 150 questions : $elo", elo in 2100..2400)
        val k = serveur.lignesDuJoueur(compte).map { it.int("elo_k") }
        assertEquals(List(30) { 40 } + List(120) { 24 }, k)

        // Banque épuisée : révision, plus rien ne bouge.
        val revision = compte.jouer { enonce, _ -> bonnes.getValue(enonce) }.last().objet("bilan").objet("elo")
        assertEquals(true, revision.bool("revision"))
        assertEquals(elo, revision.int("valeur"))
    }

    companion object {
        private val questions: List<JsonObject> = (1..150).map { n ->
            buildJsonObject {
                put("ref", "pl-%04d".format(n))
                put("enonce", "Question du plafond $n : quel choix est le bon ?")
                put("choix", buildJsonArray { "ABCD".forEach { add(JsonPrimitive("Choix $it du plafond $n")) } })
                put("bonneReponse", n % 4)
                put("active", true)
                put("niveau", 1 + n % 5)
            }
        }
        private val bonnes: Map<String, Int> = questions.associate { it.str("enonce") to it.int("bonneReponse") }

        private val serveur: Serveur by lazy {
            Serveur("plafond", questions = false).demarrer().also { it.importerListe(questions) }
        }

        @AfterClass
        @JvmStatic
        fun arreter() {
            serveur.arreter()
        }
    }
}

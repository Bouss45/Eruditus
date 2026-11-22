package com.eruditus.android.donnees.api

import com.eruditus.android.donnees.api.TransportFactice.Companion.ID_PARTIE
import com.eruditus.android.donnees.api.TransportFactice.Companion.corrigee
import com.eruditus.android.donnees.api.TransportFactice.Companion.objetQuestion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lecture de `enjeu` (question) et de `elo` (correction), spec/ecrans.md § 9, D128, D129 : tolérante
 * (D22), jamais « Pas de connexion » à cause d'elle (une réponse illisible est un échec).
 */
class ClientApiEnjeuTest {

    private val transport = TransportFactice()
    private val api = ClientApi(transport) { "jeton" }

    /** La question de `POST /parties` lue avec [enjeu] (JSON brut) ajouté, ou sans le champ si null. */
    private suspend fun enjeuLu(enjeu: String?): Enjeu? {
        val ajout = if (enjeu == null) "" else ""","enjeu":$enjeu"""
        val question = objetQuestion(1).dropLast(1) + ajout + "}"
        transport.repondre(Route.CreerPartie, 200, """{"partie":{"id":"$ID_PARTIE","total":5},"question":$question}""")
        val lue = api.creerPartie()
        assertTrue("$enjeu : $lue", lue is Resultat.Ok)
        return (lue as Resultat.Ok).valeur.question.enjeu
    }

    private suspend fun eloLu(elo: String?): EloQuestion? {
        val ajout = if (elo == null) "" else ""","elo":$elo"""
        transport.repondre(Route.Reponse, 200, corrigee(1, choix = 0, bonne = 2, raison = "fausse", contenu = ajout))
        val lue = api.repondre(ID_PARTIE, 1, 0)
        assertTrue("$elo : $lue", lue is Resultat.Ok)
        return (lue as Resultat.Ok).valeur.correction.elo
    }

    @Test
    fun `enjeu lu dans la creation, la question suivante et la redemande`() = runBlocking {
        assertEquals(Enjeu(4, 12, 28), enjeuLu("""{"niveau":4,"gain":12,"perte":28}"""))
        transport.repondre(Route.Question, 200, """{"question":${objetQuestion(2).dropLast(1)},"enjeu":{"niveau":2,"gain":5,"perte":0}}}""")
        assertEquals(Enjeu(2, 5, 0), (api.question(ID_PARTIE) as Resultat.Ok).valeur.enjeu)
    }

    @Test
    fun `enjeu de la question deja vue ou sans niveau, des null`() = runBlocking {
        assertEquals(Enjeu(4, null, null), enjeuLu("""{"niveau":4,"gain":null,"perte":null}"""))
        assertEquals(Enjeu(null, null, null), enjeuLu("""{"niveau":null,"gain":null,"perte":null}"""))
    }

    @Test
    fun `enjeu absent, null ou d'un autre type, pas de ligne et la question reste lisible`() = runBlocking {
        for (valeur in listOf(null, "null", "12", "\"x\"", "true", "[]", "[1,2]")) {
            assertNull(valeur, enjeuLu(valeur))
        }
    }

    @Test
    fun `niveau de mauvais type ou champs absents, sans niveau, sans erreur`() = runBlocking {
        for (valeur in listOf("\"4\"", "true", "[]", "{}", "4.5")) {
            assertEquals(valeur, Enjeu(null, 12, 28), enjeuLu("""{"niveau":$valeur,"gain":12,"perte":28}"""))
        }
        assertEquals(Enjeu(null, null, null), enjeuLu("{}"))
    }

    @Test
    fun `chiffres non entiers ou negatifs, illisibles`() = runBlocking {
        for (valeur in listOf("\"12\"", "12.5", "true", "[]", "{}", "-3")) {
            assertEquals(valeur, Enjeu(4, null, null, illisible = true), enjeuLu("""{"niveau":4,"gain":$valeur,"perte":28}"""))
            assertEquals(valeur, Enjeu(4, null, null, illisible = true), enjeuLu("""{"niveau":4,"gain":12,"perte":$valeur}"""))
        }
        // Un seul des deux absent : même chose, jamais « Déjà vue ».
        assertEquals(Enjeu(4, null, null, illisible = true), enjeuLu("""{"niveau":4,"gain":12}"""))
    }

    @Test
    fun `niveau hors plage gardé tel quel, la ligne l'ecarte`() = runBlocking {
        assertEquals(Enjeu(9, 12, 28), enjeuLu("""{"niveau":9,"gain":12,"perte":28}"""))
    }

    @Test
    fun `champ inconnu de l'enjeu ignore`() = runBlocking {
        assertEquals(Enjeu(3, 1, 2), enjeuLu("""{"niveau":3,"gain":1,"perte":2,"champFutur":{"a":1}}"""))
    }

    @Test
    fun `elo de la correction lu, variation signee, nulle ou null`() = runBlocking {
        assertEquals(EloQuestion(4, 12), eloLu("""{"niveau":4,"variation":12}"""))
        assertEquals(EloQuestion(4, -28), eloLu("""{"niveau":4,"variation":-28}"""))
        assertEquals(EloQuestion(4, 0), eloLu("""{"niveau":4,"variation":0}"""))
        assertEquals(EloQuestion(4, null), eloLu("""{"niveau":4,"variation":null}"""))
        assertEquals(EloQuestion(null, null), eloLu("""{"niveau":null,"variation":null}"""))
    }

    @Test
    fun `elo absent, null ou d'un autre type, pas de ligne et la correction reste lisible`() = runBlocking {
        for (valeur in listOf(null, "null", "12", "\"x\"", "[]")) {
            assertNull(valeur, eloLu(valeur))
        }
    }

    @Test
    fun `variation d'un autre type, illisible, niveau de mauvais type ignore`() = runBlocking {
        for (valeur in listOf("\"12\"", "1.5", "true", "[]", "{}")) {
            assertEquals(valeur, EloQuestion(4, null, illisible = true), eloLu("""{"niveau":4,"variation":$valeur}"""))
        }
        assertEquals(EloQuestion(null, 12), eloLu("""{"niveau":"4","variation":12,"champFutur":1}"""))
    }

    @Test
    fun `la correction garde son verdict et sa bonne reponse quand elo est mal forme`() = runBlocking {
        transport.repondre(Route.Reponse, 200, corrigee(1, choix = 0, bonne = 2, raison = "fausse", contenu = ""","elo":"x""""))
        val ok = api.repondre(ID_PARTIE, 1, 0) as Resultat.Ok
        assertEquals(Raison.Fausse, ok.valeur.correction.raison)
        assertEquals(2, ok.valeur.correction.bonneReponse)
    }
}

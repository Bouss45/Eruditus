package com.eruditus.android.donnees.api

import com.eruditus.android.donnees.api.TransportFactice.Companion.ID_PARTIE
import com.eruditus.android.donnees.api.TransportFactice.Companion.corrigee
import com.eruditus.android.donnees.api.TransportFactice.Companion.erreur
import com.eruditus.android.donnees.api.TransportFactice.Companion.objetQuestion
import com.eruditus.android.donnees.api.TransportFactice.Companion.partieCreee
import com.eruditus.android.donnees.api.TransportFactice.Companion.questionServie
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Routes de la partie (spec/serveur.md § 5) : chemins, corps, lecture des questions et des
 * corrections. Une raison inconnue n'est pas une réponse illisible (D22, D67) ; un champ
 * obligatoire absent ou du mauvais type, si (D53).
 */
class ClientApiPartieTest {

    private val transport = TransportFactice()
    private val api = ClientApi(transport) { "jeton" }

    private val question1 = QuestionServie(1, 5, "Énoncé 1 ?", listOf("A1", "B1", "C1", "D1"), 20_000, 20_000)

    @Test
    fun `routes de la partie, a jeton, l'identifiant dans le chemin`() = runBlocking {
        transport.repondre(Route.CreerPartie, 200, partieCreee())
        transport.repondre(Route.Question, 200, questionServie(2))
        transport.repondre(Route.Reponse, 200, corrigee(2, choix = null, bonne = 1, raison = "sans_reponse"))
        api.creerPartie()
        api.question(ID_PARTIE)
        api.repondre(ID_PARTIE, numero = 2, choix = null)
        assertEquals(
            listOf(
                "POST /api/eruditus/v1/parties",
                "POST /api/eruditus/v1/parties/$ID_PARTIE/question",
                "POST /api/eruditus/v1/parties/$ID_PARTIE/reponse",
            ),
            transport.requetes.map { "${it.methode} ${it.chemin}" },
        )
        assertTrue(transport.requetes.all { it.jeton == "jeton" })
        // « Sans réponse » : `choix` présent et nul, jamais absent (absent = requete_invalide).
        val corps = Json.parseToJsonElement(transport.requetesVers(Route.Reponse).single().corps!!).jsonObject
        assertEquals(JsonNull, corps["choix"])
    }

    @Test
    fun `partie creee et question servie lues, sans bonne reponse`() = runBlocking {
        transport.repondre(Route.CreerPartie, 200, partieCreee(restantMs = 19_870))
        assertEquals(Resultat.Ok(PartieCreee(ID_PARTIE, question1.copy(restantMs = 19_870))), api.creerPartie())
        transport.repondre(Route.Question, 200, questionServie(3, restantMs = 7_000))
        assertEquals(
            Resultat.Ok(QuestionServie(3, 5, "Énoncé 3 ?", listOf("A3", "B3", "C3", "D3"), 20_000, 7_000)),
            api.question(ID_PARTIE),
        )
    }

    @Test
    fun `correction et bilan lus`() = runBlocking {
        transport.repondre(
            Route.Reponse,
            200,
            corrigee(5, choix = 0, bonne = 2, raison = "fausse", score = 14, bilan = """{"bonnes":3,"points":2,"score":14}"""),
        )
        assertEquals(
            Resultat.Ok(
                ReponseCorrigee(
                    Correction(5, 0, correcte = false, bonneReponse = 2, horsDelai = false, point = 0, raison = Raison.Fausse, score = 14),
                    Bilan(bonnes = 3, points = 2, score = 14),
                ),
            ),
            api.repondre(ID_PARTIE, 5, 0),
        )
    }

    /** La correction lue avec [contenu] ajouté à ses champs (explication, source…). */
    private suspend fun correctionAvec(contenu: String): Correction {
        transport.repondre(Route.Reponse, 200, corrigee(1, choix = 0, bonne = 2, raison = "fausse", contenu = contenu))
        val lue = api.repondre(ID_PARTIE, 1, 0)
        assertTrue("$contenu : $lue", lue is Resultat.Ok)
        return (lue as Resultat.Ok).valeur.correction
    }

    @Test
    fun `explication et source lues, l'une sans l'autre, nulles ou absentes rien`() = runBlocking {
        val avec = correctionAvec(""","explication":"Dalí l'a peint en 1931.","source":"Encyclopædia Universalis"""")
        assertEquals("Dalí l'a peint en 1931.", avec.explication)
        assertEquals("Encyclopædia Universalis", avec.source)

        val sourceSeule = correctionAvec(""","explication":null,"source":"larousse.fr"""")
        assertNull(sourceSeule.explication)
        assertEquals("larousse.fr", sourceSeule.source)

        val explicationSeule = correctionAvec(""","explication":"Parce que.","source":null""")
        assertEquals("Parce que.", explicationSeule.explication)
        assertNull(explicationSeule.source)

        // Serveur d'avant le contenu sourcé : champs absents (D22).
        val ancienne = correctionAvec("")
        assertNull(ancienne.explication)
        assertNull(ancienne.source)
    }

    @Test
    fun `explication ou source d'un autre type, ou blanche, ignoree, la correction reste lisible`() = runBlocking {
        for (valeur in listOf("12", "true", "[]", "[\"Parce que.\"]", "{}", "\"\"", "\"   \"")) {
            val lue = correctionAvec(""","explication":$valeur,"source":$valeur""")
            assertNull(valeur, lue.explication)
            assertNull(valeur, lue.source)
            assertEquals(valeur, Raison.Fausse, lue.raison)
        }
    }

    @Test
    fun `explication longue gardee entiere, niveau et champs inconnus ignores`() = runBlocking {
        val longue = "Une explication plus longue que les 300 caractères d'aujourd'hui. ".repeat(8).trim()
        val lue = correctionAvec(""","explication":"$longue","source":"Source","niveau":3,"champFutur":{"lien":"x"}""")
        assertEquals(longue, lue.explication)
        assertEquals("Source", lue.source)
    }

    @Test
    fun `chaque raison connue est lue`() = runBlocking {
        val attendues = mapOf(
            "gagne" to Raison.Gagne,
            "deja_reussie" to Raison.DejaReussie,
            "fausse" to Raison.Fausse,
            "sans_reponse" to Raison.SansReponse,
            "hors_delai" to Raison.HorsDelai,
        )
        for ((code, raison) in attendues) {
            transport.repondre(Route.Reponse, 200, corrigee(1, choix = 1, bonne = 1, raison = code))
            val lue = api.repondre(ID_PARTIE, 1, 1) as Resultat.Ok
            assertEquals(code, raison, lue.valeur.correction.raison)
        }
    }

    @Test
    fun `raison inconnue d'un serveur plus recent, la correction reste lisible`() = runBlocking {
        transport.repondre(Route.Reponse, 200, corrigee(1, choix = 3, bonne = 3, raison = "bonus_rapidite", correcte = true, point = 1))
        val lue = api.repondre(ID_PARTIE, 1, 3) as Resultat.Ok
        assertEquals(Raison.Inconnue, lue.valeur.correction.raison)
        assertTrue(lue.valeur.correction.correcte)
        assertEquals(1, lue.valeur.correction.point)
    }

    @Test
    fun `champs inconnus ignores`() = runBlocking {
        transport.repondre(
            Route.Question,
            200,
            """{"question":${objetQuestion(2).dropLast(1)},"indice":"x"},"champFutur":[1,2]}""",
        )
        assertTrue(api.question(ID_PARTIE) is Resultat.Ok)
    }

    @Test
    fun `question illisible, texte serveur`() = runBlocking {
        val illisibles = listOf(
            """{"question":{"numero":1,"total":5,"enonce":"E","choix":["a","b","c"],"delaiMs":20000,"restantMs":20000}}""",
            """{"question":{"numero":1,"total":5,"enonce":"E","choix":["a","b","c","d","e"],"delaiMs":20000,"restantMs":20000}}""",
            """{"question":{"numero":1,"total":5,"enonce":"E","choix":["a","b","c",4],"delaiMs":20000,"restantMs":20000}}""",
            """{"question":{"numero":6,"total":5,"enonce":"E","choix":["a","b","c","d"],"delaiMs":20000,"restantMs":20000}}""",
            """{"question":{"numero":1,"total":5,"enonce":"E","choix":["a","b","c","d"],"delaiMs":0,"restantMs":0}}""",
            """{"question":{"numero":1,"total":5,"enonce":"E","choix":["a","b","c","d"],"delaiMs":20000,"restantMs":-1}}""",
            """{"question":{"numero":1,"total":5,"enonce":"E","choix":["a","b","c","d"],"delaiMs":20000}}""",
            """{"question":{"numero":"1","total":5,"enonce":"E","choix":["a","b","c","d"],"delaiMs":20000,"restantMs":0}}""",
            """{"question":{"numero":1,"total":5,"enonce":"","choix":["a","b","c","d"],"delaiMs":20000,"restantMs":0}}""",
            """{}""",
        )
        for (corps in illisibles) {
            transport.repondre(Route.Question, 200, corps)
            assertEquals(corps, Resultat.Echec(ErreurApi.Serveur), api.question(ID_PARTIE))
        }
    }

    @Test
    fun `identifiant de partie qui n'est pas fait de lettres et de chiffres, illisible`() = runBlocking {
        for (id in listOf("../comptes/suppression", "a b", "", "p?x=1")) {
            transport.repondre(Route.CreerPartie, 200, partieCreee(id = id))
            assertEquals(id, Resultat.Echec(ErreurApi.Serveur), api.creerPartie())
        }
    }

    @Test
    fun `correction vraiment illisible, texte serveur`() = runBlocking {
        val bonne = corrigee(1, choix = 1, bonne = 1, raison = "gagne")
        val illisibles = listOf(
            bonne.replace(""""correcte":true,""", ""),
            bonne.replace(""""correcte":true""", """"correcte":"oui""""),
            bonne.replace(""""raison":"gagne"""", """"raison":3"""),
            bonne.replace(""","raison":"gagne"""", ""),
            bonne.replace(""""bonneReponse":1""", """"bonneReponse":4"""),
            bonne.replace(""""choix":1""", """"choix":7"""),
            bonne.replace(""""point":1""", """"point":null"""),
            bonne.replace(""""bilan":null""", """"bilan":{"bonnes":3}"""),
            """{"bilan":null}""",
        )
        for (corps in illisibles) {
            transport.repondre(Route.Reponse, 200, corps)
            assertEquals(corps, Resultat.Echec(ErreurApi.Serveur), api.repondre(ID_PARTIE, 1, 1))
        }
    }

    @Test
    fun `banque insuffisante, 503 avec son code, est un refus`() = runBlocking {
        transport.repondre(Route.CreerPartie, 503, erreur("banque_insuffisante", 503))
        assertEquals(Resultat.Echec(ErreurApi.Refus(503, "banque_insuffisante")), api.creerPartie())
    }
}

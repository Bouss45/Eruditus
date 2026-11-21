package com.eruditus.android.donnees.api

import com.eruditus.android.donnees.api.ErreurApi.NonAutorise
import com.eruditus.android.donnees.api.ErreurApi.Serveur
import com.eruditus.android.donnees.api.ErreurApi.TropDeTentatives
import com.eruditus.android.donnees.api.TransportFactice.Companion.classement
import com.eruditus.android.donnees.api.TransportFactice.Companion.ligne
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `GET /classement` (spec/serveur.md § 5, D72) : route à jeton, lignes lues dans l'ordre reçu,
 * 10 premières seulement (un serveur plus récent peut en envoyer plus, D22). Réponse illisible
 * (D53) seulement si un champ obligatoire manque ou a le mauvais type ; champs inconnus ignorés.
 */
class ClientApiClassementTest {

    private val transport = TransportFactice()
    private val api = ClientApi(transport) { "jeton" }

    private fun lire(corps: String): Resultat<Classement> = runBlocking {
        transport.repondre(Route.Classement, 200, corps)
        api.classement()
    }

    private fun illisible(corps: String) = assertEquals(corps, Resultat.Echec(Serveur), lire(corps))

    private val dix = (1..10).map { ligne(it, "Joueur_$it", 20 - it) }
    private val placesDix = (1..10).map { Place(it, "Joueur_$it", 20 - it) }

    @Test
    fun `route a jeton, GET sans corps`() = runBlocking {
        transport.repondre(Route.Classement, 200, TransportFactice.CLASSEMENT)
        api.classement()
        val r = transport.requetes.single()
        assertEquals("GET /api/eruditus/v1/classement", "${r.methode} ${r.chemin}")
        assertEquals("jeton", r.jeton)
        assertEquals(null, r.corps)
    }

    @Test
    fun `top, moi et classes lus dans l'ordre recu`() {
        assertEquals(
            Resultat.Ok(Classement(placesDix, moi = Place(27, "Hibou_42", 4), classes = 48)),
            lire(TransportFactice.CLASSEMENT),
        )
    }

    @Test
    fun `ex aequo, rangs du serveur gardes tels quels`() {
        val corps = classement(listOf(ligne(1, "alice", 5), ligne(2, "bernard", 3), ligne(2, "Bob", 3), ligne(4, "Dora", 2)), classes = 4)
        assertEquals(
            Resultat.Ok(Classement(listOf(Place(1, "alice", 5), Place(2, "bernard", 3), Place(2, "Bob", 3), Place(4, "Dora", 2)), null, 4)),
            lire(corps),
        )
    }

    @Test
    fun `pas encore classe, moi nul`() {
        assertEquals(Resultat.Ok(Classement(placesDix, moi = null, classes = 48)), lire(classement(dix)))
    }

    @Test
    fun `personne de classe, top vide`() {
        assertEquals(Resultat.Ok(Classement(emptyList(), moi = null, classes = 0)), lire(classement(emptyList(), classes = 0)))
    }

    @Test
    fun `plus de 10 lignes, les 10 premieres gardees, le reste ignore meme mal forme`() {
        val douze = dix + ligne(11, "Joueur_11", 8) + """{"rang":"douze"}"""
        assertEquals(Resultat.Ok(Classement(placesDix, moi = null, classes = 48)), lire(classement(douze)))
    }

    @Test
    fun `champs inconnus ignores, dans la reponse et dans les lignes`() {
        val corps = """{"top":[{"rang":1,"pseudo":"Hypatie","score":19,"badge":"or"}],""" +
            """"moi":{"rang":1,"pseudo":"Hypatie","score":19,"serie":3},"classes":1,"periode":"toujours"}"""
        assertEquals(Resultat.Ok(Classement(listOf(Place(1, "Hypatie", 19)), Place(1, "Hypatie", 19), 1)), lire(corps))
    }

    @Test
    fun `reponse illisible, champ obligatoire absent`() {
        illisible("""{"moi":null,"classes":48}""")
        illisible("""{"top":[],"classes":48}""")
        illisible("""{"top":[],"moi":null}""")
        illisible(classement(listOf("""{"pseudo":"Hypatie","score":19}""")))
        illisible(classement(listOf("""{"rang":1,"score":19}""")))
        illisible(classement(listOf("""{"rang":1,"pseudo":"Hypatie"}""")))
        illisible(classement(dix, moi = """{"rang":27,"pseudo":"Hibou_42"}"""))
    }

    @Test
    fun `reponse illisible, champ du mauvais type`() {
        illisible("""{"top":{},"moi":null,"classes":48}""")
        illisible("""{"top":null,"moi":null,"classes":48}""")
        illisible(classement(listOf("\"Hypatie\"")))
        illisible(classement(listOf("null")))
        illisible(classement(listOf("""{"rang":"1","pseudo":"Hypatie","score":19}""")))
        illisible(classement(listOf("""{"rang":1.5,"pseudo":"Hypatie","score":19}""")))
        illisible(classement(listOf("""{"rang":1,"pseudo":42,"score":19}""")))
        illisible(classement(listOf("""{"rang":1,"pseudo":"Hypatie","score":true}""")))
        illisible(classement(dix, moi = "\"Hibou_42\""))
        illisible(classement(dix, moi = "27"))
        illisible("""{"top":[],"moi":null,"classes":"48"}""")
        // Une ligne mal formée parmi les 10 premières suffit.
        illisible(classement(dix.take(9) + """{"rang":10,"pseudo":"Joueur_10","score":"10"}"""))
    }

    @Test
    fun `erreurs de la route`() = runBlocking {
        transport.repondre(Route.Classement, 401, """{"status":401,"message":"…","data":{}}""")
        assertEquals(Resultat.Echec(NonAutorise), api.classement())
        transport.repondre(Route.Classement, 429, """{"status":429,"message":"Too Many Requests.","data":{}}""")
        assertEquals(Resultat.Echec(TropDeTentatives), api.classement())
        transport.repondre(Route.Classement, 500, """{"status":500,"message":"…","data":{}}""")
        assertEquals(Resultat.Echec(Serveur), api.classement())
        transport.echouer(Route.Classement, connexionEtablie = false)
        assertEquals(Resultat.Echec(ErreurApi.Reseau), api.classement())
    }
}

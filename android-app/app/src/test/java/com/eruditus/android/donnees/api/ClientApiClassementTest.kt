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
 * `GET /classement/elo` (spec/serveur.md § 5, D72, D109, D112) : route à jeton, lignes lues dans l'ordre reçu,
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

    private val dix = (1..10).map { ligne(it, "Joueur_$it", 2000 - 10 * it) }
    private val placesDix = (1..10).map { Place(it, "Joueur_$it", 2000 - 10 * it) }

    @Test
    fun `route a jeton, GET sans corps`() = runBlocking {
        transport.repondre(Route.Classement, 200, TransportFactice.CLASSEMENT)
        api.classement()
        val r = transport.requetes.single()
        assertEquals("GET /api/eruditus/v1/classement/elo", "${r.methode} ${r.chemin}")
        assertEquals("jeton", r.jeton)
        assertEquals(null, r.corps)
    }

    @Test
    fun `top, moi et classes lus dans l'ordre recu`() {
        assertEquals(
            Resultat.Ok(Classement(placesDix, moi = Place(27, "Hibou_42", 1180), classes = 48, restantes = 0)),
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
    fun `provisoire, moi nul et restantes lues`() {
        assertEquals(Resultat.Ok(Classement(placesDix, moi = null, classes = 48, restantes = 12)), lire(classement(dix, restantes = 12)))
    }

    @Test
    fun `personne de classe, top vide`() {
        assertEquals(Resultat.Ok(Classement(emptyList(), moi = null, classes = 0, restantes = 30)), lire(classement(emptyList(), classes = 0, restantes = 30)))
    }

    @Test
    fun `plus de 10 lignes, les 10 premieres gardees, le reste ignore meme mal forme`() {
        val douze = dix + ligne(11, "Joueur_11", 1890) + """{"rang":"douze"}"""
        assertEquals(Resultat.Ok(Classement(placesDix, moi = null, classes = 48)), lire(classement(douze)))
    }

    @Test
    fun `champs inconnus ignores, dans la reponse et dans les lignes`() {
        val corps = """{"top":[{"rang":1,"pseudo":"Hypatie","elo":2140,"badge":"or","score":19}],""" +
            """"moi":{"rang":1,"pseudo":"Hypatie","elo":2140,"serie":3},"classes":1,"restantes":0,"periode":"toujours"}"""
        assertEquals(Resultat.Ok(Classement(listOf(Place(1, "Hypatie", 2140)), Place(1, "Hypatie", 2140), 1, 0)), lire(corps))
    }

    @Test
    fun `reponse illisible, champ obligatoire absent`() {
        illisible("""{"moi":null,"classes":48,"restantes":0}""")
        illisible("""{"top":[],"classes":48,"restantes":0}""")
        illisible("""{"top":[],"moi":null,"restantes":0}""")
        illisible("""{"top":[],"moi":null,"classes":48}""")
        illisible(classement(listOf("""{"pseudo":"Hypatie","elo":2140}""")))
        illisible(classement(listOf("""{"rang":1,"elo":2140}""")))
        illisible(classement(listOf("""{"rang":1,"pseudo":"Hypatie"}""")))
        // L'ancien champ `score` ne remplace pas `elo`.
        illisible(classement(listOf("""{"rang":1,"pseudo":"Hypatie","score":19}""")))
        illisible(classement(dix, moi = """{"rang":27,"pseudo":"Hibou_42","score":4}"""))
    }

    @Test
    fun `reponse illisible, champ du mauvais type`() {
        illisible("""{"top":{},"moi":null,"classes":48,"restantes":0}""")
        illisible("""{"top":null,"moi":null,"classes":48,"restantes":0}""")
        illisible(classement(listOf("\"Hypatie\"")))
        illisible(classement(listOf("null")))
        illisible(classement(listOf("""{"rang":"1","pseudo":"Hypatie","elo":2140}""")))
        illisible(classement(listOf("""{"rang":1.5,"pseudo":"Hypatie","elo":2140}""")))
        illisible(classement(listOf("""{"rang":1,"pseudo":42,"elo":2140}""")))
        illisible(classement(listOf("""{"rang":1,"pseudo":"Hypatie","elo":true}""")))
        illisible(classement(dix, moi = "\"Hibou_42\""))
        illisible(classement(dix, moi = "27"))
        illisible("""{"top":[],"moi":null,"classes":"48","restantes":0}""")
        illisible("""{"top":[],"moi":null,"classes":48,"restantes":"12"}""")
        illisible("""{"top":[],"moi":null,"classes":48,"restantes":null}""")
        // Une ligne mal formée parmi les 10 premières suffit.
        illisible(classement(dix.take(9) + """{"rang":10,"pseudo":"Joueur_10","elo":"1900"}"""))
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

package com.eruditus.android.donnees.api

import com.eruditus.android.donnees.api.ErreurApi.NonAutorise
import com.eruditus.android.donnees.api.ErreurApi.Refus
import com.eruditus.android.donnees.api.ErreurApi.Reseau
import com.eruditus.android.donnees.api.ErreurApi.Serveur
import com.eruditus.android.donnees.api.ErreurApi.TropDeTentatives
import com.eruditus.android.donnees.api.TransportFactice.Companion.COMPTE_CREE
import com.eruditus.android.donnees.api.TransportFactice.Companion.CONNEXION
import com.eruditus.android.donnees.api.TransportFactice.Companion.ID_PARTIE
import com.eruditus.android.donnees.api.TransportFactice.Companion.INFO
import com.eruditus.android.donnees.api.TransportFactice.Companion.MOI
import com.eruditus.android.donnees.api.TransportFactice.Companion.MOI_NON_CLASSE
import com.eruditus.android.donnees.api.TransportFactice.Companion.MOT_DE_PASSE_CHANGE
import com.eruditus.android.donnees.api.TransportFactice.Companion.PSEUDO_CHANGE
import com.eruditus.android.donnees.api.TransportFactice.Companion.RECUPERATION
import com.eruditus.android.donnees.api.TransportFactice.Companion.RENOUVELLEMENT
import com.eruditus.android.donnees.api.TransportFactice.Companion.corrigee
import com.eruditus.android.donnees.api.TransportFactice.Companion.partieCreee
import com.eruditus.android.donnees.api.TransportFactice.Companion.questionServie
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Client de l'API (D52) : routes et leur accès, aucun jeton sur les routes publiques (D30),
 * lecture des réponses et des erreurs (spec/serveur.md § 5, D53).
 */
class ClientApiTest {

    private val transport = TransportFactice()
    private var jeton: String? = "jeton-en-memoire"
    private val api = ClientApi(transport) { jeton }

    private fun repondreATout() {
        transport.repondre(Route.Info, 200, INFO)
        transport.repondre(Route.CreerCompte, 200, COMPTE_CREE)
        transport.repondre(Route.Connexion, 200, CONNEXION)
        transport.repondre(Route.Renouvellement, 200, RENOUVELLEMENT)
        transport.repondre(Route.Recuperation, 200, RECUPERATION)
        transport.repondre(Route.Deconnexion, 204)
        transport.repondre(Route.Suppression, 204)
        transport.repondre(Route.ChangementPseudo, 200, PSEUDO_CHANGE)
        transport.repondre(Route.ChangementMotDePasse, 200, MOT_DE_PASSE_CHANGE)
        transport.repondre(Route.Moi, 200, MOI)
        transport.repondre(Route.Classement, 200, TransportFactice.CLASSEMENT)
        transport.repondre(Route.CreerPartie, 200, partieCreee())
        transport.repondre(Route.Question, 200, questionServie(2))
        transport.repondre(Route.Reponse, 200, corrigee(1, choix = 2, bonne = 2, raison = "gagne"))
    }

    private fun appelerTout() = runBlocking {
        api.info()
        api.creerCompte("Hibou_42", "nuit-blanche-42")
        api.connexion("Hibou_42", "nuit-blanche-42")
        api.renouveler()
        api.recuperation("Hibou_42", "7K3MQ9XA2HDEF8RT", "lune-glacee-17")
        api.deconnexion()
        api.suppression("nuit-blanche-42")
        api.changerPseudo("Chouette_7")
        api.changerMotDePasse("nuit-blanche-42", "lune-glacee-17")
        api.moi()
        api.classement()
        api.creerPartie()
        api.question(ID_PARTIE)
        api.repondre(ID_PARTIE, numero = 1, choix = 2)
    }

    @Test
    fun `les routes publiques sont celles de la spec`() {
        val publiques = Route.entries.filter { it.acces == Acces.Public }.map { "${it.methode} ${it.chemin}" }
        assertEquals(
            listOf(
                "GET /api/eruditus/v1/info",
                "POST /api/eruditus/v1/comptes",
                "POST /api/collections/eruditus_joueurs/auth-with-password",
                "POST /api/eruditus/v1/comptes/recuperation",
            ).sorted(),
            publiques.sorted(),
        )
    }

    @Test
    fun `aucun jeton sur les routes publiques, meme quand une session existe`() {
        repondreATout()
        appelerTout()
        for (route in Route.entries) {
            val envoyees = transport.requetesVers(route)
            assertEquals("${route.name} appelée une fois", 1, envoyees.size)
            when (route.acces) {
                Acces.Public -> assertNull("${route.name} : aucun jeton", envoyees.single().jeton)
                Acces.Jeton -> assertEquals("${route.name} : le jeton", "jeton-en-memoire", envoyees.single().jeton)
            }
        }
    }

    @Test
    fun `sans session, les routes a jeton partent sans jeton et le serveur repond 401`() = runBlocking {
        jeton = null
        transport.repondre(Route.Moi, 401, """{"status":401,"message":"…","data":{}}""")
        assertEquals(Resultat.Echec(NonAutorise), api.moi())
        assertNull(transport.requetes.single().jeton)
    }

    @Test
    fun `corps envoyes, champs de la spec`() {
        repondreATout()
        appelerTout()
        fun corps(route: Route) = Json.parseToJsonElement(transport.requetesVers(route).single().corps!!).jsonObject
        assertEquals("Hibou_42", corps(Route.CreerCompte)["pseudo"]!!.jsonPrimitive.content)
        assertEquals("nuit-blanche-42", corps(Route.CreerCompte)["motDePasse"]!!.jsonPrimitive.content)
        assertEquals("Hibou_42", corps(Route.Connexion)["identity"]!!.jsonPrimitive.content)
        assertEquals("nuit-blanche-42", corps(Route.Connexion)["password"]!!.jsonPrimitive.content)
        assertEquals("7K3MQ9XA2HDEF8RT", corps(Route.Recuperation)["code"]!!.jsonPrimitive.content)
        assertEquals("lune-glacee-17", corps(Route.Recuperation)["nouveauMotDePasse"]!!.jsonPrimitive.content)
        assertEquals("nuit-blanche-42", corps(Route.Suppression)["motDePasse"]!!.jsonPrimitive.content)
        assertEquals(setOf("pseudo"), corps(Route.ChangementPseudo).keys)
        assertEquals("Chouette_7", corps(Route.ChangementPseudo)["pseudo"]!!.jsonPrimitive.content)
        assertEquals(setOf("motDePasse", "nouveauMotDePasse"), corps(Route.ChangementMotDePasse).keys)
        assertEquals("nuit-blanche-42", corps(Route.ChangementMotDePasse)["motDePasse"]!!.jsonPrimitive.content)
        assertEquals("lune-glacee-17", corps(Route.ChangementMotDePasse)["nouveauMotDePasse"]!!.jsonPrimitive.content)
        assertEquals(1, corps(Route.Reponse)["numero"]!!.jsonPrimitive.content.toInt())
        assertEquals(2, corps(Route.Reponse)["choix"]!!.jsonPrimitive.content.toInt())
        for (route in listOf(Route.Info, Route.Renouvellement, Route.Deconnexion, Route.Moi, Route.Classement, Route.CreerPartie, Route.Question)) {
            assertNull(route.name, transport.requetesVers(route).single().corps)
        }
    }

    @Test
    fun `reponses lues`() = runBlocking {
        repondreATout()
        assertEquals(Resultat.Ok(1000), api.info())
        assertEquals(
            Resultat.Ok(SessionOuverte("jeton-cree", "id-hibou", "Hibou_42", "7K3M-Q9XA-2HDE-F8RT")),
            api.creerCompte("Hibou_42", "nuit-blanche-42"),
        )
        // Des routes standard, seuls token et record.id sont lus (spec/serveur.md § 5.1).
        assertEquals(Resultat.Ok(SessionOuverte("jeton-connexion", "id-hibou", null, null)), api.connexion("hibou_42", "x"))
        assertEquals(Resultat.Ok(SessionOuverte("jeton-renouvele", "id-hibou", null, null)), api.renouveler())
        assertEquals(Resultat.Ok(Moi("Hibou_42", 12, 3, 48)), api.moi())
        assertEquals(Resultat.Ok(Unit), api.deconnexion())
    }

    @Test
    fun `routes des Parametres, a jeton, reponses lues`() = runBlocking {
        assertEquals(Acces.Jeton, Route.ChangementPseudo.acces)
        assertEquals("POST /api/eruditus/v1/comptes/pseudo", "${Route.ChangementPseudo.methode} ${Route.ChangementPseudo.chemin}")
        assertEquals(Acces.Jeton, Route.ChangementMotDePasse.acces)
        assertEquals(
            "POST /api/eruditus/v1/comptes/mot-de-passe",
            "${Route.ChangementMotDePasse.methode} ${Route.ChangementMotDePasse.chemin}",
        )
        repondreATout()
        assertEquals(Resultat.Ok("Chouette_7"), api.changerPseudo("Chouette_7"))
        // Le jeton neuf et le joueur ; pas de code de récupération (il ne change pas).
        assertEquals(
            Resultat.Ok(SessionOuverte("jeton-neuf", "id-hibou", "Hibou_42", null)),
            api.changerMotDePasse("nuit-blanche-42", "lune-glacee-17"),
        )
    }

    @Test
    fun `routes des Parametres, reponse illisible et refus`() = runBlocking {
        for (corps in listOf("""{"joueur":{"id":"id-hibou"}}""", """{"joueur":"Chouette_7"}""", "{}", """{"pseudo":"Chouette_7"}""")) {
            transport.repondre(Route.ChangementPseudo, 200, corps)
            assertEquals(corps, Resultat.Echec(Serveur), api.changerPseudo("Chouette_7"))
        }
        for (corps in listOf("""{"joueur":{"id":"id-hibou","pseudo":"Hibou_42"}}""", """{"token":"","joueur":{"id":"id-hibou","pseudo":"Hibou_42"}}""", """{"token":"jeton-neuf"}""")) {
            transport.repondre(Route.ChangementMotDePasse, 200, corps)
            assertEquals(corps, Resultat.Echec(Serveur), api.changerMotDePasse("a", "lune-glacee-17"))
        }
        transport.repondre(Route.ChangementPseudo, 409, TransportFactice.erreur("pseudo_trop_recent", 409))
        assertEquals(Resultat.Echec(Refus(409, "pseudo_trop_recent")), api.changerPseudo("Chouette_7"))
        transport.repondre(Route.ChangementMotDePasse, 400, TransportFactice.erreur("mot_de_passe_incorrect"))
        assertEquals(Resultat.Echec(Refus(400, "mot_de_passe_incorrect")), api.changerMotDePasse("faux", "lune-glacee-17"))
    }

    @Test
    fun `champs inconnus ignores, rang nul tant que le score est 0`() = runBlocking {
        transport.repondre(Route.Moi, 200, MOI_NON_CLASSE)
        assertEquals(Resultat.Ok(Moi("Hibou_42", 0, null, 48)), api.moi())
    }

    @Test
    fun `erreurs lues`() = runBlocking {
        suspend fun cas(statut: Int, corps: String, attendu: ErreurApi) {
            transport.repondre(Route.CreerCompte, statut, corps)
            assertEquals("$statut $corps", Resultat.Echec(attendu), api.creerCompte("Hibou_42", "nuit-blanche-42"))
        }
        cas(400, TransportFactice.erreur("pseudo_pris"), Refus(400, "pseudo_pris"))
        cas(400, """{"status":400,"message":"Failed to authenticate.","data":{}}""", Refus(400, null))
        cas(400, "", Refus(400, null))
        cas(404, "<html>introuvable</html>", Refus(404, null))
        cas(401, TransportFactice.erreur("peu_importe"), NonAutorise)
        cas(429, """{"status":429,"message":"Too Many Requests.","data":{}}""", TropDeTentatives)
        cas(503, """{"status":503,"message":"…","data":{"code":"banque_insuffisante"}}""", Refus(503, "banque_insuffisante"))
        cas(500, """{"status":500,"message":"…","data":{}}""", Serveur)
        cas(502, "<html>Bad Gateway</html>", Serveur)
        cas(301, "", Serveur)
        // Un 200 qui n'est pas le JSON attendu (portail captif, proxy mal réglé) : comme un 5xx.
        cas(200, "<html>Connexion au Wi-Fi</html>", Serveur)
        cas(200, """{"token":"x"}""", Serveur)
        cas(200, "[]", Serveur)
    }

    @Test
    fun `reseau coupe ou serveur muet`() = runBlocking {
        transport.echouer(Route.Info, connexionEtablie = false)
        assertEquals(Resultat.Echec(Reseau), api.info())
        transport.echouer(Route.Info, connexionEtablie = true)
        assertEquals(Resultat.Echec(Serveur), api.info())
    }

    @Test
    fun `une requete ne montre ni jeton ni corps quand on l'affiche`() {
        val requete = Requete("POST", "/api/eruditus/v1/comptes/suppression", """{"motDePasse":"secret-42"}""", "jeton-secret")
        val texte = requete.toString()
        assertFalse(texte, "secret" in texte)
        assertTrue(texte, "/api/eruditus/v1/comptes/suppression" in texte)
    }
}

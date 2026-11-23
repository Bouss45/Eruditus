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
import com.eruditus.android.donnees.api.TransportFactice.Companion.ELO_CLASSE
import com.eruditus.android.donnees.api.TransportFactice.Companion.ELO_PROVISOIRE
import com.eruditus.android.donnees.api.TransportFactice.Companion.MOT_DE_PASSE_CHANGE
import com.eruditus.android.donnees.api.TransportFactice.Companion.PSEUDO_CHANGE
import com.eruditus.android.donnees.api.TransportFactice.Companion.RECUPERATION
import com.eruditus.android.donnees.api.TransportFactice.Companion.RENOUVELLEMENT
import com.eruditus.android.donnees.api.TransportFactice.Companion.corrigee
import com.eruditus.android.donnees.api.TransportFactice.Companion.moi
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
        transport.repondre(Route.Avis, 200, "{}")
        transport.repondre(Route.Signalement, 200, "{}")
        transport.repondre(Route.Historique, 200, TransportFactice.historique(emptyList()))
        transport.repondre(Route.HistoriqueDetail, 200, TransportFactice.DETAIL_VIDE)
        transport.repondre(Route.CourbeElo, 200, TransportFactice.courbe(null, emptyList()))
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
        api.envoyerAvis("Une idée :\ndes thèmes.", "1.0.6", "15")
        api.signalerQuestion(ID_PARTIE, 3, "autre", "Date fausse")
        api.historique(null)
        api.detailPartie(ID_PARTIE)
        api.courbe()
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
        // L'avis (D87) : le texte tel que donné, les deux versions ; jamais de pseudo (il vient du jeton).
        assertEquals(setOf("texte", "versionApp", "android"), corps(Route.Avis).keys)
        assertEquals("Une idée :\ndes thèmes.", corps(Route.Avis)["texte"]!!.jsonPrimitive.content)
        assertEquals("1.0.6", corps(Route.Avis)["versionApp"]!!.jsonPrimitive.content)
        assertEquals("15", corps(Route.Avis)["android"]!!.jsonPrimitive.content)
        // Le signalement (D119) : la partie et le numéro, jamais un identifiant de question.
        assertEquals(setOf("partie", "numero", "motif", "commentaire"), corps(Route.Signalement).keys)
        assertEquals(ID_PARTIE, corps(Route.Signalement)["partie"]!!.jsonPrimitive.content)
        assertEquals(3, corps(Route.Signalement)["numero"]!!.jsonPrimitive.content.toInt())
        assertEquals("autre", corps(Route.Signalement)["motif"]!!.jsonPrimitive.content)
        assertEquals("Date fausse", corps(Route.Signalement)["commentaire"]!!.jsonPrimitive.content)
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
        assertEquals(Resultat.Ok(Moi("Hibou_42", 42, EloMoi(1247, provisoire = false, restantes = 0, rang = 3, classes = 18, revision = false))), api.moi())
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
    fun `route des avis, a jeton, reponse lue, champs inconnus ignores`() = runBlocking {
        assertEquals(Acces.Jeton, Route.Avis.acces)
        assertEquals("POST /api/eruditus/v1/avis", "${Route.Avis.methode} ${Route.Avis.chemin}")
        for (corps in listOf("{}", """{"champFutur":1}""")) {
            transport.repondre(Route.Avis, 200, corps)
            assertEquals(corps, Resultat.Ok(Unit), api.envoyerAvis("Une idée.", "1.0.6", "15"))
        }
        transport.repondre(Route.Avis, 204)
        assertEquals(Resultat.Ok(Unit), api.envoyerAvis("Une idée.", "1.0.6", "15"))
        assertEquals("jeton-en-memoire", transport.requetesVers(Route.Avis).last().jeton)
    }

    @Test
    fun `route des avis, reponse illisible et refus`() = runBlocking {
        suspend fun cas(statut: Int, corps: String, attendu: ErreurApi) {
            transport.repondre(Route.Avis, statut, corps)
            assertEquals("$statut $corps", Resultat.Echec(attendu), api.envoyerAvis("Une idée.", "1.0.6", "15"))
        }
        cas(200, "[]", Serveur)
        cas(200, "<html>Connexion au Wi-Fi</html>", Serveur)
        cas(400, TransportFactice.erreur("avis_invalide"), Refus(400, "avis_invalide"))
        cas(409, TransportFactice.erreur("avis_trop_recent", 409), Refus(409, "avis_trop_recent"))
        // 503 avec son code : un refus, pas une panne (D89) ; sans code, une panne.
        cas(503, TransportFactice.erreur("avis_indisponible", 503), Refus(503, "avis_indisponible"))
        cas(503, """{"status":503,"message":"…","data":{}}""", Serveur)
        cas(401, """{"status":401,"message":"…","data":{}}""", NonAutorise)
        cas(429, """{"status":429,"message":"Too Many Requests.","data":{}}""", TropDeTentatives)
        transport.echouer(Route.Avis, connexionEtablie = false)
        assertEquals(Resultat.Echec(Reseau), api.envoyerAvis("Une idée.", "1.0.6", "15"))
        transport.echouer(Route.Avis, connexionEtablie = true)
        assertEquals(Resultat.Echec(Serveur), api.envoyerAvis("Une idée.", "1.0.6", "15"))
    }

    @Test
    fun `route du signalement, a jeton, sans commentaire vide, reponse lue et refus`() = runBlocking {
        assertEquals(Acces.Jeton, Route.Signalement.acces)
        assertEquals("POST /api/eruditus/v1/signalement", "${Route.Signalement.methode} ${Route.Signalement.chemin}")
        for (corps in listOf("{}", """{"champFutur":1}""")) {
            transport.repondre(Route.Signalement, 200, corps)
            assertEquals(corps, Resultat.Ok(Unit), api.signalerQuestion(ID_PARTIE, 1, "reponse_fausse", ""))
        }
        val envoye = Json.parseToJsonElement(transport.requetesVers(Route.Signalement).last().corps!!).jsonObject
        assertEquals(setOf("partie", "numero", "motif"), envoye.keys)
        assertEquals("jeton-en-memoire", transport.requetesVers(Route.Signalement).last().jeton)

        suspend fun cas(statut: Int, corps: String, attendu: ErreurApi) {
            transport.repondre(Route.Signalement, statut, corps)
            assertEquals("$statut $corps", Resultat.Echec(attendu), api.signalerQuestion(ID_PARTIE, 1, "autre", "x"))
        }
        cas(200, "[]", Serveur)
        cas(200, "<html>Connexion au Wi-Fi</html>", Serveur)
        cas(409, TransportFactice.erreur("signalement_trop_frequent", 409), Refus(409, "signalement_trop_frequent"))
        cas(400, TransportFactice.erreur("signalement_invalide"), Refus(400, "signalement_invalide"))
        cas(404, TransportFactice.erreur("partie_introuvable", 404), Refus(404, "partie_introuvable"))
        cas(401, """{"status":401,"message":"…","data":{}}""", NonAutorise)
        cas(429, """{"status":429,"message":"Too Many Requests.","data":{}}""", TropDeTentatives)
        transport.echouer(Route.Signalement, connexionEtablie = false)
        assertEquals(Resultat.Echec(Reseau), api.signalerQuestion(ID_PARTIE, 1, "autre", "x"))
        transport.echouer(Route.Signalement, connexionEtablie = true)
        assertEquals(Resultat.Echec(Serveur), api.signalerQuestion(ID_PARTIE, 1, "autre", "x"))
    }

    @Test
    fun `moi provisoire, elo rang nul et champ inconnu ignore`() = runBlocking {
        transport.repondre(Route.Moi, 200, moi(ELO_PROVISOIRE, score = 0))
        assertEquals(
            Resultat.Ok(Moi("Hibou_42", 0, EloMoi(1012, provisoire = true, restantes = 12, rang = null, classes = 18, revision = false))),
            api.moi(),
        )
    }

    @Test
    fun `moi, banque epuisee`() = runBlocking {
        transport.repondre(Route.Moi, 200, moi("""{"valeur":1247,"provisoire":false,"restantes":0,"rang":3,"classes":18,"revision":true}"""))
        assertTrue((api.moi() as Resultat.Ok).valeur.elo.revision)
    }

    @Test
    fun `moi illisible, champ obligatoire absent ou du mauvais type (D112)`() = runBlocking {
        val champs = mapOf(
            "valeur" to "1247", "provisoire" to "false", "restantes" to "0", "rang" to "3", "classes" to "18", "revision" to "false",
        )
        fun elo(vararg remplace: Pair<String, String?>): String {
            val m = champs + remplace.toMap()
            return m.entries.filter { it.value != null }.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }
        }
        val illisibles = buildList {
            for (nom in champs.keys) add(elo(nom to null))
            add(elo("valeur" to "\"1247\""))
            add(elo("valeur" to "12.5"))
            add(elo("valeur" to "null"))
            add(elo("provisoire" to "\"non\""))
            add(elo("restantes" to "\"0\""))
            add(elo("rang" to "\"3\""))
            add(elo("classes" to "null"))
            add(elo("revision" to "1"))
            add("null"); add("[]"); add("\"1247\"")
        }
        for (e in illisibles) {
            transport.repondre(Route.Moi, 200, moi(e))
            assertEquals(e, Resultat.Echec(ErreurApi.Serveur), api.moi())
        }
        transport.repondre(Route.Moi, 200, """{"joueur":{"id":"id-hibou","pseudo":"Hibou_42"},"score":42,"rang":3,"classes":48}""")
        assertEquals("elo absent", Resultat.Echec(ErreurApi.Serveur), api.moi())
        transport.repondre(Route.Moi, 200, """{"joueur":{"id":"id-hibou","pseudo":"Hibou_42"},"elo":$ELO_CLASSE}""")
        assertEquals("score absent", Resultat.Echec(ErreurApi.Serveur), api.moi())
    }

    @Test
    fun `moi ne lit plus ni rang ni classes par points`() = runBlocking {
        transport.repondre(Route.Moi, 200, """{"joueur":{"id":"id-hibou","pseudo":"Hibou_42"},"score":42,"elo":$ELO_CLASSE}""")
        assertTrue(api.moi() is Resultat.Ok)
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

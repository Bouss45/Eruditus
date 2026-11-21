package com.eruditus.servertests

import java.net.ServerSocket
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Avis (spec/regles.md § 7, spec/serveur.md § 5.4, D85 – D92) : message transmis au faux webhook,
 * jamais à Discord ; règle du texte (cas partagés avec l'app), versions, pannes du webhook, délai
 * par compte, limite par IP, et les tentatives T39 – T44.
 */
class AvisTest {
    private val serveur = Serveur.avis
    private val api = serveur.api

    @Before
    fun webhookQuiRepond() {
        FauxWebhook.mode = FauxWebhook.Mode.Statut(204)
    }

    /** `avis_envoye_le` du joueur, lu par le superutilisateur ("" : jamais). */
    private fun envoyeLe(s: Serveur, id: String): String =
        s.su.get("/api/collections/eruditus_joueurs/records/$id").obj.str("avis_envoye_le")

    private fun unSeulMessage(pseudo: String): FauxWebhook.Message {
        val messages = FauxWebhook.messagesDe(pseudo)
        assertEquals("Messages reçus pour $pseudo : $messages", 1, messages.size)
        return messages.single()
    }

    private val vide = JsonObject(emptyMap())

    // --- Message transmis ------------------------------------------------------------------------

    @Test
    fun `avis transmis au webhook, message exact, rien d'autre`() {
        val compte = Compte.creer(serveur)
        val texte = "Une idée : des thèmes.\nEt un bug sur l'écran du classement."
        val avant = Instant.now()
        val r = serveur.avis(compte.jeton, texte)
        assertEquals("$r", 200, r.status)
        assertEquals(vide, r.obj)

        val m = unSeulMessage(compte.pseudo)
        assertEquals("POST", m.methode)
        assertEquals(FauxWebhook.chemin, m.chemin)
        assertTrue("Content-Type : ${m.typeContenu}", m.typeContenu!!.startsWith("application/json"))
        assertEquals(setOf("username", "allowed_mentions", "embeds"), m.corps.keys)
        assertEquals("Eruditus", m.corps.str("username"))
        assertEquals(buildJsonObject { put("parse", JsonArray(emptyList())) }, m.corps.objet("allowed_mentions"))
        assertEquals(1, m.corps.tableau("embeds").size)
        val embed = m.embed
        assertEquals(setOf("title", "description", "footer", "timestamp"), embed.keys)
        assertEquals("Avis de ${compte.pseudo}", embed.str("title"))
        assertEquals(texte, embed.str("description"))
        assertEquals(buildJsonObject { put("text", "Eruditus 1.0.6 · Android 15") }, embed.objet("footer"))
        val horodatage = Instant.parse(embed.str("timestamp"))
        assertTrue("timestamp $horodatage", !horodatage.isBefore(avant.minusSeconds(5)) && !horodatage.isAfter(Instant.now().plusSeconds(5)))

        // Seule la date est écrite ; le texte n'est nulle part dans le compte.
        assertNotEquals("", envoyeLe(serveur, compte.id))
        val enBase = serveur.su.get("/api/collections/eruditus_joueurs/records/${compte.id}").corps
        assertFalse(enBase.contains("des thèmes"))
    }

    @Test
    fun `Discord qui repond 200 avec le message publie, c'est un succes`() {
        FauxWebhook.mode = FauxWebhook.Mode.Statut(200, FauxWebhook.CORPS_200)
        val compte = Compte.creer(serveur)
        val r = serveur.avis(compte.jeton, "Tout va bien.")
        assertEquals("$r", 200, r.status)
        assertEquals(vide, r.obj)
        unSeulMessage(compte.pseudo)
    }

    @Test
    fun `versions gardees seulement si elles suivent leur motif, jamais un refus`() {
        fun pied(versionApp: JsonElement?, android: JsonElement?): String {
            val compte = Compte.creer(serveur)
            val r = serveur.avis(compte.jeton, "Versions.", versionApp, android)
            assertEquals("versionApp $versionApp, android $android : $r", 200, r.status)
            return unSeulMessage(compte.pseudo).embed.objet("footer").str("text")
        }
        val versionsApp = listOf(
            JsonPrimitive("1.0.6") to "1.0.6",
            JsonPrimitive("1.0.0-preview") to "1.0.0-preview",
            JsonPrimitive("1.0") to "1.0",
            JsonPrimitive("1.0.1234") to "1.0.1234",
            JsonPrimitive("1.0.6.7") to "1.0.6.7",
            JsonPrimitive("1.0.6-" + "a".repeat(16)) to "1.0.6-" + "a".repeat(16),
            JsonPrimitive("1") to "inconnue",
            JsonPrimitive("12345.0") to "inconnue",
            JsonPrimitive("1.0.6.7.8") to "inconnue",
            JsonPrimitive("v1.0.6") to "inconnue",
            JsonPrimitive("1.0.6-Preview") to "inconnue",
            JsonPrimitive("1.0.6-") to "inconnue",
            JsonPrimitive("1.0.6-" + "a".repeat(17)) to "inconnue",
            JsonPrimitive("1.0.6 ") to "inconnue",
            JsonPrimitive("1.0.6\n") to "inconnue",
            JsonPrimitive("") to "inconnue",
            JsonPrimitive(106) to "inconnue",
            JsonNull to "inconnue",
            buildJsonObject { put("version", "1.0.6") } to "inconnue",
            null to "inconnue",
        )
        for ((valeur, attendue) in versionsApp) {
            assertEquals("versionApp $valeur", "Eruditus $attendue · Android 15", pied(valeur, JsonPrimitive("15")))
        }
        val versionsAndroid = listOf(
            JsonPrimitive("15") to "15",
            JsonPrimitive("8.1.0") to "8.1.0",
            JsonPrimitive("8.1") to "8.1",
            JsonPrimitive("16.0.1.2") to "inconnue",
            JsonPrimitive("1234") to "inconnue",
            JsonPrimitive("Android 15") to "inconnue",
            JsonPrimitive("15\n") to "inconnue",
            JsonPrimitive("") to "inconnue",
            JsonPrimitive(15) to "inconnue",
            buildJsonArray { add(JsonPrimitive("15")) } to "inconnue",
            null to "inconnue",
        )
        for ((valeur, attendue) in versionsAndroid) {
            assertEquals("android $valeur", "Eruditus 1.0.6 · Android $attendue", pied(JsonPrimitive("1.0.6"), valeur))
        }
    }

    @Test
    fun `le pseudo est celui du jeton au moment de l'envoi, apres un changement aussi`() {
        val compte = Compte.creer(serveur)
        val nouveau = Compte.pseudoUnique("Avis")
        assertEquals(200, serveur.changerPseudo(compte.jeton, nouveau).status)
        assertEquals(200, serveur.avis(compte.jeton, "Après le changement.").status)
        assertEquals(0, FauxWebhook.messagesDe(compte.pseudo).size)
        assertEquals("Après le changement.", unSeulMessage(nouveau).embed.str("description"))
    }

    // --- Règle du texte : les cas partagés avec l'app (D86) -----------------------------------------

    @Test
    fun `regle du texte, les cas de cas-avis json`() {
        val cas = Json.parseToJsonElement(Files.readString(Chemins.casAvis)).jsonObject.tableau("cas")
        assertTrue("Trop peu de cas : ${cas.size}", cas.size >= 30)
        for (element in cas) {
            val c = element.jsonObject
            val nom = c.str("cas")
            val compte = Compte.creer(serveur)
            val r = serveur.avis(compte.jeton, deplier(c.champ("entree")))
            if (c["refus"] != null) {
                assertTrue("« $nom » devrait être refusé", c.bool("refus"))
                r.doitEtreErreur(400, "avis_invalide")
                assertEquals("« $nom »", 0, FauxWebhook.messagesDe(compte.pseudo).size)
                assertEquals("« $nom »", "", envoyeLe(serveur, compte.id))
            } else {
                assertEquals("« $nom » : $r", 200, r.status)
                val attendu = deplier(c.champ("attendu"))
                assertEquals("« $nom »", attendu, unSeulMessage(compte.pseudo).embed.str("description"))
                assertEquals("« $nom » : longueur du fichier", c.int("longueur"), attendu.codePointCount(0, attendu.length))
            }
        }
    }

    // --- Ordre des vérifications, pannes du webhook (D87, D89) ---------------------------------------

    @Test
    fun `variable absente, avis_indisponible, delai intact, texte juge avant`() {
        val principal = Serveur.principal
        val compte = Compte.creer(principal)
        val r = principal.avis(compte.jeton, "Personne pour me lire ?")
        r.doitEtreErreur(503, "avis_indisponible")
        assertEquals(503, r.obj.int("status"))
        assertTrue(r.obj.str("message").isNotEmpty())
        assertEquals("", envoyeLe(principal, compte.id))
        // Le texte est jugé avant la variable ; un second essai donne la même réponse.
        principal.avis(compte.jeton, "  ").doitEtreErreur(400, "avis_invalide")
        principal.avis(compte.jeton, "Encore ?").doitEtreErreur(503, "avis_indisponible")
        assertEquals("", envoyeLe(principal, compte.id))
    }

    @Test
    fun `webhook en erreur, avis_indisponible, delai rendu, puis l'avis passe`() {
        val compte = Compte.creer(serveur)
        val pannes = listOf(
            FauxWebhook.Mode.Statut(500, """{"message":"Internal Server Error","code":0}"""),
            FauxWebhook.Mode.Statut(429, FauxWebhook.CORPS_429),
            FauxWebhook.Mode.Statut(404, """{"message":"Unknown Webhook","code":10015}"""),
            FauxWebhook.Mode.Statut(400, """{"message":"Cannot send an empty message","code":50006}"""),
        )
        for ((i, panne) in pannes.withIndex()) {
            FauxWebhook.mode = panne
            val r = serveur.avis(compte.jeton, "Essai ${i + 1}")
            r.doitEtreErreur(503, "avis_indisponible")
            assertFalse("Réponse de Discord recopiée : $r", r.corps.contains("Unknown Webhook") || r.corps.contains("rate limited"))
            assertEquals("après $panne", "", envoyeLe(serveur, compte.id))
            assertEquals(i + 1, FauxWebhook.messagesDe(compte.pseudo).size)
        }
        FauxWebhook.mode = FauxWebhook.Mode.Statut(204)
        assertEquals(200, serveur.avis(compte.jeton, "Essai 5").status)
        assertEquals(5, FauxWebhook.messagesDe(compte.pseudo).size)
        assertNotEquals("", envoyeLe(serveur, compte.id))
    }

    @Test
    fun `webhook sans reponse au-dela de 5 s, avis_indisponible avant 8 s, delai rendu`() {
        val compte = Compte.creer(serveur)
        FauxWebhook.mode = FauxWebhook.Mode.Silence(8_000)
        val debut = System.nanoTime()
        val r = serveur.avis(compte.jeton, "Discord est lent aujourd'hui.")
        val duree = Duration.ofNanos(System.nanoTime() - debut)
        r.doitEtreErreur(503, "avis_indisponible")
        assertTrue("Réponse en $duree", duree >= Duration.ofMillis(4_500) && duree < Duration.ofMillis(7_500))
        assertEquals("", envoyeLe(serveur, compte.id))
        // Discord avait publié : renvoyer le fait arriver deux fois (limite acceptée, serveur § 6.7).
        FauxWebhook.mode = FauxWebhook.Mode.Statut(204)
        assertEquals(200, serveur.avis(compte.jeton, "Discord est lent aujourd'hui.").status)
        assertEquals(2, FauxWebhook.messagesDe(compte.pseudo).size)
    }

    @Test
    fun `webhook injoignable, avis_indisponible sans l'adresse, ni dans la reponse ni dans le journal`() {
        val compte = Compte.creer(injoignable)
        val r = injoignable.avis(compte.jeton, "Y a quelqu'un ?")
        r.doitEtreErreur(503, "avis_indisponible")
        assertEquals("", envoyeLe(injoignable, compte.id))
        assertFalse("Adresse dans le journal", injoignable.journal().contains(SECRET_INJOIGNABLE))
        assertFalse("Port dans la réponse", r.corps.contains("127.0.0.1"))
    }

    @Test
    fun `delai verifie avant Discord, l'echec rend la date d'avant`() {
        val compte = Compte.creer(serveur)
        assertEquals(200, serveur.avis(compte.jeton, "Premier.").status)
        FauxWebhook.mode = FauxWebhook.Mode.Statut(500)
        serveur.avis(compte.jeton, "Deuxième.").doitEtreErreur(409, "avis_trop_recent")
        assertEquals(1, FauxWebhook.messagesDe(compte.pseudo).size)

        // Délai court (1 000 ms) : après un succès puis un échec, la date du succès revient.
        val court = Serveur.chronoCourt
        val autre = Compte.creer(court)
        FauxWebhook.mode = FauxWebhook.Mode.Statut(204)
        assertEquals(200, court.avis(autre.jeton, "Premier.").status)
        val premier = envoyeLe(court, autre.id)
        Thread.sleep(1_300)
        FauxWebhook.mode = FauxWebhook.Mode.Statut(500)
        court.avis(autre.jeton, "Deuxième.").doitEtreErreur(503, "avis_indisponible")
        assertEquals(premier, envoyeLe(court, autre.id))
        FauxWebhook.mode = FauxWebhook.Mode.Statut(204)
        assertEquals(200, court.avis(autre.jeton, "Troisième.").status)
        assertNotEquals(premier, envoyeLe(court, autre.id))
    }

    // --- Tentatives de triche T39 – T44 ----------------------------------------------------------

    @Test
    fun `T39 signer l'avis d'un autre, champs du message et versions forges ignores`() {
        val a = Compte.creer(serveur)
        val b = Compte.creer(serveur)
        val r = serveur.avis(
            a.jeton,
            "C'est moi qui écris.",
            versionApp = JsonPrimitive("1.0.6 @everyone **gras**"),
            android = buildJsonObject { put("version", "15") },
            extra = mapOf(
                "pseudo" to JsonPrimitive(b.pseudo),
                "joueur" to JsonPrimitive(b.id),
                "id" to JsonPrimitive(b.id),
                "username" to JsonPrimitive("Admin"),
                "avatar_url" to JsonPrimitive("https://exemple.invalid/a.png"),
                "content" to JsonPrimitive("@everyone"),
                "embeds" to buildJsonArray { add(buildJsonObject { put("title", "Avis de ${b.pseudo}") }) },
                "allowed_mentions" to buildJsonObject { put("parse", buildJsonArray { add(JsonPrimitive("everyone")) }) },
                "avis_envoye_le" to JsonPrimitive("2000-01-01 00:00:00.000Z"),
            ),
        )
        assertEquals("$r", 200, r.status)
        val m = unSeulMessage(a.pseudo)
        assertEquals(setOf("username", "allowed_mentions", "embeds"), m.corps.keys)
        assertEquals("Eruditus", m.corps.str("username"))
        assertEquals(JsonArray(emptyList()), m.corps.objet("allowed_mentions").tableau("parse"))
        assertEquals("Eruditus inconnue · Android inconnue", m.embed.objet("footer").str("text"))
        assertEquals(0, FauxWebhook.messagesDe(b.pseudo).size)
        assertFalse(envoyeLe(serveur, a.id).startsWith("2000"))
        // Le délai de B est intact.
        assertEquals("", envoyeLe(serveur, b.id))
        assertEquals(200, serveur.avis(b.jeton, "Et moi aussi.").status)
        unSeulMessage(b.pseudo)
    }

    @Test
    fun `T40 mentions dans le texte, transmises sans notifier personne`() {
        val compte = Compte.creer(serveur)
        val texte = "@everyone @here <@123456789012345678> <@!123456789012345678> <@&123456789012345678> regardez !"
        assertEquals(200, serveur.avis(compte.jeton, texte).status)
        val m = unSeulMessage(compte.pseudo)
        assertEquals(texte, m.embed.str("description"))
        assertFalse("content" in m.corps)
        assertEquals(buildJsonObject { put("parse", JsonArray(emptyList())) }, m.corps.objet("allowed_mentions"))
    }

    @Test
    fun `T41 deux avis d'affilee, dix en parallele, limite par IP`() {
        val compte = Compte.creer(serveur)
        assertEquals(200, serveur.avis(compte.jeton, "Premier avis.").status)
        val r = serveur.avis(compte.jeton, "Deuxième avis.")
        r.doitEtreErreur(409, "avis_trop_recent")
        assertEquals(1, FauxWebhook.messagesDe(compte.pseudo).size)

        // Dix en même temps depuis un compte neuf : un seul part.
        val rafale = Compte.creer(serveur)
        val depart = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(10)
        val reponses = (1..10).map { n ->
            pool.submit<Reponse> {
                depart.await()
                serveur.avis(rafale.jeton, "Rafale $n")
            }
        }
        depart.countDown()
        val statuts = reponses.map { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()
        assertEquals("Statuts : $statuts", 1, statuts.count { it.status == 200 })
        statuts.filter { it.status != 200 }.forEach { it.doitEtreErreur(409, "avis_trop_recent") }
        assertEquals(1, FauxWebhook.messagesDe(rafale.pseudo).size)

        // 5 par heure et par IP, refus compris ; une autre IP a son propre compteur.
        val ip = Ips.nouvelle()
        val comptes = (1..6).map { Compte.creer(serveur) }
        repeat(5) { serveur.avis(comptes[it].jeton, "", ip = ip).doitEtreErreur(400, "avis_invalide") }
        assertEquals(429, serveur.avis(comptes[5].jeton, "Sixième.", ip = ip).status)
        assertEquals(0, FauxWebhook.messagesDe(comptes[5].pseudo).size)
        assertEquals(200, serveur.avis(comptes[5].jeton, "Sixième.").status)

        // Après le délai (1 000 ms sur le serveur court), un nouvel avis passe.
        val court = Serveur.chronoCourt
        val patient = Compte.creer(court)
        assertEquals(200, court.avis(patient.jeton, "Un.").status)
        court.avis(patient.jeton, "Deux.").doitEtreErreur(409, "avis_trop_recent")
        Thread.sleep(1_300)
        assertEquals(200, court.avis(patient.jeton, "Deux.").status)
        assertEquals(2, FauxWebhook.messagesDe(patient.pseudo).size)
    }

    @Test
    fun `T42 corps geant, texte qui n'est pas une chaine, texte invalide`() {
        val compte = Compte.creer(serveur)
        val chemin = "/api/eruditus/v1/avis"
        serveur.avis(compte.jeton, "a".repeat(17_000)).doitEtreErreur(400, "requete_invalide")
        for (valeur in listOf(JsonPrimitive(42), JsonPrimitive(true), JsonNull, buildJsonArray { add(JsonPrimitive("a")) }, buildJsonObject { put("t", "a") })) {
            api.post(chemin, buildJsonObject { put("texte", valeur) }, jeton = compte.jeton).doitEtreErreur(400, "requete_invalide")
        }
        api.post(chemin, buildJsonObject { put("versionApp", "1.0.6") }, jeton = compte.jeton).doitEtreErreur(400, "requete_invalide")
        api.requete("POST", chemin, "[]", compte.jeton).doitEtreErreur(400, "requete_invalide")
        api.requete("POST", chemin, "pas du json", compte.jeton).doitEtreErreur(400, "requete_invalide")
        api.requete("POST", chemin, """{"texte":"Bonjour"}""", compte.jeton, typeContenu = "text/plain").doitEtreErreur(400, "requete_invalide")
        for (texte in listOf("", "   ", "\n\r\n", "\u0000\t\u001b", "a".repeat(1001), "😀".repeat(1001))) {
            serveur.avis(compte.jeton, texte).doitEtreErreur(400, "avis_invalide")
        }
        assertEquals(0, FauxWebhook.messagesDe(compte.pseudo).size)
        assertEquals("", envoyeLe(serveur, compte.id))
        assertEquals(200, serveur.avis(compte.jeton, "Enfin un vrai avis.").status)
    }

    @Test
    fun `T43 avis sans jeton, jeton expire, de superutilisateur ou de users`() {
        val compte = Compte.creer(serveur)
        val expire = ComptesTest.jetonQuiExpire(serveur, compte.id)
        for (jeton in listOf(null, expire, serveur.jetonSu, serveur.jetonUsers())) {
            assertEquals(401, serveur.avis(jeton, "Sans moi.").status)
        }
        assertEquals(0, FauxWebhook.tousLesMessages().count { it.embed.str("description") == "Sans moi." })
        assertEquals("", envoyeLe(serveur, compte.id))
    }

    @Test
    fun `T44 avis_envoye_le jamais renvoye, adresse et texte absents des reponses et du journal`() {
        val compte = Compte.creer(serveur)
        val marque = "marque-${Serveur.aleatoire(12)}"
        val reponses = mutableListOf(serveur.avis(compte.jeton, "Avis $marque"))
        reponses += serveur.avis(compte.jeton, "Encore $marque") // avis_trop_recent
        FauxWebhook.mode = FauxWebhook.Mode.Statut(500)
        val autre = Compte.creer(serveur)
        reponses += serveur.avis(autre.jeton, "Panne $marque") // avis_indisponible
        reponses += compte.moi()
        reponses += serveur.connexion(compte.pseudo, compte.motDePasse)
        reponses += api.post("/api/collections/eruditus_joueurs/auth-refresh", jeton = compte.jeton)
        for (r in reponses) {
            assertFalse("avis_envoye_le dans $r", r.corps.contains("avis_envoye_le"))
            assertFalse("Secret dans $r", r.corps.contains(FauxWebhook.secret))
            assertFalse("Adresse dans $r", r.corps.contains("/api/webhooks/"))
        }
        assertFalse("avis_envoye_le" in reponses[4].obj.objet("record"))
        assertNotEquals("", envoyeLe(serveur, compte.id))

        // Journal du serveur (requêtes SQL du mode dev comprises) et journaux de PocketBase.
        for (s in listOf(serveur, Serveur.chronoCourt)) {
            val journal = s.journal()
            assertFalse("Secret dans le journal de ${s.nom}", journal.contains(FauxWebhook.secret))
            assertFalse("Texte d'un avis dans le journal de ${s.nom}", journal.contains(marque))
            val logs = s.su.get("/api/logs?perPage=500")
            assertEquals("$logs", 200, logs.status)
            assertFalse("Secret dans les journaux de ${s.nom}", logs.corps.contains(FauxWebhook.secret))
            assertFalse("Texte d'un avis dans les journaux de ${s.nom}", logs.corps.contains(marque))
        }
    }

    companion object {
        /** Entrée de `cas-avis.json` : un texte, ou des morceaux (texte, ou { repete, fois }). */
        fun deplier(element: JsonElement): String = when (element) {
            is JsonPrimitive -> element.content
            is JsonArray -> element.joinToString("") { morceau ->
                if (morceau is JsonPrimitive) {
                    morceau.content
                } else {
                    val m = morceau.jsonObject
                    m.str("repete").repeat(m.int("fois"))
                }
            }
            else -> throw AssertionError("Entrée inattendue : $element")
        }

        private val SECRET_INJOIGNABLE = "secret-injoignable-" + Serveur.aleatoire(30)

        /** Webhook sur un port fermé : la connexion est refusée (D89). */
        private val injoignable: Serveur by lazy {
            TextesInterdits.liste += SECRET_INJOIGNABLE
            val port = ServerSocket(0).use { it.localPort }
            Serveur(
                "injoignable",
                env = mapOf("ERUDITUS_AVIS_WEBHOOK" to "http://127.0.0.1:$port/api/webhooks/1/$SECRET_INJOIGNABLE"),
            ).demarrer()
        }
    }
}

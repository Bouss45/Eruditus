package com.eruditus.servertests

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Migrations sur une base vide, puis au redémarrage (rien ne change), et banque insuffisante,
 * sur un serveur sans questions (spec/serveur.md § 3, § 4, § 10).
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MigrationsTest {

    private fun collection(nom: String): JsonObject {
        val r = serveur.su.get("/api/collections/$nom")
        assertEquals("Collection $nom : $r", 200, r.status)
        return r.obj
    }

    private fun champ(collection: JsonObject, nom: String): JsonObject =
        collection.tableau("fields").map { it.jsonObject }.single { it.str("name") == nom }

    private fun reglesFermees(collection: JsonObject) {
        for (regle in listOf("listRule", "viewRule", "createRule", "updateRule", "deleteRule")) {
            assertEquals("${collection.str("name")}.$regle", JsonNull, collection.champ(regle))
        }
    }

    @Test
    fun `a collections et regles d'acces`() {
        val joueurs = collection("eruditus_joueurs")
        assertEquals("auth", joueurs.str("type"))
        reglesFermees(joueurs)
        assertEquals("", joueurs.str("authRule"))
        assertEquals(JsonNull, joueurs.champ("manageRule"))
        val authParMotDePasse = joueurs.objet("passwordAuth")
        assertTrue(authParMotDePasse.bool("enabled"))
        assertEquals(buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("pseudo")) }, authParMotDePasse.tableau("identityFields"))
        for (option in listOf("oauth2", "otp", "mfa", "authAlert")) {
            assertEquals(option, false, joueurs.objet(option).bool("enabled"))
        }
        assertEquals(2_592_000, joueurs.objet("authToken").int("duration"))
        assertEquals(8, champ(joueurs, "password").int("min"))
        assertEquals(71, champ(joueurs, "password").int("max"))
        assertEquals(false, champ(joueurs, "email").bool("required"))
        assertEquals(true, champ(joueurs, "code_recuperation").bool("hidden"))
        assertEquals("^[A-Za-z0-9_]{3,20}$", champ(joueurs, "pseudo").str("pattern"))
        assertTrue(joueurs.tableau("indexes").any { it.texte()!!.contains("COLLATE NOCASE") && it.texte()!!.contains("UNIQUE") })

        val questions = collection("eruditus_questions")
        reglesFermees(questions)
        assertEquals(true, champ(questions, "bonne_reponse").bool("hidden"))

        val parties = collection("eruditus_parties")
        reglesFermees(parties)
        assertEquals(true, champ(parties, "joueur").bool("cascadeDelete"))

        val reponses = collection("eruditus_reponses")
        reglesFermees(reponses)
        assertEquals(true, champ(reponses, "partie").bool("cascadeDelete"))
        assertEquals(true, champ(reponses, "joueur").bool("cascadeDelete"))
        assertEquals(false, champ(reponses, "question").bool("cascadeDelete"))
        assertTrue(reponses.tableau("indexes").any {
            val index = it.texte()!!
            index.contains("UNIQUE") && index.contains("partie") && index.contains("numero")
        })

        reglesFermees(collection("users"))
    }

    @Test
    fun `b reglages, limiteur en tete, IP reelle, batch desactive`() {
        val reglages = serveur.su.get("/api/settings").obj
        val limiteur = reglages.objet("rateLimits")
        assertTrue(limiteur.bool("enabled"))
        val regles = limiteur.tableau("rules").map { it.jsonObject }
        val attendues = listOf(
            Triple("eruditus_joueurs:auth", 5, 60),
            Triple("POST /api/eruditus/v1/comptes", 5, 3600),
            Triple("POST /api/eruditus/v1/comptes/recuperation", 5, 3600),
            Triple("POST /api/eruditus/v1/comptes/suppression", 5, 600),
            Triple("POST /api/eruditus/v1/parties", 30, 3600),
            Triple("/api/eruditus/", 120, 60),
        )
        assertEquals(attendues, regles.take(6).map { Triple(it.str("label"), it.int("maxRequests"), it.int("duration")) })
        assertEquals(listOf("*:auth", "*:create", "/api/batch", "/api/"), regles.drop(6).map { it.str("label") })
        val proxy = reglages.objet("trustedProxy")
        assertEquals(buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("X-Forwarded-For")) }, proxy.tableau("headers"))
        assertEquals(false, proxy.bool("useLeftmostIP"))
        assertEquals(false, reglages.objet("batch").bool("enabled"))
    }

    @Test
    fun `c banque insuffisante`() {
        val compte = Compte.creer(serveur)
        compte.nouvellePartie().doitEtreErreur(503, "banque_insuffisante")
        assertEquals(0, serveur.compter("eruditus_parties", "joueur = '${compte.id}'"))

        val fichier = java.nio.file.Files.createTempFile(serveur.dossier, "quatre", ".json")
        java.nio.file.Files.writeString(fichier, buildJsonArray { Banque.questions.take(4).forEach { add(it) } }.toString())
        assertEquals(0, serveur.importer(fichier).code)
        compte.nouvellePartie().doitEtreErreur(503, "banque_insuffisante")

        val cinq = buildJsonArray {
            Banque.questions.take(4).forEach { add(it) }
            add(buildJsonObject {
                Banque.questions[4].forEach { (cle, valeur) -> put(cle, valeur) }
                put("active", false)
            })
        }
        java.nio.file.Files.writeString(fichier, cinq.toString())
        assertEquals(0, serveur.importer(fichier).code)
        compte.nouvellePartie().doitEtreErreur(503, "banque_insuffisante")

        java.nio.file.Files.writeString(fichier, buildJsonArray { Banque.questions.take(5).forEach { add(it) } }.toString())
        assertEquals(0, serveur.importer(fichier).code)
        assertEquals(200, compte.nouvellePartie().status)
    }

    @Test
    fun `d redemarrage, les migrations ne changent plus rien`() {
        val compte = Compte.creer(serveur)
        val avant = instantane()
        serveur.redemarrer()
        assertEquals(avant, instantane())
        assertEquals(200, serveur.connexion(compte.pseudo, compte.motDePasse).status)
        assertEquals(200, compte.moi().status)
    }

    private fun instantane(): List<JsonObject> {
        val collections = listOf("eruditus_joueurs", "eruditus_questions", "eruditus_parties", "eruditus_reponses", "users")
            .map { collection(it) }
        val reglages = serveur.su.get("/api/settings").obj
        val utiles = buildJsonObject {
            put("rateLimits", reglages.champ("rateLimits"))
            put("trustedProxy", reglages.champ("trustedProxy"))
            put("batch", reglages.champ("batch"))
        }
        return collections + utiles
    }

    companion object {
        private val serveur: Serveur by lazy { Serveur("vierge", questions = false).demarrer() }

        @AfterClass
        @JvmStatic
        fun arreter() {
            serveur.arreter()
        }
    }
}

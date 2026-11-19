package com.eruditus.servertests

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Corps mal formés (T32), API batch (T33), routes sans entrée. */
class GeneralTest {
    private val serveur = Serveur.principal
    private val api = serveur.api

    @Test
    fun `T32 corps trop gros, JSON invalide ou mauvais Content-Type, jamais de 500`() {
        val compte = Compte.creer(serveur)
        val (partie, _) = compte.commencer()
        val routes = listOf(
            "/api/eruditus/v1/comptes" to null,
            "/api/eruditus/v1/comptes/recuperation" to null,
            "/api/eruditus/v1/comptes/suppression" to compte.jeton,
            "/api/eruditus/v1/parties/$partie/reponse" to compte.jeton,
        )
        val trop = "{\"pseudo\":\"" + "a".repeat(16 * 1024) + "\"}"
        val corpsInvalides = listOf(
            "{" to "application/json",
            "pas du json" to "application/json",
            "[]" to "application/json",
            "[1, 2]" to "application/json",
            "null" to "application/json",
            "\"texte\"" to "application/json",
            "42" to "application/json",
            "" to "application/json",
            "{\"numero\": 1, \"choix\": 0}" to "text/plain",
            "{\"numero\": 1, \"choix\": 0}" to "application/x-www-form-urlencoded",
            "pseudo=abc&motDePasse=abcdefgh" to "application/x-www-form-urlencoded",
            "{\"numero\": 1, \"choix\": 0}" to null,
            trop to "application/json",
            "\u0000\u0001\u0002" to "application/json",
            "{\"a\":" + "[".repeat(5000) to "application/json",
        )
        for ((route, jeton) in routes) {
            for ((corps, type) in corpsInvalides) {
                val r = api.requete("POST", route, corps, jeton, typeContenu = type)
                assertEquals("$route ← ${corps.take(40)} ($type) : $r", 400, r.status)
                assertEquals("$route ← ${corps.take(40)} ($type) : $r", "requete_invalide", r.code)
            }
        }
        // 16 Ko tout juste : accepté (puis refusé pour une autre raison, pseudo trop long).
        val debut = "{\"pseudo\":\""
        val fin = "\",\"motDePasse\":\"abcdefgh\"}"
        val limite = debut + "a".repeat(16 * 1024 - debut.length - fin.length) + fin
        assertEquals(16 * 1024, limite.toByteArray().size)
        serveur.api.requete("POST", "/api/eruditus/v1/comptes", limite).doitEtreErreur(400, "pseudo_invalide")
        // Content-Type JSON avec charset : accepté.
        val avecCharset = api.requete(
            "POST", "/api/eruditus/v1/parties/$partie/reponse", "{\"numero\":1,\"choix\":0}", compte.jeton,
            typeContenu = "application/json; charset=utf-8",
        )
        assertEquals("$avecCharset", 200, avecCharset.status)
        assertEquals(1, serveur.compter("eruditus_reponses", "partie = '$partie' && repondue_le != ''"))
    }

    @Test
    fun `les routes sans entree ignorent le corps`() {
        val compte = Compte.creer(serveur)
        val creation = api.requete("POST", "/api/eruditus/v1/parties", "n'importe quoi", compte.jeton, typeContenu = "text/plain")
        assertEquals("$creation", 200, creation.status)
        val id = creation.obj.objet("partie").str("id")
        val question = api.requete("POST", "/api/eruditus/v1/parties/$id/question", "{", compte.jeton)
        assertEquals("$question", 200, question.status)
        val gros = api.requete("POST", "/api/eruditus/v1/parties/$id/question", "x".repeat(100_000), compte.jeton)
        assertEquals("$gros", 200, gros.status)
        val info = api.requete("GET", "/api/eruditus/v1/info", null, null)
        assertEquals(200, info.status)
        val deconnexion = api.requete("POST", "/api/eruditus/v1/comptes/deconnexion", "[]", compte.jeton)
        assertEquals(204, deconnexion.status)
    }

    @Test
    fun `T33 API batch desactivee`() {
        val compte = Compte.creer(serveur)
        val corps = buildJsonObject {
            put("requests", kotlinx.serialization.json.buildJsonArray {
                add(buildJsonObject {
                    put("method", "POST")
                    put("url", "/api/collections/eruditus_reponses/records")
                    put("body", buildJsonObject { put("points", 1) })
                })
            })
        }
        for (jeton in listOf(null, compte.jeton)) {
            val r = api.post("/api/batch", corps, jeton = jeton)
            assertTrue("$r", r.status in 400..404)
        }
        assertEquals(0, serveur.compter("eruditus_reponses", "joueur = '${compte.id}'"))
    }

    @Test
    fun `route inconnue sous api eruditus`() {
        val r = api.get("/api/eruditus/v1/inconnue", null)
        assertEquals(404, r.status)
        val ancienne = api.get("/api/eruditus/v2/info", null)
        assertEquals(404, ancienne.status)
    }
}

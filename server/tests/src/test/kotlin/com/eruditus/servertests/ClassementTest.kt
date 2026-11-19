package com.eruditus.servertests

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Classement (spec/regles.md § 4, D18), T30 et T31, sur un serveur à lui pour maîtriser tous les
 * scores. Les tests s'enchaînent dans l'ordre de leur nom.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ClassementTest {

    @Test
    fun `a personne de classe au depart`() {
        val r = serveur.api.get("/api/eruditus/v1/classement", zero.jeton)
        assertEquals(200, r.status)
        assertEquals(0, r.obj.tableau("top").size)
        assertNull(r.obj.objetOuNull("moi"))
        assertEquals(0, r.obj.int("classes"))
        val moi = zero.moi().obj
        assertEquals(0, moi.int("score"))
        assertNull(moi.intOuNull("rang"))
        assertEquals(0, moi.int("classes"))
    }

    @Test
    fun `b top 10, ex aequo au meme rang, puis ordre alphabetique sans la casse`() {
        preparer()
        val r = serveur.api.get("/api/eruditus/v1/classement", joueurs.getValue("alice").jeton)
        assertEquals(200, r.status)
        val top = r.obj.tableau("top").map { it.jsonObject }
        top.forEach { ContratV1Test.verifierLigne(it) }
        assertEquals(
            listOf("alice", "bernard", "Bob", "Dora", "eve", "Fred", "gina", "Hugo", "ines", "Jules"),
            top.map { it.str("pseudo") },
        )
        assertEquals(listOf(1, 2, 2, 4, 5, 5, 5, 5, 5, 5), top.map { it.int("rang") })
        assertEquals(listOf(5, 3, 3, 2, 1, 1, 1, 1, 1, 1), top.map { it.int("score") })
        assertEquals(13, r.obj.int("classes"))
        assertEquals(buildJsonObject { put("rang", 1); put("pseudo", "alice"); put("score", 5) }, r.obj.objet("moi"))
    }

    @Test
    fun `c ma ligne sous le top 10, et pas classe a 0 point`() {
        preparer()
        val zoe = joueurs.getValue("Zoe")
        val r = serveur.api.get("/api/eruditus/v1/classement", zoe.jeton)
        assertTrue(r.obj.tableau("top").none { it.jsonObject.str("pseudo") == "Zoe" })
        assertEquals(buildJsonObject { put("rang", 5); put("pseudo", "Zoe"); put("score", 1) }, r.obj.objet("moi"))
        val moi = zoe.moi().obj
        assertEquals(5, moi.int("rang"))
        assertEquals(1, moi.int("score"))
        assertEquals(13, moi.int("classes"))

        val rienDuTout = serveur.api.get("/api/eruditus/v1/classement", zero.jeton)
        assertNull(rienDuTout.obj.objetOuNull("moi"))
        assertEquals(13, rienDuTout.obj.int("classes"))
        assertNull(zero.moi().obj.intOuNull("rang"))
        assertEquals(13, zero.moi().obj.int("classes"))
    }

    @Test
    fun `d T30 classement et moi sans jeton, lignes reduites a rang pseudo score`() {
        preparer()
        assertEquals(401, serveur.api.get("/api/eruditus/v1/classement", null).status)
        assertEquals(401, serveur.api.get("/api/eruditus/v1/moi", null).status)
        val r = serveur.api.get("/api/eruditus/v1/classement", zero.jeton)
        r.obj.tableau("top").forEach {
            assertEquals(setOf("rang", "pseudo", "score"), it.jsonObject.keys)
        }
        val moi = serveur.api.get("/api/eruditus/v1/classement", joueurs.getValue("Bob").jeton).obj.objet("moi")
        assertEquals(setOf("rang", "pseudo", "score"), moi.keys)
        assertTrue(joueurs.values.none { r.corps.contains(it.id) })
    }

    @Test
    fun `e T31 compte classe supprime puis pseudo recree`() {
        preparer()
        val alice = joueurs.getValue("alice")
        val suppression = serveur.api.post(
            "/api/eruditus/v1/comptes/suppression",
            buildJsonObject { put("motDePasse", alice.motDePasse) },
            jeton = alice.jeton,
        )
        assertEquals(204, suppression.status)
        val apres = serveur.api.get("/api/eruditus/v1/classement", zero.jeton).obj
        val top = apres.tableau("top").map { it.jsonObject }
        assertTrue(top.none { it.str("pseudo").equals("alice", ignoreCase = true) })
        assertEquals(listOf("bernard", "Bob"), top.take(2).map { it.str("pseudo") })
        assertEquals(listOf(1, 1, 3), top.take(3).map { it.int("rang") })
        assertEquals(12, apres.int("classes"))

        val nouvelle = Compte.creer(serveur, "ALICE")
        val moi = nouvelle.moi().obj
        assertEquals(0, moi.int("score"))
        assertNull(moi.intOuNull("rang"))
        assertNull(serveur.api.get("/api/eruditus/v1/classement", nouvelle.jeton).obj.objetOuNull("moi"))
        assertEquals(12, moi.int("classes"))
    }

    companion object {
        private val serveur: Serveur by lazy { Serveur("classement").demarrer() }
        private val zero: Compte by lazy { Compte.creer(serveur, "zero") }
        private val joueurs = linkedMapOf<String, Compte>()

        private val scores = linkedMapOf(
            "alice" to 5, "Bob" to 3, "bernard" to 3, "Dora" to 2,
            "Zoe" to 1, "karl" to 1, "Lea" to 1, "Jules" to 1, "ines" to 1, "Hugo" to 1, "gina" to 1, "Fred" to 1,
            "eve" to 1,
        )

        @Synchronized
        private fun preparer() {
            if (joueurs.isNotEmpty()) return
            for ((pseudo, score) in scores) {
                val compte = Compte.creer(serveur, pseudo)
                compte.jouerAvec(score)
                assertEquals(score, compte.score())
                joueurs[pseudo] = compte
            }
        }

        @AfterClass
        @JvmStatic
        fun arreter() {
            serveur.arreter()
        }
    }
}

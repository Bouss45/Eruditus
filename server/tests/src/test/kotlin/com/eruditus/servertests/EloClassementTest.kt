package com.eruditus.servertests

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Classement par Elo (spec/regles.md § 4, D108, D109) sur un serveur à lui, Elo et compteurs
 * réglés par le superutilisateur (D111). Le classement par points reste celui d'avant (D22).
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class EloClassementTest {

    private fun classement(compte: Compte) = serveur.api.get("/api/eruditus/v1/classement/elo", compte.jeton).also {
        assertEquals("$it", 200, it.status)
    }.obj

    @Test
    fun `a top 10 par Elo, ex aequo au meme rang, puis pseudo sans la casse`() {
        preparer()
        val r = classement(joueurs.getValue("alice"))
        val top = r.tableau("top").map { it.jsonObject }
        top.forEach { ContratV1Test.verifierLigneElo(it) }
        assertEquals(listOf("alice", "bernard", "Bob", "Dora", "eve", "Fred", "gina", "Hugo", "ines", "Jules"), top.map { it.str("pseudo") })
        assertEquals(listOf(1, 2, 2, 4, 5, 5, 5, 5, 5, 5), top.map { it.int("rang") })
        assertEquals(listOf(2140, 1800, 1800, 1500, 1200, 1200, 1200, 1200, 1200, 1200), top.map { it.int("elo") })
        assertEquals(13, r.int("classes"))
        assertEquals(0, r.int("restantes"))
        assertEquals(buildJsonObject { put("rang", 1); put("pseudo", "alice"); put("elo", 2140) }, r.objet("moi"))
    }

    @Test
    fun `b ma ligne sous le top 10, ex aequo coupe compris`() {
        preparer()
        assertEquals(buildJsonObject { put("rang", 13); put("pseudo", "Zoe"); put("elo", 1100) }, classement(joueurs.getValue("Zoe")).objet("moi"))
        assertEquals(buildJsonObject { put("rang", 5); put("pseudo", "Lea"); put("elo", 1200) }, classement(joueurs.getValue("Lea")).objet("moi"))
        val elo = joueurs.getValue("Zoe").elo()
        assertEquals(13, elo.int("rang"))
        assertEquals(13, elo.int("classes"))
        assertEquals(false, elo.bool("provisoire"))
    }

    @Test
    fun `c provisoire, ni ligne ni Elo au classement, meme le plus haut`() {
        preparer()
        val prov = joueurs.getValue("prov")
        val r = classement(prov)
        assertNull(r.objetOuNull("moi"))
        assertEquals(1, r.int("restantes"))
        assertEquals(13, r.int("classes"))
        assertEquals(false, r.toString().contains("2500"))
        val elo = prov.elo()
        assertEquals(2500, elo.int("valeur"))
        assertEquals(true, elo.bool("provisoire"))
        assertNull(elo.intOuNull("rang"))
        assertEquals(13, elo.int("classes"))

        // Sa 30e réponse comptée le fait classer.
        serveur.reglerJoueur(prov, 2500, 30)
        val apres = classement(prov)
        assertEquals(buildJsonObject { put("rang", 1); put("pseudo", "prov"); put("elo", 2500) }, apres.objet("moi"))
        assertEquals(14, apres.int("classes"))
        serveur.reglerJoueur(prov, 2500, 29)
    }

    @Test
    fun `d le classement par points garde son sens`() {
        preparer()
        // Personne n'a de point : classés par Elo, mais pas par points (D18, D108).
        val r = serveur.api.get("/api/eruditus/v1/classement", joueurs.getValue("alice").jeton)
        assertEquals(200, r.status)
        assertEquals(0, r.obj.tableau("top").size)
        assertNull(r.obj.objetOuNull("moi"))
        assertEquals(0, r.obj.int("classes"))
        val moi = joueurs.getValue("alice").moi().obj
        assertEquals(0, moi.int("score"))
        assertNull(moi.intOuNull("rang"))
        assertEquals(0, moi.int("classes"))
    }

    companion object {
        private val serveur: Serveur by lazy { Serveur("classement-elo").demarrer() }
        private val joueurs = linkedMapOf<String, Compte>()

        private val elos = linkedMapOf(
            "alice" to 2140, "Bob" to 1800, "bernard" to 1800, "Dora" to 1500,
            "Zoe" to 1100, "karl" to 1200, "Lea" to 1200, "Jules" to 1200, "ines" to 1200, "Hugo" to 1200,
            "gina" to 1200, "Fred" to 1200, "eve" to 1200,
        )

        @Synchronized
        private fun preparer() {
            if (joueurs.isNotEmpty()) return
            for ((pseudo, elo) in elos) {
                val compte = Compte.creer(serveur, pseudo)
                serveur.reglerJoueur(compte, elo, 30)
                joueurs[pseudo] = compte
            }
            val prov = Compte.creer(serveur, "prov")
            serveur.reglerJoueur(prov, 2500, 29)
            joueurs["prov"] = prov
        }

        @AfterClass
        @JvmStatic
        fun arreter() {
            serveur.arreter()
        }
    }
}

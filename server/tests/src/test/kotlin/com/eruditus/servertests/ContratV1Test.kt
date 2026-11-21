package com.eruditus.servertests

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrat v1 (spec/serveur.md § 5, D22) : la forme de chaque réponse. Ces tests ne sont jamais
 * modifiés, seulement complétés : le serveur peut ajouter des champs, jamais en retirer ni en
 * changer le type ou le sens.
 */
class ContratV1Test {
    private val serveur = Serveur.principal

    @Test
    fun `info donne versionMinimale`() {
        val r = serveur.api.get("/api/eruditus/v1/info")
        assertEquals(200, r.status)
        assertEquals(1000, r.obj.int("versionMinimale"))
    }

    @Test
    fun `creation de compte donne jeton, joueur et code`() {
        val pseudo = Compte.pseudoUnique("Cas")
        val r = serveur.creerCompte(texte(pseudo), texte("motdepasse-contrat"))
        assertEquals(200, r.status)
        assertTrue(r.obj.str("token").isNotEmpty())
        verifierJoueur(r.obj.objet("joueur"), pseudo)
        verifierCode(r.obj.str("codeRecuperation"))
    }

    @Test
    fun `connexion et renouvellement donnent token et record id`() {
        val compte = Compte.creer(serveur)
        val connexion = serveur.connexion(compte.pseudo, compte.motDePasse)
        assertEquals(200, connexion.status)
        val jeton = connexion.obj.str("token")
        assertEquals(compte.id, connexion.obj.objet("record").str("id"))

        val renouvellement = serveur.api.post("/api/collections/eruditus_joueurs/auth-refresh", jeton = jeton)
        assertEquals(200, renouvellement.status)
        assertTrue(renouvellement.obj.str("token").isNotEmpty())
        assertEquals(compte.id, renouvellement.obj.objet("record").str("id"))
    }

    @Test
    fun `recuperation donne jeton, joueur et nouveau code`() {
        val compte = Compte.creer(serveur)
        val r = serveur.recuperer(compte.pseudo, compte.code, "nouveau-mot-de-passe")
        assertEquals(200, r.status)
        assertTrue(r.obj.str("token").isNotEmpty())
        verifierJoueur(r.obj.objet("joueur"), compte.pseudo)
        verifierCode(r.obj.str("codeRecuperation"))
    }

    @Test
    fun `deconnexion et suppression repondent 204 sans corps`() {
        val compte = Compte.creer(serveur)
        val deconnexion = serveur.api.post("/api/eruditus/v1/comptes/deconnexion", jeton = compte.jeton)
        assertEquals(204, deconnexion.status)
        assertEquals("", deconnexion.corps)

        val autre = Compte.creer(serveur)
        val suppression = serveur.api.post(
            "/api/eruditus/v1/comptes/suppression",
            buildJsonObject { put("motDePasse", autre.motDePasse) },
            jeton = autre.jeton,
        )
        assertEquals(204, suppression.status)
        assertEquals("", suppression.corps)
    }

    @Test
    fun `moi donne joueur, score, rang et classes`() {
        val compte = Compte.creer(serveur)
        val avant = compte.moi()
        assertEquals(200, avant.status)
        verifierJoueur(avant.obj.objet("joueur"), compte.pseudo)
        assertEquals(0, avant.obj.int("score"))
        assertNull(avant.obj.intOuNull("rang"))
        assertTrue(avant.obj.int("classes") >= 0)

        compte.jouerAvec(1)
        val apres = compte.moi()
        assertEquals(1, apres.obj.int("score"))
        assertTrue(apres.obj.int("rang") >= 1)
        assertTrue(apres.obj.int("classes") >= 1)
    }

    @Test
    fun `partie, question, reponse et bilan`() {
        val compte = Compte.creer(serveur)
        val creation = compte.nouvellePartie()
        assertEquals(200, creation.status)
        val partie = creation.obj.objet("partie")
        val id = partie.str("id")
        assertEquals(5, partie.int("total"))
        verifierQuestion(creation.obj.objet("question"), 1)

        val redemandee = compte.question(id)
        assertEquals(200, redemandee.status)
        val question = redemandee.obj.objet("question")
        verifierQuestion(question, 1)

        val reponse = compte.repondre(id, 1, Banque.bonne(question.str("enonce")))
        assertEquals(200, reponse.status)
        verifierCorrection(reponse.obj.objet("correction"), 1)
        assertNull(reponse.obj.objetOuNull("bilan"))

        var derniere = reponse.obj
        for (numero in 2..5) {
            val q = compte.question(id)
            assertEquals(200, q.status)
            verifierQuestion(q.obj.objet("question"), numero)
            derniere = compte.repondre(id, numero, null).obj
            verifierCorrection(derniere.objet("correction"), numero)
        }
        val bilan = derniere.objet("bilan")
        assertEquals(1, bilan.int("bonnes"))
        assertEquals(1, bilan.int("points"))
        assertEquals(compte.score(), bilan.int("score"))
    }

    @Test
    fun `classement donne top, moi et classes`() {
        val compte = Compte.creer(serveur)
        val vide = serveur.api.get("/api/eruditus/v1/classement", compte.jeton)
        assertEquals(200, vide.status)
        vide.obj.tableau("top").forEach { verifierLigne(it.jsonObject) }
        assertNull(vide.obj.objetOuNull("moi"))
        assertTrue(vide.obj.int("classes") >= 0)

        compte.jouerAvec(2)
        val r = serveur.api.get("/api/eruditus/v1/classement", compte.jeton)
        val moi = r.obj.objet("moi")
        verifierLigne(moi)
        assertEquals(compte.pseudo, moi.str("pseudo"))
        assertEquals(2, moi.int("score"))
    }

    @Test
    fun `changement de pseudo donne joueur`() {
        val compte = Compte.creer(serveur)
        val pseudo = Compte.pseudoUnique("Contrat")
        val r = serveur.changerPseudo(compte.jeton, pseudo)
        assertEquals(200, r.status)
        verifierJoueur(r.obj.objet("joueur"), pseudo)
        assertEquals(compte.id, r.obj.objet("joueur").str("id"))
    }

    @Test
    fun `changement de mot de passe donne jeton et joueur`() {
        val compte = Compte.creer(serveur)
        val r = serveur.changerMotDePasse(compte.jeton, compte.motDePasse, "nouveau-mot-de-passe")
        assertEquals(200, r.status)
        assertTrue(r.obj.str("token").isNotEmpty())
        verifierJoueur(r.obj.objet("joueur"), compte.pseudo)
        assertEquals(compte.id, r.obj.objet("joueur").str("id"))
    }

    @Test
    fun `avis donne un objet vide`() {
        val avis = Serveur.avis
        val compte = Compte.creer(avis)
        val r = avis.avis(compte.jeton, "Contrat de la route des avis.")
        assertEquals("$r", 200, r.status)
        assertEquals(JsonObject(emptyMap()), r.obj)
    }

    @Test
    fun `une erreur a status, message et data code`() {
        val r = serveur.creerCompte(texte("a"), texte("motdepasse"))
        assertEquals(400, r.status)
        assertEquals(400, r.obj.int("status"))
        assertTrue(r.obj.str("message").isNotEmpty())
        assertEquals("pseudo_invalide", r.obj.objet("data").str("code"))
    }

    companion object {
        private val CODE = Regex("^[0-9A-HJKMNP-TV-Z]{4}(-[0-9A-HJKMNP-TV-Z]{4}){3}$")
        private val RAISONS = setOf("gagne", "fausse", "sans_reponse", "hors_delai", "deja_reussie")

        fun verifierCode(code: String) = assertTrue("Code mal formé : $code", CODE.matches(code))

        fun verifierJoueur(joueur: JsonObject, pseudo: String) {
            assertTrue(joueur.str("id").isNotEmpty())
            assertEquals(pseudo, joueur.str("pseudo"))
        }

        fun verifierQuestion(question: JsonObject, numero: Int, delaiMs: Int = 20_000) {
            assertEquals(numero, question.int("numero"))
            assertEquals(5, question.int("total"))
            assertTrue(question.str("enonce").isNotEmpty())
            val choix = question.tableau("choix")
            assertEquals(4, choix.size)
            choix.forEach { assertTrue(it is JsonPrimitive && it.isString && it.content.isNotEmpty()) }
            assertEquals(delaiMs, question.int("delaiMs"))
            val restant = question.int("restantMs")
            assertTrue("restantMs = $restant", restant in 0..delaiMs)
            assertTrue("Pas de bonne réponse dans une question : $question", "bonneReponse" !in question)
        }

        fun verifierCorrection(correction: JsonObject, numero: Int) {
            assertEquals(numero, correction.int("numero"))
            correction.intOuNull("choix")?.let { assertTrue(it in 0..3) }
            correction.bool("correcte")
            assertTrue(correction.int("bonneReponse") in 0..3)
            correction.bool("horsDelai")
            assertTrue(correction.int("point") in 0..1)
            assertTrue(correction.str("raison") in RAISONS)
            assertTrue(correction.int("score") >= 0)
        }

        fun verifierLigne(ligne: JsonObject) {
            assertEquals("Une ligne ne contient que rang, pseudo et score (T30) : $ligne",
                setOf("rang", "pseudo", "score"), ligne.keys)
            assertTrue(ligne.int("rang") >= 1)
            assertTrue(ligne.str("pseudo").isNotEmpty())
            assertTrue(ligne.int("score") >= 1)
        }

    }
}

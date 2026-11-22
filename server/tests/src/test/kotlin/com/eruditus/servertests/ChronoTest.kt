package com.eruditus.servertests

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chrono et inactivité, sur le serveur aux délais raccourcis (délai 1 000 ms, marge 500 ms,
 * inactivité 3 000 ms) : T23, T24, T26.
 */
class ChronoTest {
    private val serveur = Serveur.chronoCourt

    @Test
    fun `les delais viennent des variables ERUDITUS`() {
        val compte = Compte.creer(serveur)
        val (_, q1) = compte.commencer()
        ContratV1Test.verifierQuestion(q1, 1, delaiMs = 1000)
    }

    @Test
    fun `T23 repondre apres le delai et la marge`() {
        val compte = Compte.creer(serveur)
        val (id, q1) = compte.commencer()
        Thread.sleep(1_700)
        val r = compte.repondre(id, 1, Banque.bonne(q1.str("enonce")))
        assertEquals(200, r.status)
        val correction = r.obj.objet("correction")
        assertEquals("hors_delai", correction.str("raison"))
        assertTrue(correction.bool("horsDelai"))
        assertFalse(correction.bool("correcte"))
        assertEquals(0, correction.int("point"))
        assertEquals(Banque.bonne(q1.str("enonce")), correction.int("bonneReponse"))
        assertEquals(Banque.bonne(q1.str("enonce")), correction.int("choix"))
        assertEquals(0, compte.score())
        val enregistree = serveur.lister("eruditus_reponses", "partie = '$id' && numero = 1").single()
        assertTrue(enregistree.bool("hors_delai"))
        assertEquals(0, enregistree.int("points"))
    }

    @Test
    fun `T45 hors delai, la correction donne l'explication et la source de la question`() {
        val compte = Compte.creer(serveur)
        // Une question 1 qui a une explication (8 sur 12 dans la banque d'exemple) : au plus 5
        // parties, les questions jamais servies passant d'abord.
        var partie = compte.commencer()
        for (essai in 1..4) {
            if (Banque.explication(partie.second.str("enonce")) != null) break
            partie = compte.commencer()
        }
        val (id, q1) = partie
        val enonce = q1.str("enonce")
        Thread.sleep(1_700)
        val correction = compte.repondre(id, 1, null).obj.objet("correction")
        assertEquals("hors_delai", correction.str("raison"))
        assertEquals(Banque.explication(enonce)!!, correction.strOuNull("explication"))
        assertEquals(Banque.source(enonce), correction.strOuNull("source"))
    }

    @Test
    fun `hors delai l'emporte sur sans reponse`() {
        val compte = Compte.creer(serveur)
        val (id, _) = compte.commencer()
        Thread.sleep(1_700)
        val correction = compte.repondre(id, 1, null).obj.objet("correction")
        assertEquals("hors_delai", correction.str("raison"))
        assertTrue(correction.bool("horsDelai"))
    }

    @Test
    fun `dans la marge la reponse compte encore`() {
        val compte = Compte.creer(serveur)
        val (id, q1) = compte.commencer()
        Thread.sleep(1_150)
        val question = compte.question(id).obj.objet("question")
        assertEquals(0, question.int("restantMs"))
        val correction = compte.repondre(id, 1, Banque.bonne(q1.str("enonce"))).obj.objet("correction")
        assertEquals("gagne", correction.str("raison"))
        assertFalse(correction.bool("horsDelai"))
        assertEquals(1, compte.score())
    }

    @Test
    fun `T24 le temps restant continue de baisser jusqu'a 0`() {
        val compte = Compte.creer(serveur)
        val (id, q1) = compte.commencer()
        Thread.sleep(300)
        val a = compte.question(id).obj.objet("question").int("restantMs")
        Thread.sleep(300)
        val b = compte.question(id).obj.objet("question").int("restantMs")
        assertTrue("$a puis $b", b < a && a < q1.int("restantMs"))
        Thread.sleep(800)
        assertEquals(0, compte.question(id).obj.objet("question").int("restantMs"))
        // La question suivante démarre son propre chrono : le temps de lecture ne compte pas.
        compte.repondre(id, 1, null)
        Thread.sleep(1_000)
        val q2 = compte.question(id).obj.objet("question")
        assertEquals(2, q2.int("numero"))
        assertTrue(q2.int("restantMs") > 900)
    }

    @Test
    fun `T26 partie abandonnee apres l'inactivite`() {
        val compte = Compte.creer(serveur)
        val (id, q1) = compte.commencer()
        compte.repondre(id, 1, Banque.bonne(q1.str("enonce")))
        Thread.sleep(3_400)
        compte.question(id).doitEtreErreur(409, "partie_finie")
        compte.repondre(id, 2, 0).doitEtreErreur(409, "partie_finie")
        assertEquals(1, serveur.compter("eruditus_parties", "id = '$id' && etat = 'abandonnee'"))
        // Les réponses déjà envoyées restent acquises.
        assertEquals(1, compte.score())
    }

    @Test
    fun `l'activite repousse l'abandon`() {
        val compte = Compte.creer(serveur)
        val (id, _) = compte.commencer()
        Thread.sleep(2_000)
        compte.repondre(id, 1, null)
        Thread.sleep(2_000)
        assertEquals(200, compte.question(id).status)
        Thread.sleep(2_000)
        assertEquals(200, compte.repondre(id, 2, null).status)
    }
}

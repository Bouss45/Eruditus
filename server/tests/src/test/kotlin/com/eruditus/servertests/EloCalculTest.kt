package com.eruditus.servertests

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Chaque pas de la formule de l'Elo (spec/regles.md § 8.1 – § 8.3, § 8.6, § 8.7, D103, D104,
 * D107) : la formule de référence d'abord, confrontée aux exemples de la spec, puis le serveur,
 * confronté à elle. Compteurs et difficultés réglés par le superutilisateur (D111).
 */
class EloCalculTest {
    private val serveur = Serveur.elo

    @Before
    fun questionsAuDepart() = serveur.remettreQuestions()

    private fun ligneDe(compte: Compte, enonce: String) =
        serveur.lignesDuJoueur(compte).single { it.str("question") == serveur.questionParEnonce(enonce).str("id") }

    // --- La référence, contre les exemples écrits dans la spec ----------------------------------

    @Test
    fun `reference conforme aux exemples de la spec`() {
        // Attendu : au moins 25 % (QCM à 4 choix), 0,625 à niveau égal, jamais 1.
        assertEquals(0.625, Elo.attendu(1600, 1600), 0.0)
        assertTrue(Elo.attendu(100, 2400) > 0.25 && Elo.attendu(100, 2400) < 0.2501)
        assertTrue(Elo.attendu(2400, 100) < 1.0)
        // 1000 contre 1600 (K = 40) : 1029 si juste, 989 si faux.
        val e = Elo.attendu(1000, 1600)
        assertEquals(1029, Elo.apres(1000, 40, 1, e))
        assertEquals(989, Elo.apres(1000, 40, 0, e))
        // À niveau égal, K = 24 : +9, −15 ; K = 12 : ,5 vers le haut des deux côtés.
        assertEquals(1609, Elo.apres(1600, 24, 1, 0.625))
        assertEquals(1585, Elo.apres(1600, 24, 0, 0.625))
        assertEquals(1605, Elo.apres(1600, 12, 1, 0.625)) // 1604,5
        assertEquals(1593, Elo.apres(1600, 12, 0, 0.625)) // 1592,5
        // 2300 contre 800 (K = 24) : une erreur coûte 24, une bonne réponse ne rapporte rien.
        assertEquals(2276, Elo.apres(2300, 24, 0, Elo.attendu(2300, 800)))
        assertEquals(2300, Elo.apres(2300, 24, 1, Elo.attendu(2300, 800)))
        // Plafond naturel (§ 8.7) : plus rien au-delà de + 618 (K = 24) ou + 492 (K = 12).
        assertEquals(2400 + 619, Elo.apres(2400 + 619, 24, 1, Elo.attendu(2400 + 619, 2400)))
        assertEquals(2400 + 493, Elo.apres(2400 + 493, 12, 1, Elo.attendu(2400 + 493, 2400)))
        assertTrue(Elo.apres(2400 + 600, 24, 1, Elo.attendu(2400 + 600, 2400)) > 2400 + 600)
        assertTrue(Elo.apres(2400 + 480, 12, 1, Elo.attendu(2400 + 480, 2400)) > 2400 + 480)
        // Plancher.
        assertEquals(100, Elo.apres(100, 40, 0, Elo.attendu(100, 800)))
        // K selon les réponses comptées avant celle-ci.
        assertEquals(listOf(40, 40, 24, 24, 12), listOf(0, 29, 30, 199, 200).map { Elo.k(it) })
        // Difficulté : K de 8, bornée à ± 300 autour de la valeur du niveau.
        assertEquals(1605, Elo.difficulte(1600, 3, 0, 0.625))
        assertEquals(1597, Elo.difficulte(1600, 3, 1, 0.625))
        assertEquals(1900, Elo.difficulte(1898, 3, 0, 0.99))
        assertEquals(1300, Elo.difficulte(1302, 3, 1, 0.25))
    }

    // --- Le serveur, contre la référence --------------------------------------------------------

    @Test
    fun `attendu d'au moins 25 pourcent et plancher a 100`() {
        val compte = Compte.creer(serveur)
        serveur.reglerJoueur(compte, 100, 0)
        val difficile = Banque.questions[4].str("enonce") // niveau 5 : 2400
        compte.jouer { enonce, _ -> if (enonce == difficile) Banque.bonne(enonce) else Banque.fausse(enonce) }
        // Les erreurs à 100 ne descendent pas plus bas : la question à 2400 part de 100.
        val ligne = ligneDe(compte, difficile)
        assertEquals(100, ligne.int("elo_avant"))
        assertEquals(2400, ligne.int("elo_difficulte"))
        // 100 + 40 × (1 − 0,25…) = 130 ; sans les 25 % du QCM, ce serait 140.
        assertEquals(130, ligne.int("elo_apres"))
        serveur.verifierChaine(compte, depart = 100)
    }

    @Test
    fun `plancher, tout faux a 100 reste a 100`() {
        val compte = Compte.creer(serveur)
        serveur.reglerJoueur(compte, 100, 0)
        compte.jouer { enonce, _ -> Banque.fausse(enonce) }
        assertTrue(serveur.lignesDuJoueur(compte).all { it.int("elo_apres") == 100 })
        assertEquals(100, serveur.verifierChaine(compte, depart = 100))
    }

    @Test
    fun `K de 40 puis 24 a 30 reponses comptees`() {
        val compte = Compte.creer(serveur)
        serveur.reglerJoueur(compte, 1000, 28)
        val avant = compte.elo()
        assertTrue(avant.bool("provisoire"))
        assertEquals(2, avant.int("restantes"))
        assertNull(avant.intOuNull("rang"))
        compte.jouer { enonce, numero -> if (numero % 2 == 0) Banque.bonne(enonce) else Banque.fausse(enonce) }
        assertEquals(listOf(40, 40, 24, 24, 24), serveur.lignesDuJoueur(compte).map { it.int("elo_k") })
        serveur.verifierChaine(compte, reponses = 28)
        val apres = compte.elo()
        assertEquals(false, apres.bool("provisoire"))
        assertEquals(0, apres.int("restantes"))
        assertNotNull(apres.intOuNull("rang"))
    }

    @Test
    fun `K de 24 puis 12 a 200 reponses comptees`() {
        val compte = Compte.creer(serveur)
        serveur.reglerJoueur(compte, 1500, 198)
        compte.jouer { enonce, numero -> if (numero % 2 == 1) Banque.bonne(enonce) else Banque.fausse(enonce) }
        assertEquals(listOf(24, 24, 12, 12, 12), serveur.lignesDuJoueur(compte).map { it.int("elo_k") })
        serveur.verifierChaine(compte, depart = 1500, reponses = 198)
    }

    @Test
    fun `arrondi a l'entier le plus proche, demi vers le haut`() {
        // Toutes les questions à 1600, le joueur aussi, K = 12 : perte de 7,5, gain de 4,5.
        for (q in serveur.lister("eruditus_questions")) serveur.regler("eruditus_questions", q.str("id"), "difficulte" to 1600)
        val compte = Compte.creer(serveur)
        serveur.reglerJoueur(compte, 1600, 200)
        val (id, _) = compte.commencer()
        assertEquals(1593, serveur.ligne(id, 1).int("elo_apres")) // 1592,5
        assertEquals(1593, compte.elo().int("valeur"))
        val q1 = serveur.fiche("eruditus_questions", serveur.ligne(id, 1).str("question"))
        assertEquals(200, compte.repondre(id, 1, Banque.bonne(q1.str("enonce"))).status)
        assertEquals(1605, serveur.ligne(id, 1).int("elo_apres")) // 1604,5
        assertEquals(1605, compte.elo().int("valeur"))
        serveur.verifierChaine(compte, depart = 1600, reponses = 200)
    }

    @Test
    fun `plafond naturel, une bonne reponse ne rapporte plus rien, une erreur coute presque K`() {
        val compte = Compte.creer(serveur)
        serveur.reglerJoueur(compte, 3100, 30)
        compte.jouer { enonce, numero -> if (numero < 5) Banque.bonne(enonce) else Banque.fausse(enonce) }
        val lignes = serveur.lignesDuJoueur(compte)
        lignes.take(4).forEach { assertEquals("Gain nul à 3100 : $it", 3100, it.int("elo_apres")) }
        assertTrue("Une erreur coûte presque 24 : ${lignes[4]}", lignes[4].int("elo_apres") in 3100 - 24..3100 - 23)
        serveur.verifierChaine(compte, depart = 3100, reponses = 30)
    }

    @Test
    fun `difficulte, 30 joueurs classes comptent, puis K de 8, bornee a plus ou moins 300`() {
        val cible = Banque.questions[2].str("enonce") // niveau 3 : 1600
        val idCible = serveur.questionParEnonce(cible).str("id")
        fun question() = serveur.fiche("eruditus_questions", idCible)
        fun classeQuiJoue(elo: Int, juste: Boolean): Compte {
            val compte = Compte.creer(serveur)
            serveur.reglerJoueur(compte, elo, 30)
            // Les autres questions sont laissées sans réponse : elles ne bougent pas (T52).
            compte.jouer { enonce, _ -> if (enonce != cible) null else if (juste) Banque.bonne(enonce) else Banque.fausse(enonce) }
            return compte
        }

        // Les 30 premières réponses de joueurs classés ne font que compter.
        for (i in 1..30) {
            classeQuiJoue(1600, juste = false)
            assertEquals(1600, question().int("difficulte"))
            assertEquals(i, question().int("difficulte_reponses"))
        }
        serveur.lister("eruditus_questions").filter { it.str("id") != idCible }.forEach {
            assertEquals("Sans réponse : rien ne bouge, $it", 0, it.int("difficulte_reponses"))
        }

        // La 31e, ratée : la question monte de 8 × E.
        val trenteEtUnieme = classeQuiJoue(1600, juste = false)
        val l31 = ligneDe(trenteEtUnieme, cible)
        val apres31 = Elo.difficulte(1600, 3, 0, Elo.attendu(l31.int("elo_avant"), 1600))
        assertTrue(apres31 > 1600)
        assertEquals(apres31, question().int("difficulte"))
        assertEquals(31, question().int("difficulte_reponses"))

        // La 32e, réussie : elle redescend, depuis la difficulté actuelle, avec l'attendu du joueur.
        val trenteDeuxieme = classeQuiJoue(1600, juste = true)
        val l32 = ligneDe(trenteDeuxieme, cible)
        assertEquals(apres31, l32.int("elo_difficulte"))
        assertEquals(Elo.difficulte(apres31, 3, 1, Elo.attendu(l32.int("elo_avant"), apres31)), question().int("difficulte"))

        // Bornes : 1300 – 1900 pour un niveau 3.
        serveur.regler("eruditus_questions", idCible, "difficulte" to 1898, "difficulte_reponses" to 40)
        classeQuiJoue(3000, juste = false)
        assertEquals(1900, question().int("difficulte"))
        serveur.regler("eruditus_questions", idCible, "difficulte" to 1302)
        classeQuiJoue(100, juste = true)
        assertEquals(1300, question().int("difficulte"))
    }

    @Test
    fun `question sans niveau, aucun Elo ne bouge, ni le sien`() {
        // Banque d'exemple : 7 questions à niveau, 5 sans. En 3 parties, les 12 sont servies.
        val principal = Serveur.principal
        val compte = Compte.creer(principal)
        repeat(3) { compte.jouer { enonce, numero -> if (numero % 2 == 0) Banque.bonne(enonce) else Banque.fausse(enonce) } }
        val sansNiveau = principal.lister("eruditus_questions").filter { it.int("niveau") == 0 }
        assertEquals(5, sansNiveau.size)
        principal.verifierChaine(compte)
        val avecNiveau = Banque.questions.count { "niveau" in it }
        assertEquals(avecNiveau, principal.joueurBase(compte).int("elo_reponses"))
        sansNiveau.forEach {
            assertEquals(0, it.int("difficulte"))
            assertEquals(0, it.int("difficulte_reponses"))
        }
    }
}

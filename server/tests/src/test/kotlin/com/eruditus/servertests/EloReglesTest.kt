package com.eruditus.servertests

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Règles de l'Elo (spec/regles.md § 8.4 – § 8.6, D105 – D107) : perte comptée à la mise en
 * service, gain à la réponse correcte, abandon et « sans réponse », question redemandée,
 * révision, variation de la partie. Banque de 5 questions à niveau : chaque partie les sert
 * toutes.
 */
class EloReglesTest {
    private val serveur = Serveur.elo

    @Before
    fun questionsAuDepart() = serveur.remettreQuestions()

    private fun enonce(partie: String, numero: Int): String =
        serveur.fiche("eruditus_questions", serveur.ligne(partie, numero).str("question")).str("enonce")

    @Test
    fun `perte comptee a la mise en service, puis remplacee par le gain`() {
        val compte = Compte.creer(serveur)
        val (id, q1) = compte.commencer()
        val ligne = serveur.ligne(id, 1)
        val e = Elo.attendu(1000, ligne.int("elo_difficulte"))
        val perte = Elo.apres(1000, 40, 0, e)
        // Avant toute réponse, /moi compte déjà la perte et le compteur a avancé.
        assertEquals(1000, ligne.int("elo_avant"))
        assertEquals(40, ligne.int("elo_k"))
        assertEquals(perte, ligne.int("elo_apres"))
        assertEquals(perte, compte.elo().int("valeur"))
        assertEquals(29, compte.elo().int("restantes"))
        assertEquals(1, serveur.joueurBase(compte).int("elo_reponses"))

        assertEquals(200, compte.repondre(id, 1, Banque.bonne(q1.str("enonce"))).status)
        val gain = Elo.apres(1000, 40, 1, e)
        assertEquals(gain, serveur.ligne(id, 1).int("elo_apres"))
        assertEquals(gain, compte.elo().int("valeur"))
        assertEquals(1, serveur.joueurBase(compte).int("elo_reponses"))

        // Fausse : la perte reste.
        val q2 = compte.question(id).obj.objet("question")
        val perte2 = serveur.ligne(id, 2).int("elo_apres")
        assertEquals(perte2, compte.elo().int("valeur"))
        assertEquals(200, compte.repondre(id, 2, Banque.fausse(q2.str("enonce"))).status)
        assertEquals(perte2, compte.elo().int("valeur"))
        serveur.verifierChaine(compte)
    }

    @Test
    fun `abandon, sans reponse et hors delai comptent faux et ne changent pas la difficulte`() {
        val compte = Compte.creer(serveur)
        serveur.reglerJoueur(compte, 1500, 30)
        // Toutes les questions ont passé leurs 30 réponses : une réponse donnée les ferait bouger.
        for (q in serveur.lister("eruditus_questions")) serveur.regler("eruditus_questions", q.str("id"), "difficulte_reponses" to 30)
        val difficultes = serveur.lister("eruditus_questions").associate { it.str("id") to it.int("difficulte") }

        val (id, _) = compte.commencer()
        assertEquals("sans_reponse", compte.repondre(id, 1, null).obj.objet("correction").str("raison"))
        compte.question(id)
        serveur.vieillir(serveur.ligne(id, 2))
        val horsDelai = compte.repondre(id, 2, Banque.bonne(enonce(id, 2))).obj.objet("correction")
        assertEquals("hors_delai", horsDelai.str("raison"))
        compte.question(id)
        val perteEnAttente = serveur.ligne(id, 3).int("elo_apres")
        // Nouvelle partie : la question 3 de la première est abandonnée, sa perte reste.
        compte.commencer()
        compte.repondre(id, 3, Banque.bonne(enonce(id, 3))).doitEtreErreur(409, "partie_finie")
        assertEquals(perteEnAttente, serveur.ligne(id, 3).int("elo_apres"))
        assertTrue(serveur.ligne(id, 3).str("repondue_le").isEmpty())

        for (q in serveur.lister("eruditus_questions")) {
            assertEquals("$q", difficultes.getValue(q.str("id")), q.int("difficulte"))
            assertEquals("$q", 30, q.int("difficulte_reponses"))
        }
        serveur.verifierChaine(compte, depart = 1500, reponses = 30)
    }

    @Test
    fun `question redemandee apres un retour, une seule perte, gain depuis l'Elo d'avant`() {
        val compte = Compte.creer(serveur)
        val (id, q1) = compte.commencer()
        val servie = serveur.ligne(id, 1)
        repeat(3) {
            val r = compte.question(id)
            assertEquals(200, r.status)
            assertEquals(1, r.obj.objet("question").int("numero"))
        }
        val redemandee = serveur.ligne(id, 1)
        for (champ in listOf("elo_avant", "elo_apres", "elo_difficulte", "elo_k")) {
            assertEquals(champ, servie.int(champ), redemandee.int(champ))
        }
        assertEquals(1, serveur.joueurBase(compte).int("elo_reponses"))
        assertEquals(servie.int("elo_apres"), compte.elo().int("valeur"))
        assertEquals(200, compte.repondre(id, 1, Banque.bonne(q1.str("enonce"))).status)
        assertEquals(Elo.apres(1000, 40, 1, Elo.attendu(1000, servie.int("elo_difficulte"))), compte.elo().int("valeur"))
        serveur.verifierChaine(compte)
    }

    @Test
    fun `tout vu, les parties sont des revisions sans effet`() {
        val compte = Compte.creer(serveur)
        serveur.reglerJoueur(compte, 1500, 30)
        assertEquals(false, compte.elo().bool("revision"))
        val premiere = compte.jouer { enonce, numero -> if (numero <= 2) Banque.bonne(enonce) else Banque.fausse(enonce) }
        assertEquals(false, premiere.last().objet("bilan").objet("elo").bool("revision"))
        assertEquals(true, compte.elo().bool("revision"))
        val elo = compte.elo().int("valeur")
        val questions = serveur.questionsParEnonce().mapValues { it.value.int("difficulte") to it.value.int("difficulte_reponses") }

        // Seconde partie, tout juste : les points suivent D17, l'Elo ne bouge pas.
        val seconde = compte.jouer { enonce, _ -> Banque.bonne(enonce) }
        assertEquals(mapOf("deja_reussie" to 2, "gagne" to 3), seconde.groupingBy { it.objet("correction").str("raison") }.eachCount())
        val bilan = seconde.last().objet("bilan").objet("elo")
        assertEquals(true, bilan.bool("revision"))
        assertEquals(0, bilan.int("variation"))
        assertEquals(elo, bilan.int("valeur"))
        assertEquals(elo, compte.elo().int("valeur"))
        assertEquals(35, serveur.joueurBase(compte).int("elo_reponses"))
        assertEquals(questions, serveur.questionsParEnonce().mapValues { it.value.int("difficulte") to it.value.int("difficulte_reponses") })
        serveur.verifierChaine(compte, depart = 1500, reponses = 30)
    }

    @Test
    fun `variation de la partie, somme de ses lignes comptees, revision fausse s'il y a une neuve`() {
        val compte = Compte.creer(serveur)
        // Question vue puis abandonnée : elle ne comptera plus.
        val (abandonnee, _) = compte.commencer()
        val vue = serveur.ligne(abandonnee, 1).str("question")
        val apresAbandon = compte.elo().int("valeur")

        val reponses = compte.jouer { enonce, numero -> if (numero % 2 == 1) Banque.bonne(enonce) else Banque.fausse(enonce) }
        val partie = serveur.lignes(serveur.lignesDuJoueur(compte).last().str("partie"))
        // Les 4 jamais servies d'abord, puis la vue.
        assertEquals(vue, partie[4].str("question"))
        assertEquals(0, partie[4].int("elo_avant"))
        val variation = partie.filter { it.int("elo_avant") > 0 }.sumOf { it.int("elo_apres") - it.int("elo_avant") }
        val bilan = reponses.last().objet("bilan").objet("elo")
        assertEquals(variation, bilan.int("variation"))
        assertEquals(apresAbandon + variation, bilan.int("valeur"))
        assertEquals(false, bilan.bool("revision"))
        assertEquals(5, serveur.joueurBase(compte).int("elo_reponses"))
        serveur.verifierChaine(compte)
    }
}

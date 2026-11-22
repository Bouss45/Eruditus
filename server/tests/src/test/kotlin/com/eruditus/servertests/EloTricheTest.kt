package com.eruditus.servertests

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tentatives de triche de l'Elo, T47 – T53 (spec/serveur.md § 7, D113), et la nouvelle route sans
 * jeton. « Elo inchangé » se lit par le superutilisateur.
 */
class EloTricheTest {
    private val serveur = Serveur.elo

    @Before
    fun questionsAuDepart() = serveur.remettreQuestions()

    private fun enonce(partie: String, numero: Int): String =
        serveur.fiche("eruditus_questions", serveur.ligne(partie, numero).str("question")).str("enonce")

    private fun <T> enParallele(n: Int, action: () -> T): List<T> {
        val pool = Executors.newFixedThreadPool(n)
        try {
            return pool.invokeAll((1..n).map { Callable { action() } }).map { it.get() }
        } finally {
            pool.shutdown()
        }
    }

    /** Un joueur classé, et toutes les questions passées à 30 réponses : une réponse donnée les ferait bouger. */
    private fun classeEtQuestionsMobiles(elo: Int = 1500): Compte {
        val compte = Compte.creer(serveur)
        serveur.reglerJoueur(compte, elo, 30)
        for (q in serveur.lister("eruditus_questions")) serveur.regler("eruditus_questions", q.str("id"), "difficulte_reponses" to 30)
        return compte
    }

    private fun difficultes() = serveur.lister("eruditus_questions").associate { it.str("id") to (it.int("difficulte") to it.int("difficulte_reponses")) }

    @Test
    fun `T47 abandonner ne protege jamais l'Elo`() {
        val compte = classeEtQuestionsMobiles()
        val questions = difficultes()

        // Quitter sans répondre : la perte est déjà dans /moi.
        val (a, _) = compte.commencer()
        val perteA = serveur.ligne(a, 1).int("elo_apres")
        assertTrue(perteA < 1500)
        assertEquals(perteA, compte.elo().int("valeur"))

        // Laisser filer le chrono : la réponse tardive est hors délai, la perte reste.
        serveur.vieillir(serveur.ligne(a, 1))
        assertEquals("hors_delai", compte.repondre(a, 1, Banque.bonne(enonce(a, 1))).obj.objet("correction").str("raison"))
        assertEquals(perteA, compte.elo().int("valeur"))

        // Créer une autre partie : la question en attente garde sa perte, la réponse est refusée.
        compte.question(a)
        val perteEnAttente = compte.elo().int("valeur")
        compte.commencer()
        val apresNouvelle = compte.elo().int("valeur")
        assertTrue(apresNouvelle <= perteEnAttente)
        compte.repondre(a, 2, Banque.bonne(enonce(a, 2))).doitEtreErreur(409, "partie_finie")
        assertEquals(apresNouvelle, compte.elo().int("valeur"))

        // Rester inactif (4 s sur ce serveur) : la partie est abandonnée, rien ne revient.
        val (c, _) = compte.commencer()
        val avantInactivite = compte.elo().int("valeur")
        Thread.sleep(4_300)
        compte.repondre(c, 1, Banque.bonne(enonce(c, 1))).doitEtreErreur(409, "partie_finie")
        assertEquals(avantInactivite, compte.elo().int("valeur"))

        assertEquals(questions, difficultes())
        serveur.verifierChaine(compte, depart = 1500, reponses = 30)
    }

    @Test
    fun `T48 double envoi, un seul gain, une seule perte`() {
        val compte = classeEtQuestionsMobiles()
        val (id, q1) = compte.commencer()
        val bonne = Banque.bonne(q1.str("enonce"))
        val statuts = enParallele(10) { compte.repondre(id, 1, bonne).status }
        assertEquals(1, statuts.count { it == 200 })
        compte.repondre(id, 1, Banque.fausse(q1.str("enonce"))).doitEtreErreur(409, "deja_repondu")
        val apresGain = compte.elo().int("valeur")
        val question1 = serveur.fiche("eruditus_questions", serveur.ligne(id, 1).str("question"))
        assertEquals(31, question1.int("difficulte_reponses"))

        val numeros = enParallele(10) { compte.question(id).obj.objet("question").int("numero") }
        assertEquals(List(10) { 2 }, numeros)
        assertEquals(2, serveur.lignes(id).size)
        assertEquals(32, serveur.joueurBase(compte).int("elo_reponses"))
        assertEquals(serveur.ligne(id, 2).int("elo_apres"), compte.elo().int("valeur"))
        assertEquals(apresGain, serveur.ligne(id, 2).int("elo_avant"))
        serveur.verifierChaine(compte, depart = 1500, reponses = 30)
    }

    @Test
    fun `T49 rejouer une question vue ne change rien, le point suit D17`() {
        val compte = classeEtQuestionsMobiles()
        // Question vue puis abandonnée, puis toutes vues, une partie ratée.
        compte.commencer()
        compte.jouer { enonce, _ -> Banque.fausse(enonce) }
        assertEquals(true, compte.elo().bool("revision"))
        val elo = compte.elo().int("valeur")
        val reponses = serveur.joueurBase(compte).int("elo_reponses")
        val questions = difficultes()

        // Tout juste en révision : un point chacune (jamais réussies), aucun Elo.
        val partie = compte.jouer { enonce, _ -> Banque.bonne(enonce) }
        assertTrue(partie.all { it.objet("correction").str("raison") == "gagne" })
        val bilan = partie.last().objet("bilan").objet("elo")
        assertEquals(true, bilan.bool("revision"))
        assertEquals(0, bilan.int("variation"))
        assertEquals(elo, compte.elo().int("valeur"))
        assertEquals(reponses, serveur.joueurBase(compte).int("elo_reponses"))
        assertEquals(questions, difficultes())

        // Encore une fois : déjà réussies.
        val encore = compte.jouer { enonce, _ -> Banque.bonne(enonce) }
        assertTrue(encore.all { it.objet("correction").str("raison") == "deja_reussie" })
        assertEquals(elo, compte.elo().int("valeur"))
        serveur.verifierChaine(compte, depart = 1500, reponses = 30)
    }

    @Test
    fun `T50 un Elo envoye par l'app est ignore`() {
        val forges: Map<String, JsonElement> = listOf(
            "elo", "eloAvant", "eloApres", "variation", "difficulte", "niveau", "k", "provisoire", "restantes",
            "reponsesComptees", "elo_reponses", "elo_avant", "elo_apres", "elo_k", "difficulte_reponses",
        ).associateWith { if (it == "provisoire") JsonPrimitive(false) else JsonPrimitive(9999) }

        val pseudo = Compte.pseudoUnique("Forge")
        val creation = serveur.api.post("/api/eruditus/v1/comptes", buildJsonObject {
            put("pseudo", pseudo)
            put("motDePasse", "motdepasse-forge")
            forges.forEach { (cle, valeur) -> put(cle, valeur) }
        })
        assertEquals(200, creation.status)
        val compte = Compte(serveur, pseudo, "motdepasse-forge", creation.obj.str("token"), creation.obj.objet("joueur").str("id"), "")
        assertEquals(1000, compte.elo().int("valeur"))
        assertEquals(0, serveur.joueurBase(compte).int("elo_reponses"))

        val avecForges = objetAvec(forges)
        val partie = serveur.api.post("/api/eruditus/v1/parties", avecForges, jeton = compte.jeton)
        assertEquals(200, partie.status)
        val id = partie.obj.objet("partie").str("id")
        val q1 = partie.obj.objet("question")
        assertEquals(200, serveur.api.post("/api/eruditus/v1/parties/$id/question", avecForges, jeton = compte.jeton).status)
        val reponse = compte.repondreBrut(id, objetAvec(forges + mapOf(
            "numero" to JsonPrimitive(1), "choix" to JsonPrimitive(Banque.fausse(q1.str("enonce"))),
        )))
        assertEquals(200, reponse.status)
        assertEquals(200, serveur.api.post("/api/eruditus/v1/comptes/pseudo",
            objetAvec(forges + ("pseudo" to JsonPrimitive(Compte.pseudoUnique("Forge")))), jeton = compte.jeton).status)
        val elo = serveur.verifierChaine(compte)
        assertTrue(elo < 1000)

        // L'API standard, avec le jeton du joueur : refusée, rien de modifié.
        val ligne = serveur.ligne(id, 1)
        val question = serveur.fiche("eruditus_questions", ligne.str("question"))
        for ((collection, cible, champs) in listOf(
            Triple("eruditus_joueurs", compte.id, buildJsonObject { put("elo", 3000); put("elo_reponses", 500) }),
            Triple("eruditus_questions", question.str("id"), buildJsonObject { put("difficulte", 100); put("difficulte_reponses", 0) }),
            Triple("eruditus_reponses", ligne.str("id"), buildJsonObject { put("elo_apres", 3000); put("elo_avant", 3000) }),
        )) {
            val r = serveur.api.patch("/api/collections/$collection/records/$cible", champs, jeton = compte.jeton)
            assertTrue("$collection : $r", r.status in 400..404)
        }
        assertEquals(elo, serveur.joueurBase(compte).int("elo"))
        assertEquals(question.int("difficulte"), serveur.fiche("eruditus_questions", question.str("id")).int("difficulte"))
        assertEquals(ligne.int("elo_apres"), serveur.ligne(id, 1).int("elo_apres"))
    }

    @Test
    fun `T51 l'etat interne et l'Elo des provisoires ne sortent jamais`() {
        // Les champs internes sont refusés par le client des tests sur toute réponse ; ici, l'Elo
        // d'un provisoire n'est que dans son propre /moi et son bilan.
        val provisoire = Compte.creer(serveur)
        val partie = provisoire.jouer { enonce, _ -> Banque.bonne(enonce) }
        val sonElo = provisoire.elo().int("valeur")
        assertEquals(sonElo, partie.last().objet("bilan").objet("elo").int("valeur"))
        val classe = Compte.creer(serveur)
        serveur.reglerJoueur(classe, 1234, 30)
        for (jeton in listOf(provisoire.jeton, classe.jeton)) {
            val r = serveur.api.get("/api/eruditus/v1/classement/elo", jeton)
            assertEquals(200, r.status)
            assertTrue(r.obj.tableau("top").none { it.jsonObject.str("pseudo") == provisoire.pseudo })
            assertTrue(provisoire.id !in r.corps)
        }
        assertNull(serveur.api.get("/api/eruditus/v1/classement/elo", provisoire.jeton).obj.objetOuNull("moi"))
        val autreMoi = classe.moi()
        assertTrue(provisoire.pseudo !in autreMoi.corps)
        assertEquals(1234, autreMoi.obj.objet("elo").int("valeur"))
        // Ni dans la correction, ni dans /classement par points : le client refuse `elo` hors de
        // /moi, du bilan et de /classement/elo.
        assertTrue(partie.dropLast(1).all { it.objetOuNull("bilan") == null })
        assertTrue(partie.all { "elo" !in it.objet("correction") })
        assertEquals(200, serveur.api.get("/api/eruditus/v1/classement", provisoire.jeton).status)
    }

    @Test
    fun `T52 les comptes ne s'entraident pas`() {
        val cible = Banque.questions[2].str("enonce") // niveau 3 : 1600
        val idCible = serveur.questionParEnonce(cible).str("id")
        fun question() = serveur.fiche("eruditus_questions", idCible)
        serveur.regler("eruditus_questions", idCible, "difficulte_reponses" to 30)

        // Réponses de B : l'Elo de A ne bouge pas.
        val a = Compte.creer(serveur)
        a.jouer { enonce, _ -> Banque.bonne(enonce) }
        val eloA = a.elo().int("valeur")
        val b = Compte.creer(serveur)
        b.jouer { enonce, _ -> Banque.fausse(enonce) }
        assertEquals(eloA, a.elo().int("valeur"))
        serveur.verifierChaine(a)

        // 40 provisoires qui ratent ou réussissent exprès : la question ne bouge pas.
        for (i in 1..40) {
            Compte.creer(serveur).jouer { enonce, _ -> if (enonce != cible) null else if (i % 2 == 0) Banque.bonne(enonce) else Banque.fausse(enonce) }
        }
        assertEquals(1600, question().int("difficulte"))
        assertEquals(30, question().int("difficulte_reponses"))

        // Des classés qui la laissent filer, sans réponse ou hors délai : la question ne bouge
        // pas, leur Elo compte la perte.
        for (horsDelai in listOf(false, true, false, true)) {
            val classe = Compte.creer(serveur)
            serveur.reglerJoueur(classe, 1600, 30)
            val (id, premiere) = classe.commencer()
            var enonce = premiere.str("enonce")
            for (numero in 1..5) {
                if (numero > 1) enonce = classe.question(id).obj.objet("question").str("enonce")
                val choix = if (enonce == cible && !horsDelai) null else Banque.bonne(enonce)
                if (enonce == cible && horsDelai) serveur.vieillir(serveur.ligne(id, numero))
                assertEquals(200, classe.repondre(id, numero, choix).status)
            }
            serveur.verifierChaine(classe, depart = 1600, reponses = 30)
        }
        assertEquals(1600, question().int("difficulte"))
        assertEquals(30, question().int("difficulte_reponses"))

        // Des classés qui la ratent tous : jamais au-delà de la valeur du niveau + 300.
        serveur.regler("eruditus_questions", idCible, "difficulte" to 1880)
        repeat(5) {
            val classe = Compte.creer(serveur)
            serveur.reglerJoueur(classe, 3000, 30)
            classe.jouer { enonce, _ -> if (enonce == cible) Banque.fausse(enonce) else null }
        }
        assertEquals(1900, question().int("difficulte"))
        assertEquals(35, question().int("difficulte_reponses"))
    }

    @Test
    fun `T53 redemander une question comptee n'efface ni ne rejoue sa perte`() {
        for (fin in listOf<(String) -> Int?>({ Banque.bonne(it) }, { Banque.fausse(it) }, { null })) {
            val compte = Compte.creer(serveur)
            val (id, q1) = compte.commencer()
            val servie = serveur.ligne(id, 1)
            var restant = q1.int("restantMs")
            compte.question(id)
            repeat(10) { compte.question(id) }
            val enParallele = enParallele(10) { compte.question(id).obj.objet("question") }
            assertTrue(enParallele.all { it.int("numero") == 1 && it.str("enonce") == q1.str("enonce") })
            val r = compte.question(id).obj.objet("question")
            assertTrue(r.int("restantMs") <= restant)
            restant = r.int("restantMs")
            assertTrue(restant < 20_000)
            val apres = serveur.ligne(id, 1)
            for (champ in listOf("elo_avant", "elo_apres", "elo_difficulte", "elo_k")) {
                assertEquals(champ, servie.int(champ), apres.int(champ))
            }
            assertEquals(1, serveur.joueurBase(compte).int("elo_reponses"))
            assertEquals(servie.int("elo_apres"), compte.elo().int("valeur"))
            assertEquals(200, compte.repondre(id, 1, fin(q1.str("enonce"))).status)
            val attendu = Elo.apres(1000, 40, if (serveur.ligne(id, 1).bool("correcte")) 1 else 0, Elo.attendu(1000, servie.int("elo_difficulte")))
            assertEquals(attendu, compte.elo().int("valeur"))
            serveur.verifierChaine(compte)
        }
    }

    @Test
    fun `classement par Elo sans jeton, ou avec un jeton d'une autre collection`() {
        assertEquals(401, serveur.api.get("/api/eruditus/v1/classement/elo", null).status)
        assertEquals(401, serveur.api.get("/api/eruditus/v1/classement/elo", serveur.jetonUsers()).status)
        assertEquals(401, serveur.api.get("/api/eruditus/v1/classement/elo", serveur.jetonSu).status)
        assertEquals(401, serveur.api.get("/api/eruditus/v1/classement/elo", "jeton-invente").status)
    }

    private fun objetAvec(champs: Map<String, JsonElement>) = JsonObject(champs)
}

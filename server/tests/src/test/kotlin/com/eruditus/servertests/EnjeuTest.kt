package com.eruditus.servertests

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Enjeu de difficulté (spec/regles.md § 8.8, spec/serveur.md § 5.6, D125 – D130), T63 – T70 : le
 * chiffre annoncé avant la réponse est exactement celui qui est appliqué, figé au service, et rien
 * ne fuit. Les formules viennent de [Elo], écrite d'après la spec, jamais des hooks. Elo, compteurs
 * et difficultés réglés par le superutilisateur (D111).
 */
class EnjeuTest {
    private val serveur = Serveur.elo

    @Before
    fun questionsAuDepart() = serveur.remettreQuestions()

    private fun fixerDifficulte(q: Int) {
        for (x in serveur.lister("eruditus_questions")) serveur.regler("eruditus_questions", x.str("id"), "difficulte" to q)
    }

    /** Tous les noms de champ d'un JSON, à tous les niveaux. */
    private fun cles(element: JsonElement): Set<String> = when (element) {
        is JsonObject -> element.keys + element.values.flatMap { cles(it) }
        is JsonArray -> element.flatMap { cles(it) }.toSet()
        else -> emptySet()
    }

    private fun enjeuDe(r: Reponse): JsonObject = r.obj.objet("question").objet("enjeu")

    // --- T63 --------------------------------------------------------------------------------------

    @Test
    fun `T63 l'enjeu annonce est l'Elo applique, pas a pas`() {
        val difficultes = listOf(800, 1200, 1600, 2000, 2400, 2700)
        val scenarios = listOf("juste", "fausse", "sans reponse", "hors delai", "abandon")
        for (p in listOf(100, 105, 1000, 2300)) {
            for ((reponses, k) in listOf(0 to 40, 30 to 24, 200 to 12)) {
                // Chaque compte joue 5 questions, une par scénario ; la difficulté tourne d'un compte
                // à l'autre : tous les scénarios rencontrent toutes les difficultés.
                for (decalage in difficultes.indices) {
                    val compte = Compte.creer(serveur)
                    var id = ""
                    for ((pas, scenario) in scenarios.withIndex()) {
                        val numero = pas + 1
                        val q = difficultes[(decalage + pas) % difficultes.size]
                        val cas = "P=$p K=$k Q=$q $scenario"
                        serveur.reglerJoueur(compte, p, reponses)
                        fixerDifficulte(q)
                        val service = if (pas == 0) compte.nouvellePartie() else compte.question(id)
                        assertEquals(cas, 200, service.status)
                        if (pas == 0) id = service.obj.objet("partie").str("id")
                        val question = service.obj.objet("question")
                        val enjeu = question.objet("enjeu")

                        val gain = Elo.gain(p, q, k) - p
                        val perte = p - Elo.perte(p, q, k)
                        assertEquals(cas, Elo.niveau(q), enjeu.int("niveau"))
                        assertEquals(cas, gain, enjeu.int("gain"))
                        assertEquals(cas, perte, enjeu.int("perte"))
                        // La perte annoncée est déjà comptée quand le joueur la voit (D106).
                        val ligne = serveur.ligne(id, numero)
                        assertEquals(cas, p, ligne.int("elo_avant"))
                        assertEquals(cas, q, ligne.int("elo_difficulte"))
                        assertEquals(cas, k, ligne.int("elo_k"))
                        assertEquals(cas, p - perte, ligne.int("elo_apres"))
                        assertEquals(cas, p - perte, compte.elo().int("valeur"))

                        val bonne = Banque.bonne(question.str("enonce"))
                        val variation: Int
                        when (scenario) {
                            "abandon" -> {
                                compte.nouvellePartie() // la question reste sans réponse : fausse
                                variation = serveur.ligne(id, numero).let { it.int("elo_apres") - it.int("elo_avant") }
                                assertEquals(cas, p - perte, compte.elo().int("valeur"))
                            }
                            else -> {
                                if (scenario == "hors delai") serveur.vieillir(ligne)
                                val choix = when (scenario) {
                                    "juste", "hors delai" -> bonne
                                    "fausse" -> Banque.fausse(question.str("enonce"))
                                    else -> null
                                }
                                val r = compte.repondre(id, numero, choix)
                                assertEquals(cas, 200, r.status)
                                val correction = r.obj.objet("correction")
                                if (scenario == "hors delai") assertEquals(cas, "hors_delai", correction.str("raison"))
                                val elo = correction.objet("elo")
                                assertEquals(cas, Elo.niveau(q), elo.int("niveau"))
                                variation = elo.int("variation")
                                // T70 : la variation est celle de la ligne, jamais recalculée.
                                val apres = serveur.ligne(id, numero)
                                assertEquals(cas, apres.int("elo_apres") - apres.int("elo_avant"), variation)
                            }
                        }
                        assertEquals(cas, if (scenario == "juste") gain else -perte, variation)
                        assertEquals(cas, p + variation, serveur.joueurBase(compte).int("elo"))
                    }
                }
            }
        }
    }

    // --- T64 --------------------------------------------------------------------------------------

    @Test
    fun `T64 l'enjeu est fige au service, difficulte et Elo ont beau bouger`() {
        val compte = Compte.creer(serveur)
        serveur.reglerJoueur(compte, 1500, 30)
        fixerDifficulte(1700)
        val (id, q1) = compte.commencer()
        val annonce = q1.objet("enjeu")
        assertEquals(Elo.gain(1500, 1700, 24) - 1500, annonce.int("gain"))
        val empreinte = serveur.ligne(id, 1).str("question_empreinte")
        assertEquals(64, empreinte.length)

        // Les autres joueurs font bouger la difficulté ; le joueur lui-même a déjà payé la perte.
        fixerDifficulte(2300)
        val sequence = (1..10).map { enjeuDe(compte.question(id)) }
        val pool = Executors.newFixedThreadPool(4)
        val enParallele = try {
            (1..8).map { pool.submit(Callable { compte.question(id) }) }.map { it.get() }
        } finally {
            pool.shutdown()
        }
        for (r in enParallele) assertEquals("$r", 200, r.status)
        for (enjeu in sequence + enParallele.map { enjeuDe(it) }) assertEquals(annonce, enjeu)
        assertEquals(1, serveur.lignes(id).size)
        assertEquals(empreinte, serveur.ligne(id, 1).str("question_empreinte"))
        assertEquals(1, serveur.joueurBase(compte).int("elo_reponses"))

        // Et la variation appliquée est celle du service.
        val correction = compte.repondre(id, 1, Banque.bonne(q1.str("enonce"))).obj.objet("correction")
        assertEquals(annonce.int("gain"), correction.objet("elo").int("variation"))
        assertEquals(1500 + annonce.int("gain"), compte.elo().int("valeur"))
        assertEquals(Elo.niveau(1700), correction.objet("elo").int("niveau"))
    }

    // --- T65 --------------------------------------------------------------------------------------

    @Test
    fun `T65 rien ne fuit avant la reponse, pas meme le niveau stocke`() {
        val interdits = setOf(
            "bonneReponse", "explication", "source", "bonne_reponse", "difficulte", "difficulte_reponses", "elo_avant",
            "elo_apres", "elo_difficulte", "elo_k", "elo_reponses", "question_empreinte", "ref", "observation", "variation",
        )
        val compte = Compte.creer(serveur)
        // Toutes à 1900 : « Difficile » (4) pour les cinq, quel que soit le niveau écrit (1 à 5).
        fixerDifficulte(1900)

        val creation = compte.nouvellePartie()
        val id = creation.obj.objet("partie").str("id")
        val reponses = mutableListOf(creation)
        reponses += compte.question(id) // redemande
        for (r in reponses) {
            val question = r.obj.objet("question")
            assertEquals(setOf("numero", "total", "enonce", "choix", "delaiMs", "restantMs", "enjeu"), question.keys)
            assertEquals(setOf("niveau", "gain", "perte"), question.objet("enjeu").keys)
            assertEquals(emptySet<String>(), cles(r.obj).intersect(interdits))
        }
        // Service suivant, puis réponses refusées : aucune de ces réponses ne dit rien de plus.
        compte.repondre(id, 1, null)
        val suivante = compte.question(id)
        assertEquals(emptySet<String>(), cles(suivante.obj).intersect(interdits))
        val refusees = listOf(
            compte.repondre(id, 2, 7),
            compte.repondre(id, 9, 1),
            compte.repondre(id, 1, 1),
            compte.question("introuvable"),
            serveur.api.post("/api/eruditus/v1/parties/$id/question", jeton = null),
        )
        for (r in refusees) {
            assertTrue("$r", r.status in 400..409)
            assertEquals("$r", emptySet<String>(), cles(r.json ?: JsonNull).intersect(interdits + "enjeu" + "elo"))
        }
        for (r in reponses + suivante) assertEquals(4, enjeuDe(r).int("niveau"))
    }

    // --- T66 --------------------------------------------------------------------------------------

    @Test
    fun `T66 voir l'enjeu puis abandonner coute deja la perte annoncee`() {
        val compte = Compte.creer(serveur)
        var p = 1000
        var n = 0
        // Cinq parties à la suite, chacune abandonnée dès sa première question : la banque n'a que 5
        // questions, chaque partie en sert une neuve en premier ; chacune coûte sa perte.
        repeat(5) {
            val creation = compte.nouvellePartie()
            val id = creation.obj.objet("partie").str("id")
            val enjeu = enjeuDe(creation)
            val ligne = serveur.ligne(id, 1)
            val q = ligne.int("elo_difficulte")
            val perte = p - Elo.perte(p, q, Elo.k(n))
            assertEquals(perte, enjeu.int("perte"))
            // Sans rien d'autre (l'app quitte, le chrono file) : la perte est déjà dans /moi.
            assertEquals(p - perte, compte.elo().int("valeur"))
            assertEquals(++n, serveur.joueurBase(compte).int("elo_reponses"))
            p -= perte
        }
        assertEquals(p, serveur.verifierChaine(compte))
        // Une 6e partie : tout est vu, plus rien à perdre ni à gagner.
        val revision = compte.nouvellePartie()
        assertEquals(JsonNull, enjeuDe(revision).champ("gain"))
        assertEquals(JsonNull, enjeuDe(revision).champ("perte"))
        assertEquals(p, compte.elo().int("valeur"))
    }

    // --- T67 --------------------------------------------------------------------------------------

    @Test
    fun `T67 un enjeu envoye par l'app est ignore`() {
        val forges = buildJsonObject {
            put("enjeu", buildJsonObject { put("niveau", 1); put("gain", 9999); put("perte", 0) })
            put("gain", 9999)
            put("perte", 0)
            put("niveau", 1)
            put("variation", 9999)
            put("elo", buildJsonObject { put("niveau", 1); put("variation", 9999) })
        }
        val compte = Compte.creer(serveur)
        fixerDifficulte(1600)
        val creation = serveur.api.post("/api/eruditus/v1/parties", forges, jeton = compte.jeton)
        assertEquals(200, creation.status)
        val id = creation.obj.objet("partie").str("id")
        val q1 = creation.obj.objet("question")
        val gain = Elo.gain(1000, 1600, 40) - 1000
        assertEquals(gain, q1.objet("enjeu").int("gain"))
        assertEquals(Elo.niveau(1600), q1.objet("enjeu").int("niveau"))
        val redemande = serveur.api.post("/api/eruditus/v1/parties/$id/question", forges, jeton = compte.jeton)
        assertEquals(200, redemande.status)
        assertEquals(q1.objet("enjeu"), enjeuDe(redemande))

        val corps = JsonObject(
            forges + mapOf(
                "numero" to JsonPrimitive(1),
                "choix" to JsonPrimitive(Banque.bonne(q1.str("enonce"))),
            ),
        )
        val r = compte.repondreBrut(id, corps)
        assertEquals(200, r.status)
        assertEquals(gain, r.obj.objet("correction").objet("elo").int("variation"))
        assertEquals(1000 + gain, serveur.verifierChaine(compte))
    }

    // --- T68 --------------------------------------------------------------------------------------

    @Test
    fun `T68 sans niveau, deja vue, revision et gain ou perte a 0`() {
        // Banque d'exemple : 7 questions à niveau, 5 sans ; en 3 parties tout est servi, une 4e est
        // une révision.
        val principal = Serveur.principal
        val compte = Compte.creer(principal)
        var comptees = 0
        val vues = mutableSetOf<String>()
        repeat(4) { partie ->
            val (id, premiere) = compte.commencer()
            var question = premiere
            for (numero in 1..5) {
                val enonce = question.str("enonce")
                val niveau = principal.questionParEnonce(enonce).int("niveau")
                val neuve = vues.add(enonce)
                val enjeu = question.objet("enjeu")
                when {
                    niveau == 0 -> {
                        assertEquals(JsonNull, enjeu.champ("niveau"))
                        assertEquals(JsonNull, enjeu.champ("gain"))
                        assertEquals(JsonNull, enjeu.champ("perte"))
                    }
                    neuve -> {
                        comptees++
                        assertEquals(Elo.niveau(Elo.DIFFICULTES[niveau]), enjeu.int("niveau"))
                        assertTrue(enjeu.int("gain") >= 0 && enjeu.int("perte") > 0)
                    }
                    else -> {
                        assertEquals("Déjà vue, partie $partie : $enjeu", Elo.niveau(Elo.DIFFICULTES[niveau]), enjeu.int("niveau"))
                        assertEquals(JsonNull, enjeu.champ("gain"))
                        assertEquals(JsonNull, enjeu.champ("perte"))
                    }
                }
                val avant = compte.elo().int("valeur")
                val correction = compte.repondre(id, numero, Banque.bonne(enonce)).obj.objet("correction")
                val elo = correction.objet("elo")
                if (niveau == 0) assertEquals(JsonNull, elo.champ("niveau")) else assertEquals(Elo.niveau(Elo.DIFFICULTES[niveau]), elo.int("niveau"))
                if (niveau == 0 || !neuve) {
                    assertEquals(JsonNull, elo.champ("variation"))
                    assertEquals(avant, compte.elo().int("valeur"))
                } else {
                    assertEquals(enjeu.int("gain"), elo.int("variation"))
                }
                if (numero < 5) question = compte.question(id).obj.objet("question")
            }
        }
        assertEquals(7, comptees)
        principal.verifierChaine(compte)

        // Gain à 0 (plafond naturel, D104) et perte à 0 (plancher de 100) : un 0, jamais null.
        val serveur = serveur
        val plafond = Compte.creer(serveur)
        serveur.reglerJoueur(plafond, 3100, 30)
        fixerDifficulte(800)
        val (idA, a) = plafond.commencer()
        assertEquals(0, a.objet("enjeu").int("gain"))
        assertTrue(a.objet("enjeu").int("perte") > 0)
        val juste = plafond.repondre(idA, 1, Banque.bonne(a.str("enonce"))).obj.objet("correction").objet("elo")
        assertEquals(0, juste.int("variation"))

        val plancher = Compte.creer(serveur)
        serveur.reglerJoueur(plancher, 100, 0)
        fixerDifficulte(2700)
        val (idB, b) = plancher.commencer()
        assertEquals(0, b.objet("enjeu").int("perte"))
        assertTrue(b.objet("enjeu").int("gain") > 0)
        val fausse = plancher.repondre(idB, 1, Banque.fausse(b.str("enonce"))).obj.objet("correction").objet("elo")
        assertEquals(0, fausse.int("variation"))
        assertEquals(100, plancher.elo().int("valeur"))
    }

    // --- T69 --------------------------------------------------------------------------------------

    @Test
    fun `T69 seuils du niveau`() {
        val attendus = mapOf(
            500 to 1, 999 to 1, 1000 to 2, 1399 to 2, 1400 to 3, 1799 to 3, 1800 to 4, 2199 to 4, 2200 to 5, 2700 to 5,
        )
        // Un joueur qui a tout vu : la révision donne le niveau en mots, d'après la difficulté.
        val compte = Compte.creer(serveur)
        compte.jouer { _, _ -> null }
        for ((difficulte, niveau) in attendus) {
            fixerDifficulte(difficulte)
            val r = compte.nouvellePartie()
            assertEquals("difficulté $difficulte", niveau, enjeuDe(r).int("niveau"))
        }
    }

    // --- T70 --------------------------------------------------------------------------------------

    @Test
    fun `T70 une reponse refusee ne donne aucune variation, une question redemandee garde la sienne`() {
        val compte = Compte.creer(serveur)
        val (id, q1) = compte.commencer()
        val avant = serveur.ligne(id, 1)
        compte.question(id)
        fixerDifficulte(2100)
        compte.question(id)
        val eloApresService = avant.int("elo_apres")

        // Abandon (nouvelle partie), puis réponse refusée : ni correction, ni variation, rien ne bouge.
        compte.nouvellePartie()
        val refusee = compte.repondre(id, 1, Banque.bonne(q1.str("enonce")))
        refusee.doitEtreErreur(409, "partie_finie")
        assertTrue("elo" !in refusee.corps)
        assertEquals(eloApresService, serveur.ligne(id, 1).int("elo_apres"))
        assertEquals(1, serveur.lignes(id).size)
    }
}

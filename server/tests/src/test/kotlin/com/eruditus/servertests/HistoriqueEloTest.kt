package com.eruditus.servertests

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Ce que l'historique dit de l'Elo (spec/serveur.md § 5.6, D133, D135), T76 et T79 : les
 * variations s'additionnent exactement, la chaîne est continue, les formules rejouées redonnent
 * chaque pas, et la courbe suit les parties comptées. Les formules viennent de [Elo], écrite
 * d'après la spec, jamais des hooks.
 */
class HistoriqueEloTest {
    private val serveur = Serveur.historique
    private val racine = "/api/eruditus/v1"

    @Before
    @After
    fun questionsAuDepart() = serveur.remettreQuestions()

    private fun fixerDifficulte(q: Int) {
        for (x in serveur.lister("eruditus_questions")) serveur.regler("eruditus_questions", x.str("id"), "difficulte" to q)
    }

    private fun detail(c: Compte, id: String): JsonObject {
        val r = serveur.api.get("$racine/historique/$id", c.jeton)
        assertEquals("$r", 200, r.status)
        return r.obj
    }

    private fun courbe(c: Compte): JsonObject {
        val r = serveur.api.get("$racine/elo/courbe", c.jeton)
        assertEquals("$r", 200, r.status)
        return r.obj
    }

    private fun iso(created: String): String = created.replace(' ', 'T')

    /**
     * Une partie scriptée : J juste, F fausse, S sans réponse, H juste mais hors délai. Un script de
     * moins de 5 lettres laisse la question suivante servie, sans réponse : la partie sera abandonnée
     * par la suivante.
     */
    private fun jouer(c: Compte, script: String): String {
        val (id, premiere) = c.commencer()
        var question = premiere
        for ((i, lettre) in script.withIndex()) {
            val numero = i + 1
            val enonce = question.str("enonce")
            val choix = when (lettre) {
                'J', 'H' -> Banque.bonne(enonce)
                'F' -> Banque.fausse(enonce)
                else -> null
            }
            if (lettre == 'H') serveur.vieillir(serveur.ligne(id, numero))
            assertEquals(200, c.repondre(id, numero, choix).status)
            if (numero < 5) question = c.question(id).obj.objet("question")
        }
        return id
    }

    private fun parties(c: Compte): List<JsonObject> =
        serveur.lister("eruditus_parties", "joueur = '${c.id}' && etat != 'en_cours'", "created,id")

    private fun comptees(lignes: List<JsonObject>) = lignes.filter { it.int("elo_avant") > 0 }

    // --- T76 ---------------------------------------------------------------------------------------

    /** Compare une partie, ligne à ligne, à ce que la base contient ; renvoie l'Elo après elle (null sans ligne comptée). */
    private fun verifierPartie(c: Compte, partie: JsonObject): Int? {
        val id = partie.str("id")
        val lignes = serveur.lignes(id)
        val d = detail(c, id)
        val questions = d.tableau("questions").map { it.jsonObject }
        assertEquals(lignes.size, questions.size)
        for ((l, q) in lignes.zip(questions)) {
            val comptee = l.int("elo_avant") > 0
            val variation = if (comptee) l.int("elo_apres") - l.int("elo_avant") else null
            assertEquals("variation de $l", variation, q.intOuNull("variation"))
            val niveau = if (l.int("elo_difficulte") > 0) Elo.niveau(l.int("elo_difficulte")) else null
            assertEquals("niveau de $l", niveau, q.intOuNull("niveau"))
            if (comptee) { // rejeu de la formule : plancher de 100 et ,5 compris
                val s = if (l.bool("correcte")) 1 else 0
                val attendu = Elo.apres(l.int("elo_avant"), l.int("elo_k"), s, Elo.attendu(l.int("elo_avant"), l.int("elo_difficulte")))
                assertEquals("rejeu de $l", attendu, l.int("elo_apres"))
            }
        }
        val somme = questions.mapNotNull { it.intOuNull("variation") }.sum()
        val elo = d.objet("elo")
        val lignesComptees = comptees(lignes)
        val entree = serveur.api.get("$racine/historique", c.jeton).obj.tableau("parties")
            .map { it.jsonObject }.firstOrNull { it.str("id") == id }
        entree?.let { // page 1 : seulement les 20 plus récentes
            assertEquals(elo, it.objet("elo"))
            assertEquals(lignes.count { l -> l.bool("correcte") }, it.int("bonnes"))
            assertEquals(lignes.size, it.int("servies"))
        }
        if (lignesComptees.isEmpty()) {
            assertEquals(JsonNull, elo.champ("variation"))
            assertEquals(JsonNull, elo.champ("valeur"))
            return null
        }
        for ((a, b) in lignesComptees.zipWithNext()) assertEquals("chaîne : $a puis $b", a.int("elo_apres"), b.int("elo_avant"))
        val derniere = lignesComptees.maxByOrNull { it.int("numero") }!!
        assertEquals(somme, elo.int("variation"))
        assertEquals(derniere.int("elo_apres") - lignesComptees.first().int("elo_avant"), somme)
        assertEquals(derniere.int("elo_apres"), elo.int("valeur"))
        return derniere.int("elo_apres")
    }

    @Test
    fun `T76 les variations s'additionnent, la chaine est continue, abandons et revisions comprises`() {
        val c = Compte.creer(serveur)
        // 8 parties de la même banque de 12 : des parties abandonnées, des fausses, des sans-réponse,
        // un hors-délai, et, dès la 3e, des questions déjà vues (révision partielle) ou sans niveau.
        val scripts = listOf("JFJSH", "FFFFF", "JJJ", "JJJJJ", "SSSSS", "FJFJF", "JF", "JJFJJ")
        scripts.forEach { jouer(c, it) }
        assertEquals(2, parties(c).count { it.str("etat") == "abandonnee" }) // « JJJ » et « JF »

        var courant = Elo.DEPART
        val valeurs = mutableListOf<Int>()
        var mixte = false
        for (partie in parties(c)) {
            val lignes = serveur.lignes(partie.str("id"))
            mixte = mixte || (comptees(lignes).isNotEmpty() && comptees(lignes).size < lignes.size)
            val valeur = verifierPartie(c, partie)
            if (valeur != null) {
                // D'une partie à la suivante, le départ est l'arrivée de la précédente.
                assertEquals(courant, comptees(lignes).first().int("elo_avant"))
                courant = valeur
                valeurs += valeur
            }
        }
        assertTrue("Aucune partie à moitié comptée : le scénario ne couvre pas la révision partielle", mixte)
        assertTrue("Aucune partie comptée", valeurs.isNotEmpty())
        assertEquals(courant, serveur.verifierChaine(c)) // le rejeu complet de la base
        // `/moi` est le dernier point de la courbe, aucune partie n'étant en cours.
        assertEquals(courant, c.elo().int("valeur"))
        val courbe = courbe(c)
        assertEquals(Elo.DEPART, courbe.int("depart"))
        assertEquals(valeurs, courbe.tableau("points").map { it.jsonObject.int("elo") })
    }

    @Test
    fun `T76 le plancher de 100, les K et les niveaux, partie par partie`() {
        data class Cas(val p: Int, val n: Int, val q: Int, val script: String)
        val cas = listOf(
            Cas(100, 0, 800, "FFFFF"), // au plancher : 0 partout
            Cas(100, 0, 800, "JFJFJ"),
            Cas(105, 29, 1200, "FJFJF"), // provisoire puis classé en cours de partie
            Cas(1000, 30, 1600, "JHJFS"),
            Cas(2300, 200, 2700, "JJFFS"), // K = 12
            Cas(2390, 0, 2400, "JJJJJ"),
            Cas(1000, 0, 1000, "JFJFJ"), // seuil de niveau : 1000 est « Facile »
        )
        for (x in cas) {
            val nom = "P=${x.p} n=${x.n} Q=${x.q} ${x.script}"
            serveur.remettreQuestions()
            fixerDifficulte(x.q)
            val c = Compte.creer(serveur)
            serveur.reglerJoueur(c, x.p, x.n)
            val id = jouer(c, x.script)

            var p = x.p
            val attendues = x.script.mapIndexed { i, lettre ->
                val k = Elo.k(x.n + i)
                val apres = if (lettre == 'J') Elo.gain(p, x.q, k) else Elo.perte(p, x.q, k)
                (apres - p).also { p = apres }
            }
            val d = detail(c, id)
            val questions = d.tableau("questions").map { it.jsonObject }
            assertEquals(nom, attendues, questions.map { it.int("variation") })
            for (q in questions) assertEquals(nom, Elo.niveau(x.q), q.int("niveau"))
            assertEquals(nom, attendues.sum(), d.objet("elo").int("variation"))
            assertEquals(nom, p, d.objet("elo").int("valeur"))
            assertEquals(nom, p, c.elo().int("valeur"))
            assertTrue(nom, p >= Elo.PLANCHER)
        }
        assertNotEquals(0, cas.size)
    }

    // --- T79 ---------------------------------------------------------------------------------------

    /** La courbe attendue, lue dans la base : (départ, points `date` → Elo) des 50 parties comptées les plus récentes. */
    private fun attendue(c: Compte): Pair<Int?, List<Pair<String, Int>>> {
        val comptees = parties(c).mapNotNull { p ->
            val lignes = comptees(serveur.lignes(p.str("id")))
            if (lignes.isEmpty()) null else Triple(p, lignes.first(), lignes.maxByOrNull { it.int("numero") }!!)
        }.takeLast(50)
        return comptees.firstOrNull()?.second?.int("elo_avant") to comptees.map { iso(it.first.str("created")) to it.third.int("elo_apres") }
    }

    private fun verifierCourbe(c: Compte, provisoire: Boolean? = null): JsonObject {
        val r = courbe(c)
        val (depart, points) = attendue(c)
        assertEquals(depart, r.intOuNull("depart"))
        assertEquals(points, r.tableau("points").map { it.jsonObject.let { p -> p.str("date") to p.int("elo") } })
        assertTrue(r.tableau("points").size <= 50)
        provisoire?.let { assertEquals(it, r.bool("provisoire")) }
        return r
    }

    @Test
    fun `T79 aucune partie, une seule, des revisions et une partie en cours`() {
        fixerDifficulte(1600) // toutes les questions comptent, jusqu'à ce que le joueur les ait vues
        val c = Compte.creer(serveur)
        val vide = verifierCourbe(c, provisoire = true)
        assertEquals(JsonNull, vide.champ("depart"))
        assertEquals(0, vide.tableau("points").size)

        // Partie en cours avec sa perte déjà comptée : exclue de la courbe, pas de /moi.
        val (enCours, _) = c.commencer()
        assertEquals(1, serveur.lignes(enCours).size)
        assertTrue(serveur.ligne(enCours, 1).int("elo_avant") > 0)
        assertEquals(0, verifierCourbe(c).tableau("points").size)
        assertTrue(c.elo().int("valeur") < Elo.DEPART)
        val reponse = c.repondre(enCours, 1, null)
        assertEquals(200, reponse.status)
        var question = c.question(enCours).obj.objet("question")
        for (numero in 2..5) {
            assertEquals(200, c.repondre(enCours, numero, Banque.bonne(question.str("enonce"))).status)
            if (numero < 5) question = c.question(enCours).obj.objet("question")
        }

        // Une seule partie : un point, et le départ.
        val seule = verifierCourbe(c)
        assertEquals(1, seule.tableau("points").size)
        assertEquals(Elo.DEPART, seule.int("depart"))
        assertEquals(c.elo().int("valeur"), seule.tableau("points").single().jsonObject.int("elo"))

        // Deux parties de plus, puis des révisions : les 12 questions sont vues, plus rien ne compte.
        repeat(5) { jouer(c, "JFJFJ") }
        val toutes = parties(c)
        assertEquals(6, toutes.size)
        val avecPoints = toutes.count { comptees(serveur.lignes(it.str("id"))).isNotEmpty() }
        val r = verifierCourbe(c)
        assertEquals(avecPoints, r.tableau("points").size)
        assertTrue("Il devait rester au moins une révision pure", avecPoints < toutes.size)
        assertEquals(c.elo().int("valeur"), r.tableau("points").last().jsonObject.int("elo"))
    }

    @Test
    fun `T79 provisoire jusqu'a 30 reponses comptees`() {
        val c = Compte.creer(serveur)
        serveur.reglerJoueur(c, 1000, 29)
        assertTrue(courbe(c).bool("provisoire"))
        serveur.reglerJoueur(c, 1000, 30)
        assertEquals(false, courbe(c).bool("provisoire"))
    }

    @Test
    fun `T79 plus de 50 parties, les 50 plus recentes du plus ancien au plus recent`() {
        fixerDifficulte(1600)
        val c = Compte.creer(serveur)
        repeat(52) { jouer(c, "FFFFF") }
        // Après les 3 premières, tout est révision : on fait compter la première ligne des suivantes.
        val parties = parties(c)
        assertEquals(52, parties.size)
        for ((i, p) in parties.withIndex().drop(3)) {
            serveur.regler(
                "eruditus_reponses", serveur.ligne(p.str("id"), 1).str("id"),
                "elo_avant" to 1100 + i, "elo_apres" to 1100 + i + 9, "elo_difficulte" to 1600, "elo_k" to 24,
            )
        }
        val (depart, points) = attendue(c)
        assertEquals(50, points.size)
        val r = verifierCourbe(c)
        assertEquals(50, r.tableau("points").size)
        assertEquals(depart, r.int("depart"))
        // Les 50 plus récentes : la première partie, la plus ancienne, a perdu son point.
        assertNotEquals(iso(parties.first().str("created")), r.tableau("points").first().jsonObject.str("date"))
        assertEquals(iso(parties.last().str("created")), r.tableau("points").last().jsonObject.str("date"))
        // La liste, elle, les a toutes (3 pages).
        var curseur: String? = null
        var total = 0
        do {
            val page = serveur.api.get("$racine/historique" + (curseur?.let { "?avant=" + java.net.URLEncoder.encode(it, Charsets.UTF_8) } ?: ""), c.jeton)
            total += page.obj.tableau("parties").size
            curseur = page.obj.strOuNull("suivant")
        } while (curseur != null)
        assertEquals(52, total)
    }
}

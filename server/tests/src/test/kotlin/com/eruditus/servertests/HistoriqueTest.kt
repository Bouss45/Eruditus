package com.eruditus.servertests

import java.net.URLEncoder
import java.time.Instant
import java.util.Base64
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Historique des parties (spec/serveur.md § 5.6, D131 – D134), T73 – T75, T77, T78, T80, T81 :
 * liste paginée, détail, confidentialité, aucun spoiler, lecture seule, aucune clé interne. Les
 * sommes (T76) et la courbe (T79) sont dans [HistoriqueEloTest]. Les questions, les parties et
 * les lignes sont lues et réglées par le superutilisateur (D111).
 */
class HistoriqueTest {
    private val serveur = Serveur.historique

    private val racine = "/api/eruditus/v1"

    // --- Outils ------------------------------------------------------------------------------------

    private fun liste(c: Compte, avant: String? = null, jeton: String? = c.jeton): Reponse {
        val parametre = avant?.let { "?avant=" + URLEncoder.encode(it, Charsets.UTF_8) } ?: ""
        return serveur.api.get("$racine/historique$parametre", jeton)
    }

    private fun detail(c: Compte, id: String, jeton: String? = c.jeton): Reponse =
        serveur.api.get("$racine/historique/$id", jeton)

    private fun idsAttendus(c: Compte): List<String> =
        serveur.lister("eruditus_parties", "joueur = '${c.id}' && etat != 'en_cours'", "-created,-id").map { it.str("id") }

    /** Toutes les pages, de la première à la dernière : les listes d'identifiants, page par page. */
    private fun pages(c: Compte): List<List<String>> {
        val resultat = mutableListOf<List<String>>()
        var curseur: String? = null
        do {
            val r = liste(c, curseur)
            assertEquals("$r", 200, r.status)
            val parties = r.obj.tableau("parties")
            assertTrue("Page de plus de 20 parties : $r", parties.size <= 20)
            resultat += parties.map { it.jsonObject.str("id") }
            curseur = r.obj.strOuNull("suivant")
            assertTrue("Trop de pages", resultat.size < 20)
        } while (curseur != null)
        return resultat
    }

    /** Joue une partie entière en réussissant les [bonnes] premières questions ; renvoie son identifiant. */
    private fun jouer(c: Compte, bonnes: Int = 2): String {
        val (id, premiere) = c.commencer()
        var question = premiere
        for (numero in 1..5) {
            val enonce = question.str("enonce")
            val choix = if (numero <= bonnes) Banque.bonne(enonce) else Banque.fausse(enonce)
            assertEquals(200, c.repondre(id, numero, choix).status)
            if (numero < 5) question = c.question(id).obj.objet("question")
        }
        return id
    }

    /** Une partie de chaque statut : juste, fausse, sans réponse, hors délai, juste. */
    private fun jouerVarie(c: Compte): String {
        val (id, premiere) = c.commencer()
        var question = premiere
        for (numero in 1..5) {
            val enonce = question.str("enonce")
            val choix = when (numero) {
                1, 5 -> Banque.bonne(enonce)
                2 -> Banque.fausse(enonce)
                3 -> null
                else -> {
                    serveur.vieillir(serveur.ligne(id, 4))
                    Banque.bonne(enonce)
                }
            }
            assertEquals(200, c.repondre(id, numero, choix).status)
            if (numero < 5) question = c.question(id).obj.objet("question")
        }
        return id
    }

    private fun iso(created: String): String = created.replace(' ', 'T')

    private fun cles(element: JsonElement): Set<String> = when (element) {
        is JsonObject -> element.keys + element.values.flatMap { cles(it) }
        is JsonArray -> element.flatMap { cles(it) }.toSet()
        else -> emptySet()
    }

    private fun questionDeLaLigne(partie: String, numero: Int): JsonObject =
        serveur.fiche("eruditus_questions", serveur.ligne(partie, numero).str("question"))

    // --- T73 : confidentialité ---------------------------------------------------------------------

    @Test
    fun `T73 la partie d'un autre, un identifiant inconnu ou la partie en cours recoivent la meme reponse`() {
        val a = Compte.creer(serveur)
        val b = Compte.creer(serveur)
        val finieA = jouer(a)
        val finieB = jouer(b)
        val (enCoursA, _) = a.commencer()

        // Mes parties : le détail s'ouvre ; celles d'un autre, jamais.
        assertEquals(200, detail(a, finieA).status)
        val refusees = listOf(
            detail(b, finieA), // la partie d'un autre compte
            detail(a, "abcdefghijklmno"), // inconnu, de la bonne forme
            detail(a, "x"), // inconnu, trop court
            detail(a, "a'b%22c%20OR%201=1"), // mal formé
            detail(a, "..%2F..%2Fmoi"),
            detail(a, "a".repeat(2000)), // très long
            detail(a, "%20"), // vide en apparence
            detail(a, enCoursA), // la partie en cours du compte
            serveur.api.get("$racine/historique/$finieA?x=1", b.jeton), // paramètre inconnu : rien de plus
        )
        for (r in refusees) r.doitEtreErreur(404, "partie_introuvable")
        val modele = refusees.first().corps
        for (r in refusees) assertEquals("Corps différent : $r", modele, r.corps)
        // Un identifiant vide : le chemin sans identifiant, même réponse.
        val vide = serveur.api.get("$racine/historique/", a.jeton)
        vide.doitEtreErreur(404, "partie_introuvable")
        assertEquals(modele, vide.corps)

        // Jetons qui ne sont pas ceux d'un joueur : 401, quelle que soit la partie.
        for (jeton in listOf(serveur.jetonSu, serveur.jetonUsers(), null)) {
            for (id in listOf(finieA, "abcdefghijklmno")) assertEquals(401, detail(a, id, jeton).status)
            assertEquals(401, liste(a, null, jeton).status)
            assertEquals(401, serveur.api.get("$racine/elo/courbe", jeton).status)
        }

        // La liste ne montre que mes parties, la partie en cours jamais.
        assertEquals(listOf(finieA), liste(a).obj.tableau("parties").map { it.jsonObject.str("id") })
        assertEquals(listOf(finieB), liste(b).obj.tableau("parties").map { it.jsonObject.str("id") })
        assertEquals(JsonNull, liste(a).obj.champ("suivant"))
    }

    // --- T74 : aucun spoiler -----------------------------------------------------------------------

    @Test
    fun `T74 une question non repondue, jamais servie ou d'une partie en cours ne donne aucune reponse`() {
        val c = Compte.creer(serveur)
        val (id1, q1) = c.commencer()
        assertEquals(200, c.repondre(id1, 1, Banque.bonne(q1.str("enonce"))).status)
        val q2 = c.question(id1).obj.objet("question")
        assertEquals(200, c.repondre(id1, 2, Banque.fausse(q2.str("enonce"))).status)
        val q3 = c.question(id1).obj.objet("question") // servie, jamais répondue

        // En cours : ni dans la liste, ni en détail.
        assertEquals(0, liste(c).obj.tableau("parties").size)
        detail(c, id1).doitEtreErreur(404, "partie_introuvable")

        // Abandonnée par une nouvelle partie.
        val (id2, _) = c.commencer()
        val parties = liste(c).obj.tableau("parties")
        assertEquals(listOf(id1), parties.map { it.jsonObject.str("id") })
        val ligne = parties.single().jsonObject
        assertEquals("abandonnee", ligne.str("etat"))
        assertEquals(1, ligne.int("bonnes"))
        assertEquals(3, ligne.int("servies"))

        val r = detail(c, id1)
        assertEquals("$r", 200, r.status)
        val partie = r.obj.objet("partie")
        assertEquals(setOf("id", "date", "etat", "bonnes", "servies", "total"), partie.keys)
        assertEquals("abandonnee", partie.str("etat"))
        assertEquals(1, partie.int("bonnes"))
        assertEquals(3, partie.int("servies"))
        assertEquals(5, partie.int("total"))
        val questions = r.obj.tableau("questions").map { it.jsonObject }
        assertEquals(listOf(1, 2, 3), questions.map { it.int("numero") }) // 4 et 5 : jamais servies
        assertEquals(listOf("juste", "fausse", "non_repondue"), questions.map { it.str("statut") })

        val non = questions[2]
        assertEquals("ok", non.str("detail"))
        assertEquals(q3.str("enonce"), non.str("enonce"))
        assertEquals(setOf("numero", "statut", "niveau", "variation", "detail", "enonce", "choix"), non.keys)
        // Aucune trace de la réponse de cette question, ni dans le détail, ni dans la liste.
        val explication3 = Banque.explication(q3.str("enonce"))!!
        assertFalse(explication3 in r.corps)
        assertFalse(explication3 in liste(c).corps)
        assertEquals(q3.objet("enjeu").int("niveau"), non.int("niveau"))
        // Les questions répondues, elles, ont leur correction.
        for ((i, q) in questions.take(2).withIndex()) {
            assertEquals(setOf("numero", "statut", "niveau", "variation", "detail", "enonce", "choix", "choixJoueur", "bonneReponse", "explication", "source"), q.keys)
            val enonce = q.str("enonce")
            assertEquals(Banque.bonne(enonce), q.int("bonneReponse"))
            assertEquals(Banque.explication(enonce), q.strOuNull("explication"))
            assertEquals(Banque.source(enonce), q.strOuNull("source"))
            assertEquals(if (i == 0) Banque.bonne(enonce) else Banque.fausse(enonce), q.int("choixJoueur"))
        }

        // Les questions jamais servies : ni leur énoncé, ni leurs choix.
        val toutes = serveur.fiche("eruditus_parties", id1).tableau("questions").map { (it as JsonPrimitive).content }
        for (nonServie in toutes.drop(3)) {
            val q = serveur.fiche("eruditus_questions", nonServie)
            assertFalse(q.str("enonce") in r.corps)
            assertFalse(q.str("enonce") in liste(c).corps)
        }

        // La partie en cours (id2) : absente jusqu'à sa 5e réponse, présente tout de suite après.
        var question = c.question(id2).obj.objet("question")
        for (numero in 1..5) {
            detail(c, id2).doitEtreErreur(404, "partie_introuvable")
            assertEquals(listOf(id1), liste(c).obj.tableau("parties").map { it.jsonObject.str("id") })
            assertEquals(200, c.repondre(id2, numero, Banque.bonne(question.str("enonce"))).status)
            if (numero < 5) question = c.question(id2).obj.objet("question")
        }
        assertEquals(listOf(id2, id1), liste(c).obj.tableau("parties").map { it.jsonObject.str("id") })
        assertEquals("terminee", liste(c).obj.tableau("parties")[0].jsonObject.str("etat"))
        assertEquals(200, detail(c, id2).status)
    }

    // --- T75 : question modifiée, retirée, sans empreinte --------------------------------------------

    private val champsModifies = listOf("enonce", "choix", "bonne_reponse", "active", "observation", "explication", "source", "niveau", "difficulte")

    private fun patchQuestion(id: String, corps: JsonObject) {
        val r = serveur.su.patch("/api/collections/eruditus_questions/records/$id", corps)
        assertEquals("$r", 200, r.status)
    }

    private fun restaurer(original: JsonObject) {
        patchQuestion(original.str("id"), JsonObject(original.filterKeys { it in champsModifies }))
    }

    @Test
    fun `T75 une question modifiee, retiree, en observation ou sans empreinte perd son detail`() {
        val c = Compte.creer(serveur)
        val id = jouerVarie(c)
        val lignes = serveur.lignes(id)
        val originaux = (1..5).map { questionDeLaLigne(id, it) }
        val avant = detail(c, id).obj.tableau("questions").map { it.jsonObject }
        assertEquals(listOf("juste", "fausse", "sans_reponse", "hors_delai", "juste"), avant.map { it.str("statut") })
        for (q in avant) assertEquals("ok", q.str("detail"))

        try {
            // 1 énoncé, 2 un choix, 3 la bonne réponse modifiés ; 4 désactivée ; 5 en observation.
            patchQuestion(originaux[0].str("id"), buildJsonObject { put("enonce", originaux[0].str("enonce") + " (corrigée)") })
            patchQuestion(originaux[1].str("id"), buildJsonObject {
                put("choix", buildJsonArray {
                    originaux[1].tableau("choix").forEachIndexed { i, x -> add(if (i == 0) JsonPrimitive(x.texte() + " !") else x) }
                })
            })
            patchQuestion(originaux[2].str("id"), buildJsonObject { put("bonne_reponse", originaux[2].int("bonne_reponse") % 4 + 1) })
            patchQuestion(originaux[3].str("id"), buildJsonObject { put("active", false) })
            patchQuestion(originaux[4].str("id"), buildJsonObject { put("observation", true) })

            val r = detail(c, id)
            assertEquals("$r", 200, r.status)
            val apres = r.obj.tableau("questions").map { it.jsonObject }
            assertEquals(listOf("modifiee", "modifiee", "modifiee", "retiree", "retiree"), apres.map { it.str("detail") })
            for ((i, q) in apres.withIndex()) {
                // Seulement le verdict, le niveau et la variation : jamais d'énoncé, de choix ni de réponse.
                assertEquals(setOf("numero", "statut", "niveau", "variation", "detail"), q.keys)
                assertEquals(avant[i].str("statut"), q.str("statut"))
                assertEquals(avant[i].champ("niveau"), q.champ("niveau"))
                assertEquals(avant[i].champ("variation"), q.champ("variation"))
                assertEquals(i + 1, q.int("numero"))
            }
            for (o in originaux) {
                assertFalse(o.str("enonce") in r.corps)
                assertFalse(o.str("enonce") + " (corrigée)" in r.corps)
            }
            // La désactivation et l'observation ne se distinguent pas.
            assertEquals(apres[3].keys, apres[4].keys)
            assertEquals(apres[3].str("detail"), apres[4].str("detail"))

            // Retirée passe avant modifiée.
            patchQuestion(originaux[0].str("id"), buildJsonObject { put("active", false) })
            assertEquals("retiree", detail(c, id).obj.tableau("questions")[0].jsonObject.str("detail"))
        } finally {
            originaux.forEach { restaurer(it) }
        }
        assertEquals(List(5) { "ok" }, detail(c, id).obj.tableau("questions").map { it.jsonObject.str("detail") })

        try {
            // L'explication, la source, le niveau et la difficulté n'entrent pas dans l'empreinte.
            patchQuestion(originaux[0].str("id"), buildJsonObject {
                put("explication", "Nouvelle explication")
                put("source", "Nouvelle source")
                put("niveau", 5)
                put("difficulte", 2400)
            })
            val q = detail(c, id).obj.tableau("questions")[0].jsonObject
            assertEquals("ok", q.str("detail"))
            assertEquals("Nouvelle explication", q.str("explication"))
            assertEquals("Nouvelle source", q.str("source"))
        } finally {
            restaurer(originaux[0])
        }

        // Une ligne d'avant l'empreinte (vide) : modifiée.
        serveur.regler("eruditus_reponses", lignes[2].str("id"), "question_empreinte" to "")
        val sans = detail(c, id).obj.tableau("questions").map { it.jsonObject }
        assertEquals("modifiee", sans[2].str("detail"))
        assertEquals(setOf("numero", "statut", "niveau", "variation", "detail"), sans[2].keys)
        assertEquals("sans_reponse", sans[2].str("statut"))
        assertEquals(listOf("ok", "ok", "modifiee", "ok", "ok"), sans.map { it.str("detail") })
    }

    // --- T77 : pages et curseur --------------------------------------------------------------------

    @Test
    fun `T77 les pages ne perdent et ne doublent rien, le curseur falsifie ne donne jamais les parties d'un autre`() {
        val c = Compte.creer(serveur)
        val b = Compte.creer(serveur)
        assertEquals(emptyList<List<String>>(), pages(c).filter { it.isNotEmpty() })
        repeat(20) { jouer(c, 1) }
        assertEquals(listOf(20), pages(c).map { it.size })
        assertEquals(JsonNull, liste(c).obj.champ("suivant"))
        jouer(c, 1) // 21
        assertEquals(listOf(20, 1), pages(c).map { it.size })
        repeat(19) { jouer(c, 1) } // 40
        assertEquals(listOf(20, 20), pages(c).map { it.size })
        jouer(c, 1) // 41
        val pagesA = pages(c)
        assertEquals(listOf(20, 20, 1), pagesA.map { it.size })
        assertEquals(idsAttendus(c), pagesA.flatten()) // la plus récente d'abord, ni doublon ni trou
        assertEquals(41, pagesA.flatten().toSet().size)
        for (p in liste(c).obj.tableau("parties")) {
            assertEquals(setOf("id", "date", "etat", "bonnes", "servies", "elo"), p.jsonObject.keys)
        }

        // Une partie qui arrive entre deux pages : la page 2 reste celle qu'elle devait être.
        val premiere = liste(c)
        val curseur = premiere.obj.str("suivant")
        val nouvelle = jouer(c, 3)
        val suite = liste(c, curseur)
        assertEquals(200, suite.status)
        assertEquals(pagesA[1], suite.obj.tableau("parties").map { it.jsonObject.str("id") })
        assertEquals(nouvelle, liste(c).obj.tableau("parties")[0].jsonObject.str("id"))

        // Des curseurs qui ne sont pas les siens : requete_invalide, ou mes parties seulement.
        repeat(21) { jouer(b, 1) }
        val curseurB = liste(b).obj.str("suivant")
        val miens = idsAttendus(c).toSet()
        val reel = Base64.getUrlEncoder().withoutPadding()
        val variantes = listOf(
            "" to "vide",
            curseur.dropLast(4) to "tronqué",
            curseur.dropLast(1) + (if (curseur.last() == 'A') "B" else "A") to "dernier caractère changé",
            curseur + "AA" to "rallongé",
            "@@@@####" to "pas du base64",
            reel.encodeToString("n'importe quoi".toByteArray()) to "autre contenu",
            reel.encodeToString("' OR 1=1 --|abc".toByteArray()) to "injection",
            reel.encodeToString("2026-01-01 00:00:00.000Z|".toByteArray()) to "sans identifiant",
            curseurB to "curseur d'un autre compte",
        )
        for ((valeur, nom) in variantes) {
            val r = liste(c, valeur)
            if (r.status == 200) {
                val ids = r.obj.tableau("parties").map { it.jsonObject.str("id") }
                assertTrue("$nom : parties d'un autre dans $r", ids.all { it in miens + nouvelle })
                assertTrue("$nom : page de plus de 20", ids.size <= 20)
            } else {
                r.doitEtreErreur(400, "requete_invalide")
            }
        }
        // Le curseur d'un autre compte, avec ma liste : jamais une partie de lui.
        liste(c, curseurB).let { r ->
            if (r.status == 200) {
                assertTrue(r.obj.tableau("parties").none { it.jsonObject.str("id") in idsAttendus(b) })
            }
        }
        val deux = serveur.api.get("$racine/historique?avant=$curseur&avant=$curseurB", c.jeton)
        if (deux.status == 200) {
            assertTrue(deux.obj.tableau("parties").none { it.jsonObject.str("id") in idsAttendus(b) })
        } else {
            deux.doitEtreErreur(400, "requete_invalide")
        }
        // Un paramètre inconnu ne change rien.
        assertEquals(20, serveur.api.get("$racine/historique?taille=3", c.jeton).obj.tableau("parties").size)

        // Des `created` identiques à cheval sur la limite de page : ni doublon, ni trou.
        val ordre = idsAttendus(c)
        val commun = serveur.fiche("eruditus_parties", ordre[20]).str("created")
        for (i in 18..22) serveur.regler("eruditus_parties", ordre[i], "created" to commun)
        assertEquals(5, serveur.lister("eruditus_parties", "joueur = '${c.id}' && created = '$commun'").size)
        val apres = pages(c)
        assertEquals(idsAttendus(c), apres.flatten())
        assertEquals(42, apres.flatten().toSet().size)
        assertEquals(listOf(20, 20, 2), apres.map { it.size })
        // Et même découpage depuis une page 1 relue : le curseur à cheval sur les égalités.
        val c1 = liste(c).obj.str("suivant")
        assertEquals(apres[1], liste(c, c1).obj.tableau("parties").map { it.jsonObject.str("id") })
    }

    // --- T78 : lecture seule -----------------------------------------------------------------------

    @Test
    fun `T78 lire l'historique d'une partie inactive n'ecrit rien et n'abandonne rien`() {
        val inactif = Serveur.elo // inactivité de 4 s
        val api = inactif.api
        val c = Compte.creer(inactif)
        val finie = c.jouerAvec(2).let { inactif.lister("eruditus_parties", "joueur = '${c.id}'").single().str("id") }
        val (enCours, _) = c.commencer()
        Thread.sleep(4600) // la partie en cours dépasse l'inactivité permise

        fun etat() = listOf(
            inactif.fiche("eruditus_parties", enCours).let { it.str("etat") to it.str("updated") },
            inactif.fiche("eruditus_parties", finie).let { it.str("etat") to it.str("updated") },
            inactif.fiche("eruditus_joueurs", c.id).let { it.int("elo") to it.int("elo_reponses") },
            inactif.lignes(enCours).size,
            inactif.compter("eruditus_reponses", "joueur = '${c.id}'"),
            inactif.compter("eruditus_parties", "joueur = '${c.id}'"),
        )
        val avant = etat()
        assertEquals("en_cours", inactif.fiche("eruditus_parties", enCours).str("etat"))

        val pool = Executors.newFixedThreadPool(4)
        val reponses = try {
            (1..6).flatMap {
                listOf(
                    pool.submit(Callable { api.get("$racine/historique", c.jeton) }),
                    pool.submit(Callable { api.get("$racine/historique/$enCours", c.jeton) }),
                    pool.submit(Callable { api.get("$racine/historique/$finie", c.jeton) }),
                    pool.submit(Callable { api.get("$racine/elo/courbe", c.jeton) }),
                )
            }.map { it.get() }
        } finally {
            pool.shutdown()
        }
        for ((i, r) in reponses.withIndex()) {
            when (i % 4) {
                1 -> r.doitEtreErreur(404, "partie_introuvable")
                else -> assertEquals("$r", 200, r.status)
            }
        }
        // Aucune partie abandonnée au passage, aucune question servie, rien d'écrit.
        assertEquals(avant, etat())
        assertEquals("en_cours", inactif.fiche("eruditus_parties", enCours).str("etat"))
        assertEquals(listOf(finie), api.get("$racine/historique", c.jeton).obj.tableau("parties").map { it.jsonObject.str("id") })
    }

    // --- T80 : aucune clé interne ------------------------------------------------------------------

    @Test
    fun `T80 aucune cle interne, aucun identifiant de question, rien d'un autre joueur`() {
        val c = Compte.creer(serveur)
        val autre = Compte.creer(serveur)
        jouer(autre, 3)
        jouerVarie(c)
        val (_, _) = c.commencer()
        c.commencer() // abandonne la précédente : une partie abandonnée à une question
        val ids = serveur.lister("eruditus_parties", "joueur = '${c.id}' && etat != 'en_cours'").map { it.str("id") }
        assertEquals(2, ids.size)

        val reponses = listOf(liste(c)) + ids.map { detail(c, it) } + serveur.api.get("$racine/elo/courbe", c.jeton)
        val interdits = setOf(
            "elo_k", "difficulte", "difficulte_reponses", "elo_avant", "elo_apres", "elo_difficulte", "elo_reponses",
            "question_empreinte", "ref", "observation", "bonne_reponse", "niveau_stocke", "question", "joueur", "pseudo",
            "active", "created", "updated", "collectionId", "collectionName", "token", "code_recuperation",
        )
        for (r in reponses) {
            assertEquals("$r", 200, r.status)
            assertEquals("$r", emptySet<String>(), cles(r.obj).intersect(interdits))
            assertFalse("Pseudo ou identifiant d'un autre joueur : $r", autre.pseudo in r.corps || autre.id in r.corps)
            for (q in serveur.lister("eruditus_questions")) {
                assertFalse("Identifiant de question : $r", q.str("id") in r.corps)
                assertFalse("ref de question : $r", q.str("ref") in r.corps)
            }
        }
        assertEquals(setOf("parties", "suivant"), reponses[0].obj.keys)
        assertEquals(setOf("partie", "elo", "questions"), reponses[1].obj.keys)
        assertEquals(setOf("variation", "valeur"), reponses[1].obj.objet("elo").keys)
        assertEquals(setOf("depart", "points", "provisoire"), reponses.last().obj.keys)
        for (p in reponses.last().obj.tableau("points")) assertEquals(setOf("date", "elo"), p.jsonObject.keys)
        // Les dates sont celles de la base, en ISO 8601 UTC.
        for (p in reponses[0].obj.tableau("parties")) {
            val partie = serveur.fiche("eruditus_parties", p.jsonObject.str("id"))
            assertEquals(iso(partie.str("created")), p.jsonObject.str("date"))
            Instant.parse(p.jsonObject.str("date"))
        }
    }

    // --- T81 : les réponses d'avant ne changent pas -----------------------------------------------

    @Test
    fun `T81 les reponses d'avant gardent leurs cles et ne bougent pas quand on lit l'historique`() {
        val c = Compte.creer(serveur)
        val creation = c.nouvellePartie()
        assertEquals(setOf("partie", "question"), creation.obj.keys)
        assertEquals(setOf("id", "total"), creation.obj.objet("partie").keys)
        assertEquals(setOf("numero", "total", "enonce", "choix", "delaiMs", "restantMs", "enjeu"), creation.obj.objet("question").keys)
        val id = creation.obj.objet("partie").str("id")
        val moiAvant = c.moi().corps
        // Lire l'historique pendant la partie ne change rien à ce que /moi et /question répondent.
        liste(c); serveur.api.get("$racine/elo/courbe", c.jeton); detail(c, id)
        assertEquals(moiAvant, c.moi().corps)
        assertEquals(setOf("joueur", "score", "rang", "classes", "elo"), c.moi().obj.keys)
        assertEquals(setOf("question"), c.question(id).obj.keys)
        val enonce = creation.obj.objet("question").str("enonce")
        val r = c.repondre(id, 1, Banque.bonne(enonce))
        assertEquals(setOf("correction", "bilan"), r.obj.keys)
        assertEquals(
            setOf("numero", "choix", "correcte", "bonneReponse", "horsDelai", "point", "raison", "score", "explication", "source", "elo"),
            r.obj.objet("correction").keys,
        )
        assertEquals(setOf("top", "moi", "classes"), serveur.api.get("$racine/classement", c.jeton).obj.keys)
        assertEquals(setOf("top", "moi", "classes", "restantes"), serveur.api.get("$racine/classement/elo", c.jeton).obj.keys)
        var question = c.question(id).obj.objet("question")
        var bilan: JsonElement = JsonNull
        for (numero in 2..5) {
            val rep = c.repondre(id, numero, Banque.bonne(question.str("enonce")))
            bilan = rep.obj.champ("bilan")
            if (numero < 5) question = c.question(id).obj.objet("question")
        }
        assertEquals(setOf("bonnes", "points", "score", "elo"), (bilan as JsonObject).keys)
        assertEquals(setOf("valeur", "variation", "revision"), bilan.objet("elo").keys)
    }
}

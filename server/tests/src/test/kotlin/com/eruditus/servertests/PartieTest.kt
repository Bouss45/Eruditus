package com.eruditus.servertests

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Partie (spec/regles.md § 2) et tentatives T16 – T29, sur le serveur aux délais par défaut. */
class PartieTest {
    private val serveur = Serveur.principal
    private val api = serveur.api

    private fun reponsesEnregistrees(partie: String) =
        serveur.compter("eruditus_reponses", "partie = '$partie' && repondue_le != ''")

    @Test
    fun `partie complete, corrections et bilan`() {
        val compte = Compte.creer(serveur)
        val (id, q1) = compte.commencer()
        ContratV1Test.verifierQuestion(q1, 1)

        // 1 : bonne réponse → gagne.
        val r1 = compte.repondre(id, 1, Banque.bonne(q1.str("enonce"))).obj.objet("correction")
        assertEquals("gagne", r1.str("raison"))
        assertTrue(r1.bool("correcte"))
        assertFalse(r1.bool("horsDelai"))
        assertEquals(1, r1.int("point"))
        assertEquals(Banque.bonne(q1.str("enonce")), r1.int("bonneReponse"))
        assertEquals(1, r1.int("score"))

        // 2 : mauvaise réponse → fausse.
        val q2 = compte.question(id).obj.objet("question")
        val r2 = compte.repondre(id, 2, Banque.fausse(q2.str("enonce"))).obj.objet("correction")
        assertEquals("fausse", r2.str("raison"))
        assertFalse(r2.bool("correcte"))
        assertEquals(0, r2.int("point"))
        assertEquals(Banque.fausse(q2.str("enonce")), r2.int("choix"))
        assertEquals(Banque.bonne(q2.str("enonce")), r2.int("bonneReponse"))

        // 3 : sans réponse.
        val q3 = compte.question(id).obj.objet("question")
        val r3 = compte.repondre(id, 3, null).obj
        val c3 = r3.objet("correction")
        assertEquals("sans_reponse", c3.str("raison"))
        assertNull(c3.intOuNull("choix"))
        assertFalse(c3.bool("correcte"))
        assertEquals(Banque.bonne(q3.str("enonce")), c3.int("bonneReponse"))
        assertNull(r3.objetOuNull("bilan"))

        // 4 et 5 : bonnes.
        for (numero in 4..5) {
            val q = compte.question(id).obj.objet("question")
            val r = compte.repondre(id, numero, Banque.bonne(q.str("enonce"))).obj
            assertEquals("gagne", r.objet("correction").str("raison"))
            if (numero == 5) {
                val bilan = r.objet("bilan")
                assertEquals(3, bilan.int("bonnes"))
                assertEquals(3, bilan.int("points"))
                assertEquals(3, bilan.int("score"))
            }
        }
        assertEquals(3, compte.score())
        compte.question(id).doitEtreErreur(409, "partie_finie")
        assertEquals(1, serveur.compter("eruditus_parties", "id = '$id' && etat = 'terminee'"))
    }

    @Test
    fun `tirage, jamais servies d'abord puis servies il y a le plus longtemps`() {
        val compte = Compte.creer(serveur)
        val servies = mutableListOf<String>()
        // Parties 1 et 2 : 10 questions différentes, toutes jamais servies.
        repeat(2) {
            compte.jouer { enonce, numero -> servies += enonce; if (numero % 2 == 0) Banque.bonne(enonce) else Banque.fausse(enonce) }
        }
        assertEquals(10, servies.toSet().size)
        val jamaisServies = Banque.questions.map { it.str("enonce") }.toSet() - servies.toSet()
        assertEquals(2, jamaisServies.size)

        // Partie 3 : les 2 jamais servies d'abord, puis les 3 servies le plus tôt (D105), réussies
        // ou non.
        val partie3 = mutableListOf<String>()
        compte.jouer { enonce, _ -> partie3 += enonce; Banque.bonne(enonce) }
        assertEquals(jamaisServies, partie3.take(2).toSet())
        assertEquals(servies.take(3), partie3.drop(2))

        // Partie 4 : toutes vues ; les 5 dont le dernier service est le plus ancien.
        val partie4 = mutableListOf<String>()
        compte.jouer { enonce, _ -> partie4 += enonce; Banque.fausse(enonce) }
        val ordre = (servies + partie3).withIndex().groupBy({ it.value }, { it.index }).mapValues { it.value.max() }
        assertEquals(ordre.entries.sortedBy { it.value }.take(5).map { it.key }, partie4)
    }

    @Test
    fun `T16 lire les questions par l'API standard est refuse`() {
        val compte = Compte.creer(serveur)
        val question = serveur.lister("eruditus_questions").first().str("id")
        for (jeton in listOf(null, compte.jeton)) {
            val liste = api.get("/api/collections/eruditus_questions/records", jeton)
            assertTrue("$liste", liste.status in 400..404)
            val fiche = api.get("/api/collections/eruditus_questions/records/$question", jeton)
            assertTrue("$fiche", fiche.status in 400..404)
        }
    }

    @Test
    fun `T17 parties et reponses inaccessibles par l'API standard`() {
        val compte = Compte.creer(serveur)
        val (partie, _) = compte.commencer()
        val reponse = serveur.lister("eruditus_reponses", "partie = '$partie'").single().str("id")
        val question = serveur.lister("eruditus_questions").first().str("id")
        for (jeton in listOf(null, compte.jeton)) {
            for ((collection, id) in listOf("eruditus_parties" to partie, "eruditus_reponses" to reponse)) {
                val base = "/api/collections/$collection/records"
                assertTrue(api.get(base, jeton).status in 400..404)
                assertTrue(api.get("$base/$id", jeton).status in 400..404)
                assertTrue(api.patch("$base/$id", buildJsonObject { put("etat", "terminee"); put("points", 1) }, jeton).status in 400..404)
                assertTrue(api.delete("$base/$id", jeton).status in 400..404)
            }
            val creation = api.post(
                "/api/collections/eruditus_reponses/records",
                buildJsonObject {
                    put("partie", partie)
                    put("joueur", compte.id)
                    put("question", question)
                    put("numero", 2)
                    put("points", 1)
                    put("correcte", true)
                },
                jeton = jeton,
            )
            assertTrue("$creation", creation.status in 400..404)
            val nouvellePartie = api.post(
                "/api/collections/eruditus_parties/records",
                buildJsonObject { put("joueur", compte.id); put("etat", "en_cours") },
                jeton = jeton,
            )
            assertTrue("$nouvellePartie", nouvellePartie.status in 400..404)
        }
        assertEquals(1, serveur.compter("eruditus_reponses", "partie = '$partie'"))
        assertEquals(1, serveur.compter("eruditus_parties", "joueur = '${compte.id}'"))
        assertEquals(1, serveur.compter("eruditus_parties", "id = '$partie' && etat = 'en_cours'"))
        assertEquals(0, compte.score())
    }

    @Test
    fun `T18 la bonne reponse n'apparait jamais avant la reponse`() {
        val compte = Compte.creer(serveur)
        val creation = compte.nouvellePartie()
        assertFalse(creation.corps.contains("bonneReponse"))
        val id = creation.obj.objet("partie").str("id")
        val redemandee = compte.question(id)
        assertFalse(redemandee.corps.contains("bonneReponse"))
        val erreurs = listOf(
            compte.repondre(id, 2, 0),
            compte.repondre(id, 1, 7),
            compte.repondreBrut(id, buildJsonObject { put("numero", 1); put("choix", "2") }),
            compte.question("inconnue12345ab"),
        )
        erreurs.forEach { assertFalse("$it", it.corps.contains("bonneReponse")) }
        val correction = compte.repondre(id, 1, 0)
        assertTrue(correction.corps.contains("bonneReponse"))
        val suivante = compte.question(id)
        assertFalse(suivante.corps.contains("bonneReponse"))
    }

    @Test
    fun `T19 la partie d'un autre joueur est introuvable`() {
        val a = Compte.creer(serveur)
        val b = Compte.creer(serveur)
        val (partie, q1) = a.commencer()
        val inconnue = b.question("inconnue12345ab")
        inconnue.doitEtreErreur(404, "partie_introuvable")

        val question = b.question(partie)
        question.doitEtreErreur(404, "partie_introuvable")
        assertEquals(inconnue.obj, question.obj)
        val reponse = b.repondre(partie, 1, Banque.bonne(q1.str("enonce")))
        reponse.doitEtreErreur(404, "partie_introuvable")
        assertEquals(inconnue.obj, reponse.obj)

        assertEquals(0, reponsesEnregistrees(partie))
        assertEquals(0, b.score())
        val r = a.repondre(partie, 1, Banque.bonne(q1.str("enonce")))
        assertEquals("gagne", r.obj.objet("correction").str("raison"))
    }

    @Test
    fun `T20 repondre deux fois au meme numero`() {
        val compte = Compte.creer(serveur)
        val (id, q1) = compte.commencer()
        val bonne = Banque.bonne(q1.str("enonce"))
        assertEquals(200, compte.repondre(id, 1, Banque.fausse(q1.str("enonce"))).status)
        compte.repondre(id, 1, bonne).doitEtreErreur(409, "deja_repondu")
        compte.repondre(id, 1, Banque.fausse(q1.str("enonce"))).doitEtreErreur(409, "deja_repondu")
        compte.repondre(id, 1, null).doitEtreErreur(409, "deja_repondu")
        assertEquals(0, compte.score())
        val enregistree = serveur.lister("eruditus_reponses", "partie = '$id' && numero = 1").single()
        assertEquals(Banque.fausse(q1.str("enonce")) + 1, enregistree.int("choix"))
    }

    @Test
    fun `T21 numero pas encore servi, deja repondu ou hors de 1 a 5`() {
        val compte = Compte.creer(serveur)
        val (id, _) = compte.commencer()
        for (numero in listOf(2, 5, 0, 6, -1, 99)) compte.repondre(id, numero, 0).doitEtreErreur(409, "numero_invalide")
        compte.repondreBrut(id, buildJsonObject { put("numero", 1.5); put("choix", 0) }).doitEtreErreur(409, "numero_invalide")
        compte.repondreBrut(id, buildJsonObject { put("numero", "1"); put("choix", 0) }).doitEtreErreur(400, "requete_invalide")
        compte.repondreBrut(id, buildJsonObject { put("choix", 0) }).doitEtreErreur(400, "requete_invalide")
        compte.repondreBrut(id, buildJsonObject { put("numero", JsonNull); put("choix", 0) }).doitEtreErreur(400, "requete_invalide")
        assertEquals(0, reponsesEnregistrees(id))

        assertEquals(200, compte.repondre(id, 1, 0).status)
        compte.repondre(id, 1, 0).doitEtreErreur(409, "deja_repondu")
        // Entre deux questions, aucun numéro n'est en attente.
        compte.repondre(id, 2, 0).doitEtreErreur(409, "numero_invalide")
        assertEquals(200, compte.question(id).status)
        compte.repondre(id, 3, 0).doitEtreErreur(409, "numero_invalide")
        assertEquals(200, compte.repondre(id, 2, 0).status)
    }

    @Test
    fun `T22 choix invalide, rien d'enregistre`() {
        val compte = Compte.creer(serveur)
        val (id, q1) = compte.commencer()
        val invalides: List<Pair<JsonElement?, String>> = listOf(
            JsonPrimitive(-1) to "choix_invalide",
            JsonPrimitive(4) to "choix_invalide",
            JsonPrimitive(1.5) to "choix_invalide",
            JsonPrimitive(1e300) to "choix_invalide",
            JsonPrimitive("2") to "requete_invalide",
            JsonPrimitive(true) to "requete_invalide",
            null to "requete_invalide",
        )
        for ((choix, code) in invalides) {
            val corps = buildJsonObject {
                put("numero", 1)
                if (choix != null) put("choix", choix)
            }
            compte.repondreBrut(id, corps).doitEtreErreur(400, code)
        }
        compte.repondreBrut(id, buildJsonObject { put("numero", 1); put("choix", buildJsonObject { }) })
            .doitEtreErreur(400, "requete_invalide")
        assertEquals(0, reponsesEnregistrees(id))
        val r = compte.repondre(id, 1, Banque.bonne(q1.str("enonce")))
        assertEquals("gagne", r.obj.objet("correction").str("raison"))
    }

    @Test
    fun `T24 redemander la question ne relance pas le chrono`() {
        val compte = Compte.creer(serveur)
        val (id, q1) = compte.commencer()
        Thread.sleep(400)
        val a = compte.question(id).obj.objet("question")
        Thread.sleep(400)
        val b = compte.question(id).obj.objet("question")
        assertEquals(q1.str("enonce"), a.str("enonce"))
        assertEquals(q1.str("enonce"), b.str("enonce"))
        assertEquals(1, b.int("numero"))
        assertTrue("${a.int("restantMs")} puis ${b.int("restantMs")}", b.int("restantMs") < a.int("restantMs"))
        assertTrue(a.int("restantMs") <= 20_000 - 400)
        assertEquals(1, serveur.compter("eruditus_reponses", "partie = '$id'"))
    }

    @Test
    fun `T25 une question deja reussie ne rapporte plus`() {
        val compte = Compte.creer(serveur)
        // 12 questions : 2 parties tout juste (10 réussies), la 3e reprend 3 questions réussies.
        repeat(2) { compte.jouerAvec(5) }
        assertEquals(10, compte.score())
        val corrections = compte.jouerAvec(5).map { it.objet("correction") }
        val deja = corrections.filter { it.str("raison") == "deja_reussie" }
        assertEquals(3, deja.size)
        deja.forEach {
            assertTrue(it.bool("correcte"))
            assertEquals(0, it.int("point"))
        }
        assertEquals(2, corrections.count { it.str("raison") == "gagne" })
        assertEquals(12, compte.score())
    }

    @Test
    fun `T26 repondre a une partie terminee ou abandonnee pour une nouvelle`() {
        val compte = Compte.creer(serveur)
        val terminee = compte.jouerAvec(0)
        assertTrue(terminee.size == 5)
        val idTerminee = serveur.lister("eruditus_parties", "joueur = '${compte.id}'").single().str("id")
        compte.repondre(idTerminee, 5, 0).doitEtreErreur(409, "partie_finie")
        compte.question(idTerminee).doitEtreErreur(409, "partie_finie")

        val (ancienne, q1) = compte.commencer()
        val (nouvelle, _) = compte.commencer()
        assertEquals(1, serveur.compter("eruditus_parties", "id = '$ancienne' && etat = 'abandonnee'"))
        assertEquals(1, serveur.compter("eruditus_parties", "id = '$nouvelle' && etat = 'en_cours'"))
        compte.repondre(ancienne, 1, Banque.bonne(q1.str("enonce"))).doitEtreErreur(409, "partie_finie")
        compte.question(ancienne).doitEtreErreur(409, "partie_finie")
        assertEquals(0, reponsesEnregistrees(ancienne))
        assertEquals(0, compte.score())
    }

    @Test
    fun `T27 les champs de resultat envoyes sont ignores`() {
        val compte = Compte.creer(serveur)
        val (id, q1) = compte.commencer()
        val r = compte.repondreBrut(
            id,
            buildJsonObject {
                put("numero", 1)
                put("choix", Banque.fausse(q1.str("enonce")))
                put("point", 1)
                put("points", 1)
                put("score", 50)
                put("correcte", true)
                put("horsDelai", false)
                put("bonneReponse", Banque.fausse(q1.str("enonce")))
                put("raison", "gagne")
            },
        )
        assertEquals(200, r.status)
        val correction = r.obj.objet("correction")
        assertEquals("fausse", correction.str("raison"))
        assertEquals(0, correction.int("point"))
        assertFalse(correction.bool("correcte"))
        assertEquals(Banque.bonne(q1.str("enonce")), correction.int("bonneReponse"))
        assertEquals(0, correction.int("score"))
        assertEquals(0, compte.score())
    }

    @Test
    fun `T28 la meme bonne reponse dix fois en parallele`() {
        val compte = Compte.creer(serveur)
        val (id, q1) = compte.commencer()
        val bonne = Banque.bonne(q1.str("enonce"))
        val pool = Executors.newFixedThreadPool(10)
        try {
            val resultats = pool.invokeAll((1..10).map { Callable { compte.repondre(id, 1, bonne) } }).map { it.get() }
            assertEquals("$resultats", 1, resultats.count { it.status == 200 })
            resultats.filter { it.status != 200 }.forEach { it.doitEtreErreur(409, "deja_repondu") }
        } finally {
            pool.shutdown()
        }
        assertEquals(1, reponsesEnregistrees(id))
        assertEquals(1, compte.score())
    }

    @Test
    fun `T28 creer des parties et servir des questions en parallele`() {
        val compte = Compte.creer(serveur)
        val (id, q1) = compte.commencer()
        compte.repondre(id, 1, Banque.bonne(q1.str("enonce")))
        val pool = Executors.newFixedThreadPool(8)
        try {
            val questions = pool.invokeAll((1..8).map { Callable { compte.question(id) } }).map { it.get() }
            questions.forEach { assertEquals("$it", 200, it.status) }
            assertEquals(1, questions.map { it.obj.objet("question").str("enonce") }.toSet().size)
            assertEquals(2, serveur.compter("eruditus_reponses", "partie = '$id'"))

            val parties = pool.invokeAll((1..6).map { Callable { compte.nouvellePartie() } }).map { it.get() }
            parties.forEach { assertEquals("$it", 200, it.status) }
            assertEquals(1, serveur.compter("eruditus_parties", "joueur = '${compte.id}' && etat = 'en_cours'"))
        } finally {
            pool.shutdown()
        }
    }

    @Test
    fun `T29 creer des parties en rafale`() {
        val compte = Compte.creer(serveur)
        val ip = Ips.nouvelle()
        repeat(30) { assertEquals(200, compte.nouvellePartie(ip).status) }
        assertEquals(429, compte.nouvellePartie(ip).status)
        assertEquals(30, serveur.compter("eruditus_parties", "joueur = '${compte.id}'"))
        // Autre IP : compteur distinct.
        assertEquals(200, compte.nouvellePartie(Ips.nouvelle()).status)
    }

    @Test
    fun `limiteur sur le reste des routes Eruditus, 120 par minute et par IP`() {
        val ip = Ips.nouvelle()
        repeat(120) { assertEquals(200, api.get("/api/eruditus/v1/info", null, ip).status) }
        assertEquals(429, api.get("/api/eruditus/v1/info", null, ip).status)
        assertEquals(200, api.get("/api/eruditus/v1/info", null, Ips.nouvelle()).status)
    }
}

package com.eruditus.servertests

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Signalement d'une question (D118 – D124, spec/serveur.md § 5.4) : route, message transmis au faux
 * webhook, retrait à 3 comptes, plancher, import, Elo, et les tentatives T54 – T62.
 */
class SignalementTest {
    private val serveur = Serveur.signalement
    private val vide = JsonObject(emptyMap())

    @Before
    fun webhookQuiRepond() {
        FauxWebhook.mode = FauxWebhook.Mode.Statut(204)
    }

    // --- Outils --------------------------------------------------------------------------------

    private fun refLibre(prefixe: String = "sig"): String = "$prefixe-%03d".format(prochain.getAndIncrement())

    private fun idDe(s: Serveur, ref: String): String = s.lister("eruditus_questions", "ref = '$ref'").single().str("id")

    /** Partie dont la question n°1 est [question] (id) si donnée, répondue ; renvoie l'id de la partie. */
    private fun partieRepondue(s: Serveur, compte: Compte, question: String? = null): String {
        val (id, _) = compte.commencer()
        if (question != null) s.regler("eruditus_reponses", s.ligne(id, 1).str("id"), "question" to question)
        assertEquals(200, compte.repondre(id, 1, 0).status)
        return id
    }

    private fun signalerPar(s: Serveur, ref: String, motif: String = "reponse_fausse", commentaire: String? = null): Reponse {
        val compte = Compte.creer(s)
        val partie = partieRepondue(s, compte, idDe(s, ref))
        return s.signaler(compte.jeton, partie, 1, motif, commentaire)
    }

    private fun lignes(s: Serveur, question: String) = s.lister("eruditus_signalements", "question = '$question'")

    private fun observation(s: Serveur, question: String) = s.fiche("eruditus_questions", question).bool("observation")

    private fun uniqueMessage(ref: String): FauxWebhook.Message {
        val m = FauxWebhook.messagesQuestion(ref)
        assertEquals("Messages pour $ref : $m", 1, m.size)
        return m.single()
    }

    private fun champ(m: FauxWebhook.Message, nom: String): String? =
        m.embed.tableau("fields").map { it.jsonObject }.firstOrNull { it.str("name") == nom }?.str("value")

    // --- Message et ligne (D119, D120) ------------------------------------------------------------

    @Test
    fun `signalement ecrit et message exact, rien du joueur`() {
        val compte = Compte.creer(serveur)
        val ref = refLibre()
        val q = idDe(serveur, ref)
        val partie = partieRepondue(serveur, compte, q)
        val r = serveur.signaler(compte.jeton, partie, 1, "reponse_fausse", "  Vérifier la date.  ")
        assertEquals("$r", 200, r.status)
        assertEquals(vide, r.obj)

        val ligne = lignes(serveur, q).single()
        assertEquals("reponse_fausse", ligne.str("motif"))
        assertEquals("Vérifier la date.", ligne.str("commentaire"))
        assertEquals(compte.id, ligne.str("joueur"))

        val m = uniqueMessage(ref)
        assertEquals(FauxWebhook.chemin, m.chemin)
        assertEquals(setOf("username", "allowed_mentions", "embeds"), m.corps.keys)
        assertEquals(buildJsonObject { put("parse", JsonArray(emptyList())) }, m.corps.objet("allowed_mentions"))
        assertEquals(setOf("title", "description", "fields", "footer", "timestamp"), m.embed.keys)
        val numero = ref.substringAfter('-').toInt()
        assertEquals("Question de signalement numéro $numero ?", m.embed.str("description"))
        assertEquals(buildJsonObject { put("text", "Eruditus") }, m.embed.objet("footer"))
        assertEquals(listOf("Bonne réponse", "Motif", "Signalements", "Commentaire"), m.embed.tableau("fields").map { it.jsonObject.str("name") })
        assertEquals("${"ABCD"[numero % 4]}. Choix ${"ABCD"[numero % 4]} $numero", champ(m, "Bonne réponse"))
        assertEquals("Réponse fausse", champ(m, "Motif"))
        assertEquals("1 / 3", champ(m, "Signalements"))
        assertEquals("Vérifier la date.", champ(m, "Commentaire"))

        // T61 : rien du joueur dans le message.
        val texte = m.corps.toString()
        for (interdit in listOf(compte.pseudo, compte.id, compte.jeton, "10.0.")) assertFalse(interdit, interdit in texte)
    }

    @Test
    fun `les quatre motifs, libelles du message, commentaire absent sans champ`() {
        val libelles = mapOf(
            "reponse_fausse" to "Réponse fausse", "enonce_ambigu" to "Énoncé ambigu",
            "explication_fausse" to "Explication fausse", "autre" to "Autre",
        )
        for ((motif, libelle) in libelles) {
            val ref = refLibre()
            assertEquals(200, signalerPar(serveur, ref, motif).status)
            val m = uniqueMessage(ref)
            assertEquals(libelle, champ(m, "Motif"))
            assertEquals(null, champ(m, "Commentaire"))
        }
    }

    // --- Tentatives T54 – T57 ----------------------------------------------------------------------

    @Test
    fun `T54 rafale d'un compte, le 6e signalement de l'heure est refuse`() {
        val compte = Compte.creer(serveur)
        val refs = List(6) { refLibre() }
        val parties = refs.map { partieRepondue(serveur, compte, idDe(serveur, it)) }
        for (i in 0 until 5) assertEquals("Signalement $i", 200, serveur.signaler(compte.jeton, parties[i]).status)
        serveur.signaler(compte.jeton, parties[5]).doitEtreErreur(409, "signalement_trop_frequent")
        assertEquals(5, serveur.compter("eruditus_signalements", "joueur = '${compte.id}'"))
        assertEquals(0, FauxWebhook.messagesQuestion(refs[5]).size)
        // Un renvoi d'un signalement déjà fait reste inoffensif, même au-delà de la limite.
        assertEquals(200, serveur.signaler(compte.jeton, parties[0]).status)
        assertEquals(5, serveur.compter("eruditus_signalements", "joueur = '${compte.id}'"))
    }

    @Test
    fun `T55 le meme signalement dix fois, dont dix en parallele, une seule ligne`() {
        val compte = Compte.creer(serveur)
        val ref = refLibre()
        val q = idDe(serveur, ref)
        val partie = partieRepondue(serveur, compte, q)
        repeat(3) { assertEquals(200, serveur.signaler(compte.jeton, partie).status) }
        val pool = Executors.newFixedThreadPool(10)
        val depart = CountDownLatch(1)
        val statuts = (1..10).map {
            pool.submit<Reponse> { depart.await(); serveur.signaler(compte.jeton, partie) }
        }
        depart.countDown()
        val reponses = statuts.map { it.get(60, TimeUnit.SECONDS) }
        pool.shutdown()
        reponses.forEach { assertEquals("$it", 200, it.status) }
        assertEquals(1, lignes(serveur, q).size)
        assertEquals(1, FauxWebhook.messagesQuestion(ref).size)
    }

    @Test
    fun `T56 question jamais recue, partie inconnue ou d'un autre, numero hors partie, champs ajoutes ignores`() {
        val compte = Compte.creer(serveur)
        val autre = Compte.creer(serveur)
        val ref = refLibre()
        val q = idDe(serveur, ref)
        val partie = partieRepondue(serveur, compte, q)
        val (nonRepondue, _) = autre.commencer()

        fun rien() = assertEquals(0, serveur.compter("eruditus_signalements", "joueur = '${compte.id}' || joueur = '${autre.id}'"))

        val inconnue = serveur.signaler(compte.jeton, "abcdefghijklmno")
        inconnue.doitEtreErreur(404, "partie_introuvable")
        val dunAutre = serveur.signaler(autre.jeton, partie)
        dunAutre.doitEtreErreur(404, "partie_introuvable")
        assertEquals(inconnue.obj.str("message"), dunAutre.obj.str("message"))
        for (numero in listOf(0, 2, 6, -1, 100)) serveur.signaler(compte.jeton, partie, numero).doitEtreErreur(409, "numero_invalide")
        serveur.signaler(autre.jeton, nonRepondue, 1).doitEtreErreur(409, "numero_invalide")
        rien()

        val corps = { numero: JsonElement?, partieId: JsonElement? ->
            buildJsonObject {
                partieId?.let { put("partie", it) }
                numero?.let { put("numero", it) }
                put("motif", "autre")
            }
        }
        val chemin = "/api/eruditus/v1/signalement"
        serveur.api.post(chemin, corps(JsonPrimitive(1.5), JsonPrimitive(partie)), jeton = compte.jeton).doitEtreErreur(409, "numero_invalide")
        for (numero in listOf(JsonPrimitive("1"), JsonNull, JsonPrimitive(true), null)) {
            serveur.api.post(chemin, corps(numero, JsonPrimitive(partie)), jeton = compte.jeton).doitEtreErreur(400, "requete_invalide")
        }
        for (partieId in listOf(JsonPrimitive(12), JsonNull, null)) {
            serveur.api.post(chemin, corps(JsonPrimitive(1), partieId), jeton = compte.jeton).doitEtreErreur(400, "requete_invalide")
        }
        rien()

        // Un identifiant de question, une ref ou un joueur dans le corps ne désignent rien.
        val autreQuestion = idDe(serveur, refLibre())
        val r = serveur.signaler(
            compte.jeton, partie, 1, "autre",
            extra = mapOf(
                "question" to JsonPrimitive(autreQuestion), "ref" to JsonPrimitive("sig-069"),
                "joueur" to JsonPrimitive(autre.id), "observation" to JsonPrimitive(true), "active" to JsonPrimitive(false),
            ),
        )
        assertEquals("$r", 200, r.status)
        val ligne = lignes(serveur, q).single()
        assertEquals(compte.id, ligne.str("joueur"))
        assertEquals(0, lignes(serveur, autreQuestion).size)
        val fiche = serveur.fiche("eruditus_questions", q)
        assertFalse(fiche.bool("observation"))
        assertTrue(fiche.bool("active"))
    }

    @Test
    fun `T56 on signale aussi une partie terminee`() {
        val compte = Compte.creer(serveur)
        compte.jouer { _, _ -> 0 }
        val partie = serveur.lister("eruditus_parties", "joueur = '${compte.id}'").single()
        assertEquals("terminee", partie.str("etat"))
        assertEquals(200, serveur.signaler(compte.jeton, partie.str("id"), 5).status)
    }

    @Test
    fun `T57 motif et commentaire hors de la regle, champs etrangers sans effet`() {
        val compte = Compte.creer(serveur)
        val partie = partieRepondue(serveur, compte)
        val chemin = "/api/eruditus/v1/signalement"
        for (motif in listOf("inconnu", "", "REPONSE_FAUSSE", " autre", "autre ")) {
            serveur.signaler(compte.jeton, partie, 1, motif).doitEtreErreur(400, "signalement_invalide")
        }
        for (motif in listOf(JsonPrimitive(5), JsonNull, JsonPrimitive(true))) {
            serveur.api.post(chemin, buildJsonObject { put("partie", partie); put("numero", 1); put("motif", motif) }, jeton = compte.jeton)
                .doitEtreErreur(400, "requete_invalide")
        }
        serveur.api.post(chemin, buildJsonObject { put("partie", partie); put("numero", 1) }, jeton = compte.jeton)
            .doitEtreErreur(400, "requete_invalide")
        serveur.signaler(compte.jeton, partie, 1, "autre", "a".repeat(201)).doitEtreErreur(400, "signalement_invalide")
        serveur.signaler(compte.jeton, partie, 1, "autre", "é".repeat(201)).doitEtreErreur(400, "signalement_invalide")
        for (commentaire in listOf(JsonPrimitive(5), JsonNull, JsonPrimitive(false))) {
            serveur.api.post(
                chemin, buildJsonObject { put("partie", partie); put("numero", 1); put("motif", "autre"); put("commentaire", commentaire) },
                jeton = compte.jeton,
            ).doitEtreErreur(400, "requete_invalide")
        }
        assertEquals(0, serveur.compter("eruditus_signalements", "joueur = '${compte.id}'"))
    }

    @Test
    fun `commentaire, la regle de l'avis puis 200 caracteres, mentions sans effet`() {
        // Contrôles retirés avant le compte ; blancs seuls = pas de commentaire.
        val refs = List(3) { refLibre() }
        assertEquals(200, signalerPar(serveur, refs[0], "autre", "a".repeat(200) + "\u0000\r").status)
        assertEquals("a".repeat(200), champ(uniqueMessage(refs[0]), "Commentaire"))
        assertEquals(200, signalerPar(serveur, refs[1], "autre", "  \n  ").status)
        assertEquals(null, champ(uniqueMessage(refs[1]), "Commentaire"))
        assertEquals("", lignes(serveur, idDe(serveur, refs[1])).single().str("commentaire"))
        assertEquals(200, signalerPar(serveur, refs[2], "autre", "@everyone @here <@123> <@&456>").status)
        val m = uniqueMessage(refs[2])
        assertEquals(buildJsonObject { put("parse", JsonArray(emptyList())) }, m.corps.objet("allowed_mentions"))
        assertEquals("@everyone @here <@123> <@&456>", champ(m, "Commentaire"))
    }

    // --- Seuil et retrait (D121), T58 --------------------------------------------------------------

    @Test
    fun `T58 trois comptes distincts mettent la question en observation`() {
        val ref = refLibre()
        val q = idDe(serveur, ref)
        // Un seul compte, plusieurs fois : jamais plus d'une ligne.
        val seul = Compte.creer(serveur)
        val partieSeul = partieRepondue(serveur, seul, q)
        repeat(4) { serveur.signaler(seul.jeton, partieSeul) }
        assertEquals(1, lignes(serveur, q).size)
        assertFalse(observation(serveur, q))

        assertEquals(200, signalerPar(serveur, ref).status)
        assertFalse(observation(serveur, q))
        assertEquals(200, signalerPar(serveur, ref).status)
        assertTrue(observation(serveur, q))
        val messages = FauxWebhook.messagesQuestion(ref)
        assertEquals(listOf("1 / 3", "2 / 3", "3 / 3 · mise en observation"), messages.map { champ(it, "Signalements") })
        // Toujours active : l'observation est un état à part.
        assertTrue(serveur.fiche("eruditus_questions", q).bool("active"))
    }

    @Test
    fun `T58 un compte supprime retire son vote`() {
        val ref = refLibre()
        val q = idDe(serveur, ref)
        val a = Compte.creer(serveur)
        val b = Compte.creer(serveur)
        for (c in listOf(a, b)) assertEquals(200, serveur.signaler(c.jeton, partieRepondue(serveur, c, q)).status)
        val suppression = serveur.api.post(
            "/api/eruditus/v1/comptes/suppression", buildJsonObject { put("motDePasse", b.motDePasse) }, jeton = b.jeton,
        )
        assertEquals("$suppression", 204, suppression.status)
        assertEquals(1, lignes(serveur, q).size)
        assertEquals(200, signalerPar(serveur, ref).status)
        assertFalse(observation(serveur, q))
        assertEquals(200, signalerPar(serveur, ref).status)
        assertTrue(observation(serveur, q))
    }

    @Test
    fun `T61 Discord en panne ou muet, le joueur recoit 200 et la question est retiree`() {
        val ref = refLibre()
        val q = idDe(serveur, ref)
        FauxWebhook.mode = FauxWebhook.Mode.Statut(500)
        assertEquals(200, signalerPar(serveur, ref).status)
        FauxWebhook.mode = FauxWebhook.Mode.Statut(429, FauxWebhook.CORPS_429)
        assertEquals(200, signalerPar(serveur, ref).status)
        FauxWebhook.mode = FauxWebhook.Mode.Silence(7_000)
        val debut = System.nanoTime()
        assertEquals(200, signalerPar(serveur, ref).status)
        val duree = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - debut)
        assertTrue("Attente de Discord : $duree ms", duree < 8_000)
        assertEquals(3, lignes(serveur, q).size)
        assertTrue(observation(serveur, q))
    }

    @Test
    fun `T61 sans webhook la route marche et ne montre aucune adresse`() {
        val principal = Serveur.principal
        val compte = Compte.creer(principal)
        val partie = partieRepondue(principal, compte)
        val ligneReponse = principal.ligne(partie, 1)
        val r = principal.signaler(compte.jeton, partie)
        assertEquals("$r", 200, r.status)
        assertEquals(1, principal.compter("eruditus_signalements", "question = '${ligneReponse.str("question")}'"))
    }

    @Test
    fun `T61 vingt signalements par IP et par heure, le 21e est refuse`() {
        val compte = Compte.creer(serveur)
        val ip = "203.0.113.77"
        repeat(20) {
            serveur.api.post("/api/eruditus/v1/signalement", buildJsonObject {}, jeton = compte.jeton, ip = ip)
                .doitEtreErreur(400, "requete_invalide")
        }
        assertEquals(429, serveur.api.post("/api/eruditus/v1/signalement", buildJsonObject {}, jeton = compte.jeton, ip = ip).status)
    }

    @Test
    fun `route reservee aux joueurs, ni sans jeton, ni jeton de superutilisateur ou de users`() {
        val compte = Compte.creer(serveur)
        val partie = partieRepondue(serveur, compte)
        assertEquals(401, serveur.signaler(null, partie).status)
        assertEquals(401, serveur.signaler("faux.jeton.pas.valable", partie).status)
        assertEquals(401, serveur.signaler(serveur.jetonSu, partie).status)
        assertEquals(401, serveur.signaler(serveur.jetonUsers(), partie).status)
        assertEquals(0, serveur.compter("eruditus_signalements", "joueur = '${compte.id}'"))
    }

    // --- Import (T59), plancher (T60) ---------------------------------------------------------------

    @Test
    fun `T59 un nouvel import ne remet pas la question en jeu`() {
        val ref = refLibre()
        val q = idDe(serveur, ref)
        repeat(3) { assertEquals(200, signalerPar(serveur, ref).status) }
        assertTrue(observation(serveur, q))

        serveur.importerListe(questions("sig", 70))
        assertTrue(observation(serveur, q))
        val modifiees = questions("sig", 70).map { question ->
            if (question.str("ref") == ref) JsonObject(question + ("explication" to JsonPrimitive("Explication corrigée."))) else question
        }
        serveur.importerListe(modifiees)
        val fiche = serveur.fiche("eruditus_questions", q)
        assertEquals("Explication corrigée.", fiche.str("explication"))
        assertTrue(fiche.bool("observation"))
        assertTrue(fiche.bool("active"))
        assertEquals(3, lignes(serveur, q).size)
    }

    @Test
    fun `T60 sous le plancher de 50 questions servables la question reste en jeu`() {
        val s = Serveur.plancher
        val retiree = "pla-000"
        repeat(3) { assertEquals(200, signalerPar(s, retiree).status) }
        assertTrue(observation(s, idDe(s, retiree)))
        assertEquals(
            listOf("1 / 3", "2 / 3", "3 / 3 · mise en observation"),
            FauxWebhook.messagesQuestion(retiree).map { champ(it, "Signalements") },
        )

        val gardee = "pla-001"
        repeat(3) { assertEquals(200, signalerPar(s, gardee).status) }
        val q = idDe(s, gardee)
        assertFalse(observation(s, q))
        assertEquals(3, lignes(s, q).size)
        assertEquals("3 / 3 · gardée en jeu : banque trop petite", champ(FauxWebhook.messagesQuestion(gardee).last(), "Signalements"))
        assertEquals(51 - 1, s.compter("eruditus_questions", "active = true && observation = false"))
    }

    // --- Effet sur le jeu et sur l'Elo (D122), T62 ---------------------------------------------------

    @Test
    fun `T62 une question en observation n'est jamais tiree`() {
        val ref = refLibre()
        val q = idDe(serveur, ref)
        serveur.regler("eruditus_questions", q, "observation" to true)
        val compte = Compte.creer(serveur)
        repeat(40) { assertEquals(200, compte.nouvellePartie().status) }
        val tirees = serveur.lister("eruditus_parties", "joueur = '${compte.id}'")
            .flatMap { it.tableau("questions").map { id -> (id as JsonPrimitive).content } }
        assertTrue("Questions tirées : ${tirees.toSet().size}", tirees.toSet().size >= 30)
        assertFalse(q in tirees)
        serveur.regler("eruditus_questions", q, "observation" to false)
    }

    @Test
    fun `T62 la partie en cours compte la reponse mais la difficulte ne bouge plus`() {
        val observee = idDe(serveur, refLibre())
        val normale = idDe(serveur, refLibre())
        serveur.regler("eruditus_questions", observee, "observation" to true)

        fun repondreA(question: String): JsonObject {
            val compte = Compte.creer(serveur)
            serveur.reglerJoueur(compte, 1000, 40) // ni provisoire, ni plafonné : K = 24
            val partie = partieRepondue(serveur, compte, question)
            return serveur.ligne(partie, 1)
        }
        val avantO = serveur.fiche("eruditus_questions", observee)
        val avantN = serveur.fiche("eruditus_questions", normale)
        val ligneO = repondreA(observee)
        val ligneN = repondreA(normale)
        // La réponse du joueur compte comme d'habitude.
        assertEquals(24, ligneO.int("elo_k"))
        assertEquals(24, ligneN.int("elo_k"))
        val apresO = serveur.fiche("eruditus_questions", observee)
        val apresN = serveur.fiche("eruditus_questions", normale)
        assertEquals(avantN.int("difficulte_reponses") + 1, apresN.int("difficulte_reponses"))
        assertEquals(avantO.int("difficulte_reponses"), apresO.int("difficulte_reponses"))
        assertEquals(avantO.int("difficulte"), apresO.int("difficulte"))
        serveur.regler("eruditus_questions", observee, "observation" to false)
    }

    @Test
    fun `T62 revision ne compte pas une question en observation`() {
        val s = Serveur.sixQuestions
        val compte = Compte.creer(s)
        compte.jouer { _, _ -> 0 }
        assertFalse(compte.elo().bool("revision"))
        val vues = s.lignesDuJoueur(compte).map { it.str("question") }.toSet()
        val jamaisVue = s.lister("eruditus_questions").map { it.str("id") }.single { it !in vues }
        s.regler("eruditus_questions", jamaisVue, "observation" to true)
        assertTrue(compte.elo().bool("revision"))
    }

    companion object {
        private val prochain = AtomicInteger(0)

        /**
         * [n] questions factices de ref `<prefixe>-NNN` : énoncé « Question de signalement numéro N ? »,
         * choix « Choix A N »…, bonne réponse N mod 4, niveau 3.
         */
        fun questions(prefixe: String, n: Int): List<JsonObject> = (0 until n).map { i ->
            buildJsonObject {
                put("ref", "$prefixe-%03d".format(i))
                put("enonce", "Question de signalement numéro $i ?")
                put("choix", buildJsonArray { "ABCD".forEach { add(JsonPrimitive("Choix $it $i")) } })
                put("bonneReponse", i % 4)
                put("active", true)
                put("explication", "Explication factice $i.")
                put("source", "Source factice")
                put("niveau", 3)
            }
        }
    }
}

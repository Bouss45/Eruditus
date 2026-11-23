package com.eruditus.android.donnees.api

import com.eruditus.android.donnees.api.ErreurApi.NonAutorise
import com.eruditus.android.donnees.api.ErreurApi.Reseau
import com.eruditus.android.donnees.api.ErreurApi.Serveur
import com.eruditus.android.donnees.api.ErreurApi.TropDeTentatives
import com.eruditus.android.donnees.api.TransportFactice.Companion.ID_PARTIE
import com.eruditus.android.donnees.api.TransportFactice.Companion.courbe
import com.eruditus.android.donnees.api.TransportFactice.Companion.erreur
import com.eruditus.android.donnees.api.TransportFactice.Companion.historique
import com.eruditus.android.donnees.api.TransportFactice.Companion.partieListee
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * `GET /historique`, `GET /historique/{id}` et `GET /elo/courbe` (spec/serveur.md § 5.1, § 5.2, D131,
 * D132, D135) : routes à jeton, en lecture. Lecture tolérante (D22, spec/ecrans.md § 16 et § 17) :
 * un champ obligatoire manquant ou mal typé rend la réponse illisible (D53), un champ inconnu est
 * ignoré, un `etat` inconnu vaut « terminée », un `statut` ou un `detail` inconnu donne un détail
 * indisponible.
 */
class ClientApiHistoriqueTest {

    private val transport = TransportFactice()
    private val api = ClientApi(transport) { "jeton" }

    private val le22 = Instant.parse("2026-11-22T10:00:00.123Z")

    // Liste ------------------------------------------------------------------------------------

    private fun page(corps: String, avant: String? = null): Resultat<PageHistorique> = runBlocking {
        transport.repondre(Route.Historique, 200, corps)
        api.historique(avant)
    }

    private fun pageIllisible(corps: String) = assertEquals(corps, Resultat.Echec(Serveur), page(corps))

    @Test
    fun `liste, route a jeton, GET sans corps, premiere page sans curseur`() {
        page(historique(emptyList()))
        val r = transport.requetes.single()
        assertEquals("GET /api/eruditus/v1/historique", "${r.methode} ${r.chemin}")
        assertEquals("jeton", r.jeton)
        assertEquals(null, r.corps)
    }

    @Test
    fun `liste, le curseur est envoye tel quel dans avant, encode`() {
        page(historique(emptyList()), avant = "323032362d31")
        assertEquals("/api/eruditus/v1/historique?avant=323032362d31", transport.requetes.single().chemin)
        transport.requetes.clear()
        page(historique(emptyList()), avant = "a b&c=d")
        assertEquals("/api/eruditus/v1/historique?avant=a+b%26c%3Dd", transport.requetes.single().chemin)
    }

    @Test
    fun `liste, parties lues dans l'ordre recu, avec leur curseur`() {
        val corps = historique(
            listOf(
                partieListee("pa", bonnes = 3, servies = 5, variation = 14, valeur = 1261),
                partieListee("pb", date = "2026-11-21T08:30:00.000Z", etat = "abandonnee", bonnes = 2, servies = 3, variation = -6, valeur = 1047),
                partieListee("pc", bonnes = 4, servies = 5, variation = null, valeur = null),
            ),
            suivant = "c0ffee",
        )
        assertEquals(
            Resultat.Ok(
                PageHistorique(
                    listOf(
                        PartieListee("pa", le22, FinPartie.Terminee, 3, 5, EloPartie(14, 1261)),
                        PartieListee("pb", Instant.parse("2026-11-21T08:30:00.000Z"), FinPartie.Abandonnee, 2, 3, EloPartie(-6, 1047)),
                        PartieListee("pc", le22, FinPartie.Terminee, 4, 5, EloPartie(null, null)),
                    ),
                    suivant = "c0ffee",
                ),
            ),
            page(corps),
        )
    }

    @Test
    fun `liste, suivant nul, absent ou mal type, pas de page suivante`() {
        val sans = historique(listOf(partieListee("pa")))
        for (corps in listOf(sans, sans.replace(""","suivant":null""", ""), sans.replace("null", "12"), sans.replace("null", "\"\""))) {
            assertEquals(corps, null, (page(corps) as Resultat.Ok).valeur.suivant)
        }
    }

    @Test
    fun `liste, un etat inconnu est une partie terminee`() {
        val corps = historique(listOf(partieListee("pa", etat = "suspendue")))
        assertEquals(FinPartie.Terminee, (page(corps) as Resultat.Ok).valeur.parties.single().fin)
    }

    @Test
    fun `liste, champs inconnus ignores, a tous les niveaux`() {
        val corps = historique(listOf(partieListee("pa")), suivant = "c0ffee")
            .replace("""{"parties":""", """{"futur":{"a":1},"parties":""")
            .replace(""""etat":"terminee",""", """"etat":"terminee","futur":[1],""")
            .replace(""""variation":14,""", """"variation":14,"futur":true,""")
        assertEquals(
            Resultat.Ok(PageHistorique(listOf(PartieListee("pa", le22, FinPartie.Terminee, 3, 5, EloPartie(14, 1261))), "c0ffee")),
            page(corps),
        )
    }

    @Test
    fun `liste, aucune partie, liste vide lisible`() {
        assertEquals(Resultat.Ok(PageHistorique(emptyList(), null)), page(historique(emptyList())))
    }

    @Test
    fun `liste, champ obligatoire manquant ou mal type, reponse illisible`() {
        val bonne = partieListee("pa")
        val dans = { corps: String -> historique(listOf(corps)) }
        pageIllisible("""{"suivant":null}""")
        pageIllisible("""{"parties":{},"suivant":null}""")
        pageIllisible("""{"parties":[1],"suivant":null}""")
        pageIllisible(dans(bonne.replace(""""id":"pa",""", "")))
        pageIllisible(dans(bonne.replace(""""id":"pa",""", """"id":"",""")))
        pageIllisible(dans(bonne.replace(""""id":"pa",""", """"id":12,""")))
        pageIllisible(dans(bonne.replace(""""date":"2026-11-22T10:00:00.123Z",""", "")))
        pageIllisible(dans(bonne.replace("2026-11-22T10:00:00.123Z", "hier")))
        pageIllisible(dans(bonne.replace(""""etat":"terminee",""", "")))
        pageIllisible(dans(bonne.replace(""""etat":"terminee",""", """"etat":1,""")))
        pageIllisible(dans(bonne.replace(""""bonnes":3,""", "")))
        pageIllisible(dans(bonne.replace(""""bonnes":3,""", """"bonnes":"3",""")))
        pageIllisible(dans(bonne.replace(""""servies":5,""", "")))
        pageIllisible(dans(bonne.replace(""""servies":5,""", """"servies":5.5,""")))
        pageIllisible(dans(bonne.replace(""","elo":{"variation":14,"valeur":1261}""", "")))
        pageIllisible(dans(bonne.replace(""""variation":14,""", "")))
        pageIllisible(dans(bonne.replace(""""variation":14,""", """"variation":"14",""")))
        pageIllisible(dans(bonne.replace(""""valeur":1261""", """"valeur":true""")))
        pageIllisible(dans(bonne.replace(""""valeur":1261""", "")))
    }

    @Test
    fun `liste, une partie illisible rend toute la page illisible, aucune partie a moitie`() {
        pageIllisible(historique(listOf(partieListee("pa"), partieListee("pb").replace(""""bonnes":3,""", ""))))
    }

    // Courbe -----------------------------------------------------------------------------------

    private fun courbeLue(corps: String): Resultat<CourbeLue> = runBlocking {
        transport.repondre(Route.CourbeElo, 200, corps)
        api.courbe()
    }

    private fun courbeIllisible(corps: String) = assertEquals(corps, Resultat.Echec(Serveur), courbeLue(corps))

    @Test
    fun `courbe, route a jeton, GET sans corps`() {
        courbeLue(courbe(null, emptyList()))
        val r = transport.requetes.single()
        assertEquals("GET /api/eruditus/v1/elo/courbe", "${r.methode} ${r.chemin}")
        assertEquals("jeton", r.jeton)
        assertEquals(null, r.corps)
    }

    @Test
    fun `courbe, depart, points dans l'ordre recu et provisoire`() {
        val c = courbe(1000, listOf("2026-11-20T10:00:00.000Z" to 1012, "2026-11-22T10:00:00.123Z" to 1003), provisoire = true)
        assertEquals(
            Resultat.Ok(CourbeLue(1000, listOf(PointElo(Instant.parse("2026-11-20T10:00:00Z"), 1012), PointElo(le22, 1003)), provisoire = true)),
            courbeLue(c),
        )
    }

    @Test
    fun `courbe, aucun point, depart nul`() {
        assertEquals(Resultat.Ok(CourbeLue(null, emptyList(), provisoire = false)), courbeLue(courbe(null, emptyList())))
    }

    @Test
    fun `courbe, champs inconnus ignores`() {
        val c = courbe(1000, listOf("2026-11-22T10:00:00.123Z" to 1012))
            .replace("""{"depart":""", """{"futur":1,"depart":""")
            .replace(""""elo":1012""", """"elo":1012,"futur":"x"""")
        assertEquals(Resultat.Ok(CourbeLue(1000, listOf(PointElo(le22, 1012)), provisoire = false)), courbeLue(c))
    }

    @Test
    fun `courbe, champ obligatoire manquant ou mal type, reponse illisible`() {
        val bonne = courbe(1000, listOf("2026-11-22T10:00:00.123Z" to 1012))
        courbeIllisible(bonne.replace(""""depart":1000,""", ""))
        courbeIllisible(bonne.replace(""""depart":1000,""", """"depart":"1000","""))
        courbeIllisible(bonne.replace(""""points":""", """"autres":"""))
        courbeIllisible(bonne.replace(""""points":[""", """"points":{"a":""").replace("}],", "}},"))
        courbeIllisible(bonne.replace(""""date":"2026-11-22T10:00:00.123Z",""", ""))
        courbeIllisible(bonne.replace("2026-11-22T10:00:00.123Z", "hier"))
        courbeIllisible(bonne.replace(""""elo":1012""", """"elo":null"""))
        courbeIllisible(bonne.replace(""""elo":1012""", """"elo":"1012""""))
        courbeIllisible(bonne.replace(""","provisoire":false""", ""))
        courbeIllisible(bonne.replace("false", "\"non\""))
    }

    @Test
    fun `courbe, les valeurs a tracer, le depart puis les points`() {
        val c = (courbeLue(courbe(1000, listOf("2026-11-20T10:00:00Z" to 1012, "2026-11-22T10:00:00Z" to 987))) as Resultat.Ok).valeur
        assertEquals(listOf(1000, 1012, 987), c.valeurs)
        assertEquals(emptyList<Int>(), CourbeLue(null, emptyList(), false).valeurs)
    }

    // Détail -----------------------------------------------------------------------------------

    private fun detail(corps: String, id: String = ID_PARTIE): Resultat<DetailPartie> = runBlocking {
        transport.repondre(Route.HistoriqueDetail, 200, corps)
        api.detailPartie(id)
    }

    private fun detailIllisible(corps: String) = assertEquals(corps, Resultat.Echec(Serveur), detail(corps))

    private val choix = """["René Magritte","Max Ernst","Salvador Dalí","Joan Miró"]"""

    /** Une question du détail ; [reste] : les champs après `detail`. */
    private fun question(numero: Int = 1, statut: String = "juste", detail: String = "ok", niveau: String = "4", variation: String = "12", reste: String = "") =
        """{"numero":$numero,"statut":"$statut","niveau":$niveau,"variation":$variation,"detail":"$detail"$reste}"""

    private fun corpsDetail(vararg questions: String, partie: String? = null, elo: String = """{"variation":14,"valeur":1261}""") =
        """{"partie":${partie ?: """{"id":"$ID_PARTIE","date":"2026-11-22T10:00:00.123Z","etat":"terminee","bonnes":3,"servies":5,"total":5}"""},""" +
            """"elo":$elo,"questions":[${questions.joinToString(",")}]}"""

    private val complet = ""","enonce":"Qui a peint La Persistance de la mémoire ?","choix":$choix,"choixJoueur":2,"bonneReponse":2,"explication":"Dalí, en 1931.","source":"MoMA""""

    private fun premiere(corps: String): QuestionRelue = (detail(corps) as Resultat.Ok).valeur.questions.first()

    @Test
    fun `detail, route a jeton, GET sans corps, identifiant dans le chemin`() {
        detail(corpsDetail())
        val r = transport.requetes.single()
        assertEquals("GET /api/eruditus/v1/historique/$ID_PARTIE", "${r.methode} ${r.chemin}")
        assertEquals("jeton", r.jeton)
        assertEquals(null, r.corps)
    }

    @Test
    fun `detail, un identifiant qui n'est pas fait de lettres et de chiffres n'est jamais envoye`() = runBlocking {
        for (id in listOf("", "../moi", "a/b", "a?b", "a b")) assertEquals(id, Resultat.Echec(Serveur), api.detailPartie(id))
        assertEquals(emptyList<Requete>(), transport.requetes)
    }

    @Test
    fun `detail, la partie, l'Elo et les questions dans l'ordre recu`() {
        val corps = corpsDetail(question(1, reste = complet), question(2, "fausse", variation = "-19", niveau = "2"))
        val d = (detail(corps) as Resultat.Ok).valeur
        assertEquals(PartieDetail(ID_PARTIE, le22, FinPartie.Terminee, 3, 5, 5), d.partie)
        assertEquals(EloPartie(14, 1261), d.elo)
        assertEquals(listOf(1, 2), d.questions.map { it.numero })
        assertEquals(listOf(StatutQuestion.Juste, StatutQuestion.Fausse), d.questions.map { it.statut })
        assertEquals(listOf(4, 2), d.questions.map { it.elo.niveau })
        assertEquals(listOf(12, -19), d.questions.map { it.elo.variation })
    }

    @Test
    fun `detail, partie abandonnee et sans effet sur l'Elo`() {
        val corps = corpsDetail(
            partie = """{"id":"$ID_PARTIE","date":"2026-11-22T10:00:00.123Z","etat":"abandonnee","bonnes":2,"servies":3,"total":5}""",
            elo = """{"variation":null,"valeur":null}""",
        )
        val d = (detail(corps) as Resultat.Ok).valeur
        assertEquals(FinPartie.Abandonnee, d.partie.fin)
        assertEquals(3, d.partie.servies)
        assertEquals(EloPartie(null, null), d.elo)
    }

    @Test
    fun `detail, detail ok, enonce, choix, choix du joueur, bonne reponse, explication et source`() {
        assertEquals(
            QuestionRelue(
                1, StatutQuestion.Juste, EloQuestion(4, 12),
                detail = DetailQuestion.Complet(
                    enonce = "Qui a peint La Persistance de la mémoire ?",
                    choix = listOf("René Magritte", "Max Ernst", "Salvador Dalí", "Joan Miró"),
                    choixJoueur = 2, bonneReponse = 2, explication = "Dalí, en 1931.", source = "MoMA",
                ),
            ),
            premiere(corpsDetail(question(reste = complet))),
        )
    }

    @Test
    fun `detail, les cinq statuts`() {
        for ((code, statut) in listOf(
            "juste" to StatutQuestion.Juste, "fausse" to StatutQuestion.Fausse, "sans_reponse" to StatutQuestion.SansReponse,
            "hors_delai" to StatutQuestion.HorsDelai, "non_repondue" to StatutQuestion.NonRepondue,
        )) {
            assertEquals(code, statut, premiere(corpsDetail(question(statut = code, detail = "modifiee"))).statut)
        }
    }

    @Test
    fun `detail, modifiee et retiree, le verdict seulement`() {
        assertEquals(DetailQuestion.Modifiee, premiere(corpsDetail(question(detail = "modifiee"))).detail)
        assertEquals(DetailQuestion.Retiree, premiere(corpsDetail(question(detail = "retiree"))).detail)
        // Même si un serveur d'après envoyait un énoncé : ce détail-là n'est plus affiché.
        assertEquals(DetailQuestion.Retiree, premiere(corpsDetail(question(detail = "retiree", reste = ""","enonce":"x","choix":$choix"""))).detail)
    }

    @Test
    fun `detail, sans choix du joueur, sans explication ni source, null ou absents`() {
        val sansReponse = ""","enonce":"Énoncé ?","choix":$choix,"choixJoueur":null,"bonneReponse":1,"explication":null,"source":null"""
        assertEquals(
            DetailQuestion.Complet("Énoncé ?", listOf("René Magritte", "Max Ernst", "Salvador Dalí", "Joan Miró"), null, 1, null, null),
            premiere(corpsDetail(question(statut = "sans_reponse", reste = sansReponse))).detail,
        )
        val absents = ""","enonce":"Énoncé ?","choix":$choix"""
        assertEquals(
            DetailQuestion.Complet("Énoncé ?", listOf("René Magritte", "Max Ernst", "Salvador Dalí", "Joan Miró"), null, null, null, null),
            premiere(corpsDetail(question(statut = "non_repondue", reste = absents))).detail,
        )
    }

    @Test
    fun `detail, facultatifs mal types ignores, jamais illisible`() {
        val mal = ""","enonce":"Énoncé ?","choix":$choix,"choixJoueur":"2","bonneReponse":9,"explication":12,"source":["x"]"""
        val q = premiere(corpsDetail(question(reste = mal)))
        assertEquals(DetailQuestion.Complet("Énoncé ?", listOf("René Magritte", "Max Ernst", "Salvador Dalí", "Joan Miró"), null, null, null, null), q.detail)
        // niveau et variation mal types : ignores, et la ligne d'enjeu n'affirme rien (illisible).
        val sans = premiere(corpsDetail(question(niveau = "\"x\"", variation = "\"y\"", detail = "modifiee")))
        assertEquals(EloQuestion(null, null, illisible = true), sans.elo)
        val absents = premiere(corpsDetail("""{"numero":1,"statut":"juste","detail":"modifiee"}"""))
        assertEquals(EloQuestion(null, null), absents.elo)
    }

    @Test
    fun `detail, detail ok sans enonce ou sans quatre choix, indisponible`() {
        assertEquals(DetailQuestion.Indisponible, premiere(corpsDetail(question())).detail)
        assertEquals(DetailQuestion.Indisponible, premiere(corpsDetail(question(reste = ""","enonce":"x","choix":["a","b","c"]"""))).detail)
        assertEquals(DetailQuestion.Indisponible, premiere(corpsDetail(question(reste = ""","enonce":"","choix":$choix"""))).detail)
    }

    @Test
    fun `detail, statut ou detail inconnu, seulement le numero, detail indisponible`() {
        val inconnuStatut = premiere(corpsDetail(question(numero = 3, statut = "annulee", reste = complet)))
        assertEquals(QuestionRelue(3, null, EloQuestion(4, 12), DetailQuestion.Indisponible), inconnuStatut)
        val inconnuDetail = premiere(corpsDetail(question(numero = 4, detail = "archivee")))
        assertEquals(QuestionRelue(4, StatutQuestion.Juste, EloQuestion(4, 12), DetailQuestion.Indisponible), inconnuDetail)
    }

    @Test
    fun `detail, champs inconnus ignores`() {
        val corps = corpsDetail(question(reste = ""","futur":{"a":1}"""), partie = """{"id":"$ID_PARTIE","date":"2026-11-22T10:00:00.123Z","etat":"terminee","bonnes":3,"servies":5,"total":5,"futur":1}""")
            .replace("""{"partie":""", """{"futur":true,"partie":""")
        val d = (detail(corps) as Resultat.Ok).valeur
        assertEquals(PartieDetail(ID_PARTIE, le22, FinPartie.Terminee, 3, 5, 5), d.partie)
        assertEquals(DetailQuestion.Indisponible, d.questions.single().detail)
    }

    @Test
    fun `detail, un etat de partie inconnu est une partie terminee`() {
        val corps = corpsDetail(partie = """{"id":"$ID_PARTIE","date":"2026-11-22T10:00:00.123Z","etat":"suspendue","bonnes":3,"servies":5,"total":5}""")
        assertEquals(FinPartie.Terminee, (detail(corps) as Resultat.Ok).valeur.partie.fin)
    }

    @Test
    fun `detail, champ obligatoire manquant ou mal type, reponse illisible`() {
        val bonne = corpsDetail(question())
        detailIllisible(bonne.replace(""""partie":""", """"autre":"""))
        detailIllisible(bonne.replace(""""elo":{"variation":14,"valeur":1261}""", """"elo":null"""))
        detailIllisible(bonne.replace(""""elo":{"variation":14,"valeur":1261}""", """"elo":{"valeur":1261}"""))
        detailIllisible(bonne.replace(""""questions":""", """"autres":"""))
        detailIllisible(bonne.replace(""""questions":[""", """"questions":{"a":""").replace("}]}", "}}}"))
        detailIllisible(bonne.replace(""""id":"$ID_PARTIE",""", ""))
        detailIllisible(bonne.replace(""""bonnes":3,""", ""))
        detailIllisible(bonne.replace(""""servies":5,""", """"servies":"5","""))
        detailIllisible(bonne.replace(""","total":5""", ""))
        detailIllisible(bonne.replace("2026-11-22T10:00:00.123Z", "hier"))
        detailIllisible(corpsDetail("""{"statut":"juste","detail":"ok"}"""))
        detailIllisible(corpsDetail("""{"numero":"1","statut":"juste","detail":"ok"}"""))
        detailIllisible(corpsDetail("""{"numero":1,"detail":"ok"}"""))
        detailIllisible(corpsDetail("""{"numero":1,"statut":"juste"}"""))
        detailIllisible(corpsDetail("""{"numero":1,"statut":7,"detail":"ok"}"""))
        detailIllisible(corpsDetail("""{"numero":1,"statut":"juste","detail":null}"""))
        detailIllisible(corpsDetail("3"))
    }

    // Erreurs ----------------------------------------------------------------------------------

    @Test
    fun `les trois routes, 401, 429, refus avec code, reseau et serveur`() = runBlocking {
        val appels: List<Pair<Route, suspend () -> Resultat<*>>> = listOf(
            Route.Historique to { api.historique(null) },
            Route.HistoriqueDetail to { api.detailPartie(ID_PARTIE) },
            Route.CourbeElo to { api.courbe() },
        )
        for ((route, appel) in appels) {
            transport.repondre(route, 401, erreur("x", 401))
            assertEquals("401 ${route.name}", Resultat.Echec(NonAutorise), appel())
            transport.repondre(route, 429, """{"status":429,"message":"Too Many Requests.","data":{}}""")
            assertEquals("429 ${route.name}", Resultat.Echec(TropDeTentatives), appel())
            transport.repondre(route, 404, erreur("partie_introuvable", 404))
            assertEquals("404 ${route.name}", Resultat.Echec(ErreurApi.Refus(404, "partie_introuvable")), appel())
            transport.repondre(route, 400, erreur("requete_invalide"))
            assertEquals("400 ${route.name}", Resultat.Echec(ErreurApi.Refus(400, "requete_invalide")), appel())
            transport.repondre(route, 500, "oups")
            assertEquals("500 ${route.name}", Resultat.Echec(Serveur), appel())
            transport.echouer(route, connexionEtablie = false)
            assertEquals("réseau ${route.name}", Resultat.Echec(Reseau), appel())
            transport.echouer(route, connexionEtablie = true)
            assertEquals("délai ${route.name}", Resultat.Echec(Serveur), appel())
        }
    }
}

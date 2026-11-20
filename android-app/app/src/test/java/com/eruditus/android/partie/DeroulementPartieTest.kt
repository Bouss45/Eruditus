package com.eruditus.android.partie

import com.eruditus.android.compte.Aiguillage
import com.eruditus.android.compte.DepotCompte
import com.eruditus.android.compte.TexteErreur
import com.eruditus.android.donnees.api.Bilan
import com.eruditus.android.donnees.api.Raison
import com.eruditus.android.donnees.api.ReponseHttp
import com.eruditus.android.donnees.api.Route
import com.eruditus.android.donnees.api.TransportEchoue
import com.eruditus.android.donnees.api.TransportFactice
import com.eruditus.android.donnees.api.TransportFactice.Companion.CONNEXION
import com.eruditus.android.donnees.api.TransportFactice.Companion.INFO
import com.eruditus.android.donnees.api.TransportFactice.Companion.RENOUVELLEMENT
import com.eruditus.android.donnees.api.TransportFactice.Companion.corrigee
import com.eruditus.android.donnees.api.TransportFactice.Companion.erreur
import com.eruditus.android.donnees.api.TransportFactice.Companion.partieCreee
import com.eruditus.android.donnees.api.TransportFactice.Companion.questionServie
import com.eruditus.android.donnees.session.ChiffreurLogiciel
import com.eruditus.android.donnees.session.CoffreSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Déroulement d'une partie dans l'app (spec/ecrans.md § 9, § 10, D65 – D68), en temps virtuel :
 * le chrono part du temps restant envoyé par le serveur, un seul envoi par question, « sans
 * réponse » à 0 et jamais en arrière-plan, question redemandée au retour, bilan après la 5e,
 * abandon, erreurs. Le serveur est le `TransportFactice`, joint au vrai `DepotCompte`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeroulementPartieTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private val transport = TransportFactice()
    private val coffre by lazy { CoffreSession(File(dossier.root, "session"), ChiffreurLogiciel()) }
    private val depot by lazy { DepotCompte(transport, coffre, disque = Dispatchers.Unconfined) }
    private val sorties = mutableListOf<SortiePartie>()

    private val TestScope.maintenant: Long get() = testScheduler.currentTime

    private fun TestScope.deroulement() = DeroulementPartie(
        api = depot.api,
        effacerSession = depot::effacerSession,
        horloge = { testScheduler.currentTime },
        portee = backgroundScope,
        sortir = { sorties += it },
    )

    /** « Jouer » : la partie est créée, la question 1 servie avec [restantMs]. */
    private fun TestScope.commencer(restantMs: Long = 20_000): DeroulementPartie {
        transport.repondre(Route.CreerPartie, 200, partieCreee(restantMs))
        val d = deroulement()
        d.jouer()
        runCurrent()
        return d
    }

    private val DeroulementPartie.e: EtatPartie get() = etat.value!!

    private fun reponses() = transport.requetesVers(Route.Reponse).map { r ->
        val corps = Json.parseToJsonElement(r.corps!!).jsonObject
        corps["numero"]!!.jsonPrimitive.int to corps["choix"]!!.jsonPrimitive.intOrNull
    }

    /** Le serveur corrige chaque réponse : [bonne] est la bonne réponse, bilan à la 5e. */
    private fun corriger(bonne: Int = 2, bilan: String = """{"bonnes":3,"points":2,"score":14}""") {
        transport.repondreAvec(Route.Reponse) { r ->
            val corps = Json.parseToJsonElement(r.corps!!).jsonObject
            val numero = corps["numero"]!!.jsonPrimitive.int
            val choix = corps["choix"]!!.jsonPrimitive.intOrNull
            val raison = when (choix) {
                null -> "sans_reponse"
                bonne -> "gagne"
                else -> "fausse"
            }
            ReponseHttp(200, corrigee(numero, choix, bonne, raison, bilan = if (numero == 5) bilan else "null"))
        }
    }

    /** Le serveur sert la question qui suit la dernière réponse. */
    private fun servirLaSuivante() {
        transport.repondreAvec(Route.Question) { ReponseHttp(200, questionServie(transport.requetesVers(Route.Reponse).size + 1)) }
    }

    private suspend fun ouvrirSession() {
        transport.repondre(Route.Connexion, 200, CONNEXION)
        depot.connecter("Hibou_42", "nuit-blanche-42")
    }

    // Chrono (D66) ------------------------------------------------------------------------------

    @Test
    fun `question neuve, le compte a rebours part des 20 s envoyees par le serveur`() = runTest {
        val d = commencer(restantMs = 20_000)
        assertEquals(listOf<SortiePartie>(SortiePartie.Ouverte), sorties)
        assertEquals(1, d.e.question.numero)
        assertEquals(Phase.Attente, d.e.phase)
        assertEquals(20_000, d.e.restantMs(maintenant))
        advanceTimeBy(5_000)
        assertEquals(15_000, d.e.restantMs(maintenant))
    }

    @Test
    fun `question neuve servie avec du retard, le chrono part de ce qui reste, jamais de 20 s fixes`() = runTest {
        corriger()
        val d = commencer(restantMs = 18_500)
        assertEquals(18_500, d.e.restantMs(maintenant))
        advanceTimeBy(18_499)
        runCurrent()
        assertTrue(reponses().isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(1 to null), reponses())
    }

    @Test
    fun `a 0, un seul envoi sans reponse, puis la correction`() = runTest {
        corriger(bonne = 3)
        val d = commencer()
        advanceTimeBy(19_999)
        runCurrent()
        assertTrue(reponses().isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(1 to null), reponses())
        val corrigee = d.e.phase as Phase.Corrigee
        assertEquals(Raison.SansReponse, corrigee.reponse.correction.raison)
        assertEquals(0, d.e.restantMs(maintenant))
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, reponses().size)
    }

    @Test
    fun `arriere-plan, aucun envoi ; au retour la question est redemandee et, a 0 s restante, sans reponse part aussitot`() = runTest {
        val d = commencer()
        d.enArrierePlan()
        advanceTimeBy(30_000)
        runCurrent()
        assertTrue(transport.requetesVers(Route.Reponse).isEmpty())
        assertTrue(transport.requetesVers(Route.Question).isEmpty())

        transport.repondre(Route.Question, 200, questionServie(1, restantMs = 0))
        transport.repondre(Route.Reponse, 200, corrigee(1, choix = null, bonne = 2, raison = "hors_delai"))
        d.auPremierPlan()
        runCurrent()
        assertEquals(1, transport.requetesVers(Route.Question).size)
        assertEquals(listOf(1 to null), reponses())
        assertEquals(Raison.HorsDelai, (d.e.phase as Phase.Corrigee).reponse.correction.raison)
    }

    @Test
    fun `ecran recree, la question redemandee recale le chrono sur le temps restant du serveur`() = runTest {
        corriger()
        val d = commencer()
        advanceTimeBy(4_000)
        transport.repondre(Route.Question, 200, questionServie(1, restantMs = 7_000))
        d.enArrierePlan()
        d.auPremierPlan()
        runCurrent()
        assertEquals(1, transport.requetesVers(Route.Question).size)
        assertEquals(7_000, d.e.restantMs(maintenant))
        advanceTimeBy(6_999)
        runCurrent()
        assertTrue(reponses().isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(1 to null), reponses())
    }

    @Test
    fun `premier passage au premier plan, sans arret avant, rien n'est redemande`() = runTest {
        val d = commencer()
        d.auPremierPlan()
        runCurrent()
        assertTrue(transport.requetesVers(Route.Question).isEmpty())
    }

    @Test
    fun `retour au premier plan pendant la correction, rien n'est redemande`() = runTest {
        corriger()
        val d = commencer()
        d.choisir(2)
        runCurrent()
        d.enArrierePlan()
        advanceTimeBy(60_000)
        d.auPremierPlan()
        runCurrent()
        assertTrue(transport.requetesVers(Route.Question).isEmpty())
        assertTrue(d.e.phase is Phase.Corrigee)
    }

    @Test
    fun `pendant la redemande, l'envoi automatique attend sa reponse`() = runTest {
        corriger()
        val d = commencer()
        d.enArrierePlan()
        advanceTimeBy(25_000)
        val redemande = transport.retenir(Route.Question)
        d.auPremierPlan()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(reponses().isEmpty())
        redemande.complete(ReponseHttp(200, questionServie(1, restantMs = 0)))
        runCurrent()
        assertEquals(listOf(1 to null), reponses())
    }

    @Test
    fun `pendant la redemande, un choix touche part et la redemande arrivee ensuite est ignoree`() = runTest {
        corriger()
        val d = commencer()
        d.enArrierePlan()
        val redemande = transport.retenir(Route.Question)
        d.auPremierPlan()
        runCurrent()
        d.choisir(1)
        runCurrent()
        assertEquals(listOf(1 to 1), reponses())
        redemande.complete(ReponseHttp(200, questionServie(1, restantMs = 3_000)))
        runCurrent()
        assertTrue(d.e.phase is Phase.Corrigee)
    }

    // Un envoi par question (D67) ------------------------------------------------------------

    @Test
    fun `deux appuis, un seul envoi, chrono arrete pendant l'envoi`() = runTest {
        val d = commencer()
        advanceTimeBy(5_000)
        val envoi = transport.retenir(Route.Reponse)
        d.choisir(2)
        d.choisir(1)
        runCurrent()
        assertEquals(listOf(1 to 2), reponses())
        assertEquals(Phase.Envoi(choix = 2, restantMs = 15_000), d.e.phase)
        advanceTimeBy(10_000)
        assertEquals(15_000, d.e.restantMs(maintenant))
        d.choisir(0)
        runCurrent()
        envoi.complete(ReponseHttp(200, corrigee(1, choix = 2, bonne = 2, raison = "gagne")))
        runCurrent()
        assertTrue(d.e.phase is Phase.Corrigee)
        d.choisir(3)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(listOf(1 to 2), reponses())
    }

    @Test
    fun `question suivante touchee deux fois, une seule demande`() = runTest {
        corriger()
        val d = commencer()
        d.choisir(2)
        runCurrent()
        val suite = transport.retenir(Route.Question)
        d.continuer()
        d.continuer()
        runCurrent()
        assertEquals(1, transport.requetesVers(Route.Question).size)
        assertTrue((d.e.phase as Phase.Corrigee).suite)
        suite.complete(ReponseHttp(200, questionServie(2)))
        runCurrent()
        assertEquals(2, d.e.question.numero)
        assertEquals(Phase.Attente, d.e.phase)
        assertEquals(20_000, d.e.restantMs(maintenant))
    }

    // Bilan et Résultat (D69) ---------------------------------------------------------------

    @Test
    fun `bilan apres la 5e question, Voir le resultat, puis Rejouer`() = runTest {
        corriger(bonne = 2)
        servirLaSuivante()
        val d = commencer()
        for (n in 1..5) {
            assertEquals(n, d.e.question.numero)
            d.choisir(if (n <= 3) 2 else 0)
            runCurrent()
            val corrigee = d.e.phase as Phase.Corrigee
            assertEquals("bilan à la question $n", n == 5, corrigee.reponse.bilan != null)
            d.continuer()
            runCurrent()
        }
        assertNull(d.etat.value)
        assertEquals(SortiePartie.VoirResultat(Bilan(bonnes = 3, points = 2, score = 14), total = 5), sorties.last())
        assertEquals("questions 2 à 5, aucune après la 5e", 4, transport.requetesVers(Route.Question).size)

        d.jouer()
        runCurrent()
        assertEquals(SortiePartie.Ouverte, sorties.last())
        assertEquals(1, d.e.question.numero)
    }

    // Abandon -------------------------------------------------------------------------------

    @Test
    fun `abandon, aucun appel au serveur, plus d'envoi automatique`() = runTest {
        corriger()
        val d = commencer()
        val avant = transport.requetes.size
        d.quitter()
        advanceTimeBy(60_000)
        runCurrent()
        assertNull(d.etat.value)
        assertEquals(avant, transport.requetes.size)
        assertEquals(listOf<SortiePartie>(SortiePartie.Ouverte), sorties)
    }

    @Test
    fun `abandon pendant un envoi, la reponse arrivee ensuite est ignoree, meme une coupure`() = runTest {
        val d = commencer()
        val envoi = transport.retenir(Route.Reponse)
        d.choisir(1)
        runCurrent()
        d.quitter()
        envoi.completeExceptionally(TransportEchoue(connexionEtablie = false))
        runCurrent()
        assertNull(d.etat.value)
        assertEquals(listOf<SortiePartie>(SortiePartie.Ouverte), sorties)
    }

    // Erreurs (D68) ---------------------------------------------------------------------------

    @Test
    fun `coupure reseau, Pas de connexion, partie perdue, puis Reessayer mene a l'Accueil`() = runTest {
        ouvrirSession()
        val d = commencer()
        transport.echouer(Route.Reponse, connexionEtablie = false)
        d.choisir(0)
        runCurrent()
        assertEquals(SortiePartie.PasDeConnexion(serveur = false), sorties.last())
        assertNull(d.etat.value)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals("aucun envoi automatique après la coupure", 1, transport.requetesVers(Route.Reponse).size)

        // « Réessayer » relance l'aiguillage : la session est gardée, il mène à l'Accueil.
        transport.repondre(Route.Info, 200, INFO)
        transport.repondre(Route.Renouvellement, 200, RENOUVELLEMENT)
        assertEquals(Aiguillage.Accueil, depot.aiguiller(1000))
    }

    @Test
    fun `serveur muet ou en panne, Pas de connexion texte serveur`() = runTest {
        corriger()
        val d = commencer()
        d.choisir(2)
        runCurrent()
        transport.repondre(Route.Question, 502, "<html>Bad Gateway</html>")
        d.continuer()
        runCurrent()
        assertEquals(SortiePartie.PasDeConnexion(serveur = true), sorties.last())
        assertNull(d.etat.value)
    }

    @Test
    fun `correction d'un autre numero, illisible, Pas de connexion texte serveur`() = runTest {
        val d = commencer()
        transport.repondre(Route.Reponse, 200, corrigee(2, choix = 1, bonne = 1, raison = "gagne"))
        d.choisir(1)
        runCurrent()
        assertEquals(SortiePartie.PasDeConnexion(serveur = true), sorties.last())
        assertNull(d.etat.value)
    }

    @Test
    fun `banque insuffisante, aucune partie, Aucune partie disponible en haut de l'Accueil`() = runTest {
        transport.repondre(Route.CreerPartie, 503, erreur("banque_insuffisante", statut = 503))
        val d = deroulement()
        d.jouer()
        assertTrue(d.creation.value)
        runCurrent()
        assertEquals(listOf<SortiePartie>(SortiePartie.Accueil(TexteErreur.AucunePartie)), sorties)
        assertNull(d.etat.value)
        assertFalse(d.creation.value)
    }

    @Test
    fun `Jouer touche deux fois, une seule partie creee`() = runTest {
        val creation = transport.retenir(Route.CreerPartie)
        val d = deroulement()
        d.jouer()
        d.jouer()
        runCurrent()
        assertEquals(1, transport.requetesVers(Route.CreerPartie).size)
        creation.complete(ReponseHttp(200, partieCreee()))
        runCurrent()
        assertEquals(listOf<SortiePartie>(SortiePartie.Ouverte), sorties)
    }

    @Test
    fun `Jouer refuse par le limiteur ou par une erreur, message sur l'ecran en cours`() = runTest {
        val d = deroulement()
        transport.repondre(Route.CreerPartie, 429, """{"status":429,"message":"Too Many Requests.","data":{}}""")
        d.jouer()
        runCurrent()
        transport.repondre(Route.CreerPartie, 400, erreur("requete_invalide"))
        d.jouer()
        runCurrent()
        assertEquals(
            listOf(SortiePartie.MessageIci(TexteErreur.TropDeTentatives), SortiePartie.MessageIci(TexteErreur.Generique)),
            sorties,
        )
        assertNull(d.etat.value)
    }

    @Test
    fun `partie qui n'est plus valable, a la reponse, a la question suivante ou a la redemande, retour a l'Accueil`() = runTest {
        val codes = listOf("partie_introuvable" to 404, "partie_finie" to 409, "numero_invalide" to 409, "deja_repondu" to 409)
        for ((code, statut) in codes) {
            // À la réponse.
            sorties.clear()
            var d = commencer()
            transport.repondre(Route.Reponse, statut, erreur(code, statut))
            d.choisir(0)
            runCurrent()
            assertEquals(code, listOf(SortiePartie.Ouverte, SortiePartie.Accueil(TexteErreur.PartiePlusValable)), sorties)
            assertNull(d.etat.value)

            // À la question suivante.
            sorties.clear()
            corriger()
            d = commencer()
            d.choisir(2)
            runCurrent()
            transport.repondre(Route.Question, statut, erreur(code, statut))
            d.continuer()
            runCurrent()
            assertEquals(code, SortiePartie.Accueil(TexteErreur.PartiePlusValable), sorties.last())
            assertNull(d.etat.value)

            // À la redemande, au retour de l'arrière-plan.
            sorties.clear()
            d = commencer()
            d.enArrierePlan()
            d.auPremierPlan()
            runCurrent()
            assertEquals(code, SortiePartie.Accueil(TexteErreur.PartiePlusValable), sorties.last())
            assertNull(d.etat.value)
        }
    }

    @Test
    fun `session expiree pendant la partie, session effacee, Bienvenue`() = runTest {
        ouvrirSession()
        val d = commencer()
        transport.repondre(Route.Reponse, 401, """{"status":401,"message":"…","data":{}}""")
        d.choisir(0)
        runCurrent()
        assertEquals(SortiePartie.SessionExpiree, sorties.last())
        assertNull(depot.session)
        assertNull(coffre.lire())
        assertNull(d.etat.value)
    }

    @Test
    fun `reponse refusee par le limiteur, message en haut, les choix reviennent tant que le chrono tourne`() = runTest {
        val d = commencer()
        advanceTimeBy(5_000)
        transport.repondre(Route.Reponse, 429, """{"status":429,"message":"Too Many Requests.","data":{}}""")
        d.choisir(1)
        runCurrent()
        assertEquals(Phase.Attente, d.e.phase)
        assertEquals(TexteErreur.TropDeTentatives, d.e.message)
        assertEquals(15_000, d.e.restantMs(maintenant))

        corriger()
        d.choisir(2)
        runCurrent()
        assertEquals(listOf(1 to 1, 1 to 2), reponses())
        assertTrue(d.e.phase is Phase.Corrigee)
        assertNull(d.e.message)
    }

    @Test
    fun `a 0, l'envoi automatique refuse n'est pas repete et les choix restent verrouilles`() = runTest {
        val d = commencer()
        transport.repondre(Route.Reponse, 400, erreur("requete_invalide"))
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(listOf(1 to null), reponses())
        assertEquals(Phase.Attente, d.e.phase)
        assertEquals(TexteErreur.Generique, d.e.message)
        d.choisir(1)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(listOf(1 to null), reponses())
    }

    @Test
    fun `question suivante refusee par le limiteur, message, le bouton revient`() = runTest {
        corriger()
        val d = commencer()
        d.choisir(2)
        runCurrent()
        transport.repondre(Route.Question, 429, """{"status":429,"message":"Too Many Requests.","data":{}}""")
        d.continuer()
        runCurrent()
        val corrigee = d.e.phase as Phase.Corrigee
        assertFalse(corrigee.suite)
        assertEquals(TexteErreur.TropDeTentatives, d.e.message)

        servirLaSuivante()
        d.continuer()
        runCurrent()
        assertEquals(2, d.e.question.numero)
        assertNull(d.e.message)
    }
}

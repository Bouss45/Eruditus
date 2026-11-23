package com.eruditus.android.historique

import com.eruditus.android.compte.Affichage
import com.eruditus.android.compte.DepotCompte
import com.eruditus.android.compte.TexteErreur
import com.eruditus.android.donnees.api.CourbeLue
import com.eruditus.android.donnees.api.EloPartie
import com.eruditus.android.donnees.api.FinPartie
import com.eruditus.android.donnees.api.PartieListee
import com.eruditus.android.donnees.api.PointElo
import com.eruditus.android.donnees.api.ReponseHttp
import com.eruditus.android.donnees.api.Route
import com.eruditus.android.donnees.api.TransportFactice
import com.eruditus.android.donnees.api.TransportFactice.Companion.CONNEXION
import com.eruditus.android.donnees.api.TransportFactice.Companion.courbe
import com.eruditus.android.donnees.api.TransportFactice.Companion.erreur
import com.eruditus.android.donnees.api.TransportFactice.Companion.historique
import com.eruditus.android.donnees.api.TransportFactice.Companion.partieListee
import com.eruditus.android.donnees.session.ChiffreurLogiciel
import com.eruditus.android.donnees.session.CoffreSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

/**
 * Chargement de l'Historique (spec/ecrans.md § 16, D131, D135) : la courbe et la première page à
 * chaque ouverture, rechargées en tirant vers le bas ; « Voir plus » ajoute les parties suivantes
 * sans doublon et sans tri ; erreurs ; l'échec seul de la courbe laisse la liste. Chargement du
 * détail (§ 17). Le serveur est le `TransportFactice`, joint au vrai `DepotCompte`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChargementHistoriqueTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private val transport = TransportFactice()
    private val coffre by lazy { CoffreSession(File(dossier.root, "session"), ChiffreurLogiciel()) }
    private val depot by lazy { DepotCompte(transport, coffre, disque = Dispatchers.Unconfined) }
    private val sorties = mutableListOf<Affichage>()

    private val le22 = Instant.parse("2026-11-22T10:00:00.123Z")
    private fun partie(id: String, variation: Int? = 14) =
        PartieListee(id, le22, FinPartie.Terminee, 3, 5, EloPartie(variation, variation?.let { 1261 }))

    private val courbeCorps = courbe(1000, listOf("2026-11-22T10:00:00.123Z" to 1014))
    private val courbeLue = CourbeLue(1000, listOf(PointElo(le22, 1014)), provisoire = false)
    private val trop = """{"status":429,"message":"Too Many Requests.","data":{}}"""

    private fun page1(suivant: String? = "c0ffee") = historique(listOf(partieListee("pa"), partieListee("pb", variation = null, valeur = null)), suivant)

    private suspend fun TestScope.chargement(): ChargementHistorique {
        transport.repondre(Route.Connexion, 200, CONNEXION)
        depot.connecter("Hibou_42", "nuit-blanche-42")
        return ChargementHistorique(depot::historique, depot::courbe, backgroundScope) { sorties += it }
    }

    private val ChargementHistorique.e: EtatHistorique get() = etat.value

    private fun demandes() = transport.requetesVers(Route.Historique).size

    // Ouverture, rechargement ------------------------------------------------------------------

    @Test
    fun `a l'ouverture, indicateur centre jusqu'a la reponse, la courbe et la premiere page`() = runTest {
        val c = chargement()
        val attente = transport.retenir(Route.Historique)
        transport.repondre(Route.CourbeElo, 200, courbeCorps)
        c.ouvrir()
        runCurrent()
        assertTrue(c.e.indicateurCentre)
        assertFalse(c.e.tirageEnCours)
        assertNull(c.e.parties)
        attente.complete(ReponseHttp(200, page1()))
        runCurrent()
        assertEquals(EtatHistorique(courbe = courbeLue, parties = listOf(partie("pa"), partie("pb", null)), suivant = "c0ffee"), c.e)
        assertFalse(c.e.indicateurCentre)
        assertEquals("/api/eruditus/v1/historique", transport.requetesVers(Route.Historique).single().chemin)
        assertEquals(1, transport.requetesVers(Route.CourbeElo).size)
    }

    @Test
    fun `une seule requete par route a l'ouverture, meme demandee deux fois`() = runTest {
        val c = chargement()
        transport.repondre(Route.Historique, 200, page1())
        transport.repondre(Route.CourbeElo, 200, courbeCorps)
        c.ouvrir()
        c.tirer()
        runCurrent()
        assertEquals(1, demandes())
        assertEquals(1, transport.requetesVers(Route.CourbeElo).size)
    }

    @Test
    fun `aucune partie, liste vide et pas de courbe`() = runTest {
        val c = chargement()
        transport.repondre(Route.Historique, 200, historique(emptyList()))
        transport.repondre(Route.CourbeElo, 200, courbe(null, emptyList()))
        c.ouvrir()
        runCurrent()
        assertEquals(emptyList<PartieListee>(), c.e.parties)
        assertNull(c.e.suivant)
        assertEquals(CourbeLue(null, emptyList(), false), c.e.courbe)
    }

    @Test
    fun `tirer recharge, les valeurs restent jusqu'a la nouvelle reponse, suivant et liste repartent de la premiere page`() = runTest {
        val c = chargement()
        transport.repondre(Route.Historique, 200, page1())
        transport.repondre(Route.CourbeElo, 200, courbeCorps)
        c.ouvrir()
        runCurrent()
        c.voirPlus()
        runCurrent()
        val attente = transport.retenir(Route.Historique)
        c.tirer()
        runCurrent()
        assertTrue(c.e.tirageEnCours)
        assertFalse(c.e.indicateurCentre)
        assertEquals(courbeLue, c.e.courbe)
        attente.complete(ReponseHttp(200, historique(listOf(partieListee("pz")), null)))
        runCurrent()
        assertEquals(listOf(partie("pz")), c.e.parties)
        assertNull(c.e.suivant)
        assertFalse(c.e.tirageEnCours)
    }

    @Test
    fun `tirer est ignore avant l'ouverture et pendant un chargement`() = runTest {
        val c = chargement()
        c.tirer()
        runCurrent()
        assertEquals(0, demandes())
        transport.retenir(Route.Historique)
        transport.retenir(Route.CourbeElo)
        c.ouvrir()
        runCurrent()
        c.tirer()
        runCurrent()
        assertEquals(1, demandes())
    }

    @Test
    fun `a la reouverture, rien de la precedente n'est garde`() = runTest {
        val c = chargement()
        transport.repondre(Route.Historique, 200, page1())
        transport.repondre(Route.CourbeElo, 200, courbeCorps)
        c.ouvrir()
        runCurrent()
        c.quitter()
        assertEquals(EtatHistorique(), c.e)
        transport.retenir(Route.Historique)
        c.ouvrir()
        runCurrent()
        assertTrue(c.e.indicateurCentre)
        assertEquals(2, demandes())
    }

    @Test
    fun `une reponse arrivee apres quitter est ignoree`() = runTest {
        val c = chargement()
        val attente = transport.retenir(Route.Historique)
        transport.repondre(Route.CourbeElo, 200, courbeCorps)
        c.ouvrir()
        runCurrent()
        c.quitter()
        attente.complete(ReponseHttp(200, page1()))
        runCurrent()
        assertEquals(EtatHistorique(), c.e)
    }

    // Voir plus --------------------------------------------------------------------------------

    private suspend fun TestScope.ouverte(suivant: String? = "c0ffee"): ChargementHistorique {
        val c = chargement()
        transport.repondre(Route.Historique, 200, page1(suivant))
        transport.repondre(Route.CourbeElo, 200, courbeCorps)
        c.ouvrir()
        runCurrent()
        return c
    }

    @Test
    fun `voir plus ajoute les parties suivantes, dans l'ordre du serveur, avec le curseur`() = runTest {
        val c = ouverte()
        val attente = transport.retenir(Route.Historique)
        c.voirPlus()
        runCurrent()
        assertTrue(c.e.suiteEnCours)
        assertEquals(listOf(partie("pa"), partie("pb", null)), c.e.parties)
        attente.complete(ReponseHttp(200, historique(listOf(partieListee("p9"), partieListee("p7")), suivant = "beef")))
        runCurrent()
        assertFalse(c.e.suiteEnCours)
        assertEquals(listOf("pa", "pb", "p9", "p7"), c.e.parties!!.map { it.id })
        assertEquals("beef", c.e.suivant)
        assertEquals("/api/eruditus/v1/historique?avant=c0ffee", transport.requetesVers(Route.Historique).last().chemin)
        assertEquals(courbeLue, c.e.courbe)
        assertEquals(1, transport.requetesVers(Route.CourbeElo).size)
    }

    @Test
    fun `voir plus ne trie rien et n'ajoute aucun doublon`() = runTest {
        val c = ouverte()
        // Une partie de la première page revient, et la page suivante n'est pas triée par l'app.
        transport.repondre(Route.Historique, 200, historique(listOf(partieListee("pb"), partieListee("p1"), partieListee("p3"), partieListee("p1")), null))
        c.voirPlus()
        runCurrent()
        assertEquals(listOf("pa", "pb", "p1", "p3"), c.e.parties!!.map { it.id })
        assertNull(c.e.suivant)
    }

    @Test
    fun `voir plus sans page suivante ou pendant un chargement, aucune requete`() = runTest {
        val sans = ouverte(suivant = null)
        sans.voirPlus()
        runCurrent()
        assertEquals(1, demandes())
        val c = ouverte()
        transport.retenir(Route.Historique)
        c.voirPlus()
        c.voirPlus()
        c.tirer()
        runCurrent()
        assertEquals(1 + 1 + 1, demandes())
    }

    @Test
    fun `voir plus en erreur, message en haut, la liste et le lien restent, on peut reessayer`() = runTest {
        val c = ouverte()
        transport.repondre(Route.Historique, 500, "oups")
        c.voirPlus()
        runCurrent()
        assertEquals(listOf(Affichage.PasDeConnexion(serveur = true)), sorties)
        sorties.clear()
        transport.repondre(Route.Historique, 429, trop)
        c.voirPlus()
        runCurrent()
        assertEquals(TexteErreur.TropDeTentatives, c.e.message)
        assertEquals(listOf("pa", "pb"), c.e.parties!!.map { it.id })
        assertEquals("c0ffee", c.e.suivant)
        assertFalse(c.e.suiteEnCours)
        transport.repondre(Route.Historique, 200, historique(listOf(partieListee("p9")), null))
        c.voirPlus()
        runCurrent()
        assertNull(c.e.message)
        assertEquals(listOf("pa", "pb", "p9"), c.e.parties!!.map { it.id })
    }

    // Erreurs ----------------------------------------------------------------------------------

    @Test
    fun `liste refusee, le message en haut et les valeurs deja affichees gardees`() = runTest {
        val c = ouverte()
        transport.repondre(Route.Historique, 429, trop)
        c.tirer()
        runCurrent()
        assertEquals(TexteErreur.TropDeTentatives, c.e.message)
        assertEquals(listOf("pa", "pb"), c.e.parties!!.map { it.id })
        assertEquals(courbeLue, c.e.courbe)
        assertFalse(c.e.tirageEnCours)
        assertTrue(sorties.isEmpty())
        // Relancer l'action efface le message.
        transport.repondre(Route.Historique, 200, page1())
        c.tirer()
        runCurrent()
        assertNull(c.e.message)
    }

    @Test
    fun `premier chargement refuse, le message seul, tirer reessaie`() = runTest {
        val c = chargement()
        transport.repondre(Route.Historique, 400, erreur("requete_invalide"))
        transport.repondre(Route.CourbeElo, 200, courbeCorps)
        c.ouvrir()
        runCurrent()
        assertEquals(TexteErreur.Generique, c.e.message)
        assertNull(c.e.parties)
        assertFalse(c.e.indicateurCentre)
        transport.repondre(Route.Historique, 200, page1())
        c.tirer()
        runCurrent()
        assertEquals(listOf("pa", "pb"), c.e.parties!!.map { it.id })
        assertNull(c.e.message)
    }

    @Test
    fun `session expiree, pas de connexion et reponse illisible quittent l'ecran`() = runTest {
        val c = chargement()
        transport.repondre(Route.CourbeElo, 200, courbeCorps)
        transport.repondre(Route.Historique, 401, erreur("x", 401))
        c.ouvrir()
        runCurrent()
        assertEquals(listOf<Affichage>(Affichage.SessionExpiree), sorties)
        sorties.clear()
        val d = chargement()
        transport.echouer(Route.Historique, connexionEtablie = false)
        d.ouvrir()
        runCurrent()
        assertEquals(listOf<Affichage>(Affichage.PasDeConnexion(serveur = false)), sorties)
        sorties.clear()
        transport.repondre(Route.Historique, 200, """{"suivant":null}""")
        d.ouvrir()
        runCurrent()
        assertEquals(listOf<Affichage>(Affichage.PasDeConnexion(serveur = true)), sorties)
    }

    @Test
    fun `echec seul de la courbe, la liste s'affiche sans elle et un message en haut`() = runTest {
        val c = chargement()
        transport.repondre(Route.Historique, 200, page1())
        val cas = listOf(
            Triple(500, "oups", TexteErreur.Generique),
            Triple(400, erreur("requete_invalide"), TexteErreur.Generique),
            Triple(429, trop, TexteErreur.TropDeTentatives),
            Triple(200, """{"depart":1}""", TexteErreur.Generique),
        )
        for ((statut, corps, texte) in cas) {
            transport.repondre(Route.CourbeElo, statut, corps)
            c.ouvrir()
            runCurrent()
            assertEquals("$statut", listOf("pa", "pb"), c.e.parties!!.map { it.id })
            assertNull("$statut", c.e.courbe)
            assertEquals("$statut", texte, c.e.message)
            assertTrue(sorties.isEmpty())
        }
        // Pas de connexion sur la courbe seule : la liste a répondu, donc pas de page « Pas de connexion ».
        transport.echouer(Route.CourbeElo, connexionEtablie = false)
        c.ouvrir()
        runCurrent()
        assertEquals(TexteErreur.Generique, c.e.message)
        assertEquals(listOf("pa", "pb"), c.e.parties!!.map { it.id })
        assertTrue(sorties.isEmpty())
    }

    @Test
    fun `echec seul de la courbe, une session expiree quitte quand meme`() = runTest {
        val c = chargement()
        transport.repondre(Route.Historique, 200, page1())
        transport.repondre(Route.CourbeElo, 401, erreur("x", 401))
        c.ouvrir()
        runCurrent()
        assertEquals(listOf<Affichage>(Affichage.SessionExpiree), sorties)
    }

    @Test
    fun `quand tout echoue, l'erreur de la liste prime`() = runTest {
        val c = chargement()
        transport.repondre(Route.Historique, 429, trop)
        transport.repondre(Route.CourbeElo, 500, "oups")
        c.ouvrir()
        runCurrent()
        assertEquals(TexteErreur.TropDeTentatives, c.e.message)
        assertTrue(sorties.isEmpty())
    }

    @Test
    fun `le message du detail introuvable s'affiche en haut de l'Historique`() = runTest {
        val c = ouverte()
        c.afficher(TexteErreur.PartieIndisponible)
        assertEquals(TexteErreur.PartieIndisponible, c.e.message)
        assertEquals(listOf("pa", "pb"), c.e.parties!!.map { it.id })
        // Un nouveau chargement l'efface.
        c.tirer()
        runCurrent()
        assertNull(c.e.message)
    }

    // Détail -----------------------------------------------------------------------------------

    private val idPartie = TransportFactice.ID_PARTIE
    private val corpsDetail =
        """{"partie":{"id":"$idPartie","date":"2026-11-22T10:00:00.123Z","etat":"terminee","bonnes":3,"servies":5,"total":5},""" +
            """"elo":{"variation":14,"valeur":1261},"questions":[]}"""

    private suspend fun TestScope.detail(): ChargementDetail {
        transport.repondre(Route.Connexion, 200, CONNEXION)
        depot.connecter("Hibou_42", "nuit-blanche-42")
        return ChargementDetail(depot::detailPartie, backgroundScope) { sorties += it }
    }

    private val ChargementDetail.e: EtatDetail get() = etat.value

    @Test
    fun `detail, indicateur centre jusqu'a la reponse`() = runTest {
        val d = detail()
        val attente = transport.retenir(Route.HistoriqueDetail)
        d.ouvrir(idPartie)
        runCurrent()
        assertTrue(d.e.indicateurCentre)
        attente.complete(ReponseHttp(200, corpsDetail))
        runCurrent()
        assertFalse(d.e.indicateurCentre)
        assertEquals(idPartie, d.e.detail!!.partie.id)
        assertEquals("/api/eruditus/v1/historique/$idPartie", transport.requetesVers(Route.HistoriqueDetail).single().chemin)
    }

    @Test
    fun `detail, quitter oublie tout et une reponse tardive est ignoree`() = runTest {
        val d = detail()
        val attente = transport.retenir(Route.HistoriqueDetail)
        d.ouvrir(idPartie)
        runCurrent()
        d.quitter()
        attente.complete(ReponseHttp(200, corpsDetail))
        runCurrent()
        assertEquals(EtatDetail(), d.e)
    }

    @Test
    fun `detail, partie introuvable, retour a l'Historique avec son message`() = runTest {
        val d = detail()
        transport.repondre(Route.HistoriqueDetail, 404, erreur("partie_introuvable", 404))
        d.ouvrir(idPartie)
        runCurrent()
        assertEquals(listOf<Affichage>(Affichage.VersHistorique(TexteErreur.PartieIndisponible)), sorties)
    }

    @Test
    fun `detail, session expiree et pas de connexion quittent l'ecran`() = runTest {
        val d = detail()
        transport.repondre(Route.HistoriqueDetail, 401, erreur("x", 401))
        d.ouvrir(idPartie)
        runCurrent()
        assertEquals(listOf<Affichage>(Affichage.SessionExpiree), sorties)
        sorties.clear()
        transport.repondre(Route.HistoriqueDetail, 503, "oups")
        d.ouvrir(idPartie)
        runCurrent()
        assertEquals(listOf<Affichage>(Affichage.PasDeConnexion(serveur = true)), sorties)
        sorties.clear()
        transport.repondre(Route.HistoriqueDetail, 200, """{"partie":{}}""")
        d.ouvrir(idPartie)
        runCurrent()
        assertEquals(listOf<Affichage>(Affichage.PasDeConnexion(serveur = true)), sorties)
    }

    @Test
    fun `detail, 429 et tout autre cas, message en haut, la barre reste, tirer reessaie`() = runTest {
        val d = detail()
        transport.repondre(Route.HistoriqueDetail, 429, trop)
        d.ouvrir(idPartie)
        runCurrent()
        assertEquals(TexteErreur.TropDeTentatives, d.e.message)
        assertNull(d.e.detail)
        assertFalse(d.e.indicateurCentre)
        transport.repondre(Route.HistoriqueDetail, 400, erreur("requete_invalide"))
        d.tirer()
        runCurrent()
        assertEquals(TexteErreur.Generique, d.e.message)
        assertTrue(sorties.isEmpty())
        transport.repondre(Route.HistoriqueDetail, 200, corpsDetail)
        d.tirer()
        runCurrent()
        assertNull(d.e.message)
        assertEquals(idPartie, d.e.detail!!.partie.id)
    }
}

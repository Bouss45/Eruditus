package com.eruditus.android.classement

import com.eruditus.android.compte.Affichage
import com.eruditus.android.compte.DepotCompte
import com.eruditus.android.compte.TexteErreur
import com.eruditus.android.donnees.api.Classement
import com.eruditus.android.donnees.api.Place
import com.eruditus.android.donnees.api.ReponseHttp
import com.eruditus.android.donnees.api.Route
import com.eruditus.android.donnees.api.TransportFactice
import com.eruditus.android.donnees.api.TransportFactice.Companion.CLASSEMENT
import com.eruditus.android.donnees.api.TransportFactice.Companion.CONNEXION
import com.eruditus.android.donnees.api.TransportFactice.Companion.classement
import com.eruditus.android.donnees.api.TransportFactice.Companion.erreur
import com.eruditus.android.donnees.api.TransportFactice.Companion.ligne
import com.eruditus.android.donnees.session.ChiffreurLogiciel
import com.eruditus.android.donnees.session.CoffreSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Chargement de l'écran Classement (spec/ecrans.md § 11, D73) : indicateur centré à chaque
 * ouverture, jusqu'à la première réponse ; tirer vers le bas ou revenir au premier plan recharge
 * et les lignes restent ; un seul chargement à la fois, une seule requête à l'ouverture ; erreurs ;
 * rien de gardé en quittant l'écran. Le serveur est le
 * `TransportFactice`, joint au vrai `DepotCompte`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChargementClassementTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private val transport = TransportFactice()
    private val coffre by lazy { CoffreSession(File(dossier.root, "session"), ChiffreurLogiciel()) }
    private val depot by lazy { DepotCompte(transport, coffre, disque = Dispatchers.Unconfined) }
    private val sorties = mutableListOf<Affichage>()

    private val lu = Classement((1..10).map { Place(it, "Joueur_$it", 20 - it) }, Place(27, "Hibou_42", 4), 48)
    private val autre = classement(listOf(ligne(1, "Hypatie", 25)), moi = ligne(1, "Hypatie", 25), classes = 1)
    private val luAutre = Classement(listOf(Place(1, "Hypatie", 25)), Place(1, "Hypatie", 25), 1)

    private suspend fun TestScope.chargement(): ChargementClassement {
        transport.repondre(Route.Connexion, 200, CONNEXION)
        depot.connecter("Hibou_42", "nuit-blanche-42")
        return ChargementClassement(charger = depot::classement, portee = backgroundScope, sortir = { sorties += it })
    }

    private val ChargementClassement.e: EtatClassement get() = etat.value

    private fun demandes() = transport.requetesVers(Route.Classement).size

    private val trop = """{"status":429,"message":"Too Many Requests.","data":{}}"""

    // Premier affichage et tirage --------------------------------------------------------------

    @Test
    fun `a l'ouverture, indicateur centre jusqu'a la premiere reponse`() = runTest {
        val c = chargement()
        val attente = transport.retenir(Route.Classement)
        c.ouvrir()
        runCurrent()
        assertTrue(c.e.indicateurCentre)
        assertFalse(c.e.tirageEnCours)
        assertNull(c.e.classement)
        attente.complete(ReponseHttp(200, CLASSEMENT))
        runCurrent()
        assertEquals(EtatClassement(classement = lu), c.e)
        assertFalse(c.e.indicateurCentre)
        assertNotNull(c.e.vue)
    }

    @Test
    fun `tirer recharge, les lignes restent a l'ecran jusqu'a la nouvelle reponse`() = runTest {
        val c = chargement()
        transport.repondre(Route.Classement, 200, CLASSEMENT)
        c.ouvrir()
        runCurrent()
        val attente = transport.retenir(Route.Classement)
        c.tirer()
        runCurrent()
        assertTrue(c.e.tirageEnCours)
        assertFalse(c.e.indicateurCentre)
        assertEquals(lu, c.e.classement)
        attente.complete(ReponseHttp(200, autre))
        runCurrent()
        assertEquals(EtatClassement(classement = luAutre), c.e)
        assertEquals(2, demandes())
    }

    @Test
    fun `un seul chargement a la fois`() = runTest {
        val c = chargement()
        val premiere = transport.retenir(Route.Classement)
        c.ouvrir()
        c.tirer()
        runCurrent()
        assertEquals(1, demandes())
        premiere.complete(ReponseHttp(200, CLASSEMENT))
        runCurrent()
        val seconde = transport.retenir(Route.Classement)
        c.tirer()
        c.tirer()
        runCurrent()
        assertEquals(2, demandes())
        seconde.complete(ReponseHttp(200, autre))
        runCurrent()
        assertEquals(luAutre, c.e.classement)
    }

    // Retour au premier plan -------------------------------------------------------------------

    @Test
    fun `au retour au premier plan, l'ecran ouvert se recharge, les lignes restent`() = runTest {
        val c = chargement()
        transport.repondre(Route.Classement, 200, CLASSEMENT)
        c.ouvrir()
        runCurrent()
        val attente = transport.retenir(Route.Classement)
        c.auPremierPlan()
        runCurrent()
        assertEquals(2, demandes())
        assertTrue(c.e.tirageEnCours)
        assertFalse(c.e.indicateurCentre)
        assertEquals(lu, c.e.classement)
        attente.complete(ReponseHttp(200, autre))
        runCurrent()
        assertEquals(EtatClassement(classement = luAutre), c.e)
    }

    @Test
    fun `une seule requete a l'ouverture, meme si le premier plan arrive aussitot`() = runTest {
        val c = chargement()
        val attente = transport.retenir(Route.Classement)
        c.ouvrir()
        c.auPremierPlan()
        runCurrent()
        c.auPremierPlan()
        runCurrent()
        assertEquals(1, demandes())
        assertTrue(c.e.indicateurCentre)
        attente.complete(ReponseHttp(200, CLASSEMENT))
        runCurrent()
        assertEquals(EtatClassement(classement = lu), c.e)
        assertEquals(1, demandes())
    }

    @Test
    fun `aucune requete au premier plan tant qu'un tirage est en cours, ni l'inverse`() = runTest {
        val c = chargement()
        transport.repondre(Route.Classement, 200, CLASSEMENT)
        c.ouvrir()
        runCurrent()
        val tirage = transport.retenir(Route.Classement)
        c.tirer()
        c.auPremierPlan()
        runCurrent()
        assertEquals(2, demandes())
        tirage.complete(ReponseHttp(200, autre))
        runCurrent()
        val premierPlan = transport.retenir(Route.Classement)
        c.auPremierPlan()
        c.tirer()
        runCurrent()
        assertEquals(3, demandes())
        premierPlan.complete(ReponseHttp(200, CLASSEMENT))
        runCurrent()
        assertEquals(EtatClassement(classement = lu), c.e)
    }

    @Test
    fun `ecran ferme, rien au retour au premier plan`() = runTest {
        val c = chargement()
        c.auPremierPlan()
        runCurrent()
        assertEquals(0, demandes())
        transport.repondre(Route.Classement, 200, CLASSEMENT)
        c.ouvrir()
        runCurrent()
        c.quitter()
        c.auPremierPlan()
        runCurrent()
        assertEquals(1, demandes())
        assertEquals(EtatClassement(), c.e)
    }

    @Test
    fun `429 au retour au premier plan, message en haut, lignes gardees, efface au rechargement suivant`() = runTest {
        val c = chargement()
        transport.repondre(Route.Classement, 200, CLASSEMENT)
        c.ouvrir()
        runCurrent()
        transport.repondre(Route.Classement, 429, trop)
        c.auPremierPlan()
        runCurrent()
        assertEquals(EtatClassement(classement = lu, message = TexteErreur.TropDeTentatives), c.e)
        val attente = transport.retenir(Route.Classement)
        c.auPremierPlan()
        runCurrent()
        assertNull(c.e.message)
        attente.complete(ReponseHttp(200, autre))
        runCurrent()
        assertEquals(EtatClassement(classement = luAutre), c.e)
    }

    // Erreurs ----------------------------------------------------------------------------------

    @Test
    fun `429 au tirage, message en haut, lignes gardees, efface au tirage suivant`() = runTest {
        val c = chargement()
        transport.repondre(Route.Classement, 200, CLASSEMENT)
        c.ouvrir()
        runCurrent()
        transport.repondre(Route.Classement, 429, trop)
        c.tirer()
        runCurrent()
        assertEquals(EtatClassement(classement = lu, message = TexteErreur.TropDeTentatives), c.e)
        val attente = transport.retenir(Route.Classement)
        c.tirer()
        runCurrent()
        assertNull(c.e.message)
        assertEquals(lu, c.e.classement)
        attente.complete(ReponseHttp(200, autre))
        runCurrent()
        assertEquals(EtatClassement(classement = luAutre), c.e)
        assertTrue(sorties.isEmpty())
    }

    @Test
    fun `429 au premier chargement, le message seul, tirer reessaie`() = runTest {
        val c = chargement()
        transport.repondre(Route.Classement, 429, trop)
        c.ouvrir()
        runCurrent()
        assertEquals(EtatClassement(message = TexteErreur.TropDeTentatives), c.e)
        assertFalse(c.e.indicateurCentre)
        assertNull(c.e.vue)
        transport.repondre(Route.Classement, 200, CLASSEMENT)
        c.tirer()
        runCurrent()
        assertEquals(EtatClassement(classement = lu), c.e)
    }

    @Test
    fun `autre refus, une erreur est survenue en haut, lignes gardees`() = runTest {
        val c = chargement()
        transport.repondre(Route.Classement, 200, CLASSEMENT)
        c.ouvrir()
        runCurrent()
        transport.repondre(Route.Classement, 400, erreur("requete_invalide"))
        c.tirer()
        runCurrent()
        assertEquals(EtatClassement(classement = lu, message = TexteErreur.Generique), c.e)
        assertTrue(TexteErreur.Generique.alerte)
    }

    @Test
    fun `session expiree, session effacee, Bienvenue`() = runTest {
        val c = chargement()
        transport.repondre(Route.Classement, 401, """{"status":401,"message":"…","data":{}}""")
        c.ouvrir()
        runCurrent()
        assertEquals(listOf<Affichage>(Affichage.SessionExpiree), sorties)
        assertNull(depot.session)
        assertNull(coffre.lire())
    }

    @Test
    fun `reseau coupe, pas de connexion`() = runTest {
        val c = chargement()
        transport.echouer(Route.Classement, connexionEtablie = false)
        c.ouvrir()
        runCurrent()
        assertEquals(listOf<Affichage>(Affichage.PasDeConnexion(serveur = false)), sorties)
        assertNotNull(depot.session)
    }

    @Test
    fun `5xx, serveur muet ou reponse illisible, pas de connexion, texte serveur`() = runTest {
        val c = chargement()
        transport.repondre(Route.Classement, 502, "<html>Bad Gateway</html>")
        c.ouvrir()
        runCurrent()
        transport.echouer(Route.Classement, connexionEtablie = true)
        c.ouvrir()
        runCurrent()
        transport.repondre(Route.Classement, 200, """{"top":[{"rang":"1","pseudo":"Hypatie","score":19}],"moi":null,"classes":1}""")
        c.ouvrir()
        runCurrent()
        assertEquals(List(3) { Affichage.PasDeConnexion(serveur = true) }, sorties)
    }

    // Quitter l'écran --------------------------------------------------------------------------

    @Test
    fun `quitter pendant le chargement, la reponse est ignoree`() = runTest {
        val c = chargement()
        val attente = transport.retenir(Route.Classement)
        c.ouvrir()
        runCurrent()
        c.quitter()
        attente.complete(ReponseHttp(401, """{"status":401,"message":"…","data":{}}"""))
        runCurrent()
        assertEquals(EtatClassement(), c.e)
        assertTrue(sorties.isEmpty())
        assertNotNull(depot.session)
    }

    @Test
    fun `rien n'est garde en quittant, l'indicateur centre revient a la reouverture`() = runTest {
        val c = chargement()
        transport.repondre(Route.Classement, 200, CLASSEMENT)
        c.ouvrir()
        runCurrent()
        c.quitter()
        assertNull(c.e.classement)
        val attente = transport.retenir(Route.Classement)
        c.ouvrir()
        runCurrent()
        assertTrue(c.e.indicateurCentre)
        assertNull(c.e.classement)
        attente.complete(ReponseHttp(200, autre))
        runCurrent()
        assertEquals(luAutre, c.e.classement)
    }
}

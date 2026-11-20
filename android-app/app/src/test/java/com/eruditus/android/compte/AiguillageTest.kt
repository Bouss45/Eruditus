package com.eruditus.android.compte

import com.eruditus.android.donnees.api.Route
import com.eruditus.android.donnees.api.TransportFactice
import com.eruditus.android.donnees.api.TransportFactice.Companion.INFO
import com.eruditus.android.donnees.api.TransportFactice.Companion.RENOUVELLEMENT
import com.eruditus.android.donnees.session.ChiffreurLogiciel
import com.eruditus.android.donnees.session.CoffreSession
import com.eruditus.android.donnees.session.Session
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Aiguillage au démarrage (spec/ecrans.md § 1.2, D21, D53) : `/info` sans jeton, puis la session
 * locale et son renouvellement.
 */
class AiguillageTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private val transport = TransportFactice()
    private val chiffreur = ChiffreurLogiciel()
    private val fichier by lazy { File(dossier.root, "session") }
    private val coffre by lazy { CoffreSession(fichier, chiffreur) }
    private val depot by lazy { DepotCompte(transport, coffre) }
    private val ancienne = Session("jeton-ancien", "id-hibou", "Hibou_42")

    private fun aiguiller(versionCode: Int = 1000) = runBlocking { depot.aiguiller(versionCode) }

    @Test
    fun `jeton absent, Bienvenue sans message`() {
        transport.repondre(Route.Info, 200, INFO)
        assertEquals(Aiguillage.Bienvenue(sessionExpiree = false), aiguiller())
        assertTrue(transport.requetesVers(Route.Renouvellement).isEmpty())
        assertNull(depot.session)
    }

    @Test
    fun `session valide, renouvelee, Accueil, le nouveau jeton remplace l'ancien`() {
        coffre.ecrire(ancienne)
        transport.repondre(Route.Info, 200, INFO)
        transport.repondre(Route.Renouvellement, 200, RENOUVELLEMENT)
        assertEquals(Aiguillage.Accueil, aiguiller())
        assertEquals("jeton-ancien", transport.requetesVers(Route.Renouvellement).single().jeton)
        assertEquals(ancienne.copy(jeton = "jeton-renouvele"), depot.session)
        assertEquals(ancienne.copy(jeton = "jeton-renouvele"), coffre.lire())
    }

    @Test
    fun `jeton expire, session effacee, Bienvenue avec session expiree`() {
        coffre.ecrire(ancienne)
        transport.repondre(Route.Info, 200, INFO)
        transport.repondre(Route.Renouvellement, 401, """{"status":401,"message":"…","data":{}}""")
        assertEquals(Aiguillage.Bienvenue(sessionExpiree = true), aiguiller())
        assertNull(depot.session)
        assertFalse(fichier.exists())
    }

    @Test
    fun `version trop ancienne, Mise a jour necessaire, rien d'autre n'est appele`() {
        coffre.ecrire(ancienne)
        transport.repondre(Route.Info, 200, """{"versionMinimale":1012}""")
        assertEquals(Aiguillage.MiseAJour, aiguiller(versionCode = 1011))
        assertEquals(1, transport.requetes.size)
        assertEquals(ancienne, coffre.lire())
    }

    @Test
    fun `version egale ou plus recente que le minimum, on continue`() {
        transport.repondre(Route.Info, 200, """{"versionMinimale":1012}""")
        assertEquals(Aiguillage.Bienvenue(false), aiguiller(versionCode = 1012))
        assertEquals(Aiguillage.Bienvenue(false), aiguiller(versionCode = 1013))
    }

    @Test
    fun `info sans jeton, meme avec une session`() {
        coffre.ecrire(ancienne)
        transport.repondre(Route.Info, 200, INFO)
        transport.repondre(Route.Renouvellement, 200, RENOUVELLEMENT)
        aiguiller()
        assertNull(transport.requetesVers(Route.Info).single().jeton)
    }

    @Test
    fun `reseau coupe sur info, Pas de connexion reseau`() {
        coffre.ecrire(ancienne)
        transport.echouer(Route.Info, connexionEtablie = false)
        assertEquals(Aiguillage.PasDeConnexion(serveur = false), aiguiller())
        assertEquals(ancienne, coffre.lire())
    }

    @Test
    fun `serveur muet, 5xx ou reponse illisible sur info, Pas de connexion serveur`() {
        transport.echouer(Route.Info, connexionEtablie = true)
        assertEquals(Aiguillage.PasDeConnexion(serveur = true), aiguiller())
        transport.repondre(Route.Info, 502, "<html>Bad Gateway</html>")
        assertEquals(Aiguillage.PasDeConnexion(serveur = true), aiguiller())
        transport.repondre(Route.Info, 200, "<html>Portail</html>")
        assertEquals(Aiguillage.PasDeConnexion(serveur = true), aiguiller())
        transport.repondre(Route.Info, 429, "")
        assertEquals(Aiguillage.PasDeConnexion(serveur = true), aiguiller())
    }

    @Test
    fun `reseau coupe au renouvellement, Pas de connexion, session gardee`() {
        coffre.ecrire(ancienne)
        transport.repondre(Route.Info, 200, INFO)
        transport.echouer(Route.Renouvellement, connexionEtablie = false)
        assertEquals(Aiguillage.PasDeConnexion(serveur = false), aiguiller())
        assertEquals(ancienne, coffre.lire())
    }

    @Test
    fun `autre echec du renouvellement, Pas de connexion serveur, session gardee`() {
        coffre.ecrire(ancienne)
        transport.repondre(Route.Info, 200, INFO)
        for ((statut, corps) in listOf(500 to "", 429 to "", 400 to TransportFactice.erreur("inattendu"))) {
            transport.repondre(Route.Renouvellement, statut, corps)
            assertEquals("$statut", Aiguillage.PasDeConnexion(serveur = true), aiguiller())
            assertEquals(ancienne, coffre.lire())
        }
    }

    @Test
    fun `session illisible, effacee, Bienvenue sans message`() {
        coffre.ecrire(ancienne)
        chiffreur.perdreCle()
        transport.repondre(Route.Info, 200, INFO)
        assertEquals(Aiguillage.Bienvenue(sessionExpiree = false), aiguiller())
        assertFalse(fichier.exists())
    }
}

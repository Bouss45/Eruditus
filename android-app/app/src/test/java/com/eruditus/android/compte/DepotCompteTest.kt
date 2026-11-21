package com.eruditus.android.compte

import com.eruditus.android.donnees.api.ErreurApi
import com.eruditus.android.donnees.api.Moi
import com.eruditus.android.donnees.api.Resultat
import com.eruditus.android.donnees.api.Route
import com.eruditus.android.donnees.api.TransportFactice
import com.eruditus.android.donnees.api.TransportFactice.Companion.CODE
import com.eruditus.android.donnees.api.TransportFactice.Companion.COMPTE_CREE
import com.eruditus.android.donnees.api.TransportFactice.Companion.CONNEXION
import com.eruditus.android.donnees.api.TransportFactice.Companion.MOI
import com.eruditus.android.donnees.api.TransportFactice.Companion.MOT_DE_PASSE_CHANGE
import com.eruditus.android.donnees.api.TransportFactice.Companion.PSEUDO_CHANGE
import com.eruditus.android.donnees.api.TransportFactice.Companion.RECUPERATION
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
 * Session ouverte, mise à jour et effacée (D13, D14, D54, spec/donnees.md § 5) : effacement
 * complet (mémoire et fichier) à la déconnexion, quel que soit le résultat, et après la
 * suppression ; jamais après un refus.
 */
class DepotCompteTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private val transport = TransportFactice()
    private val fichier by lazy { File(dossier.root, "session") }
    private val coffre by lazy { CoffreSession(fichier, ChiffreurLogiciel()) }
    private val depot by lazy { DepotCompte(transport, coffre) }
    private val connecte = Session("jeton-connexion", "id-hibou", "Hibou_42")

    private fun connecter() = runBlocking {
        transport.repondre(Route.Connexion, 200, CONNEXION)
        assertEquals(Resultat.Ok(Unit), depot.connecter("Hibou_42", "nuit-blanche-42"))
        assertEquals(connecte, coffre.lire())
    }

    private fun assertEfface() {
        assertNull("session en mémoire", depot.session)
        assertNull("session relue", coffre.lire())
        assertFalse("fichier de session", fichier.exists())
    }

    private fun assertGardee(session: Session = connecte) {
        assertEquals(session, depot.session)
        assertEquals(session, coffre.lire())
    }

    @Test
    fun `creation, session ouverte avec le pseudo du serveur, code rendu`() = runBlocking {
        transport.repondre(Route.CreerCompte, 200, COMPTE_CREE)
        assertEquals(Resultat.Ok(CODE), depot.creer(" Hibou_42 ", "nuit-blanche-42"))
        assertEquals("Hibou_42", transport.requetes.single().corps!!.substringAfter("\"pseudo\":\"").substringBefore('"'))
        assertGardee(Session("jeton-cree", "id-hibou", "Hibou_42"))
    }

    @Test
    fun `connexion, pseudo saisi sans espaces jusqu'au premier moi, qui donne la casse de creation`() = runBlocking {
        transport.repondre(Route.Connexion, 200, CONNEXION)
        depot.connecter("  hibou_42 ", "nuit-blanche-42")
        assertGardee(Session("jeton-connexion", "id-hibou", "hibou_42"))
        transport.repondre(Route.Moi, 200, MOI)
        assertEquals(Resultat.Ok(Moi("Hibou_42", 12, 3, 48)), depot.moi())
        assertGardee(Session("jeton-connexion", "id-hibou", "Hibou_42"))
    }

    @Test
    fun `connexion refusee, aucune session`() = runBlocking {
        transport.repondre(Route.Connexion, 400, """{"status":400,"message":"Failed to authenticate.","data":{}}""")
        assertEquals(Resultat.Echec(ErreurApi.Refus(400, null)), depot.connecter("Hibou_42", "faux"))
        assertEfface()
    }

    @Test
    fun `recuperation, nouvelle session et nouveau code`() = runBlocking {
        connecter()
        transport.repondre(Route.Recuperation, 200, RECUPERATION)
        assertEquals(Resultat.Ok("Q9XA-7K3M-F8RT-2HDE"), depot.recuperer("Hibou_42", "7K3MQ9XA2HDEF8RT", "lune-glacee-17"))
        assertGardee(Session("jeton-recupere", "id-hibou", "Hibou_42"))
    }

    @Test
    fun `deconnexion reussie, tout est efface`() = runBlocking {
        connecter()
        transport.repondre(Route.Deconnexion, 204)
        depot.deconnecter()
        assertEquals("jeton-connexion", transport.requetesVers(Route.Deconnexion).single().jeton)
        assertEfface()
    }

    @Test
    fun `deconnexion sans reseau, serveur muet, 401 ou erreur, tout est efface quand meme`() {
        val echecs: List<(TransportFactice) -> Unit> = listOf(
            { it.echouer(Route.Deconnexion, connexionEtablie = false) },
            { it.echouer(Route.Deconnexion, connexionEtablie = true) },
            { it.repondre(Route.Deconnexion, 401) },
            { it.repondre(Route.Deconnexion, 500) },
            { it.repondre(Route.Deconnexion, 429) },
        )
        for (echec in echecs) {
            connecter()
            echec(transport)
            runBlocking { depot.deconnecter() }
            assertEfface()
        }
    }

    @Test
    fun `suppression reussie, tout est efface`() = runBlocking {
        connecter()
        transport.repondre(Route.Suppression, 204)
        assertEquals(Resultat.Ok(Unit), depot.supprimer("nuit-blanche-42"))
        assertEfface()
    }

    @Test
    fun `suppression refusee, session gardee`() {
        connecter()
        val refus = listOf(
            Triple(400, TransportFactice.erreur("mot_de_passe_incorrect"), ErreurApi.Refus(400, "mot_de_passe_incorrect")),
            Triple(429, "", ErreurApi.TropDeTentatives),
            Triple(500, "", ErreurApi.Serveur),
        )
        for ((statut, corps, erreur) in refus) {
            transport.repondre(Route.Suppression, statut, corps)
            assertEquals(Resultat.Echec(erreur), runBlocking { depot.supprimer("faux") })
            assertGardee()
        }
        transport.echouer(Route.Suppression, connexionEtablie = false)
        assertEquals(Resultat.Echec(ErreurApi.Reseau), runBlocking { depot.supprimer("nuit-blanche-42") })
        assertGardee()
    }

    @Test
    fun `401 a la suppression, session effacee`() = runBlocking {
        connecter()
        transport.repondre(Route.Suppression, 401)
        assertEquals(Resultat.Echec(ErreurApi.NonAutorise), depot.supprimer("nuit-blanche-42"))
        assertEfface()
    }

    @Test
    fun `changement de pseudo, sans espaces autour, nouveau pseudo dans la session et le fichier`() = runBlocking {
        connecter()
        transport.repondre(Route.ChangementPseudo, 200, PSEUDO_CHANGE)
        assertEquals(Resultat.Ok("Chouette_7"), depot.changerPseudo("  Chouette_7 "))
        val envoyee = transport.requetesVers(Route.ChangementPseudo).single()
        assertEquals("""{"pseudo":"Chouette_7"}""", envoyee.corps)
        assertEquals("jeton-connexion", envoyee.jeton)
        // Le pseudo du serveur ; jeton et identifiant inchangés.
        assertGardee(Session("jeton-connexion", "id-hibou", "Chouette_7"))
    }

    @Test
    fun `changement de pseudo refuse, session gardee, apres un 401 session effacee`() = runBlocking {
        connecter()
        for ((statut, corps) in listOf(
            400 to TransportFactice.erreur("pseudo_pris"),
            409 to TransportFactice.erreur("pseudo_trop_recent", 409),
            429 to "",
            500 to "",
        )) {
            transport.repondre(Route.ChangementPseudo, statut, corps)
            assertTrue(depot.changerPseudo("Chouette_7") is Resultat.Echec)
            assertGardee()
        }
        transport.echouer(Route.ChangementPseudo, connexionEtablie = false)
        assertEquals(Resultat.Echec(ErreurApi.Reseau), depot.changerPseudo("Chouette_7"))
        assertGardee()
        transport.repondre(Route.ChangementPseudo, 401)
        assertEquals(Resultat.Echec(ErreurApi.NonAutorise), depot.changerPseudo("Chouette_7"))
        assertEfface()
    }

    @Test
    fun `changement de mot de passe, le jeton neuf enregistre et envoye ensuite`() = runBlocking {
        connecter()
        transport.repondre(Route.ChangementMotDePasse, 200, MOT_DE_PASSE_CHANGE)
        assertEquals(Resultat.Ok(Unit), depot.changerMotDePasse("nuit-blanche-42", "lune-glacee-17"))
        assertEquals("jeton-connexion", transport.requetesVers(Route.ChangementMotDePasse).single().jeton)
        assertGardee(Session("jeton-neuf", "id-hibou", "Hibou_42"))
        // Relu au prochain lancement, et seul jeton envoyé désormais.
        assertEquals("jeton-neuf", coffre.lire()?.jeton)
        transport.repondre(Route.Moi, 200, MOI)
        depot.moi()
        assertEquals("jeton-neuf", transport.requetesVers(Route.Moi).single().jeton)
    }

    @Test
    fun `changement de mot de passe refuse, ancien jeton garde, apres un 401 session effacee`() = runBlocking {
        connecter()
        for ((statut, corps) in listOf(
            400 to TransportFactice.erreur("mot_de_passe_incorrect"),
            400 to TransportFactice.erreur("mot_de_passe_invalide"),
            429 to "",
            502 to "",
        )) {
            transport.repondre(Route.ChangementMotDePasse, statut, corps)
            assertTrue(depot.changerMotDePasse("faux", "lune-glacee-17") is Resultat.Echec)
            assertGardee()
        }
        transport.echouer(Route.ChangementMotDePasse, connexionEtablie = true)
        assertEquals(Resultat.Echec(ErreurApi.Serveur), depot.changerMotDePasse("nuit-blanche-42", "lune-glacee-17"))
        assertGardee()
        transport.repondre(Route.ChangementMotDePasse, 401)
        assertEquals(Resultat.Echec(ErreurApi.NonAutorise), depot.changerMotDePasse("nuit-blanche-42", "lune-glacee-17"))
        assertEfface()
    }

    @Test
    fun `401 sur moi, session effacee`() = runBlocking {
        connecter()
        transport.repondre(Route.Moi, 401)
        assertEquals(Resultat.Echec(ErreurApi.NonAutorise), depot.moi())
        assertEfface()
    }

    @Test
    fun `erreur sur moi autre que 401, session gardee`() = runBlocking {
        connecter()
        transport.repondre(Route.Moi, 429)
        assertEquals(Resultat.Echec(ErreurApi.TropDeTentatives), depot.moi())
        transport.echouer(Route.Moi, connexionEtablie = false)
        assertEquals(Resultat.Echec(ErreurApi.Reseau), depot.moi())
        assertGardee()
    }

    @Test
    fun `apres effacement, plus aucun jeton ne part`() = runBlocking {
        connecter()
        transport.repondre(Route.Deconnexion, 204)
        depot.deconnecter()
        transport.repondre(Route.Moi, 401)
        depot.moi()
        assertNull(transport.requetesVers(Route.Moi).single().jeton)
        assertTrue(transport.requetes.none { it.jeton != null && it.chemin != Route.Deconnexion.chemin })
    }
}

package com.eruditus.android.partie

import com.eruditus.android.compte.DepotCompte
import com.eruditus.android.compte.TexteErreur
import com.eruditus.android.donnees.api.ReponseHttp
import com.eruditus.android.donnees.api.Route
import com.eruditus.android.donnees.api.TransportFactice
import com.eruditus.android.donnees.api.TransportFactice.Companion.CONNEXION
import com.eruditus.android.donnees.api.TransportFactice.Companion.corrigee
import com.eruditus.android.donnees.api.TransportFactice.Companion.erreur
import com.eruditus.android.donnees.api.TransportFactice.Companion.partieCreee
import com.eruditus.android.donnees.api.TransportFactice.Companion.questionServie
import com.eruditus.android.donnees.session.ChiffreurLogiciel
import com.eruditus.android.donnees.session.CoffreSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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
 * Signaler une question depuis la correction (spec/ecrans.md § 9, D118, D119) : la boîte, le
 * motif obligatoire, le commentaire passé par la règle de l'avis (200 caractères), un envoi par
 * question, les erreurs sous le champ sans quitter la partie, la remise à zéro à la question
 * suivante. Le serveur est le `TransportFactice`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignalementQuestionTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private val transport = TransportFactice()
    private val coffre by lazy { CoffreSession(File(dossier.root, "session"), ChiffreurLogiciel()) }
    private val depot by lazy { DepotCompte(transport, coffre, disque = Dispatchers.Unconfined) }
    private val sorties = mutableListOf<SortiePartie>()

    private fun TestScope.deroulement() = DeroulementPartie(
        api = depot.api,
        effacerSession = depot::effacerSession,
        horloge = { testScheduler.currentTime },
        portee = backgroundScope,
        sortir = { sorties += it },
    )

    /** Une partie dont la question 1 est répondue : la correction est affichée. */
    private fun TestScope.apresCorrection(): DeroulementPartie {
        transport.repondre(Route.CreerPartie, 200, partieCreee(20_000))
        transport.repondre(Route.Reponse, 200, corrigee(1, choix = 0, bonne = 2, raison = "fausse"))
        transport.repondre(Route.Signalement, 200, "{}")
        val d = deroulement()
        d.jouer()
        runCurrent()
        d.choisir(0)
        runCurrent()
        assertTrue(d.etat.value!!.phase is Phase.Corrigee)
        return d
    }

    private val DeroulementPartie.s: EtatSignalement get() = etat.value!!.signalement

    private fun envoyes() = transport.requetesVers(Route.Signalement).map { Json.parseToJsonElement(it.corps!!).jsonObject }

    private fun TestScope.pret(motif: MotifSignalement = MotifSignalement.ReponseFausse): DeroulementPartie {
        val d = apresCorrection()
        d.ouvrirSignalement()
        d.choisirMotif(motif)
        return d
    }

    @Test
    fun `la boite s'ouvre sur la correction, vide, et l'envoi attend un motif`() = runTest {
        val d = apresCorrection()
        assertEquals(EtatSignalement(), d.s)
        d.ouvrirSignalement()
        assertTrue(d.s.ouvert)
        assertNull(d.s.motif)
        assertFalse(d.s.actif)
        d.signaler()
        runCurrent()
        assertTrue(envoyes().isEmpty())
        d.choisirMotif(MotifSignalement.EnonceAmbigu)
        assertTrue(d.s.actif)
        d.fermerSignalement()
        assertFalse(d.s.ouvert)
        assertTrue(envoyes().isEmpty())
    }

    @Test
    fun `pas de signalement avant la correction`() = runTest {
        transport.repondre(Route.CreerPartie, 200, partieCreee(20_000))
        val d = deroulement()
        d.jouer()
        runCurrent()
        d.ouvrirSignalement()
        assertFalse(d.s.ouvert)
        d.choisirMotif(MotifSignalement.Autre)
        d.signaler()
        runCurrent()
        assertTrue(envoyes().isEmpty())
    }

    @Test
    fun `envoi de la partie, du numero, du motif et du commentaire passe par la regle`() = runTest {
        val d = pret(MotifSignalement.ExplicationFausse)
        d.saisirCommentaire("  Date\r\nfausse\u0000.  ")
        d.signaler()
        runCurrent()
        val corps = envoyes().single()
        assertEquals(setOf("partie", "numero", "motif", "commentaire"), corps.keys)
        assertEquals(TransportFactice.ID_PARTIE, corps["partie"]!!.jsonPrimitive.content)
        assertEquals(1, corps["numero"]!!.jsonPrimitive.content.toInt())
        assertEquals("explication_fausse", corps["motif"]!!.jsonPrimitive.content)
        assertEquals("Date\nfausse.", corps["commentaire"]!!.jsonPrimitive.content)
    }

    @Test
    fun `succes, la boite se ferme, le signalement est fait et ne repart pas`() = runTest {
        val d = pret()
        d.signaler()
        runCurrent()
        assertTrue(d.s.envoye)
        assertFalse(d.s.ouvert)
        assertFalse(d.s.envoi)
        assertNull(d.s.erreur)
        assertEquals(setOf("partie", "numero", "motif"), envoyes().single().keys)
        d.ouvrirSignalement()
        d.signaler()
        runCurrent()
        assertFalse(d.s.ouvert)
        assertEquals(1, envoyes().size)
    }

    @Test
    fun `un seul envoi tant que le premier n'est pas revenu`() = runTest {
        val d = pret()
        val attente = transport.retenir(Route.Signalement)
        d.signaler()
        runCurrent()
        assertTrue(d.s.envoi)
        assertFalse(d.s.actif)
        d.signaler()
        d.choisirMotif(MotifSignalement.Autre)
        d.saisirCommentaire("Autre")
        runCurrent()
        assertEquals(1, envoyes().size)
        assertEquals(MotifSignalement.ReponseFausse, d.s.motif)
        assertEquals("", d.s.commentaire)
        attente.complete(ReponseHttp(200, "{}"))
        runCurrent()
        assertTrue(d.s.envoye)
    }

    @Test
    fun `commentaire de plus de 200 caracteres, l'envoi est bloque, la saisie jamais coupee`() = runTest {
        val d = pret()
        d.saisirCommentaire("a".repeat(201))
        assertEquals(201, d.s.commentaire.length)
        assertTrue(d.s.tropLong)
        assertFalse(d.s.actif)
        d.signaler()
        runCurrent()
        assertTrue(envoyes().isEmpty())
        d.saisirCommentaire("a".repeat(200) + "\u0000")
        assertFalse(d.s.tropLong)
        assertTrue(d.s.actif)
        // La longueur compte en points de code, comme le serveur.
        d.saisirCommentaire("😀".repeat(200))
        assertTrue(d.s.actif)
        d.saisirCommentaire("😀".repeat(201))
        assertFalse(d.s.actif)
    }

    @Test
    fun `trop de signalements, l'erreur sous le champ, motif et commentaire restent, on peut renvoyer`() = runTest {
        val d = pret(MotifSignalement.Autre)
        d.saisirCommentaire("Un détail.")
        transport.repondre(Route.Signalement, 409, erreur("signalement_trop_frequent", 409))
        d.signaler()
        runCurrent()
        assertEquals(TexteErreur.SignalementTropFrequent, d.s.erreur)
        assertTrue(d.s.ouvert)
        assertFalse(d.s.envoi)
        assertFalse(d.s.envoye)
        assertEquals(MotifSignalement.Autre, d.s.motif)
        assertEquals("Un détail.", d.s.commentaire)
        assertTrue(sorties.none { it !is SortiePartie.Ouverte })
        // Toucher au motif ou au texte efface l'erreur ; la partie continue.
        d.saisirCommentaire("Un détail de plus.")
        assertNull(d.s.erreur)
        transport.repondre(Route.Signalement, 200, "{}")
        d.signaler()
        runCurrent()
        assertTrue(d.s.envoye)
        assertEquals(2, envoyes().size)
    }

    @Test
    fun `reseau, serveur et refus inconnus, sous le champ, sans quitter la partie`() = runTest {
        val d = pret()
        transport.echouer(Route.Signalement, connexionEtablie = false)
        d.signaler()
        runCurrent()
        assertEquals(TexteErreur.ReseauEnHaut, d.s.erreur)
        transport.echouer(Route.Signalement, connexionEtablie = true)
        d.signaler()
        runCurrent()
        assertEquals(TexteErreur.ServeurEnHaut, d.s.erreur)
        transport.repondre(Route.Signalement, 429, """{"status":429,"message":"Too Many Requests.","data":{}}""")
        d.signaler()
        runCurrent()
        assertEquals(TexteErreur.Generique, d.s.erreur)
        transport.repondre(Route.Signalement, 404, erreur("partie_introuvable", 404))
        d.signaler()
        runCurrent()
        assertEquals(TexteErreur.Generique, d.s.erreur)
        assertTrue(d.s.ouvert)
        assertEquals(listOf<SortiePartie>(SortiePartie.Ouverte), sorties)
        assertTrue(d.etat.value!!.phase is Phase.Corrigee)
    }

    @Test
    fun `session expiree, session effacee, Bienvenue`() = runTest {
        transport.repondre(Route.Connexion, 200, CONNEXION)
        depot.connecter("Hibou_42", "nuit-blanche-42")
        val d = pret()
        transport.repondre(Route.Signalement, 401, """{"status":401,"message":"…","data":{}}""")
        d.signaler()
        runCurrent()
        assertEquals(SortiePartie.SessionExpiree, sorties.last())
        assertNull(d.etat.value)
    }

    @Test
    fun `la question suivante repart sans signalement ni boite`() = runTest {
        val d = pret()
        d.saisirCommentaire("Brouillon")
        d.signaler()
        runCurrent()
        assertTrue(d.s.envoye)
        transport.repondre(Route.Question, 200, questionServie(2))
        d.continuer()
        runCurrent()
        assertEquals(2, d.etat.value!!.question.numero)
        assertEquals(EtatSignalement(), d.s)
    }

    @Test
    fun `une reponse qui arrive apres la question suivante est ignoree`() = runTest {
        val d = pret()
        val attente = transport.retenir(Route.Signalement)
        d.signaler()
        runCurrent()
        transport.repondre(Route.Question, 200, questionServie(2))
        d.continuer()
        runCurrent()
        attente.complete(ReponseHttp(200, "{}"))
        runCurrent()
        assertEquals(2, d.etat.value!!.question.numero)
        assertEquals(EtatSignalement(), d.s)
    }
}

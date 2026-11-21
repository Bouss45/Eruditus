package com.eruditus.android.ui.ecrans

import com.eruditus.android.R
import com.eruditus.android.TextesRessources
import com.eruditus.android.compte.Affichage
import com.eruditus.android.compte.EcranErreur
import com.eruditus.android.compte.TexteErreur
import com.eruditus.android.compte.affichage
import com.eruditus.android.donnees.api.ErreurApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Envoyer un avis (D85, D93, spec/ecrans.md § 12.3) : bouton, compteur, boîte d'abandon, et le
 * texte toujours gardé après une erreur.
 */
class FormulaireAvisTest {

    @Test
    fun `vide a l'ouverture, bouton inactif, retour direct`() {
        val etat = EtatAvis()
        assertEquals("", etat.texte)
        assertEquals(0, etat.longueur)
        assertFalse(etat.actif)
        assertFalse(etat.tropLong)
        assertFalse(etat.confirmerRetour)
        assertNull(etat.erreur)
    }

    @Test
    fun `compteur et bouton suivent le texte tel qu'il partira`() {
        val etat = EtatAvis()
        assertEquals(7, etat.saisir("  Bonjour \n").longueur)
        assertTrue(etat.saisir("  Bonjour \n").actif)
        assertEquals("Ligne 1\nLigne 2", etat.saisir("Ligne 1\r\nLigne 2\t").envoye)
        assertFalse(etat.saisir(" \n\t ").actif)
        assertEquals(0, etat.saisir(" \n\t ").longueur)
        assertTrue(etat.saisir("😀".repeat(1000)).actif)
        assertEquals(1000, etat.saisir("😀".repeat(1000)).longueur)
    }

    @Test
    fun `au-dela de 1000, rien n'est coupe, compteur trop long, bouton inactif`() {
        val colle = "a".repeat(1042)
        val etat = EtatAvis().saisir(colle)
        assertEquals(colle, etat.texte)
        assertEquals(1042, etat.longueur)
        assertTrue(etat.tropLong)
        assertFalse(etat.actif)
        assertTrue(EtatAvis().saisir("a".repeat(1000)).let { it.actif && !it.tropLong })
    }

    @Test
    fun `retour avec du texte, la boite, envoi en cours compris`() {
        assertTrue(EtatAvis().saisir(" ").confirmerRetour)
        assertTrue(EtatAvis().saisir("Une idée").envoyer().confirmerRetour)
        assertFalse(EtatAvis().saisir("Une idée").saisir("").confirmerRetour)
    }

    @Test
    fun `envoi, bouton inactif, erreurs et message effaces`() {
        val etat = EtatAvis(texte = "Une idée", erreurServeur = TexteErreur.AvisTropRecent, message = TexteErreur.TropDeTentatives)
        val envoi = etat.envoyer()
        assertTrue(envoi.envoi)
        assertFalse(envoi.actif)
        assertNull(envoi.erreur)
        assertNull(envoi.message)
        assertEquals("Une idée", envoi.texte)
    }

    @Test
    fun `chaque erreur a sa place, le texte reste toujours`() {
        val texte = "Mon avis, long à écrire."
        val envoi = EtatAvis().saisir(texte).envoyer()
        val cas = listOf(
            ErreurApi.Refus(400, "avis_invalide") to (TexteErreur.AvisInvalide to null),
            ErreurApi.Refus(409, "avis_trop_recent") to (TexteErreur.AvisTropRecent to null),
            ErreurApi.Refus(503, "avis_indisponible") to (null to TexteErreur.AvisIndisponible),
            ErreurApi.TropDeTentatives to (null to TexteErreur.TropDeTentatives),
            ErreurApi.Refus(400, "requete_invalide") to (null to TexteErreur.Generique),
            ErreurApi.Reseau to (null to TexteErreur.ReseauEnHaut),
            ErreurApi.Serveur to (null to TexteErreur.ServeurEnHaut),
        )
        for ((erreur, attendu) in cas) {
            val apres = envoi.apresEchec(affichage(erreur, EcranErreur.Avis))
            assertEquals("$erreur", texte, apres.texte)
            assertFalse("$erreur", apres.envoi)
            assertEquals("$erreur", attendu.first, apres.erreur)
            assertEquals("$erreur", attendu.second, apres.message)
            assertTrue("$erreur", apres.actif)
        }
        assertEquals(Affichage.SessionExpiree, affichage(ErreurApi.NonAutorise, EcranErreur.Avis))
    }

    @Test
    fun `modifier le texte efface l'erreur du serveur, garde le message du haut`() {
        val etat = EtatAvis(texte = "Une idée", erreurServeur = TexteErreur.AvisTropRecent, message = TexteErreur.AvisIndisponible)
        val apres = etat.saisir("Une idée !")
        assertNull(apres.erreur)
        assertEquals(TexteErreur.AvisIndisponible, apres.message)
    }

    @Test
    fun `boite d'abandon ouverte puis fermee, texte intact`() {
        val etat = EtatAvis().saisir("Une idée")
        assertTrue(etat.copy(abandon = true).abandon)
        assertEquals(etat, etat.copy(abandon = true).copy(abandon = false))
    }

    @Test
    fun `le texte n'apparait jamais quand on affiche l'etat`() {
        val etat = EtatAvis().saisir("Un secret dans mon avis")
        assertFalse(etat.toString(), "secret" in etat.toString())
    }

    @Test
    fun `compteur et sa lecture par TalkBack, textes de la spec`() {
        val textes = TextesRessources()
        assertEquals("0 / 1000", textes.texte(R.string.ds_compteur, 0, 1000))
        assertEquals("12 sur 1000 caractères", textes.texte(R.string.ds_compteur_description, 12, 1000))
        assertEquals("1012 sur 1000 caractères, trop long", textes.texte(R.string.ds_compteur_trop_long, 1012, 1000))
        assertEquals("Merci, ton avis a été envoyé.", textes.texte(R.string.parametres_avis_envoye))
    }
}

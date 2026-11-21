package com.eruditus.android.ui.ecrans

import com.eruditus.android.compte.TexteErreur
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Formulaires de Paramètres (D83, spec/ecrans.md § 12.1, § 12.2) : quand le bouton est actif, et
 * quelle erreur s'affiche pendant la saisie, avec les mêmes règles qu'à la création (D55).
 */
class FormulairesCompteTest {

    // --- Changer de pseudo ------------------------------------------------------------------

    @Test
    fun `pseudo prerempli avec l'actuel, bouton inactif tant qu'il ne change pas`() {
        val etat = EtatChangementPseudo("Hibou_42")
        assertEquals("Hibou_42", etat.pseudo)
        assertFalse(etat.actif)
        assertFalse(etat.copy(pseudo = "  Hibou_42 ").actif)
        assertNull(etat.erreurPseudo)
    }

    @Test
    fun `nouveau pseudo valide, bouton actif, la casse seule compte comme un changement`() {
        val etat = EtatChangementPseudo("Hibou_42")
        assertTrue(etat.copy(pseudo = "Hibou_43").actif)
        assertTrue(etat.copy(pseudo = "hibou_42").actif)
        assertTrue(etat.copy(pseudo = " Chouette ").actif)
        assertFalse(etat.copy(pseudo = "Hibou_43", envoi = true).actif)
    }

    @Test
    fun `pendant la saisie, les regles et les textes de la creation`() {
        val etat = EtatChangementPseudo("Hibou_42")
        for (pseudo in listOf("", "Hi", "Hi_")) {
            assertNull(pseudo, etat.copy(pseudo = pseudo).erreurPseudo)
            assertEquals(pseudo, erreurPseudoSaisi(pseudo), etat.copy(pseudo = pseudo).erreurPseudo)
        }
        for (pseudo in listOf("Hibou-42", "héron", "a".repeat(21), "Hibou 42")) {
            assertEquals(pseudo, TexteErreur.PseudoInvalide, etat.copy(pseudo = pseudo).erreurPseudo)
            assertEquals(pseudo, EtatCreation(pseudo = pseudo).erreurPseudo, etat.copy(pseudo = pseudo).erreurPseudo)
            assertFalse(pseudo, etat.copy(pseudo = pseudo).actif)
        }
        assertFalse(etat.copy(pseudo = "Hi").actif)
    }

    @Test
    fun `erreur du serveur avant celle de la saisie`() {
        val etat = EtatChangementPseudo("Hibou_42", pseudo = "Chouette", erreurServeur = TexteErreur.PseudoTropRecent)
        assertEquals(TexteErreur.PseudoTropRecent, etat.erreurPseudo)
        assertEquals(TexteErreur.PseudoPris, etat.copy(erreurServeur = TexteErreur.PseudoPris).erreurPseudo)
    }

    // --- Changer de mot de passe -----------------------------------------------------------------

    @Test
    fun `mot de passe, actif quand l'actuel est rempli et le nouveau respecte la regle`() {
        val etat = EtatChangementMotDePasse()
        assertFalse(etat.actif)
        assertFalse(etat.copy(actuel = "nuit-blanche-42").actif)
        assertFalse(etat.copy(nouveau = "lune-glacee-17").actif)
        assertTrue(etat.copy(actuel = "x", nouveau = "lune-glacee-17").actif)
        assertTrue(etat.copy(actuel = "x", nouveau = "é".repeat(36)).actif)
        assertFalse(etat.copy(actuel = "x", nouveau = "é".repeat(37)).actif)
        assertFalse(etat.copy(actuel = "x", nouveau = "court").actif)
        assertFalse(etat.copy(actuel = "x", nouveau = "lune-glacee-17", envoi = true).actif)
    }

    @Test
    fun `nouveau mot de passe verifie pendant la saisie comme a la creation`() {
        val etat = EtatChangementMotDePasse(actuel = "nuit-blanche-42")
        assertNull(etat.copy(nouveau = "court").erreurNouveau)
        assertEquals(TexteErreur.MotDePasseInvalide, etat.copy(nouveau = "a".repeat(72)).erreurNouveau)
        assertEquals(TexteErreur.MotDePasseInvalide, etat.copy(nouveau = "é".repeat(37)).erreurNouveau)
        for (nouveau in listOf("court", "a".repeat(72), "lune-glacee-17")) {
            assertEquals(nouveau, EtatCreation(motDePasse = nouveau).erreurMotDePasse, etat.copy(nouveau = nouveau).erreurNouveau)
        }
        assertEquals(
            TexteErreur.MotDePasseInvalide,
            etat.copy(nouveau = "lune-glacee-17", erreurNouveauServeur = TexteErreur.MotDePasseInvalide).erreurNouveau,
        )
    }

    @Test
    fun `aucun mot de passe quand on affiche l'etat`() {
        val texte = EtatChangementMotDePasse(actuel = "secret-actuel", nouveau = "secret-nouveau").toString()
        assertFalse(texte, "secret" in texte)
    }
}

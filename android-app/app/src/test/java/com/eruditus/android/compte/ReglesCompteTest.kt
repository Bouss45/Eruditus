package com.eruditus.android.compte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pseudo (D9) et mot de passe (D10) : l'app applique exactement les règles du serveur avant l'envoi
 * (spec/regles.md § 1.1, § 1.2), et sait si une saisie peut encore devenir valide (D55).
 */
class ReglesCompteTest {

    @Test
    fun `pseudo de 3 a 20 caracteres parmi lettres, chiffres et _`() {
        assertTrue(ReglesPseudo.valide("abc"))
        assertTrue(ReglesPseudo.valide("Hibou_42"))
        assertTrue(ReglesPseudo.valide("a".repeat(20)))
        assertFalse(ReglesPseudo.valide("ab"))
        assertFalse(ReglesPseudo.valide("a".repeat(21)))
        assertFalse(ReglesPseudo.valide(""))
    }

    @Test
    fun `pseudo sans espace, accent, tiret ni emoji`() {
        for (refuse in listOf("hibou 42", "héron", "hibou-42", "hibou😀", "hibou.42", "ｈｉｂｏｕ")) {
            assertFalse(refuse, ReglesPseudo.valide(refuse))
        }
    }

    @Test
    fun `espaces au debut et a la fin retires, jamais au milieu`() {
        assertEquals("Hibou_42", ReglesPseudo.nettoyer("  Hibou_42 \t"))
        assertTrue(ReglesPseudo.valide(ReglesPseudo.nettoyer(" Hibou_42 ")))
        assertEquals(EtatSaisie.Valide, ReglesPseudo.etat(" Hibou_42 "))
        assertEquals(EtatSaisie.Invalide, ReglesPseudo.etat(" Hibou 42 "))
    }

    @Test
    fun `pseudo trop court mais encore possible, ou impossible`() {
        assertEquals(EtatSaisie.TropCourt, ReglesPseudo.etat(""))
        assertEquals(EtatSaisie.TropCourt, ReglesPseudo.etat("ab"))
        assertEquals(EtatSaisie.TropCourt, ReglesPseudo.etat("   "))
        assertEquals(EtatSaisie.Invalide, ReglesPseudo.etat("a-"))
        assertEquals(EtatSaisie.Invalide, ReglesPseudo.etat("a".repeat(21)))
        assertEquals(EtatSaisie.Valide, ReglesPseudo.etat("abc"))
    }

    @Test
    fun `mot de passe de 8 a 71 caracteres`() {
        assertEquals(EtatSaisie.TropCourt, ReglesMotDePasse.etat("a".repeat(7)))
        assertEquals(EtatSaisie.Valide, ReglesMotDePasse.etat("a".repeat(8)))
        assertEquals(EtatSaisie.Valide, ReglesMotDePasse.etat("a".repeat(71)))
        assertEquals(EtatSaisie.Invalide, ReglesMotDePasse.etat("a".repeat(72)))
        assertEquals(EtatSaisie.TropCourt, ReglesMotDePasse.etat(""))
    }

    @Test
    fun `72 octets au plus en UTF-8, comme bcrypt`() {
        // Les cas des tests serveur (spec/regles.md § 1.2) : 36 « é » font 72 octets, 37 en font 74.
        assertTrue(ReglesMotDePasse.valide("é".repeat(36)))
        assertFalse(ReglesMotDePasse.valide("é".repeat(37)))
        assertEquals(EtatSaisie.Invalide, ReglesMotDePasse.etat("é".repeat(37)))
        // Un emoji compte pour 1 caractère et 4 octets.
        assertEquals(1, ReglesMotDePasse.caracteres("😀"))
        assertEquals(4, ReglesMotDePasse.octets("😀"))
        assertTrue(ReglesMotDePasse.valide("😀".repeat(18)))
        assertFalse(ReglesMotDePasse.valide("😀".repeat(19)))
        assertTrue(ReglesMotDePasse.valide("😀".repeat(8)))
        assertEquals(EtatSaisie.TropCourt, ReglesMotDePasse.etat("é".repeat(7)))
    }

    @Test
    fun `caracteres et octets comptes comme le serveur, surrogate isole compris`() {
        // Le serveur compte les points de code (http.js, nombreDeCaracteres) ; une moitié d'emoji
        // isolée compte pour 1 caractère et devient U+FFFD, 3 octets.
        val isole = "\uD83D"
        assertEquals(1, ReglesMotDePasse.caracteres(isole))
        assertEquals(3, ReglesMotDePasse.octets(isole))
        assertEquals(2, ReglesMotDePasse.octets("é"))
        assertEquals(3, ReglesMotDePasse.octets("€"))
    }

    @Test
    fun `le mot de passe n'est jamais nettoye`() {
        assertTrue(ReglesMotDePasse.valide(" ".repeat(8)))
        // 8 caractères avec ses deux espaces : valide ; sans eux, il serait trop court.
        assertEquals(EtatSaisie.Valide, ReglesMotDePasse.etat(" abcdef "))
        assertEquals(EtatSaisie.TropCourt, ReglesMotDePasse.etat("abcdef"))
    }
}

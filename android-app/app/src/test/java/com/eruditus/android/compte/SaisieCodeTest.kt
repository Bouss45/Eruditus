package com.eruditus.android.compte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Code de récupération (spec/regles.md § 1.6) : saisie tolérante, puis exactement la règle de
 * format du serveur (D55). Les constantes sont confrontées aux hooks par `ReglesServeurTest`.
 */
class SaisieCodeTest {

    private val code = "7K3MQ9XA2HDEF8RT"

    @Test
    fun `minuscules, espaces et tirets acceptes`() {
        assertEquals(code, SaisieCode.normaliser("7K3M-Q9XA-2HDE-F8RT"))
        assertEquals(code, SaisieCode.normaliser("7k3m q9xa 2hde f8rt"))
        assertEquals(code, SaisieCode.normaliser("  7k3m-q9xa 2HDE-f8rt  "))
        assertEquals(code, SaisieCode.normaliser("7K3M\tQ9XA 2HDE\nF8RT"))
        assertEquals(code, SaisieCode.normaliser("7-K-3-M-Q-9-X-A-2-H-D-E-F-8-R-T"))
    }

    @Test
    fun `O lu 0, I et L lus 1`() {
        assertEquals("0000111100001111", SaisieCode.normaliser("OOOO-IIII-oooo-llll"))
        assertEquals("7K3MQ9XA2HDEF8R0", SaisieCode.normaliser("7K3M-Q9XA-2HDE-F8RO"))
    }

    @Test
    fun `symbole hors de l'alphabet refuse`() {
        assertNull(SaisieCode.normaliser("7K3M-Q9XA-2HDE-F8RU"))
        assertNull(SaisieCode.normaliser("7K3M-Q9XA-2HDE-F8R*"))
        assertNull(SaisieCode.normaliser("7K3M_Q9XA_2HDE_F8RT"))
        assertNull(SaisieCode.normaliser("7K3M–Q9XA–2HDE–F8RT")) // tiret demi-cadratin : le serveur ne l'ignore pas
    }

    @Test
    fun `16 symboles exactement`() {
        assertNull(SaisieCode.normaliser("7K3M-Q9XA-2HDE-F8R"))
        assertNull(SaisieCode.normaliser("7K3M-Q9XA-2HDE-F8RTT"))
        assertNull(SaisieCode.normaliser(""))
    }

    @Test
    fun `64 caracteres saisis au plus, espaces compris`() {
        val avecEspaces64 = code + " ".repeat(48)
        assertEquals(64, avecEspaces64.length)
        assertEquals(code, SaisieCode.normaliser(avecEspaces64))
        assertNull(SaisieCode.normaliser("$avecEspaces64 "))
    }

    @Test
    fun `format affiche et copie, 4 groupes de 4`() {
        assertEquals("7K3M-Q9XA-2HDE-F8RT", SaisieCode.formater(code))
    }
}

package com.eruditus.android.ui.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Chrono de la partie (DESIGN.md § 6 et § 7, spec/ecrans.md § 9) : 20 s calées sur restantMs. */
class ReglesChronoTest {

    @Test
    fun `les secondes affichees sont arrondies au-dessus, de 20 a 0`() {
        assertEquals(20, ReglesChrono.secondesAffichees(20_000))
        assertEquals(20, ReglesChrono.secondesAffichees(19_001))
        assertEquals(19, ReglesChrono.secondesAffichees(19_000))
        assertEquals(14, ReglesChrono.secondesAffichees(13_200))
        assertEquals(1, ReglesChrono.secondesAffichees(1))
        assertEquals(0, ReglesChrono.secondesAffichees(0))
        assertEquals(0, ReglesChrono.secondesAffichees(-250))
    }

    @Test
    fun `la barre suit le temps restant, bornee entre 0 et 1`() {
        assertEquals(1f, ReglesChrono.fraction(20_000, 20_000, paliers = false))
        assertEquals(0.66f, ReglesChrono.fraction(13_200, 20_000, paliers = false), 1e-6f)
        assertEquals(0f, ReglesChrono.fraction(-10, 20_000, paliers = false))
        assertEquals(1f, ReglesChrono.fraction(25_000, 20_000, paliers = false))
        assertEquals(0f, ReglesChrono.fraction(5_000, 0, paliers = false))
    }

    @Test
    fun `animations desactivees, la barre avance par paliers d'une seconde`() {
        assertEquals(0.7f, ReglesChrono.fraction(13_200, 20_000, paliers = true), 1e-6f)
        assertEquals(0.7f, ReglesChrono.fraction(14_000, 20_000, paliers = true), 1e-6f)
        assertEquals(0.05f, ReglesChrono.fraction(1, 20_000, paliers = true), 1e-6f)
        assertEquals(0f, ReglesChrono.fraction(0, 20_000, paliers = true))
    }

    @Test
    fun `TalkBack annonce le temps a 10 s et a 5 s, une fois chacun`() {
        assertNull(ReglesChrono.annonce(precedentMs = null, restantMs = 10_000))
        assertNull(ReglesChrono.annonce(precedentMs = 11_000, restantMs = 10_001))
        assertEquals(10, ReglesChrono.annonce(precedentMs = 10_001, restantMs = 10_000))
        assertNull(ReglesChrono.annonce(precedentMs = 10_000, restantMs = 9_000))
        assertEquals(5, ReglesChrono.annonce(precedentMs = 5_400, restantMs = 4_900))
        assertNull(ReglesChrono.annonce(precedentMs = 4_900, restantMs = 4_000))
        // Un saut par-dessus les deux seuils (appli en pause) n'annonce que le plus proche.
        assertEquals(5, ReglesChrono.annonce(precedentMs = 12_000, restantMs = 3_000))
        // Nouvelle question : le temps remonte, rien à annoncer.
        assertNull(ReglesChrono.annonce(precedentMs = 2_000, restantMs = 20_000))
    }
}

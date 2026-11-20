package com.eruditus.android.ui.designsystem.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `RangeeBoutons` (DESIGN.md § 7) : parts égales, écart 12 ; l'un sous l'autre dès qu'un libellé
 * ne tient plus dans sa part sans couper un mot (largeur minimale = mot le plus long + marges).
 */
class RangeeBoutonsTest {

    @Test
    fun `deux boutons qui tiennent chacun dans sa moitie restent cote a cote`() {
        // 350 de large, écart 12 : 169 par bouton.
        assertTrue(rangeeEnLigne(listOf(120, 169), largeurDisponible = 350, ecart = 12))
    }

    @Test
    fun `un mot trop long pour sa part fait passer a la ligne`() {
        assertFalse(rangeeEnLigne(listOf(120, 170), largeurDisponible = 350, ecart = 12))
    }

    @Test
    fun `un seul bouton est toujours en ligne`() {
        assertTrue(rangeeEnLigne(listOf(500), largeurDisponible = 350, ecart = 12))
        assertTrue(rangeeEnLigne(emptyList(), largeurDisponible = 350, ecart = 12))
    }

    @Test
    fun `trois boutons se partagent la largeur moins deux ecarts`() {
        // (350 − 24) / 3 = 108,67.
        assertTrue(rangeeEnLigne(listOf(108, 108, 108), largeurDisponible = 350, ecart = 12))
        assertFalse(rangeeEnLigne(listOf(108, 109, 108), largeurDisponible = 350, ecart = 12))
    }
}

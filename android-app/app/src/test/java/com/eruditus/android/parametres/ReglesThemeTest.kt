package com.eruditus.android.parametres

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le thème comme mode nuit de l'app (D77) : ce qu'on donne à Android 12 et plus
 * (`UiModeManager.setApplicationNightMode`) et le `uiMode` imposé à l'activité avant.
 * Valeurs d'Android : `MODE_NIGHT_AUTO` 0, `MODE_NIGHT_NO` 1, `MODE_NIGHT_YES` 2 ;
 * `UI_MODE_NIGHT_MASK` 0x30, `UI_MODE_NIGHT_NO` 0x10, `UI_MODE_NIGHT_YES` 0x20.
 */
class ReglesThemeTest {

    /** `uiMode` d'un téléphone : type normal (1), en clair ou en sombre. */
    private val telephoneClair = 0x11
    private val telephoneSombre = 0x21

    @Test
    fun `mode nuit de l'app, Android 12 et plus`() {
        assertEquals(0, ReglesTheme.modeNuit(Theme.Systeme))
        assertEquals(2, ReglesTheme.modeNuit(Theme.Sombre))
        assertEquals(1, ReglesTheme.modeNuit(Theme.Clair))
    }

    @Test
    fun `Systeme ne change rien au telephone`() {
        assertNull(ReglesTheme.uiModeImpose(telephoneClair, Theme.Systeme))
        assertNull(ReglesTheme.uiModeImpose(telephoneSombre, Theme.Systeme))
        assertEquals(telephoneClair, ReglesTheme.uiMode(telephoneClair, Theme.Systeme))
        assertEquals(telephoneSombre, ReglesTheme.uiMode(telephoneSombre, Theme.Systeme))
    }

    @Test
    fun `Sombre et Clair imposent la nuit, le reste du uiMode garde`() {
        assertEquals(0x21, ReglesTheme.uiModeImpose(telephoneClair, Theme.Sombre))
        assertEquals(0x21, ReglesTheme.uiModeImpose(telephoneSombre, Theme.Sombre))
        assertEquals(0x11, ReglesTheme.uiModeImpose(telephoneSombre, Theme.Clair))
        assertEquals(0x11, ReglesTheme.uiModeImpose(telephoneClair, Theme.Clair))
        // Type de l'appareil (voiture : 3) gardé ; nuit non définie remplacée.
        assertEquals(0x23, ReglesTheme.uiModeImpose(0x13, Theme.Sombre))
        assertEquals(0x14, ReglesTheme.uiModeImpose(0x04, Theme.Clair))
    }

    @Test
    fun `sombre lu dans le uiMode`() {
        assertTrue(ReglesTheme.sombre(telephoneSombre))
        assertFalse(ReglesTheme.sombre(telephoneClair))
        assertTrue(ReglesTheme.sombre(ReglesTheme.uiMode(telephoneClair, Theme.Sombre)))
        assertFalse(ReglesTheme.sombre(ReglesTheme.uiMode(telephoneSombre, Theme.Clair)))
    }
}

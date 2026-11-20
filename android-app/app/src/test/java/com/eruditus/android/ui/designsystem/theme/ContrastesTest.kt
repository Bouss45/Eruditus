package com.eruditus.android.ui.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Contrastes de DESIGN.md § 3, recalculés depuis les tokens (formule WCAG 2.x). Chaque paire du
 * tableau, dans l'ordre : valeur annoncée en sombre et en clair (au dixième), seuil minimal.
 */
class ContrastesTest {

    private class Paire(
        val nom: String,
        val sombre: Double,
        val clair: Double,
        /** Null : paire acceptée sous 3:1, documentée dans DESIGN.md (notes ² et ³). */
        val seuil: Double?,
        val premierPlan: (CouleursEruditus) -> Color,
        val fond: (CouleursEruditus) -> Color,
    )

    private val paires = listOf(
        Paire("texte sur fond", 16.7, 16.3, TEXTE, { it.texte }, { it.fond }),
        Paire("texte sur surface", 15.4, 17.7, TEXTE, { it.texte }, { it.surface }),
        Paire("texte sur surface-haute", 13.9, 17.7, TEXTE, { it.texte }, { it.surfaceHaute }),
        Paire("texte-secondaire sur fond", 8.6, 6.0, TEXTE, { it.texteSecondaire }, { it.fond }),
        Paire("texte-secondaire sur surface", 8.0, 6.5, TEXTE, { it.texteSecondaire }, { it.surface }),
        Paire("texte-secondaire sur piste", 6.4, 4.8, TEXTE, { it.texteSecondaire }, { it.piste }),
        Paire("accent sur fond", 11.6, 5.6, TEXTE, { it.accent }, { it.fond }),
        Paire("accent sur accent-teinte", 9.1, 5.2, TEXTE, { it.accent }, { it.accentTeinte }),
        Paire("sur-accent sur accent", 11.6, 6.0, TEXTE, { it.surAccent }, { it.accent }),
        Paire("vert sur fond", 10.5, 5.2, TEXTE, { it.vert }, { it.fond }),
        Paire("sur-vert sur vert", 9.5, 5.6, TEXTE, { it.surVert }, { it.vert }),
        Paire("lettre sur pastille-vert", 6.7, 6.8, TEXTE, { it.surVert }, { it.pastilleVert.sur(it.vert) }),
        Paire("rouge sur fond", 6.9, 5.6, TEXTE, { it.rouge }, { it.fond }),
        Paire("rouge sur rouge-conteneur", 5.7, 5.3, TEXTE, { it.rouge }, { it.rougeConteneur }),
        Paire("sur-rouge-conteneur sur rouge-conteneur", 12.7, 9.2, TEXTE, { it.surRougeConteneur }, { it.rougeConteneur }),
        Paire("texte sur piste (lettre)", 12.3, 13.2, TEXTE, { it.texte }, { it.piste }),
        Paire("sur-rouge sur rouge (lettre)", 5.7, 6.1, TEXTE, { it.surRouge }, { it.rouge }),
        Paire("texte sur accent-teinte", 13.0, 15.4, TEXTE, { it.texte }, { it.accentTeinte }),
        Paire("texte-secondaire sur accent-teinte", 6.7, 5.6, TEXTE, { it.texteSecondaire }, { it.accentTeinte }),
        Paire("texte-secondaire sur surface-haute", 7.2, 6.5, TEXTE, { it.texteSecondaire }, { it.surfaceHaute }),
        Paire("rouge sur surface-haute", 5.8, 6.1, TEXTE, { it.rouge }, { it.surfaceHaute }),
        Paire("contour-champ sur surface", 3.3, 3.1, NON_TEXTE, { it.contourChamp }, { it.surface }),
        Paire("accent sur piste", 8.6, 4.5, NON_TEXTE, { it.accent }, { it.piste }),
        Paire("accent sur surface", 10.7, 6.0, NON_TEXTE, { it.accent }, { it.surface }),
        Paire("rouge sur surface", 6.4, 6.1, NON_TEXTE, { it.rouge }, { it.surface }),
        Paire("contour sur surface", 2.0, 1.7, null, { it.contour }, { it.surface }),
        Paire("neutre sur piste", 3.7, 2.7, null, { it.neutre }, { it.piste }),
    )

    @Test
    fun `chaque paire atteint son seuil, en sombre et en clair`() {
        val sousLeSeuil = buildList {
            for (paire in paires) {
                val seuil = paire.seuil ?: continue
                for ((theme, couleurs) in THEMES) {
                    val valeur = contraste(paire.premierPlan(couleurs), paire.fond(couleurs))
                    if (valeur < seuil) add("${paire.nom} ($theme) : %.2f < $seuil".format(valeur))
                }
            }
        }
        assertTrue(sousLeSeuil.joinToString("\n"), sousLeSeuil.isEmpty())
    }

    @Test
    fun `chaque valeur annoncee par DESIGN md est celle des tokens, au dixieme`() {
        for (paire in paires) {
            for ((theme, couleurs) in THEMES) {
                val annoncee = if (theme == "sombre") paire.sombre else paire.clair
                val calculee = contraste(paire.premierPlan(couleurs), paire.fond(couleurs))
                assertEquals("${paire.nom} ($theme), calculé %.3f".format(calculee), annoncee, arrondi(calculee), 1e-9)
            }
        }
    }

    @Test
    fun `la pastille verte de l'export Claude Design serait sous 4,5 en clair`() {
        // DESIGN.md § 3, note ¹ : blanc 18 % donnait 3,9:1, d'où noir 12 %.
        val couleurs = CouleursClaires
        val export = Color.White.copy(alpha = 0.18f).sur(couleurs.vert)
        assertEquals(3.9, arrondi(contraste(couleurs.surVert, export)), 1e-9)
    }

    private companion object {
        const val TEXTE = 4.5
        const val NON_TEXTE = 3.0
        val THEMES = listOf("sombre" to CouleursSombres, "clair" to CouleursClaires)

        fun arrondi(valeur: Double): Double = (valeur * 10).roundToInt() / 10.0

        /** Couleur translucide composée sur un fond opaque, dans l'espace sRGB encodé. */
        fun Color.sur(fond: Color): Color = Color(
            red = alpha * red + (1 - alpha) * fond.red,
            green = alpha * green + (1 - alpha) * fond.green,
            blue = alpha * blue + (1 - alpha) * fond.blue,
        )

        fun lineaire(canal: Float): Double {
            val c = canal.toDouble()
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }

        fun luminance(couleur: Color): Double =
            0.2126 * lineaire(couleur.red) + 0.7152 * lineaire(couleur.green) + 0.0722 * lineaire(couleur.blue)

        fun contraste(a: Color, b: Color): Double {
            assertEquals("contraste calculé sur une couleur opaque", 1f, a.alpha)
            assertEquals("contraste calculé sur un fond opaque", 1f, b.alpha)
            val la = luminance(a)
            val lb = luminance(b)
            return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
        }
    }
}

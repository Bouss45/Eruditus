package com.eruditus.android.ui.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.ResourceFont
import androidx.compose.ui.unit.Density
import com.eruditus.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La typographie suit DESIGN.md § 4, lu dans le fichier : police, taille et interligne en sp,
 * graisse, espacement des lettres en em, fonctions OpenType ; `opsz` réglé sur la taille.
 */
class TypographieDesignTest {

    private val typo = TypographieEruditus()

    private val styles: Map<String, TextStyle> = mapOf(
        "enonce" to typo.enonce,
        "marque" to typo.marque,
        "titre-1" to typo.titre1,
        "titre-barre" to typo.titreBarre,
        "chiffre" to typo.chiffre,
        "code" to typo.code,
        "titre-boite" to typo.titreBoite,
        "choix" to typo.choix,
        "corps" to typo.corps,
        "verdict" to typo.verdict,
        "bouton" to typo.bouton,
        "bouton-grand" to typo.boutonGrand,
        "libelle" to typo.libelle,
        "lettre" to typo.lettre,
        "petit" to typo.petit,
        "etiquette" to typo.etiquette,
    )

    private class Ligne(
        val police: String,
        val taille: Float,
        val interligne: Float,
        val graisse: Int,
        val espacement: Float?,
        val fonctions: List<String>,
    )

    @Test
    fun `chaque style suit le tableau de DESIGN md`() {
        val tableau = tableauTypographique()
        assertEquals("styles du tableau", styles.keys, tableau.keys)
        for ((nom, style) in styles) {
            val ligne = tableau.getValue(nom)
            assertEquals("$nom : taille", ligne.taille, style.fontSize.value)
            assertEquals("$nom : interligne", ligne.interligne, style.lineHeight.value)
            assertTrue("$nom : taille et interligne en sp", style.fontSize.isSp && style.lineHeight.isSp)
            assertEquals("$nom : graisse", ligne.graisse, style.fontWeight?.weight)
            if (ligne.espacement != null) {
                assertTrue("$nom : espacement en em", style.letterSpacing.isEm)
                assertEquals("$nom : espacement", ligne.espacement, style.letterSpacing.value, 1e-6f)
            }
            val reglages = style.fontFeatureSettings.orEmpty()
            for (fonction in ligne.fonctions) {
                assertTrue("$nom : '$fonction' dans « $reglages »", reglages.contains("'$fonction'"))
            }
            val police = police(style, nom)
            val attendue = if (ligne.police == "Newsreader") R.font.newsreader else R.font.inter
            assertEquals("$nom : police ${ligne.police}", attendue, police.resId)
        }
    }

    @Test
    fun `chaque graisse de chaque style regle opsz sur la taille et wght sur la graisse`() {
        val densite = Density(density = 1f, fontScale = 1f)
        for ((nom, style) in styles) {
            for (police in polices(style, nom)) {
                val reglages = police.variationSettings.settings.associateBy { it.axisName }
                val opsz = reglages["opsz"]
                assertNotNull("$nom ${police.weight} : axe opsz", opsz)
                assertEquals("$nom ${police.weight} : opsz = taille", style.fontSize.value, opsz!!.toVariationValue(densite))
                val wght = reglages["wght"]
                assertNotNull("$nom ${police.weight} : axe wght", wght)
                assertEquals("$nom : wght = graisse", police.weight.weight.toFloat(), wght!!.toVariationValue(densite))
            }
        }
    }

    @Test
    fun `les graisses de DESIGN md existent vraiment, sans gras simule`() {
        // Newsreader 400 et 500 ; Inter 400 à 700 (DESIGN.md § 4).
        assertEquals(listOf(400, 500), polices(typo.enonce, "enonce").map { it.weight.weight })
        assertEquals(listOf(400, 500, 600, 700), polices(typo.petit, "petit").map { it.weight.weight })
    }

    @Test
    fun `texte a 200 pour cent, opsz suit la taille agrandie`() {
        val reglage = police(typo.petit, "petit").variationSettings.settings.first { it.axisName == "opsz" }
        assertEquals(26f, reglage.toVariationValue(Density(density = 1f, fontScale = 2f)))
    }

    /** Les polices de la famille d'un style : une [ResourceFont] par graisse, toutes à sa taille. */
    private fun polices(style: TextStyle, nom: String): List<ResourceFont> {
        val famille = style.fontFamily as? FontListFontFamily ?: error("$nom : famille de polices embarquées attendue")
        return famille.fonts.map { it as ResourceFont }
    }

    /** La police de la graisse du style. */
    private fun police(style: TextStyle, nom: String): ResourceFont =
        polices(style, nom).single { it.weight == style.fontWeight }

    /** Lignes « | `nom` | Police | 28 / 36 | 400 (…) | −0,02 em, `tnum` | usage | » de DESIGN.md § 4. */
    private fun tableauTypographique(): Map<String, Ligne> {
        val design = File(System.getProperty("eruditus.depot"), "DESIGN.md").readText()
        val section = design.substringAfter("## 4. Typographie").substringBefore("## 5.")
        val tableau = mutableMapOf<String, Ligne>()
        for (ligne in section.lines().filter { it.startsWith("| `") }) {
            val c = ligne.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
            val nom = c[0].trim('`')
            val (taille, interligne) = c[2].split("/").map { it.trim().toFloat() }
            val graisse = Regex("\\d+").find(c[3])!!.value.toInt()
            val espacement = Regex("([+−-]?\\d+,\\d+) em").find(c[4])?.groupValues?.get(1)
                ?.replace('−', '-')?.replace(',', '.')?.toFloat()
            val fonctions = Regex("`([a-z0-9]{4})`").findAll(c[4]).map { it.groupValues[1] }.toList()
            tableau[nom] = Ligne(c[1], taille, interligne, graisse, espacement, fonctions)
        }
        return tableau
    }
}

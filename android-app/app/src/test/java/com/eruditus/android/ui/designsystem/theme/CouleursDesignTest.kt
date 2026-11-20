package com.eruditus.android.ui.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Les tokens de couleur sont ceux de DESIGN.md § 3, qui fait foi : les couleurs opaques sont lues
 * dans le tableau du fichier, les translucides (décrites en mots) recopiées ici.
 */
class CouleursDesignTest {

    private val tokensOpaques: Map<String, (CouleursEruditus) -> Color> = mapOf(
        "fond" to { it.fond },
        "surface" to { it.surface },
        "surface-haute" to { it.surfaceHaute },
        "contour" to { it.contour },
        "contour-champ" to { it.contourChamp },
        "separateur" to { it.separateur },
        "texte" to { it.texte },
        "texte-secondaire" to { it.texteSecondaire },
        "neutre" to { it.neutre },
        "piste" to { it.piste },
        "accent" to { it.accent },
        "sur-accent" to { it.surAccent },
        "accent-teinte" to { it.accentTeinte },
        "vert" to { it.vert },
        "sur-vert" to { it.surVert },
        "rouge" to { it.rouge },
        "sur-rouge" to { it.surRouge },
        "rouge-conteneur" to { it.rougeConteneur },
        "sur-rouge-conteneur" to { it.surRougeConteneur },
        "desactive-fond" to { it.desactiveFond },
        "desactive-texte" to { it.desactiveTexte },
    )

    @Test
    fun `les couleurs opaques sont celles du tableau de DESIGN md`() {
        val tableau = tableauDesCouleurs()
        assertEquals("tokens opaques du tableau", tokensOpaques.keys, tableau.keys)
        for ((nom, token) in tokensOpaques) {
            val (sombre, clair) = tableau.getValue(nom)
            assertEquals("$nom (sombre)", sombre, hexa(token(CouleursSombres)))
            assertEquals("$nom (clair)", clair, hexa(token(CouleursClaires)))
        }
    }

    @Test
    fun `les couleurs translucides suivent leur description`() {
        val noir = Color.Black
        val blanc = Color.White
        val s = CouleursSombres
        val c = CouleursClaires
        verifie("pastille-vert (sombre) : noir 16 %", noir, 0.16f, s.pastilleVert)
        verifie("pastille-vert (clair) : noir 12 %", noir, 0.12f, c.pastilleVert)
        verifie("voile (sombre) : noir 64 %", noir, 0.64f, s.voile)
        verifie("voile (clair) : #14191C 45 %", Color(0xFF14191C), 0.45f, c.voile)
        verifie("pression (sombre) : texte 10 %", s.texte, 0.10f, s.pression)
        verifie("pression (clair) : texte 8 %", c.texte, 0.08f, c.pression)
        verifie("pression-accent (sombre) : #080A0C 12 %", Color(0xFF080A0C), 0.12f, s.pressionAccent)
        verifie("pression-accent (clair) : blanc 14 %", blanc, 0.14f, c.pressionAccent)
        verifie("ombre-boite (sombre) : noir 50 %", noir, 0.50f, s.ombreBoite)
        verifie("ombre-boite (clair) : #14191C 16 %", Color(0xFF14191C), 0.16f, c.ombreBoite)
    }

    @Test
    fun `chaque theme dit s'il est sombre`() {
        assertTrue(CouleursSombres.sombre)
        assertTrue(!CouleursClaires.sombre)
    }

    private fun verifie(nom: String, base: Color, alpha: Float, token: Color) {
        assertEquals("$nom : teinte", hexa(base), hexa(token.copy(alpha = 1f)))
        // L'opacité est stockée sur 8 bits : 50 % devient 128/255.
        assertEquals("$nom : opacité", alpha, token.alpha, 1f / 255f)
    }

    /** Lignes « | `nom` | rôle | `#SOMBRE` | `#CLAIR` | … » de DESIGN.md § 3. */
    private fun tableauDesCouleurs(): Map<String, Pair<String, String>> {
        val design = File(System.getProperty("eruditus.depot"), "DESIGN.md").readText()
        val section = design.substringAfter("## 3. Couleurs").substringBefore("## 4.")
        val nomRegex = Regex("`(-?[a-z][a-z-]*)`")
        val hexaRegex = Regex("`(#[0-9A-F]{6})`")
        val tableau = mutableMapOf<String, Pair<String, String>>()
        for (ligne in section.lines().filter { it.startsWith("| `") }) {
            val cellules = ligne.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
            if (cellules.size < 4) continue
            val noms = nomRegex.findAll(cellules[0]).map { it.groupValues[1] }.toList()
            val sombres = hexaRegex.findAll(cellules[2]).map { it.groupValues[1] }.toList()
            val clairs = hexaRegex.findAll(cellules[3]).map { it.groupValues[1] }.toList()
            if (noms.isEmpty() || sombres.size != noms.size || clairs.size != noms.size) continue
            noms.forEachIndexed { i, nom ->
                // « `desactive-fond` / `-texte` » : le second nom reprend le début du premier.
                val complet = if (nom.startsWith("-")) noms.first().substringBefore("-") + nom else nom
                tableau[complet] = sombres[i] to clairs[i]
            }
        }
        return tableau
    }

    private fun hexa(couleur: Color): String {
        assertEquals("couleur opaque attendue", 1f, couleur.alpha)
        fun canal(v: Float) = "%02X".format((v * 255 + 0.5f).toInt())
        return "#" + canal(couleur.red) + canal(couleur.green) + canal(couleur.blue)
    }
}

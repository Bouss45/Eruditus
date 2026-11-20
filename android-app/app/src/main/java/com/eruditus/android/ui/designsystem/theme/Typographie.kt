package com.eruditus.android.ui.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.eruditus.android.R

/*
 * Typographie (DESIGN.md § 4) : Newsreader et Inter, polices variables embarquées (`res/font/`,
 * SIL OFL 1.1, D44), jamais téléchargées. Tailles et interlignes en sp : l'échelle non linéaire
 * d'Android 14 les agrandit ensemble à 200 %. Vérifiée par `TypographieDesignTest`.
 */

private enum class Police(val resId: Int, val graisses: List<Int>) {
    Newsreader(R.font.newsreader, listOf(400, 500)),
    Inter(R.font.inter, listOf(400, 500, 600, 700)),
}

/**
 * Une famille par style : chaque graisse de la police, avec `opsz` réglé sur la taille du style
 * (« opsz suit la taille du texte »). Les variantes de graisse d'un composant (détail du verdict en
 * 400, encadré en 500…) restent de vraies graisses, jamais du gras simulé.
 */
private fun famille(police: Police, taille: Int): FontFamily = FontFamily(
    police.graisses.map { graisse ->
        Font(
            resId = police.resId,
            weight = FontWeight(graisse),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(graisse),
                FontVariation.opticalSizing(taille.sp),
            ),
        )
    },
)

private fun style(
    police: Police,
    taille: Int,
    interligne: Int,
    graisse: Int,
    espacement: TextUnit = TextUnit.Unspecified,
    fonctions: String? = null,
    coupure: LineBreak = LineBreak.Unspecified,
) = TextStyle(
    fontFamily = famille(police, taille),
    fontWeight = FontWeight(graisse),
    fontSize = taille.sp,
    lineHeight = interligne.sp,
    letterSpacing = espacement,
    fontFeatureSettings = fonctions,
    lineBreak = coupure,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    // Comme l'interligne CSS des maquettes : l'espace se répartit au-dessus et au-dessous.
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

/*
 * Coupure des lignes, comme les maquettes : titres équilibrés (`text-wrap: balance`), énoncé et
 * textes soignés (`text-wrap: pretty`). L'équilibrage vient d'Android 13 ; avant, coupure simple.
 */
private val Titre = LineBreak.Heading
private val Paragraphe = LineBreak.Paragraph

/** Chiffres tabulaires : rien ne bouge quand une valeur change. */
private const val TABULAIRES = "'tnum'"

/** Code de récupération : tabulaires, I à empattements et zéro barré. */
private const val CODE = "'tnum', 'ss02', 'zero'"

/** Les styles de DESIGN.md § 4. Les graisses d'état (erreur, détail…) sont appliquées par les composants. */
@Immutable
class TypographieEruditus {
    /** Newsreader 28/36 · 400 — énoncé. */
    val enonce = style(Police.Newsreader, 28, 36, 400, coupure = Paragraphe)

    /** Newsreader 52/52 · 500 — « Eruditus ». */
    val marque = style(Police.Newsreader, 52, 52, 500, espacement = (-0.02).em)

    /** Inter 26/32 · 600 — titre sans barre, pseudo de l'Accueil. */
    val titre1 = style(Police.Inter, 26, 32, 600, espacement = (-0.015).em, coupure = Titre)

    /** Inter 22/28 · 600 — titre de la barre. */
    val titreBarre = style(Police.Inter, 22, 28, 600, espacement = (-0.015).em)

    /** Inter 32/37 · 600 · tabulaire — score, rang, points gagnés. */
    val chiffre = style(Police.Inter, 32, 37, 600, espacement = (-0.02).em, fonctions = TABULAIRES)

    /** Inter 30/36 · 600 · +0,12 em — code de récupération. */
    val code = style(Police.Inter, 30, 36, 600, espacement = 0.12.em, fonctions = CODE)

    /** Inter 20/26 · 600 — titre de boîte. */
    val titreBoite = style(Police.Inter, 20, 26, 600, espacement = (-0.01).em, coupure = Titre)

    /** Inter 17/23 · 500 (saisie 400) — choix, pseudo de ligne, champ. */
    val choix = style(Police.Inter, 17, 23, 500)

    /** Inter 16/24 · 400 (message, encadré 500) — textes. */
    val corps = style(Police.Inter, 16, 24, 400, coupure = Paragraphe)

    /** Inter 16/22 · 700 (détail 400) — ligne de résultat. */
    val verdict = style(Police.Inter, 16, 22, 700)

    /** Inter 16/21 · 600 — bouton de 56, lien. */
    val bouton = style(Police.Inter, 16, 21, 600)

    /** Inter 18/23 · 700 — « Jouer ». */
    val boutonGrand = style(Police.Inter, 18, 23, 700)

    /** Inter 15/20 · 600 (erreur 500) · tabulaire — barre de partie, libellé, rang, petit bouton. */
    val libelle = style(Police.Inter, 15, 20, 600, fonctions = TABULAIRES)

    /** Inter 15/20 · 700 — lettres A à D. */
    val lettre = style(Police.Inter, 15, 20, 700)

    /** Inter 13/18 · 400 à 700 — aide, en-tête, étiquette d'un choix, pied. */
    val petit = style(Police.Inter, 13, 18, 400)

    /** Inter 12/12 · 700 — « Toi ». */
    val etiquette = style(Police.Inter, 12, 12, 700)
}

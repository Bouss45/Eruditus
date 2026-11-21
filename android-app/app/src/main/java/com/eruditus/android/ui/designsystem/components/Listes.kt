package com.eruditus.android.ui.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.eruditus.android.R
import com.eruditus.android.ui.designsystem.theme.Eruditus

/**
 * `ListeGroupee` (DESIGN.md § 7) : en-tête facultatif (`petit` 600 `texte-secondaire`, marges 16,
 * écart 8), puis le groupe : rayon 20, `surface` ([teintee] : `accent-teinte`). Les lignes tracent
 * elles-mêmes leur séparateur (paramètre `separateur` : toutes sauf la première).
 */
@Composable
fun ListeGroupee(
    modifier: Modifier = Modifier,
    enTete: String? = null,
    enTeteDroite: String? = null,
    teintee: Boolean = false,
    lignes: @Composable ColumnScope.() -> Unit,
) {
    val c = Eruditus.couleurs
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Eruditus.espacements.icone)) {
        if (enTete != null || enTeteDroite != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Eruditus.espacements.groupe),
                horizontalArrangement = Arrangement.spacedBy(Eruditus.espacements.blocs),
            ) {
                val style = Eruditus.typo.petit.copy(fontWeight = FontWeight.SemiBold)
                Text(enTete.orEmpty(), Modifier.weight(1f), style = style, color = c.texteSecondaire)
                if (enTeteDroite != null) Text(enTeteDroite, style = style, color = c.texteSecondaire)
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Eruditus.rayons.groupe)
                .background(if (teintee) c.accentTeinte else c.surface),
            content = lignes,
        )
    }
}

/** Trait de 1 en haut d'une ligne, à partir de [debut]. */
private fun Modifier.traitHaut(visible: Boolean, couleur: Color, debut: Float = 0f): Modifier =
    if (!visible) this else drawBehind {
        val epaisseur = 1.dp.toPx()
        drawLine(couleur, Offset(debut, epaisseur / 2), Offset(size.width, epaisseur / 2), epaisseur)
    }

/**
 * `LigneClassement` (DESIGN.md § 7) : ≥ 52 ; rang en colonne ≥ 52, `libelle` tabulaire
 * `texte-secondaire` ; pseudo `choix` 500 à la ligne plutôt que coupé ; nombre `choix` 600 +
 * unité `texte-secondaire` ; marges 12 × 16. [moi] : `accent-teinte`, rang `texte`, pseudo 700,
 * « Toi » (`etiquette`, contour et texte `accent`, marges 4 × 8), sans séparateur au-dessus.
 * TalkBack lit la ligne d'un seul bloc : [description], donnée par l'écran (« rang 27, Hibou_42,
 * 4 points, toi », SPEC D74), à la place de ses textes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LigneClassement(
    rang: String,
    pseudo: String,
    nombre: String,
    unite: String,
    description: String,
    modifier: Modifier = Modifier,
    moi: Boolean = false,
    separateur: Boolean = false,
) {
    val c = Eruditus.couleurs
    val typo = Eruditus.typo
    val largeurRang = max(52.dp, with(LocalDensity.current) { typo.libelle.fontSize.toDp() } * 3.4f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .defaultMinSize(minHeight = Eruditus.tailles.ligneClassement)
            .background(if (moi) c.accentTeinte else Color.Transparent)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Box(Modifier.width(largeurRang).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text(rang, style = typo.libelle, color = if (moi) c.texte else c.texteSecondaire)
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .traitHaut(separateur && !moi, c.separateur)
                .padding(top = 12.dp, bottom = 12.dp, end = Eruditus.espacements.groupe),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Eruditus.espacements.icone),
                verticalArrangement = Arrangement.spacedBy(Eruditus.espacements.decalage),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    pseudo,
                    style = typo.choix.copy(fontWeight = if (moi) FontWeight.Bold else FontWeight.Medium),
                    color = c.texte,
                )
                if (moi) Toi()
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(nombre) }
                    withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = c.texteSecondaire)) { append(unite) }
                },
                style = typo.choix.copy(fontFeatureSettings = "'tnum'"),
                color = c.texte,
                textAlign = TextAlign.End,
            )
        }
    }
}

/** Étiquette « Toi » de [LigneClassement]. */
@Composable
private fun Toi() {
    val c = Eruditus.couleurs
    Text(
        stringResource(R.string.ds_toi),
        modifier = Modifier
            .border(Eruditus.tailles.contour, c.accent, Eruditus.rayons.pilule)
            .padding(horizontal = Eruditus.espacements.icone, vertical = Eruditus.espacements.decalage),
        style = Eruditus.typo.etiquette,
        color = c.accent,
    )
}

/** `SeparateurPoints` (DESIGN.md § 7) : trois points `neutre` sur 28, décoratifs (ma ligne sous le top 10). */
@Composable
fun SeparateurPoints(modifier: Modifier = Modifier) {
    val couleur = Eruditus.couleurs.neutre
    Box(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = 4.dp, height = 18.dp)) {
            val rayon = 1.6.dp.toPx()
            listOf(2f, 9f, 16f).forEach { y -> drawCircle(couleur, rayon, Offset(size.width / 2, y.dp.toPx())) }
        }
    }
}

/** `Encadre` (DESIGN.md § 7) : `accent-teinte`, rayon 20, marge 16, `corps` 500. */
@Composable
fun Encadre(texte: String, modifier: Modifier = Modifier) {
    Text(
        texte,
        modifier = modifier
            .fillMaxWidth()
            .background(Eruditus.couleurs.accentTeinte, Eruditus.rayons.groupe)
            .padding(Eruditus.espacements.groupe),
        style = Eruditus.typo.corps.copy(fontWeight = FontWeight.Medium),
        color = Eruditus.couleurs.texte,
    )
}

/**
 * `LigneInfo` (DESIGN.md § 7) : ≥ 56, marges 14 × 16 ; clé `corps` `texte-secondaire`, valeur
 * `corps` 600, à droite, ou dessous quand elle ne tient pas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LigneInfo(cle: String, valeur: String, modifier: Modifier = Modifier, separateur: Boolean = false) {
    val c = Eruditus.couleurs
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Eruditus.tailles.bouton)
            .traitHaut(separateur, c.separateur)
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = Eruditus.espacements.groupe, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(Eruditus.espacements.decalage),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(cle, Modifier.padding(end = Eruditus.espacements.groupe), style = Eruditus.typo.corps, color = c.texteSecondaire)
        Text(valeur, style = Eruditus.typo.corps.copy(fontWeight = FontWeight.SemiBold), color = c.texte)
    }
}

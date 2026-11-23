package com.eruditus.android.ui.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.eruditus.android.ui.designsystem.theme.Eruditus
import kotlin.math.max

/** Hauteur de la courbe, à toutes les tailles de texte. */
private val HAUTEUR = 120.dp

/** Un point n'est dessiné que si deux points voisins sont écartés d'au moins 12 dp (le dernier l'est toujours). */
private val ECART_POINTS = 12.dp

/**
 * `CourbeElo` (DESIGN.md § 7, D135) : l'Elo [valeurs] après chaque partie, la première étant le
 * départ (repère creux). 120 de haut, pleine largeur moins les marges de 16 ; ligne de 2 en `texte`,
 * sans remplissage ; `piste` pour trois repères horizontaux (plus haut, milieu, plus bas), avec le
 * plus haut et le plus bas en `petit` `texte-secondaire` à gauche ; points de 6 aux parties, le dernier
 * de 8 en `accent` ; jamais de couleur sémantique (D115). [provisoire] : ligne en tirets. Sans
 * animation. TalkBack ignore le dessin : l'écran donne le résumé en texte (spec/ecrans.md § 16).
 */
@Composable
fun CourbeElo(valeurs: List<Int>, provisoire: Boolean, modifier: Modifier = Modifier) {
    if (valeurs.isEmpty()) return
    val c = Eruditus.couleurs
    val mesure = rememberTextMeasurer()
    val styleValeur = Eruditus.typo.petit.copy(fontWeight = FontWeight.Medium, color = c.texteSecondaire)
    Canvas(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Eruditus.espacements.groupe)
            .height(HAUTEUR)
            .clearAndSetSemantics { },
    ) {
        val haut = valeurs.max()
        val bas = valeurs.min()
        val plat = haut == bas
        val etiquettes = (if (plat) listOf(bas) else listOf(haut, bas)).map { it to mesure.measure(it.toString(), styleValeur) }
        val largeurEtiquette = etiquettes.maxOf { it.second.size.width }
        val marge = max(8.dp.toPx(), etiquettes.first().second.size.height * 0.7f)
        val x0 = largeurEtiquette + 12.dp.toPx()
        val x1 = size.width - 6.dp.toPx()
        val n = valeurs.size - 1
        fun x(i: Int) = if (n == 0) x1 else x0 + (x1 - x0) * i / n
        fun y(v: Float) = if (plat) size.height / 2 else marge + (haut - v) / (haut - bas) * (size.height - 2 * marge)

        // Repères : plus haut, milieu, plus bas (un seul si la courbe est plate).
        val reperes = if (plat) listOf(bas.toFloat()) else listOf(haut.toFloat(), (haut + bas) / 2f, bas.toFloat())
        reperes.forEach { v -> drawLine(c.piste, Offset(x0, y(v)), Offset(x1, y(v)), 1.dp.toPx()) }
        etiquettes.forEach { (v, texte) ->
            drawText(mesure, v.toString(), Offset(largeurEtiquette - texte.size.width.toFloat(), y(v.toFloat()) - texte.size.height / 2f), styleValeur)
        }

        if (n > 0) {
            val trace = Path().apply { valeurs.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v.toFloat())) else lineTo(x(i), y(v.toFloat())) } }
            drawPath(
                trace,
                c.texte,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = if (provisoire) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())) else null,
                ),
            )
            // Le départ : un repère creux.
            drawCircle(c.fond, 3.dp.toPx(), Offset(x(0), y(valeurs[0].toFloat())))
            drawCircle(c.texte, 3.dp.toPx(), Offset(x(0), y(valeurs[0].toFloat())), style = Stroke(1.5.dp.toPx()))
        }
        val pas = if (n > 0) (x1 - x0) / n else Float.MAX_VALUE
        for (i in (if (n > 0) 1 else 0)..n) {
            val dernier = i == n
            if (dernier || pas >= ECART_POINTS.toPx()) {
                drawCircle(if (dernier) c.accent else c.texte, (if (dernier) 4.dp else 3.dp).toPx(), Offset(x(i), y(valeurs[i].toFloat())))
            }
        }
    }
}

package com.eruditus.android.ui.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eruditus.android.ui.designsystem.theme.Eruditus

/** Un tour par seconde, linéaire (DESIGN.md § 7). */
private const val TOUR_MS = 1000

/**
 * `Indicateur` (DESIGN.md § 7) : quart de cercle sur une piste du même ton, trait 2,4 sur une
 * grille de 24. 24 dans un choix (piste 25 %), 20 dans un bouton (piste 30 %), 40 à l'écran.
 * Tourne ; fixe quand les animations sont désactivées. Décoratif : le composant qui le porte dit
 * « Chargement » ou « Envoi en cours » à TalkBack.
 */
@Composable
fun Indicateur(
    couleur: Color,
    modifier: Modifier = Modifier,
    taille: Dp = 24.dp,
    trait: Dp = taille * (2.4f / 24f),
    opacitePiste: Float = 0.25f,
) {
    val tourne = Eruditus.mouvement.indicateursTournent && !LocalInspectionMode.current
    val angle = if (tourne) {
        rememberInfiniteTransition(label = "indicateur").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(TOUR_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "angle",
        ).value
    } else {
        0f
    }
    Canvas(modifier.size(taille)) {
        val epaisseur = trait.toPx()
        // Cercle de rayon 9 sur la grille de 24, comme le dessin de la maquette.
        val rayon = size.minDimension * (9f / 24f)
        val coin = Offset(center.x - rayon, center.y - rayon)
        drawCircle(couleur.copy(alpha = couleur.alpha * opacitePiste), rayon, style = Stroke(epaisseur))
        drawArc(
            color = couleur,
            startAngle = -90f + angle,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = coin,
            size = Size(rayon * 2, rayon * 2),
            style = Stroke(epaisseur, cap = StrokeCap.Round),
        )
    }
}

package com.eruditus.android.ui.designsystem.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Icônes au trait (DESIGN.md § 7) : tracés de `Ecran.dc.html` sur une grille de 24, épaisseur du
 * trait de la maquette. Une couleur, celle du texte qui les accompagne.
 */

/** Tracé SVG d'un cercle. */
private fun cercle(cx: Float, cy: Float, r: Float) =
    "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0Z"

/** Tracé SVG d'un rectangle arrondi. */
private fun rectangle(x: Float, y: Float, l: Float, h: Float, r: Float) =
    "M${x + r},${y}H${x + l - r}A$r,$r 0 0 1 ${x + l},${y + r}V${y + h - r}" +
        "A$r,$r 0 0 1 ${x + l - r},${y + h}H${x + r}A$r,$r 0 0 1 $x,${y + h - r}V${y + r}" +
        "A$r,$r 0 0 1 ${x + r},${y}Z"

/**
 * Une icône : [traits] dessinés au trait [epaisseur] ; [points] (les points des « i » et « ! »)
 * pleins et entourés du même trait, comme les `<circle fill="currentColor">` de la maquette.
 */
private fun icone(
    nom: String,
    epaisseur: Float,
    traits: List<String>,
    points: List<String> = emptyList(),
    jointure: StrokeJoin = StrokeJoin.Round,
): ImageVector = ImageVector.Builder(nom, 24.dp, 24.dp, 24f, 24f).apply {
    val noir = SolidColor(Color.Black)
    traits.forEach {
        addPath(
            pathData = addPathNodes(it),
            stroke = noir,
            strokeLineWidth = epaisseur,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = jointure,
        )
    }
    points.forEach {
        addPath(
            pathData = addPathNodes(it),
            fill = noir,
            stroke = noir,
            strokeLineWidth = epaisseur,
            strokeLineCap = StrokeCap.Round,
        )
    }
}.build()

object Icones {
    /** Flèche de retour, 24, trait 1,9. */
    val Retour = icone("retour", 1.9f, listOf("M19 12H5M11 6l-6 6 6 6"))

    /** Fermer la partie, 22, trait 1,8. */
    val Fermer = icone("fermer", 1.8f, listOf("M6 6l12 12M18 6L6 18"))

    /** ✓ d'un choix, 24, trait 2,4. */
    val Coche = icone("coche", 2.4f, listOf(TRACE_COCHE))

    /** ✓ du verdict, 20, trait 2,6. */
    val CocheVerdict = icone("coche-verdict", 2.6f, listOf(TRACE_COCHE))

    /** ✗ d'un choix, 24, trait 2,4. */
    val Croix = icone("croix", 2.4f, listOf(TRACE_CROIX))

    /** ✗ du verdict, 20, trait 2,6. */
    val CroixVerdict = icone("croix-verdict", 2.6f, listOf(TRACE_CROIX))

    /** Horloge du temps écoulé, 20, trait 2,2. */
    val Horloge = icone("horloge", 2.2f, listOf(cercle(12f, 13f, 8f), "M12 9v4l2.5 2M9.5 2.5h5"))

    /** Message d'information, 20, trait 2. */
    val Info = icone("info", 2f, listOf(cercle(12f, 12f, 9f), "M12 11v5"), listOf(cercle(12f, 7.8f, 0.6f)))

    /** Message d'alerte, 20, trait 2. */
    val Alerte = icone(
        "alerte",
        2f,
        listOf("M12 3.5l9.5 16.5h-19z", "M12 10v4.5"),
        listOf(cercle(12f, 17.2f, 0.6f)),
        jointure = StrokeJoin.Miter,
    )

    /** Erreur de formulaire, 18, trait 2,2. */
    val Erreur = icone("erreur", 2.2f, listOf(cercle(12f, 12f, 9f), "M12 7.5v5.5"), listOf(cercle(12f, 16.4f, 0.6f)))

    /** Erreur sous un champ, 16, trait 2,4. */
    val ErreurChamp = icone("erreur-champ", 2.4f, listOf(cercle(12f, 12f, 9f), "M12 7.5v5.5"), listOf(cercle(12f, 16.4f, 0.6f)))

    /** Afficher le mot de passe, 22, trait 1,8. */
    val Oeil = icone("oeil", 1.8f, listOf(TRACE_OEIL, cercle(12f, 12f, 3f)))

    /** Masquer le mot de passe, 22, trait 1,8. */
    val OeilBarre = icone("oeil-barre", 1.8f, listOf(TRACE_OEIL, cercle(12f, 12f, 3f), "M4 4l16 16"))

    /** Copier, 20, trait 1,8. */
    val Copier = icone(
        "copier",
        1.8f,
        listOf(
            rectangle(8.5f, 8.5f, 11.5f, 11.5f, 2.5f),
            "M15.5 8.5V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v7.5a2 2 0 0 0 2 2h2.5",
        ),
    )

    /** Pas de connexion, 48, trait 1,5. */
    val Reseau = icone(
        "reseau",
        1.5f,
        listOf(
            "M2.5 8.8a14 14 0 0 1 19 0M5.8 12.2a9.2 9.2 0 0 1 12.4 0M9.1 15.6a4.4 4.4 0 0 1 5.8 0",
            "M3.5 3.5l17 17",
        ),
        listOf(cercle(12f, 19f, 0.9f)),
    )

    /** Serveur injoignable, 48, trait 1,5. */
    val Serveur = icone(
        "serveur",
        1.5f,
        listOf(rectangle(4f, 4f, 16f, 7f, 2f), rectangle(4f, 13f, 16f, 7f, 2f), "M8 7.5h.01M8 16.5h.01"),
    )

    /** Mise à jour nécessaire, 48, trait 1,5. */
    val MiseAJour = icone("mise-a-jour", 1.5f, listOf("M12 4v11M7 10l5 5 5-5M5 20h14"))
}

/** Tracé du ✓, aussi dessiné progressivement par les choix (DESIGN.md § 6, `duree.trait`). */
internal const val TRACE_COCHE = "M5 12.5l4.5 4.5L19 7.5"
private const val TRACE_CROIX = "M7 7l10 10M17 7L7 17"
private const val TRACE_OEIL = "M2.5 12s3.5-6.5 9.5-6.5 9.5 6.5 9.5 6.5-3.5 6.5-9.5 6.5S2.5 12 2.5 12z"

/** Icône décorative de [taille], de la [couleur] du texte qu'elle accompagne. */
@Composable
fun IconeTrait(image: ImageVector, taille: Dp, couleur: Color, modifier: Modifier = Modifier) {
    Image(
        painter = rememberVectorPainter(image),
        contentDescription = null,
        modifier = modifier.size(taille),
        colorFilter = ColorFilter.tint(couleur),
    )
}

package com.eruditus.android.ui.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eruditus.android.R
import com.eruditus.android.ui.designsystem.theme.Eruditus
import com.eruditus.android.ui.designsystem.theme.IconeTrait
import com.eruditus.android.ui.designsystem.theme.IndicationPression

/*
 * Boutons (DESIGN.md § 7) : pilule, contour 1, marges 8 × 24, écart 10. Principal (un par écran,
 * en bas), secondaire (neutre), destructeur (ce qui efface ou interrompt). Chargement : indicateur
 * de 20 + libellé, bouton inactif ; l'écran désactive aussi ses autres boutons et champs.
 */

/** Marge horizontale des boutons : 24, ou 20 dans les actions d'une `Boite`. */
internal val LocalMargeBouton = staticCompositionLocalOf { 24.dp }

private enum class Genre { Principal, Secondaire, Destructeur }

private enum class Taille { Normal, Grand, Petit }

/** Bouton principal : `accent` / `sur-accent`, 56 ; [grand] : 64 en `bouton-grand` (« Jouer »). */
@Composable
fun BoutonPrincipal(
    libelle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    grand: Boolean = false,
    chargement: Boolean = false,
    actif: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) = Bouton(Genre.Principal, if (grand) Taille.Grand else Taille.Normal, libelle, onClick, modifier, null, chargement, actif, interactionSource)

/** Bouton secondaire : sans fond, contour `contour`, `texte`. [petit] : 48 en `libelle`, icône de 20 facultative. */
@Composable
fun BoutonSecondaire(
    libelle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    petit: Boolean = false,
    icone: ImageVector? = null,
    chargement: Boolean = false,
    actif: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) = Bouton(Genre.Secondaire, if (petit) Taille.Petit else Taille.Normal, libelle, onClick, modifier, icone, chargement, actif, interactionSource)

/** Bouton destructeur : `rouge-conteneur`, contour `rouge`, `sur-rouge-conteneur`. */
@Composable
fun BoutonDestructeur(
    libelle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    chargement: Boolean = false,
    actif: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) = Bouton(Genre.Destructeur, Taille.Normal, libelle, onClick, modifier, null, chargement, actif, interactionSource)

@Composable
private fun Bouton(
    genre: Genre,
    taille: Taille,
    libelle: String,
    onClick: () -> Unit,
    modifier: Modifier,
    icone: ImageVector?,
    chargement: Boolean,
    actif: Boolean,
    interactionSource: MutableInteractionSource?,
) {
    val c = Eruditus.couleurs
    val typo = Eruditus.typo
    val forme = Eruditus.rayons.pilule
    val desactive = !actif
    val (fondCible, texteCible, contourCible) = when {
        desactive && genre == Genre.Secondaire -> Triple(Color.Transparent, c.desactiveTexte, c.separateur)
        desactive -> Triple(c.desactiveFond, c.desactiveTexte, c.desactiveFond)
        genre == Genre.Principal -> Triple(c.accent, c.surAccent, c.accent)
        genre == Genre.Secondaire -> Triple(Color.Transparent, c.texte, c.contour)
        else -> Triple(c.rougeConteneur, c.surRougeConteneur, c.rouge)
    }
    val spec = Eruditus.mouvement.couleur<Color>()
    val fond by animateColorAsState(fondCible, spec, label = "fond")
    val texte by animateColorAsState(texteCible, spec, label = "texte")
    val contour by animateColorAsState(contourCible, spec, label = "contour")
    val pression = if (genre == Genre.Principal) c.pressionAccent else c.pression
    val hauteur: Dp = when (taille) {
        Taille.Normal -> Eruditus.tailles.bouton
        Taille.Grand -> Eruditus.tailles.grand
        Taille.Petit -> Eruditus.tailles.cible
    }
    val style = when (taille) {
        Taille.Normal -> typo.bouton
        Taille.Grand -> typo.boutonGrand
        Taille.Petit -> typo.libelle
    }
    val chargementTexte = stringResource(R.string.ds_chargement)
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = hauteur)
            .background(fond, forme)
            .border(Eruditus.tailles.contour, contour, forme)
            .clickable(
                interactionSource = interactionSource,
                indication = IndicationPression(pression, forme),
                enabled = actif && !chargement,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { if (chargement) stateDescription = chargementTexte }
            .padding(horizontal = LocalMargeBouton.current, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chargement) Indicateur(texte, taille = 20.dp, opacitePiste = 0.3f)
        if (icone != null) IconeTrait(icone, 20.dp, texte)
        Text(libelle, style = style, color = texte, textAlign = TextAlign.Center)
    }
}

/**
 * `RangeeBoutons` (DESIGN.md § 7) : des boutons à parts égales, écart 12, de même hauteur ; l'un
 * sous l'autre, sur toute la largeur, dès qu'un libellé ne tient plus dans sa part sans couper un
 * mot ([rangeeEnLigne]). Petits boutons de l'Accueil, actions d'une `Boite`.
 */
@Composable
fun RangeeBoutons(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val ecart = Eruditus.espacements.blocs
    Layout(content, modifier) { mesurables, contraintes ->
        val ecartPx = ecart.roundToPx()
        val largeur = if (contraintes.hasBoundedWidth) {
            contraintes.maxWidth
        } else {
            mesurables.sumOf { it.maxIntrinsicWidth(Constraints.Infinity) } + ecartPx * (mesurables.size - 1).coerceAtLeast(0)
        }
        val minimales = mesurables.map { it.minIntrinsicWidth(Constraints.Infinity) }
        if (rangeeEnLigne(minimales, largeur, ecartPx)) {
            val n = mesurables.size.coerceAtLeast(1)
            val disponible = largeur - ecartPx * (n - 1)
            val parts = List(mesurables.size) { i -> disponible / n + if (i < disponible % n) 1 else 0 }
            val hauteur = mesurables.mapIndexed { i, m -> m.maxIntrinsicHeight(parts[i]) }.maxOrNull() ?: 0
            val places = mesurables.mapIndexed { i, m -> m.measure(Constraints.fixed(parts[i], hauteur)) }
            layout(largeur, hauteur) {
                var x = 0
                places.forEach {
                    it.placeRelative(x, 0)
                    x += it.width + ecartPx
                }
            }
        } else {
            val places = mesurables.map { it.measure(Constraints(minWidth = largeur, maxWidth = largeur)) }
            val hauteur = places.sumOf { it.height } + ecartPx * (places.size - 1)
            layout(largeur, hauteur) {
                var y = 0
                places.forEach {
                    it.placeRelative(0, y)
                    y += it.height + ecartPx
                }
            }
        }
    }
}

/**
 * Règle de [RangeeBoutons] : en ligne si chaque bouton tient dans sa part de la largeur (moins les
 * écarts) sans couper un mot. Vérifiée par `RangeeBoutonsTest`.
 */
fun rangeeEnLigne(largeursMinimales: List<Int>, largeurDisponible: Int, ecart: Int): Boolean {
    val n = largeursMinimales.size
    if (n <= 1) return true
    val part = (largeurDisponible - ecart * (n - 1)).toFloat() / n
    return largeursMinimales.all { it <= part }
}

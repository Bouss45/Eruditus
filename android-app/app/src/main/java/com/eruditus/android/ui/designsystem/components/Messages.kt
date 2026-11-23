package com.eruditus.android.ui.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eruditus.android.R
import com.eruditus.android.ui.designsystem.theme.Eruditus
import com.eruditus.android.ui.designsystem.theme.IconeTrait
import com.eruditus.android.ui.designsystem.theme.Icones
import com.eruditus.android.ui.designsystem.theme.IndicationPression

/** Genre d'un [MessageHaut] : alerte pour 429 et « Une erreur est survenue », info sinon (DESIGN.md § 8). */
enum class GenreMessage { Info, Alerte }

/** Un message en haut d'écran. */
@Immutable
data class Message(val texte: String, val genre: GenreMessage = GenreMessage.Info)

/**
 * `MessageHaut` (DESIGN.md § 7) : `surface-haute`, contour `contour`, rayon 16, marges 14 × 16,
 * écart 12 ; icône de 20 `texte-secondaire` (cercle i ou triangle !) ; `corps` 500 ; monochrome :
 * il informe sans juger. Zone annoncée. Pousse le contenu, ne recouvre jamais rien.
 */
@Composable
fun MessageHaut(message: Message, modifier: Modifier = Modifier) {
    val c = Eruditus.couleurs
    val forme = Eruditus.rayons.choix
    val style = Eruditus.typo.corps.copy(fontWeight = FontWeight.Medium)
    val hauteurLigne = with(LocalDensity.current) { style.lineHeight.toDp() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(c.surfaceHaute, forme)
            .border(Eruditus.tailles.contour, c.contour, forme)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(Eruditus.espacements.blocs),
    ) {
        Box(Modifier.height(hauteurLigne), contentAlignment = Alignment.Center) {
            IconeTrait(if (message.genre == GenreMessage.Alerte) Icones.Alerte else Icones.Info, 20.dp, c.texteSecondaire)
        }
        Text(message.texte, Modifier.weight(1f), style = style, color = c.texte)
    }
}

/**
 * Place d'un [MessageHaut] dans un gabarit : il s'ouvre (`duree.message`) et pousse le contenu ;
 * il se referme quand [message] devient nul. [marges] : sous la barre (8 en haut, 20 sur les côtés)
 * ou en haut du contenu.
 */
@Composable
fun ZoneMessage(message: Message?, marges: PaddingValues, modifier: Modifier = Modifier) {
    var dernier by remember { mutableStateOf(message) }
    if (message != null) dernier = message
    val mouvement = Eruditus.mouvement
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = expandVertically(mouvement.message()) + fadeIn(mouvement.message()),
        exit = shrinkVertically(mouvement.message()) + fadeOut(mouvement.message()),
    ) {
        dernier?.let { MessageHaut(it, Modifier.padding(marges)) }
    }
}

/**
 * `ErreurFormulaire` (DESIGN.md § 7) : icône de 18 + `libelle` 500 `rouge`, annoncée ; au-dessus
 * du lien ou de l'action, décalée de 4.
 */
@Composable
fun ErreurFormulaire(texte: String, modifier: Modifier = Modifier) {
    val c = Eruditus.couleurs
    val style = Eruditus.typo.libelle.copy(fontWeight = FontWeight.Medium)
    val hauteurLigne = with(LocalDensity.current) { style.lineHeight.toDp() }
    Row(
        modifier = modifier
            .padding(top = Eruditus.espacements.decalage)
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
        horizontalArrangement = Arrangement.spacedBy(Eruditus.espacements.icone),
    ) {
        Box(Modifier.height(hauteurLigne), contentAlignment = Alignment.Center) {
            IconeTrait(Icones.Erreur, 18.dp, c.rouge)
        }
        Text(texte, Modifier.weight(1f), style = style, color = c.rouge)
    }
}

/**
 * `Lien` (DESIGN.md § 7) : `bouton` en `accent`, cible ≥ 48, marges 0 × 4 ; pressé : `pression`
 * dans une pilule. Inactif pendant un envoi, comme les boutons. [chargement] (« Voir plus », D131) :
 * un indicateur de 20 devant le texte, écart 10, et le lien ne se touche plus.
 */
@Composable
fun Lien(
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actif: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    chargement: Boolean = false,
) {
    val c = Eruditus.couleurs
    val chargementTexte = stringResource(R.string.ds_chargement)
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = Eruditus.tailles.cible)
            .clickable(
                interactionSource = interactionSource,
                indication = IndicationPression(c.pression, Eruditus.rayons.pilule),
                enabled = actif && !chargement,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { if (chargement) stateDescription = chargementTexte }
            .padding(horizontal = Eruditus.espacements.decalage),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chargement) Indicateur(c.accent, taille = 20.dp, opacitePiste = 0.3f)
        Text(texte, style = Eruditus.typo.bouton, color = if (actif) c.accent else c.desactiveTexte)
    }
}

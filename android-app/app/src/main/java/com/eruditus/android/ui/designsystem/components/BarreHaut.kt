package com.eruditus.android.ui.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eruditus.android.R
import com.eruditus.android.ui.designsystem.theme.Eruditus
import com.eruditus.android.ui.designsystem.theme.IconeTrait
import com.eruditus.android.ui.designsystem.theme.Icones
import com.eruditus.android.ui.designsystem.theme.IndicationPression

/*
 * `BarreHaut` (DESIGN.md § 7) : ≥ 56, marges 4 à gauche et 20 à droite, bouton icône de 48. Deux
 * formes : retour + titre (Formulaire, Liste), ou celle de la partie (fermer, compteur, secondes).
 * Sous la barre système : le gabarit pose les marges de la barre d'état.
 */

/** Barre avec retour (flèche 24 `texte`) et titre en `titre-barre`. */
@Composable
fun BarreHaut(
    titre: String,
    onRetour: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
) {
    LigneBarre(modifier) {
        BoutonIcone(Icones.Retour, 24.dp, Eruditus.couleurs.texte, stringResource(R.string.ds_retour), onRetour, interactionSource)
        Text(
            titre,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp)
                .semantics { heading() },
            style = Eruditus.typo.titreBarre,
            color = Eruditus.couleurs.texte,
        )
    }
}

/**
 * Barre de la partie : fermer (22, `texte-secondaire`), « Question n/5 » et secondes en `libelle`.
 * Secondes en `texte`, puis `texte-secondaire` quand le chrono s'arrête. TalkBack lit le temps sur
 * le `Chrono`, pas ici.
 */
@Composable
fun BarreHautPartie(
    compteur: String,
    restantMs: Long,
    chronoEnCours: Boolean,
    onFermer: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
) {
    val c = Eruditus.couleurs
    val couleurSecondes by animateColorAsState(
        if (chronoEnCours) c.texte else c.texteSecondaire,
        Eruditus.mouvement.couleur(),
        label = "secondes",
    )
    LigneBarre(modifier) {
        BoutonIcone(Icones.Fermer, 22.dp, c.texteSecondaire, stringResource(R.string.ds_fermer), onFermer, interactionSource)
        Text(compteur, Modifier.weight(1f), style = Eruditus.typo.libelle, color = c.texte)
        Text(
            stringResource(R.string.ds_secondes, ReglesChrono.secondesAffichees(restantMs)),
            modifier = Modifier.clearAndSetSemantics { },
            style = Eruditus.typo.libelle,
            color = couleurSecondes,
        )
    }
}

@Composable
private fun LigneBarre(modifier: Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Eruditus.tailles.bouton)
            .padding(start = Eruditus.espacements.decalage, end = Eruditus.espacements.ecran),
        horizontalArrangement = Arrangement.spacedBy(Eruditus.espacements.decalage),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Bouton icône rond de 48 : la cible tactile minimale. */
@Composable
internal fun BoutonIcone(
    image: ImageVector,
    tailleIcone: Dp,
    couleur: Color,
    description: String,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource? = null,
    actif: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(Eruditus.tailles.cible)
            .clickable(
                interactionSource = interactionSource,
                indication = IndicationPression(Eruditus.couleurs.pression, CircleShape),
                enabled = actif,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        IconeTrait(image, tailleIcone, couleur)
    }
}

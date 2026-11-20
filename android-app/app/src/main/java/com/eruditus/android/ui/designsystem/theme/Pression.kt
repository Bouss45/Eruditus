package com.eruditus.android.ui.designsystem.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Appui (DESIGN.md § 6) : couche `pression` (ou `pression-accent`) à plat, bornée à la forme du
 * composant, sous son contenu. Ni onde, ni échelle, rien qui déborde ; identique quand les
 * animations sont désactivées.
 */
class IndicationPression(private val couleur: Color, private val forme: Shape = RectangleShape) :
    IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        NoeudPression(interactionSource, couleur, forme)

    override fun equals(other: Any?): Boolean =
        other is IndicationPression && other.couleur == couleur && other.forme == forme

    override fun hashCode(): Int = 31 * couleur.hashCode() + forme.hashCode()
}

/** Source toujours pressée : montre l'état « Pressé » dans les aperçus et le catalogue. */
object AppuiForce : MutableInteractionSource {
    override val interactions: Flow<Interaction> = flowOf(PressInteraction.Press(Offset.Zero))
    override suspend fun emit(interaction: Interaction) = Unit
    override fun tryEmit(interaction: Interaction): Boolean = true
}

private class NoeudPression(
    private val source: InteractionSource,
    private val couleur: Color,
    private val forme: Shape,
) : Modifier.Node(), DrawModifierNode {

    private val appuis = mutableListOf<PressInteraction.Press>()
    private var presse = source === AppuiForce

    override fun onAttach() {
        if (source === AppuiForce) return
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> appuis += interaction
                    is PressInteraction.Release -> appuis -= interaction.press
                    is PressInteraction.Cancel -> appuis -= interaction.press
                }
                val nouveau = appuis.isNotEmpty()
                if (nouveau != presse) {
                    presse = nouveau
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        if (presse) drawOutline(forme.createOutline(size, layoutDirection, this), couleur)
        drawContent()
    }
}

package com.eruditus.android.ui.designsystem.components

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.eruditus.android.ui.designsystem.theme.Eruditus
import com.eruditus.android.ui.designsystem.theme.MouvementEruditus

/**
 * Contenu de la `Boite` (DESIGN.md § 7), sans sa fenêtre : 340 au plus, marge 24, rayon 28,
 * `surface-haute`, `ombre-boite`, écart 16 ; [titre] en `titre-boite` (une question) ; [texte] en
 * `corps`, `texte-secondaire` sous un titre ; [champ] facultatif ; [actions] côte à côte (marges
 * 8 × 20, écart 12), l'une sous l'autre quand le texte grandit, l'action sûre à gauche. Plus
 * haute que l'écran (texte à 200 %), elle défile.
 */
@Composable
fun ContenuBoite(
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    titre: String? = null,
    texte: String? = null,
    champ: (@Composable () -> Unit)? = null,
) {
    val c = Eruditus.couleurs
    val forme = Eruditus.rayons.boite
    Column(
        modifier = modifier
            .widthIn(max = 340.dp)
            .fillMaxWidth()
            .dropShadow(forme, Shadow(radius = 32.dp, color = c.ombreBoite, offset = DpOffset(0.dp, 12.dp)))
            .background(c.surfaceHaute, forme)
            .semantics { paneTitle = titre ?: texte.orEmpty() }
            // À 200 %, une boîte plus haute que l'écran défile au lieu de couper ses actions.
            .verticalScroll(rememberScrollState())
            .padding(Eruditus.espacements.boite),
        verticalArrangement = Arrangement.spacedBy(Eruditus.espacements.groupe),
    ) {
        if (titre != null) {
            Text(titre, Modifier.semantics { heading() }, style = Eruditus.typo.titreBoite, color = c.texte)
        }
        if (texte != null) {
            Text(texte, style = Eruditus.typo.corps, color = if (titre != null) c.texteSecondaire else c.texte)
        }
        if (champ != null) {
            CompositionLocalProvider(LocalEspaceHautChamp provides 0.dp) { champ() }
        }
        CompositionLocalProvider(LocalMargeBouton provides 20.dp) {
            RangeeBoutons(Modifier.padding(top = Eruditus.espacements.decalage), actions)
        }
    }
}

/**
 * `Boite` (DESIGN.md § 7) : [ContenuBoite] dans sa fenêtre, sur le `voile` du thème ; fondu et
 * échelle 0,95 → 1 (`duree.boite`), à l'envers en sortie : la boîte reste affichée le temps de
 * sortir après [visible] faux. Retour système et toucher du voile : [onFermer].
 */
@Composable
fun Boite(
    visible: Boolean,
    onFermer: () -> Unit,
    actions: @Composable () -> Unit,
    titre: String? = null,
    texte: String? = null,
    champ: (@Composable () -> Unit)? = null,
) {
    val etat = remember { MutableTransitionState(false) }
    etat.targetState = visible
    if (!etat.currentState && !etat.targetState && etat.isIdle) return

    val mouvement = Eruditus.mouvement
    val transition = rememberTransition(etat, label = "boite")
    val opacite by transition.animateFloat({ mouvement.boite() }, label = "opacite") { if (it) 1f else 0f }
    val echelle by transition.animateFloat({ mouvement.boite() }, label = "echelle") {
        if (it) 1f else MouvementEruditus.BOITE_ECHELLE
    }

    Dialog(
        onDismissRequest = onFermer,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Le voile du thème remplace l'assombrissement de la fenêtre d'Android.
        val fenetre = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { fenetre?.setDimAmount(0f) }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = opacite }
                .background(Eruditus.couleurs.voile)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onFermer)
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            ContenuBoite(
                actions = actions,
                titre = titre,
                texte = texte,
                champ = champ,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = echelle
                        scaleY = echelle
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            )
        }
    }
}

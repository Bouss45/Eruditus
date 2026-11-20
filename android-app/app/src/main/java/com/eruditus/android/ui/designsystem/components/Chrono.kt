package com.eruditus.android.ui.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.eruditus.android.R
import com.eruditus.android.ui.designsystem.theme.Eruditus

/**
 * `Chrono` (DESIGN.md § 7) : sous la barre, marges 2 en haut et 20 sur les côtés ; barre de 4,
 * rayon 2, sur `piste` ; largeur = restant / total. En cours `accent`, jamais de rouge ; arrêté
 * `neutre`. Piloté par l'horloge de la partie : l'écran fait varier [restantMs] à chaque image.
 *
 * À chaque nouvelle [question], la barre se remplit (`duree.chrono`) ; animations désactivées, elle
 * avance par secondes. Barre de progression pour TalkBack, qui annonce le temps à 10 s et à 5 s.
 */
@Composable
fun Chrono(
    restantMs: Long,
    totalMs: Long,
    enCours: Boolean,
    question: Int,
    modifier: Modifier = Modifier,
) {
    val c = Eruditus.couleurs
    val mouvement = Eruditus.mouvement
    val cible = ReglesChrono.fraction(restantMs, totalMs, paliers = !mouvement.animationsActives)
    val couleur by animateColorAsState(if (enCours) c.accent else c.neutre, mouvement.couleur(), label = "chrono")

    // Remplissage à l'arrivée d'une question ; rien au premier affichage (DESIGN.md § 6).
    val remplissage = remember { Animatable(1f) }
    var premiereQuestion by remember { mutableStateOf(true) }
    LaunchedEffect(question) {
        if (premiereQuestion) {
            premiereQuestion = false
        } else {
            remplissage.snapTo(0f)
            remplissage.animateTo(1f, mouvement.chrono())
        }
    }

    // Annonces de TalkBack à 10 s et à 5 s, une fois chacune.
    val secondes = ReglesChrono.secondesAffichees(restantMs)
    var precedentMs by remember { mutableStateOf<Long?>(null) }
    var seuilAnnonce by remember { mutableStateOf<Int?>(null) }
    SideEffect {
        if (enCours) ReglesChrono.annonce(precedentMs, restantMs)?.let { seuilAnnonce = it }
        precedentMs = restantMs
    }
    val tempsRestant = stringResource(R.string.ds_temps_restant)
    val etat = pluralStringResource(R.plurals.ds_secondes_restantes, secondes, secondes)
    val annonce = seuilAnnonce?.let { pluralStringResource(R.plurals.ds_secondes_restantes, it, it) }.orEmpty()
    val totalSecondes = (totalMs / 1000).toFloat().coerceAtLeast(1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp, start = Eruditus.espacements.ecran, end = Eruditus.espacements.ecran),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Eruditus.tailles.chrono)
                .clip(RoundedCornerShape(2.dp))
                .background(c.piste)
                .clearAndSetSemantics {
                    contentDescription = tempsRestant
                    stateDescription = etat
                    progressBarRangeInfo = ProgressBarRangeInfo(secondes.toFloat().coerceAtMost(totalSecondes), 0f..totalSecondes)
                },
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(cible * remplissage.value)
                    .clip(RoundedCornerShape(2.dp))
                    .background(couleur),
            )
        }
        // Zone annoncée : son texte ne change qu'au passage de 10 s et de 5 s.
        Box(
            Modifier.clearAndSetSemantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = annonce
            },
        )
    }
}

package com.eruditus.android.ui.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.eruditus.android.R
import com.eruditus.android.ui.designsystem.theme.Eruditus
import com.eruditus.android.ui.designsystem.theme.IconeTrait
import com.eruditus.android.ui.designsystem.theme.Icones
import com.eruditus.android.ui.designsystem.theme.IndicationPression
import com.eruditus.android.ui.designsystem.theme.MouvementEruditus
import com.eruditus.android.ui.designsystem.theme.TRACE_COCHE
import com.eruditus.android.ui.designsystem.theme.rememberVibrations
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

/** Comment la bonne réponse s'allume : seul le mouvement diffère, l'état final est le même. */
enum class ArriveeBonne {
    /** Choisie par le joueur : remplissage depuis la lettre, rebond, ✓ tracé ; Confirm. */
    Choisie,

    /** Révélée après une erreur : fondu à +320 ms, ✓ à +440 ms. */
    ApresErreur,

    /** Révélée au temps écoulé : fondu, ✓ à +140 ms ; Reject. */
    TempsEcoule,
}

/** États d'un choix (DESIGN.md § 7). « Pressé » est l'appui réel, pas un état. */
@Immutable
sealed interface EtatChoix {
    /** `surface`, `contour`, pastille `piste` ; seul état qui se touche. */
    data object Neutre : EtatChoix

    /** Le choix touché, en attente du serveur : contour `texte` 2 et indicateur. */
    data object Envoi : EtatChoix

    /** Les autres choix, pendant l'envoi et la correction : contour `separateur`, `texte-secondaire`. */
    data object Verrouille : EtatChoix

    /** Bonne réponse, choisie ou révélée : `vert`, « Bonne réponse », ✓. */
    data class Bonne(val arrivee: ArriveeBonne) : EtatChoix

    /** Mauvaise réponse choisie : `rouge-conteneur`, contour `rouge`, « Ta réponse », ✗, « non ». */
    data object Mauvaise : EtatChoix
}

/**
 * `ChoixReponse` (DESIGN.md § 7) : pleine largeur, ≥ 64, rayon 16, contour 1 ; marges 11 / 14 / 11 /
 * 12, écart 14 ; pastille de 36 au moins (2,4 × la lettre), rayon 10, `lettre` ; texte en `choix`
 * sur plusieurs lignes, étiquette `petit` 700 dessous ; à droite ✓, ✗ ou indicateur, 24.
 *
 * Seuls les changements d'[etat] s'animent et vibrent (DESIGN.md § 6) : un choix affiché
 * directement dans un état montre l'état final. TalkBack : « A, René Magritte » + l'état.
 */
@Composable
fun ChoixReponse(
    lettre: String,
    texte: String,
    etat: EtatChoix,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
) {
    val c = Eruditus.couleurs
    val mouvement = Eruditus.mouvement
    val vibrations = rememberVibrations()
    val forme = Eruditus.rayons.choix
    val bonne = etat is EtatChoix.Bonne
    val arrivee = (etat as? EtatChoix.Bonne)?.arrivee

    // Spécification des couleurs : celle du mouvement qui amène l'état.
    val specCouleur: AnimationSpec<Color> = when (arrivee) {
        ArriveeBonne.ApresErreur -> mouvement.revelation()
        ArriveeBonne.TempsEcoule -> mouvement.fonduBonneReponse()
        else -> mouvement.couleur()
    }
    val cibleTexte = when (etat) {
        EtatChoix.Verrouille -> c.texteSecondaire
        EtatChoix.Mauvaise -> c.surRougeConteneur
        is EtatChoix.Bonne -> c.surVert
        else -> c.texte
    }
    val cibleContour = when (etat) {
        EtatChoix.Neutre -> c.contour
        EtatChoix.Envoi -> c.texte
        EtatChoix.Verrouille -> c.separateur
        EtatChoix.Mauvaise -> c.rouge
        is EtatChoix.Bonne -> c.vert
    }
    val ciblePastille = when (etat) {
        EtatChoix.Mauvaise -> c.rouge
        is EtatChoix.Bonne -> c.pastilleVert.compositeOver(c.vert)
        else -> c.piste
    }
    val cibleLettre = when (etat) {
        EtatChoix.Verrouille -> c.texteSecondaire
        EtatChoix.Mauvaise -> c.surRouge
        is EtatChoix.Bonne -> c.surVert
        else -> c.texte
    }
    val couleurTexte by animateColorAsState(cibleTexte, specCouleur, label = "texte")
    val couleurContour by animateColorAsState(cibleContour, specCouleur, label = "contour")
    val couleurPastille by animateColorAsState(ciblePastille, specCouleur, label = "pastille")
    val couleurLettre by animateColorAsState(cibleLettre, specCouleur, label = "lettre")
    val fondBase by animateColorAsState(
        if (etat == EtatChoix.Mauvaise) c.rougeConteneur else c.surface,
        mouvement.couleur(),
        label = "fond",
    )

    // Calque vert (rayon et opacité), échelle, « non », ✓ et ✗ : état final au premier affichage.
    val rayonVert = remember { Animatable(if (bonne) 1f else 0f) }
    val opaciteVert = remember { Animatable(if (bonne) 1f else 0f) }
    val echelle = remember { Animatable(1f) }
    val decalageNon = remember { Animatable(0f) }
    val coche = remember { Animatable(if (bonne) 1f else 0f) }
    val croix = remember { Animatable(if (etat == EtatChoix.Mauvaise) 1f else 0f) }
    var etatAffiche by remember { mutableStateOf(etat) }

    LaunchedEffect(etat) {
        if (etat == etatAffiche) return@LaunchedEffect
        etatAffiche = etat
        animerVers(etat, mouvement, rayonVert, opaciteVert, echelle, decalageNon, coche, croix) {
            when (it) {
                Vibration.Confirmation -> vibrations.confirmation()
                Vibration.Rejet -> vibrations.rejet()
            }
        }
    }

    val densite = LocalDensity.current
    val pastille: Dp = max(36.dp, with(densite) { Eruditus.typo.lettre.fontSize.toDp() } * 2.4f)
    val margeGauche = 12.dp
    val cliquable = etat == EtatChoix.Neutre

    val description = stringResource(R.string.ds_choix, lettre, texte)
    val etatTalkBack = when (etat) {
        EtatChoix.Envoi -> stringResource(R.string.ds_choix_envoi)
        EtatChoix.Mauvaise -> stringResource(R.string.ds_choix_mauvaise)
        is EtatChoix.Bonne -> stringResource(
            if (etat.arrivee == ArriveeBonne.Choisie) R.string.ds_choix_bonne_choisie else R.string.ds_choix_bonne_revelee,
        )
        else -> null
    }
    val etiquette = when (etat) {
        is EtatChoix.Bonne -> stringResource(R.string.ds_bonne_reponse)
        EtatChoix.Mauvaise -> stringResource(R.string.ds_ta_reponse)
        else -> null
    }

    Row(
        modifier = modifier
            .graphicsLayer {
                translationX = decalageNon.value * density
                scaleX = echelle.value
                scaleY = echelle.value
            }
            .fillMaxWidth()
            .defaultMinSize(minHeight = Eruditus.tailles.grand)
            .clip(forme)
            .background(fondBase)
            .drawBehind {
                if (opaciteVert.value > 0f && rayonVert.value > 0f) {
                    // Le vert part du centre de la lettre et couvre la case.
                    val centre = Offset((margeGauche + pastille / 2).toPx(), size.height / 2)
                    val loin = hypot(size.width - centre.x, maxOf(centre.y, size.height - centre.y))
                    drawCircle(c.vert.copy(alpha = opaciteVert.value), loin * rayonVert.value, centre)
                }
            }
            .border(if (etat == EtatChoix.Envoi) Eruditus.tailles.contourFort else Eruditus.tailles.contour, couleurContour, forme)
            .clickable(
                interactionSource = interactionSource,
                indication = IndicationPression(c.pression, forme),
                enabled = cliquable,
                onClick = onClick,
            )
            .clearAndSetSemantics {
                contentDescription = description
                etatTalkBack?.let { stateDescription = it }
                if (cliquable) {
                    role = Role.Button
                    onClick { onClick(); true }
                }
            }
            .padding(start = margeGauche, top = 11.dp, end = 14.dp, bottom = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(pastille)
                .background(couleurPastille, Eruditus.rayons.pastille),
            contentAlignment = Alignment.Center,
        ) {
            Text(lettre, style = Eruditus.typo.lettre, color = couleurLettre)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(texte, style = Eruditus.typo.choix, color = couleurTexte)
            if (etiquette != null) {
                Text(
                    etiquette,
                    style = Eruditus.typo.petit.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    color = if (bonne) c.surVert else c.rouge,
                )
            }
        }
        when (etat) {
            EtatChoix.Envoi -> Indicateur(c.texte, taille = 24.dp, opacitePiste = 0.25f)
            is EtatChoix.Bonne -> CocheTracee(coche.value, c.surVert)
            EtatChoix.Mauvaise -> IconeTrait(
                Icones.Croix,
                24.dp,
                c.rouge,
                Modifier.graphicsLayer {
                    alpha = croix.value
                    val e = MouvementEruditus.CROIX_ECHELLE + (1 - MouvementEruditus.CROIX_ECHELLE) * croix.value
                    scaleX = e
                    scaleY = e
                },
            )
            else -> Unit
        }
    }
}

private enum class Vibration { Confirmation, Rejet }

/** Mouvement vers [etat] (DESIGN.md § 6, tableau des moments) ; vibre au bon moment. */
private suspend fun animerVers(
    etat: EtatChoix,
    mouvement: MouvementEruditus,
    rayonVert: Animatable<Float, *>,
    opaciteVert: Animatable<Float, *>,
    echelle: Animatable<Float, *>,
    decalageNon: Animatable<Float, *>,
    coche: Animatable<Float, *>,
    croix: Animatable<Float, *>,
    vibrer: (Vibration) -> Unit,
) = coroutineScope {
    when (etat) {
        is EtatChoix.Bonne -> when (etat.arrivee) {
            ArriveeBonne.Choisie -> {
                vibrer(Vibration.Confirmation)
                opaciteVert.snapTo(1f)
                rayonVert.snapTo(0f)
                coche.snapTo(0f)
                launch { rayonVert.animateTo(1f, mouvement.remplissage()) }
                launch { coche.animateTo(1f, mouvement.trait(delai = MouvementEruditus.COCHE_DELAI_MS)) }
                launch {
                    if (mouvement.animationsActives) delay(MouvementEruditus.REBOND_DEPART_MS)
                    echelle.animateTo(MouvementEruditus.REBOND_ECHELLE, mouvement.elanRebond())
                    echelle.animateTo(1f, mouvement.rebond())
                }
            }
            ArriveeBonne.ApresErreur -> {
                rayonVert.snapTo(1f)
                coche.snapTo(0f)
                launch { opaciteVert.animateTo(1f, mouvement.revelation()) }
                launch { coche.animateTo(1f, mouvement.trait(delai = MouvementEruditus.COCHE_DELAI_APRES_ERREUR_MS)) }
            }
            ArriveeBonne.TempsEcoule -> {
                vibrer(Vibration.Rejet)
                rayonVert.snapTo(1f)
                coche.snapTo(0f)
                launch { opaciteVert.animateTo(1f, mouvement.fonduBonneReponse()) }
                launch { coche.animateTo(1f, mouvement.trait(delai = MouvementEruditus.COCHE_DELAI_MS)) }
            }
        }
        EtatChoix.Mauvaise -> {
            vibrer(Vibration.Rejet)
            croix.snapTo(0f)
            launch { croix.animateTo(1f, mouvement.croix()) }
            launch {
                decalageNon.snapTo(0f)
                if (mouvement.animationsActives) {
                    decalageNon.animateTo(0f, mouvement.non(), initialVelocity = MouvementEruditus.NON_VITESSE_DP_PAR_S)
                }
            }
        }
        else -> {
            rayonVert.snapTo(0f)
            opaciteVert.snapTo(0f)
            coche.snapTo(0f)
            croix.snapTo(0f)
            echelle.snapTo(1f)
            decalageNon.snapTo(0f)
        }
    }
}

/** ✓ de 24, tracé jusqu'à [progres] (DESIGN.md § 6, `duree.trait`, PathMeasure). */
@Composable
private fun CocheTracee(progres: Float, couleur: Color, modifier: Modifier = Modifier) {
    val trace = remember { addPathNodes(TRACE_COCHE).toPath() }
    val mesure = remember { PathMeasure().apply { setPath(trace, false) } }
    val partiel = remember { Path() }
    Canvas(modifier.size(24.dp)) {
        val epaisseur = 2.4f
        val chemin = if (progres >= 1f) {
            trace
        } else {
            partiel.reset()
            mesure.getSegment(0f, mesure.length * progres.coerceIn(0f, 1f), partiel, true)
            partiel
        }
        scale(size.width / 24f, pivot = Offset.Zero) {
            drawPath(chemin, couleur, style = Stroke(epaisseur, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

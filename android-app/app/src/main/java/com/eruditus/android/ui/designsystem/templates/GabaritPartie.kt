package com.eruditus.android.ui.designsystem.templates

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedFiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import com.eruditus.android.ui.designsystem.components.BarreHautPartie
import com.eruditus.android.ui.designsystem.components.BoutonSecondaire
import com.eruditus.android.ui.designsystem.components.ChoixReponse
import com.eruditus.android.ui.designsystem.components.Chrono
import com.eruditus.android.ui.designsystem.components.EtatChoix
import com.eruditus.android.ui.designsystem.components.Lien
import com.eruditus.android.ui.designsystem.components.LigneResultat
import com.eruditus.android.ui.designsystem.components.Message
import com.eruditus.android.ui.designsystem.components.Resultat
import com.eruditus.android.ui.designsystem.components.ZoneMessage
import com.eruditus.android.ui.designsystem.theme.Eruditus
import com.eruditus.android.ui.designsystem.theme.MouvementEruditus
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/**
 * Le signalement de la question corrigée (SPEC D118) : [libelle] est le lien « Signaler cette
 * question » ; [fait] : il devient le texte « Question signalée. Merci. », sans action.
 */
@Immutable
data class SignalementAffiche(val libelle: String, val fait: Boolean)

/** Un choix de la question affichée. */
@Immutable
data class ChoixPartie(val lettre: String, val texte: String, val etat: EtatChoix)

/**
 * La question affichée par [GabaritPartie] : ce qui part et arrive quand [numero] change.
 * [resultatsPossibles] : les lignes de résultat que cette question peut donner ; la plus longue
 * fixe la place réservée sous le choix D (DESIGN.md § 8). [explication] et [source] (« Source :
 * … ») : montrées sous le verdict, avec lui (SPEC D101), puis [signalement] (D118).
 */
@Immutable
data class QuestionPartie(
    val numero: Int,
    val enonce: String,
    val choix: List<ChoixPartie>,
    val resultat: Resultat? = null,
    val resultatsPossibles: List<Resultat> = emptyList(),
    val explication: String? = null,
    val source: String? = null,
    val signalement: SignalementAffiche? = null,
)

/**
 * Partie (DESIGN.md § 8) : `BarreHautPartie` ; `Chrono` ; énoncé (8) ; espace ; 4 `ChoixReponse` ;
 * `LigneResultat` juste sous le choix D ; bouton. Verdict et bouton ont leur place réservée dès la
 * question : rien ne saute.
 *
 * Question suivante : l'ancienne part (`duree.sortie`), puis la nouvelle arrive du bord droit
 * (`ressort.arrivee`) ; le chrono se remplit ; le compteur change sans animation.
 *
 * À 200 % : énoncé, choix, verdict et détail défilent ensemble ; seul le bouton reste fixé en bas.
 * Pendant le chrono, la zone qui défile est calée sur le choix D : c'est le haut de l'énoncé qui
 * sort de l'écran. À la correction, elle se cale sur le bas du verdict (`duree.resultat`), visible
 * au-dessus du bouton (SPEC D70). [boutonChargement] : la question suivante est demandée.
 *
 * Explication et source (SPEC D101), sans place réservée : elles s'ouvrent sous le verdict avec
 * lui ; choix et verdict remontent dans l'espace libre sous l'énoncé, puis la zone se cale sur le
 * bas de la source, jamais au-delà du haut du verdict.
 */
@Composable
fun GabaritPartie(
    compteur: String,
    restantMs: Long,
    totalMs: Long,
    chronoEnCours: Boolean,
    question: QuestionPartie,
    onChoix: (index: Int) -> Unit,
    bouton: String,
    boutonVisible: Boolean,
    onBouton: () -> Unit,
    onFermer: () -> Unit,
    modifier: Modifier = Modifier,
    onSignaler: () -> Unit = {},
    message: Message? = null,
    boutonChargement: Boolean = false,
) {
    val e = Eruditus.espacements
    val mouvement = Eruditus.mouvement
    val densite = LocalDensity.current
    Column(
        modifier
            .fillMaxSize()
            .background(Eruditus.couleurs.fond)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
            BarreHautPartie(compteur, restantMs, chronoEnCours, onFermer)
            Chrono(restantMs, totalMs, chronoEnCours, question.numero)
            ZoneMessage(message, PaddingValues(start = e.ecran, end = e.ecran, top = e.icone))
        }
        AnimatedContent(
            targetState = question,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            transitionSpec = {
                val decalage = with(densite) { MouvementEruditus.SORTIE_DECALAGE.roundToPx() }
                val arrivee = slideInHorizontally(apresLaSortie(mouvement.arrivee(), mouvement.animationsActives)) { largeur -> largeur }
                val sortie = fadeOut(mouvement.sortie()) + slideOutHorizontally(mouvement.sortie()) { -decalage }
                (arrivee togetherWith sortie).using(SizeTransform(clip = false))
            },
            contentKey = { it.numero },
            label = "question",
        ) { q ->
            ContenuQuestion(q, onChoix, onSignaler)
        }
        // Bouton : place réservée ; il apparaît avec le résultat (`duree.resultat`).
        val apparition by animateFloatAsState(
            if (boutonVisible) 1f else 0f,
            if (boutonVisible) mouvement.resultat() else snap(),
            label = "bouton",
        )
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(start = e.ecran, end = e.ecran, top = e.blocs, bottom = e.groupe),
        ) {
            BoutonSecondaire(
                bouton,
                onBouton,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = apparition
                        translationY = (1 - apparition) * MouvementEruditus.RESULTAT_DECALAGE.toPx()
                    }
                    .then(if (boutonVisible) Modifier else Modifier.clearAndSetSemantics { }),
                chargement = boutonChargement,
                actif = boutonVisible,
            )
        }
    }
}

@Composable
private fun ContenuQuestion(q: QuestionPartie, onChoix: (Int) -> Unit, onSignaler: () -> Unit) {
    val e = Eruditus.espacements
    val mouvement = Eruditus.mouvement
    val defilement = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val hauteurVisible = maxHeight
        val hauteurVisiblePx = constraints.maxHeight
        var basDuChoixD by remember { mutableIntStateOf(0) }
        var hautDuVerdict by remember { mutableIntStateOf(0) }
        val corrigee = q.resultat != null
        // Apparition du verdict (`duree.resultat`) : elle mène aussi l'ouverture de l'explication et
        // le défilement. Une question affichée directement corrigée montre l'état final.
        val apparition = remember { Animatable(if (corrigee) 1f else 0f) }
        LaunchedEffect(corrigee) {
            if (!corrigee) apparition.snapTo(0f) else if (apparition.value < 1f) apparition.animateTo(1f, mouvement.resultat())
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(defilement),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = hauteurVisible)
                    .padding(start = e.ecran, end = e.ecran, top = e.blocs),
                verticalArrangement = Arrangement.spacedBy(e.blocs),
            ) {
                Text(
                    q.enonce,
                    modifier = Modifier
                        .padding(top = e.icone)
                        .semantics { heading() },
                    style = Eruditus.typo.enonce,
                    color = Eruditus.couleurs.texte,
                )
                Spacer(Modifier.weight(1f))
                Column(
                    Modifier.onPlaced { basDuChoixD = (it.positionInParent().y + it.size.height).toInt() },
                    verticalArrangement = Arrangement.spacedBy(e.blocs),
                ) {
                    q.choix.forEachIndexed { index, choix ->
                        ChoixReponse(choix.lettre, choix.texte, choix.etat, onClick = { onChoix(index) })
                    }
                }
                ZoneResultat(
                    q,
                    { apparition.value },
                    onSignaler,
                    Modifier
                        .onPlaced { hautDuVerdict = it.positionInParent().y.toInt() }
                        .padding(top = e.icone),
                )
            }
        }
        // 200 % : pendant le chrono, le choix D au bas de la zone visible.
        LaunchedEffect(basDuChoixD, hauteurVisiblePx, corrigee) {
            if (!corrigee && defilement.maxValue > 0) {
                defilement.scrollTo((basDuChoixD - hauteurVisiblePx).coerceIn(0, defilement.maxValue))
            }
        }
        // À la correction, le bas du contenu (source, explication ou place du verdict) au bas de la
        // zone visible, sans que le haut du verdict sorte par le haut (SPEC D70, D101) ; une question
        // affichée directement corrigée y est calée sans mouvement.
        val corrigeeAuDepart = remember { corrigee }
        LaunchedEffect(corrigee, hauteurVisiblePx) {
            if (!corrigee) return@LaunchedEffect
            withFrameNanos { } // la mise en page de la correction (étiquettes des choix, verdict) d'abord
            val cible = { min(defilement.maxValue, hautDuVerdict) }
            if (!corrigeeAuDepart && mouvement.animationsActives) {
                // Le contenu grandit avec l'apparition (explication) : le défilement la suit.
                val depart = defilement.value
                snapshotFlow { apparition.value to cible() }.first { (p, bas) ->
                    defilement.scrollTo(depart + ((bas - depart) * p).roundToInt())
                    p >= 1f
                }
                withFrameNanos { } // la dernière mise en page, explication ouverte
            }
            defilement.scrollTo(cible())
        }
    }
}

/**
 * Ligne de résultat sous le choix D, dans la place de la plus longue possible (invisible), puis
 * l'explication et la source de la question, qui s'ouvrent avec [apparition] (0 → 1).
 */
@Composable
private fun ZoneResultat(q: QuestionPartie, apparition: () -> Float, onSignaler: () -> Unit, modifier: Modifier = Modifier) {
    val e = Eruditus.espacements
    val c = Eruditus.couleurs
    val resultat = q.resultat
    Box(modifier.fillMaxWidth()) {
        q.resultatsPossibles.forEach { possible ->
            Box(
                Modifier
                    .graphicsLayer { alpha = 0f }
                    .clearAndSetSemantics { },
            ) { LigneResultat(possible) }
        }
        if (resultat != null) {
            Column(
                Modifier.graphicsLayer {
                    alpha = apparition()
                    translationY = (1 - apparition()) * MouvementEruditus.RESULTAT_DECALAGE.toPx()
                },
            ) {
                LigneResultat(resultat)
                if (q.explication != null || q.source != null || q.signalement != null) {
                    Column(
                        Modifier
                            .layout { mesurable, contraintes ->
                                // Hauteur ouverte avec l'apparition : choix et verdict remontent d'un seul mouvement.
                                val placeable = mesurable.measure(contraintes)
                                layout(placeable.width, (placeable.height * apparition()).roundToInt()) { placeable.place(0, 0) }
                            }
                            .padding(top = e.blocs),
                        verticalArrangement = Arrangement.spacedBy(e.decalage),
                    ) {
                        q.explication?.let { Text(it, style = Eruditus.typo.corps, color = c.texte) }
                        q.source?.let { Text(it, style = Eruditus.typo.petit, color = c.texteSecondaire) }
                        q.signalement?.let { s ->
                            if (s.fait) {
                                // Même hauteur que le lien : rien ne saute quand il devient ce texte.
                                Box(
                                    Modifier
                                        .defaultMinSize(minHeight = Eruditus.tailles.cible)
                                        .padding(horizontal = e.decalage)
                                        .semantics { liveRegion = LiveRegionMode.Polite },
                                    contentAlignment = Alignment.CenterStart,
                                ) { Text(s.libelle, style = Eruditus.typo.petit, color = c.texteSecondaire) }
                            } else {
                                Lien(s.libelle, onSignaler)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * La nouvelle question n'arrive qu'une fois l'ancienne partie (`duree.sortie`, 130 ms) : le ressort
 * d'arrivée attend la fin de la sortie.
 */
private fun apresLaSortie(spec: FiniteAnimationSpec<IntOffset>, actives: Boolean): FiniteAnimationSpec<IntOffset> =
    if (actives) Retardee(DUREE_SORTIE_MS, spec) else spec

private const val DUREE_SORTIE_MS = 130

private class Retardee<T>(private val delaiMs: Int, private val spec: FiniteAnimationSpec<T>) : FiniteAnimationSpec<T> {
    override fun <V : AnimationVector> vectorize(converter: TwoWayConverter<T, V>): VectorizedFiniteAnimationSpec<V> =
        RetardeeVectorisee(delaiMs * 1_000_000L, spec.vectorize(converter))
}

private class RetardeeVectorisee<V : AnimationVector>(
    private val delaiNs: Long,
    private val spec: VectorizedFiniteAnimationSpec<V>,
) : VectorizedFiniteAnimationSpec<V> {
    override fun getValueFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V =
        if (playTimeNanos < delaiNs) initialValue else spec.getValueFromNanos(playTimeNanos - delaiNs, initialValue, targetValue, initialVelocity)

    override fun getVelocityFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V =
        if (playTimeNanos < delaiNs) initialVelocity else spec.getVelocityFromNanos(playTimeNanos - delaiNs, initialValue, targetValue, initialVelocity)

    override fun getDurationNanos(initialValue: V, targetValue: V, initialVelocity: V): Long =
        delaiNs + spec.getDurationNanos(initialValue, targetValue, initialVelocity)
}

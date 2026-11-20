package com.eruditus.android.ui.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/** Décélération accentuée de Material 3 (0.05, 0.7, 0.1, 1). */
val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** Accélération accentuée de Material 3 (0.3, 0, 0.8, 0.15). */
val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** Tracé du ✓ (0.2, 0, 0, 1). */
private val Trace: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Ressorts et durées (DESIGN.md § 6). Calme : seuls bougent le chrono, la correction et le
 * passage à la question suivante ; ailleurs, jamais de ressort.
 *
 * [animationsActives] faux (animations désactivées dans Android) : chaque token devient `snap()`,
 * les indicateurs ne tournent plus ; couleur, icône, libellé et vibration restent. Vérifié par
 * `MouvementTest`.
 */
@Immutable
class MouvementEruditus(val animationsActives: Boolean) {

    private fun <T> ou(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (animationsActives) spec else snap()

    /** Indicateurs de chargement : tournent, ou arc fixe. */
    val indicateursTournent: Boolean get() = animationsActives

    /** Retour de la bonne réponse à 1 après [REBOND_ECHELLE]. */
    fun <T> rebond(): FiniteAnimationSpec<T> = ou(spring(dampingRatio = 0.45f, stiffness = 600f))

    /** Montée de la bonne réponse à [REBOND_ECHELLE], avant le rebond. */
    fun <T> elanRebond(): FiniteAnimationSpec<T> = ou(tween(80, easing = FastOutSlowInEasing))

    /** « Non » horizontal de la mauvaise réponse, lancé à [NON_VITESSE_DP_PAR_S] : 8 dp au plus. */
    fun <T> non(): FiniteAnimationSpec<T> = ou(spring(dampingRatio = 0.25f, stiffness = 1200f))

    /** Question suivante, depuis le bord droit (dépasse d'environ 3,8 %). */
    fun <T> arrivee(): FiniteAnimationSpec<T> = ou(spring(dampingRatio = 0.72f, stiffness = 380f))

    /** Couleur d'un choix, d'un bouton. */
    fun <T> couleur(): FiniteAnimationSpec<T> = ou(tween(120, easing = LinearEasing))

    /** Le vert part du centre de la lettre et couvre la case. */
    fun <T> remplissage(): FiniteAnimationSpec<T> = ou(tween(280, easing = EmphasizedDecelerate))

    /** ✓ dessiné ; [delai] après le début de la correction. */
    fun <T> trait(delai: Int = 0): FiniteAnimationSpec<T> = ou(tween(220, delayMillis = delai, easing = Trace))

    /** ✗ : fondu et échelle [CROIX_ECHELLE] → 1. */
    fun <T> croix(): FiniteAnimationSpec<T> = ou(tween(160, easing = EmphasizedDecelerate))

    /** Bonne réponse allumée après une erreur. */
    fun <T> revelation(): FiniteAnimationSpec<T> = ou(tween(220, delayMillis = 320))

    /** Bonne réponse allumée au temps écoulé. */
    fun <T> fonduBonneReponse(): FiniteAnimationSpec<T> = ou(tween(220))

    /** Ligne de résultat et bouton : fondu et [RESULTAT_DECALAGE] vers le haut. */
    fun <T> resultat(): FiniteAnimationSpec<T> = ou(tween(200, delayMillis = 160))

    /** Question qui part : fondu, [SORTIE_DECALAGE] à gauche. */
    fun <T> sortie(): FiniteAnimationSpec<T> = ou(tween(130, easing = EmphasizedAccelerate))

    /** Barre du chrono qui se remplit. */
    fun <T> chrono(): FiniteAnimationSpec<T> = ou(tween(300, easing = EmphasizedDecelerate))

    /** Pile d'écrans, entrée : fondu et [ECRAN_DECALAGE]. */
    fun <T> ecranEntree(): FiniteAnimationSpec<T> = ou(tween(200, easing = EmphasizedDecelerate))

    /** Pile d'écrans, sortie. */
    fun <T> ecranSortie(): FiniteAnimationSpec<T> = ou(tween(100))

    /** Boîte : fondu et échelle [BOITE_ECHELLE] → 1, sortie à l'envers. */
    fun <T> boite(): FiniteAnimationSpec<T> = ou(tween(150))

    /** Message en haut : déplié (ou replié) et fondu. */
    fun <T> message(): FiniteAnimationSpec<T> = ou(tween(200))

    companion object {
        const val REBOND_ECHELLE = 1.03f

        /** Départ de l'échelle après le début du remplissage. */
        const val REBOND_DEPART_MS = 120L
        const val NON_VITESSE_DP_PAR_S = 390f
        const val CROIX_ECHELLE = 0.6f
        const val BOITE_ECHELLE = 0.95f

        /** ✓ de la bonne réponse choisie, ou au temps écoulé. */
        const val COCHE_DELAI_MS = 140

        /** ✓ de la bonne réponse révélée après une erreur. */
        const val COCHE_DELAI_APRES_ERREUR_MS = 440
        val RESULTAT_DECALAGE = 6.dp
        val SORTIE_DECALAGE = 24.dp
        val ECRAN_DECALAGE = 30.dp
    }
}

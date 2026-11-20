package com.eruditus.android.ui.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Vibrations (DESIGN.md § 6, D28, D41) : retour haptique du système, sans permission, soumis au
 * réglage « Vibreur au toucher ». Aucun son.
 *
 * Avant Android 11, Compose passe par `ViewCompat.performHapticFeedback`, qui joue `VIRTUAL_KEY`
 * (tic court) pour Confirm et `LONG_PRESS` (plus appuyé) pour Reject.
 */
@Stable
class Vibrations internal constructor(private val retour: HapticFeedback) {
    /** Bonne réponse, à la correction. */
    fun confirmation() = retour.performHapticFeedback(HapticFeedbackType.Confirm)

    /** Mauvaise réponse (au début du « non ») ou temps écoulé. */
    fun rejet() = retour.performHapticFeedback(HapticFeedbackType.Reject)
}

@Composable
fun rememberVibrations(): Vibrations {
    val retour = LocalHapticFeedback.current
    return remember(retour) { Vibrations(retour) }
}

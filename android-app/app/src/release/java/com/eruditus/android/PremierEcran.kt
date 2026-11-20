package com.eruditus.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eruditus.android.ui.designsystem.theme.Eruditus
import com.eruditus.android.ui.designsystem.theme.EruditusTheme

/**
 * Build release (D42) : un écran vide au thème de l'app, jusqu'à l'étape Écrans (SPEC § 4.2).
 * Debug et preview ont le leur, le catalogue, dans `src/catalogue/`.
 */
@Composable
fun PremierEcran() {
    EruditusTheme {
        Box(Modifier.fillMaxSize().background(Eruditus.couleurs.fond))
    }
}

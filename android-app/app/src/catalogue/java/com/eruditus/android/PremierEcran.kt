package com.eruditus.android

import androidx.compose.runtime.Composable
import com.eruditus.android.catalogue.Catalogue

/** Builds debug et preview (D42) : l'app s'ouvre sur le catalogue du design system. */
@Composable
fun PremierEcran() {
    Catalogue()
}

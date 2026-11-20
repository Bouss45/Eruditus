package com.eruditus.android.catalogue

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * Le catalogue du design system derrière sa propre icône, « Catalogue » (D58) : déclaré par le
 * manifeste de `src/catalogue/`, ajouté aux seuls builds debug et preview. L'app, elle, s'ouvre
 * sur `MainActivity` dans les trois builds.
 */
class CatalogueActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Catalogue() }
    }
}

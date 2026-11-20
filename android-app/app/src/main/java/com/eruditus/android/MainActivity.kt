package com.eruditus.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.eruditus.android.ui.app.AppEruditus
import com.eruditus.android.ui.app.ModeleApp
import com.eruditus.android.ui.designsystem.theme.EruditusTheme

/**
 * Seule activité de l'app, dans les trois builds (D58). L'écran de lancement d'Android
 * (DESIGN.md § 10) reste affiché le temps de l'aiguillage (spec/ecrans.md § 1.2, § 2), puis
 * l'écran où il mène. Le catalogue du design system a sa propre activité, en debug et preview.
 */
class MainActivity : ComponentActivity() {

    private val modele: ModeleApp by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val lancement = installSplashScreen()
        super.onCreate(savedInstanceState)
        lancement.setKeepOnScreenCondition { modele.demarrage }
        enableEdgeToEdge()
        setContent { EruditusTheme { AppEruditus(modele) } }
    }
}

package com.eruditus.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

/**
 * Seule activité. Écran de démarrage d'Android (DESIGN.md § 10), puis [PremierEcran] : le
 * catalogue du design system en debug et en preview, un écran vide au thème en release (D42).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PremierEcran() }
    }
}

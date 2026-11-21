package com.eruditus.android

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.eruditus.android.parametres.FichierPreferences
import com.eruditus.android.parametres.ReglesTheme
import com.eruditus.android.parametres.Theme
import com.eruditus.android.ui.app.AppEruditus
import com.eruditus.android.ui.app.ModeleApp
import com.eruditus.android.ui.designsystem.theme.EruditusTheme
import java.io.File

/**
 * Seule activité de l'app, dans les trois builds (D58). L'écran de lancement d'Android
 * (DESIGN.md § 10) reste affiché le temps de l'aiguillage (spec/ecrans.md § 1.2, § 2), puis
 * l'écran où il mène. Le catalogue du design system a sa propre activité, en debug et preview.
 *
 * Thème (D77) : appliqué comme mode nuit de l'app, pour que tout le suive (Compose, fenêtre,
 * barres système, écran de démarrage). À partir d'Android 12, Android le garde pour l'app
 * (`setApplicationNightMode`) ; avant, l'activité impose le `uiMode` lu dans le fichier (D78).
 */
class MainActivity : ComponentActivity() {

    private val modele: ModeleApp by viewModels()

    /** Le thème appliqué à cette activité, lu dans le fichier avant le premier affichage. */
    private var themeApplique = Theme.Systeme

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        themeApplique = FichierPreferences(File(base.noBackupFilesDir, FichierPreferences.NOM)).lire().theme
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            ReglesTheme.uiModeImpose(base.resources.configuration.uiMode, themeApplique)?.let { uiMode ->
                applyOverrideConfiguration(Configuration().apply { this.uiMode = uiMode })
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val lancement = installSplashScreen()
        super.onCreate(savedInstanceState)
        // À chaque lancement (jamais après une recréation), Android 12 et plus reçoit de nouveau
        // le choix du fichier, qui fait foi : un fichier effacé ramène « Système ».
        if (savedInstanceState == null) donnerModeNuit(themeApplique)
        lancement.setKeepOnScreenCondition { modele.demarrage }
        enableEdgeToEdge()
        setContent {
            val theme = modele.theme
            LaunchedEffect(theme) { if (theme != themeApplique) appliquer(theme) }
            EruditusTheme { AppEruditus(modele) }
        }
    }

    /**
     * Un nouveau thème, déjà écrit dans le fichier : Android 12 et plus recrée l'activité si les
     * couleurs changent ; avant, l'activité se recrée elle-même, et relit le fichier.
     */
    private fun appliquer(theme: Theme) {
        val avant = resources.configuration.uiMode
        themeApplique = theme
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            donnerModeNuit(theme)
        } else {
            val voulu = ReglesTheme.uiMode(applicationContext.resources.configuration.uiMode, theme)
            if (ReglesTheme.sombre(voulu) != ReglesTheme.sombre(avant)) recreate()
        }
    }

    /** Android 12 et plus : le mode nuit de l'app, qu'Android garde et applique au démarrage. */
    private fun donnerModeNuit(theme: Theme) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        getSystemService(UiModeManager::class.java)?.setApplicationNightMode(ReglesTheme.modeNuit(theme))
    }
}

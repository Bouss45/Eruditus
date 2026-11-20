package com.eruditus.android.ui.designsystem.theme

import android.animation.ValueAnimator
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.compose.LifecycleResumeEffect

private val TypographieParDefaut = TypographieEruditus()

private val LocalCouleurs = staticCompositionLocalOf { CouleursSombres }
private val LocalTypographie = staticCompositionLocalOf { TypographieParDefaut }
private val LocalEspacements = staticCompositionLocalOf { Espacements() }
private val LocalRayons = staticCompositionLocalOf { Rayons() }
private val LocalTailles = staticCompositionLocalOf { Tailles() }
private val LocalMouvement = staticCompositionLocalOf { MouvementEruditus(animationsActives = true) }

/**
 * Thème d'Eruditus (DESIGN.md § 2) : sombre par défaut, clair quand le téléphone est en clair,
 * sans réglage dans l'app (D27) ; pas de couleurs dynamiques. [animationsActives] suit le réglage
 * d'Android, relu à chaque retour dans l'app ; le catalogue peut le forcer.
 *
 * Fournit aussi un `MaterialTheme` rempli par les tokens (colonne M3 de DESIGN.md § 3), pour les
 * rares composants Material utilisés à l'intérieur (indicateur de tirage).
 */
@Composable
fun EruditusTheme(
    sombre: Boolean = isSystemInDarkTheme(),
    animationsActives: Boolean = animationsActivesDansAndroid(),
    content: @Composable () -> Unit,
) {
    val couleurs = if (sombre) CouleursSombres else CouleursClaires
    val mouvement = remember(animationsActives) { MouvementEruditus(animationsActives) }
    CompositionLocalProvider(
        LocalCouleurs provides couleurs,
        LocalTypographie provides TypographieParDefaut,
        LocalMouvement provides mouvement,
    ) {
        MaterialTheme(colorScheme = couleurs.versColorScheme(), typography = TypographieMaterial) {
            CompositionLocalProvider(
                LocalContentColor provides couleurs.texte,
                LocalTextStyle provides TypographieParDefaut.corps,
                LocalIndication provides IndicationPression(couleurs.pression),
                content = content,
            )
        }
    }
}

/** Accès aux tokens : `Eruditus.couleurs.accent`, `Eruditus.typo.enonce`… */
object Eruditus {
    val couleurs: CouleursEruditus
        @Composable @ReadOnlyComposable
        get() = LocalCouleurs.current

    val typo: TypographieEruditus
        @Composable @ReadOnlyComposable
        get() = LocalTypographie.current

    val espacements: Espacements
        @Composable @ReadOnlyComposable
        get() = LocalEspacements.current

    val rayons: Rayons
        @Composable @ReadOnlyComposable
        get() = LocalRayons.current

    val tailles: Tailles
        @Composable @ReadOnlyComposable
        get() = LocalTailles.current

    val mouvement: MouvementEruditus
        @Composable @ReadOnlyComposable
        get() = LocalMouvement.current
}

/**
 * Animations désactivées dans Android (« Supprimer les animations », échelle d'animation à 0) :
 * `ValueAnimator.areAnimatorsEnabled()`, relu à chaque retour dans l'app. Aperçus : actives (les
 * composants y montrent de toute façon leur état final, DESIGN.md § 6).
 */
@Composable
fun animationsActivesDansAndroid(): Boolean {
    if (LocalInspectionMode.current) return true
    var actives by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    LifecycleResumeEffect(Unit) {
        actives = ValueAnimator.areAnimatorsEnabled()
        onPauseOrDispose { }
    }
    return actives
}

/** Rôles Material 3 tirés des tokens (colonne M3 de DESIGN.md § 3). */
private fun CouleursEruditus.versColorScheme(): ColorScheme {
    val base = if (sombre) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = surAccent,
        primaryContainer = accentTeinte,
        onPrimaryContainer = texte,
        inversePrimary = accent,
        secondary = texteSecondaire,
        onSecondary = fond,
        secondaryContainer = surfaceHaute,
        onSecondaryContainer = texte,
        tertiary = accent,
        onTertiary = surAccent,
        background = fond,
        onBackground = texte,
        surface = fond,
        onSurface = texte,
        surfaceVariant = surface,
        onSurfaceVariant = texteSecondaire,
        surfaceTint = fond,
        inverseSurface = texte,
        inverseOnSurface = fond,
        error = rouge,
        onError = surRouge,
        errorContainer = rougeConteneur,
        onErrorContainer = surRougeConteneur,
        outline = contour,
        outlineVariant = separateur,
        scrim = voile,
        surfaceBright = surfaceHaute,
        surfaceDim = fond,
        surfaceContainerLowest = fond,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceHaute,
        surfaceContainerHighest = surfaceHaute,
    )
}

/** Typographie Material : les styles d'Eruditus, pour les composants Material éventuels. */
private val TypographieMaterial = Typography(
    headlineSmall = TypographieParDefaut.titre1,
    titleLarge = TypographieParDefaut.titreBarre,
    titleMedium = TypographieParDefaut.titreBoite,
    bodyLarge = TypographieParDefaut.corps,
    bodyMedium = TypographieParDefaut.corps,
    bodySmall = TypographieParDefaut.petit,
    labelLarge = TypographieParDefaut.bouton,
    labelMedium = TypographieParDefaut.libelle,
    labelSmall = TypographieParDefaut.petit,
)

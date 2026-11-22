package com.eruditus.android.catalogue

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.em
import com.eruditus.android.ui.designsystem.theme.Eruditus
import com.eruditus.android.ui.designsystem.theme.EruditusTheme

/*
 * Aperçus Compose du design system (DESIGN.md § 12, D42) : source set `catalogue`, compilé dans
 * les seuls builds debug et preview. Rendus en PNG par `compose-preview` (render_preview) pour les
 * comparer aux planches de `design/claude-design/`.
 */

/** Largeur d'un composant sur la planche « Composants et états » (382, marges de 16 comprises). */
const val LARGEUR_PLANCHE = 382

/**
 * Les aperçus de composant s'ajustent à leur contenu, jusqu'à 800 de haut : les composants aux
 * nombreux états ont plusieurs aperçus. À 200 %, l'aperçu a une hauteur fixe, sur le fond sombre.
 */
const val HAUTEUR_200 = 1800

/** `fond` sombre, derrière les aperçus à 200 %. */
const val FOND_SOMBRE = 0xFF080A0CL

/** « Clair 200 % » d'un gabarit, en plus de [ApercusGabarit] (Elo : Résultat, Classement, Accueil). */
@Preview(name = "Clair 200 %", group = "Gabarits 200 %", locale = "fr", widthDp = 390, heightDp = 844 - 44 - 24, fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL)
annotation class ApercusGabaritClair200

/** « Clair 200 % » d'un composant, en plus de [ApercusTexte] (`LigneClassement`, `Statistiques`). */
@Preview(name = "Clair 200 %", group = "Composants 200 %", locale = "fr", widthDp = LARGEUR_PLANCHE, heightDp = HAUTEUR_200, fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL)
annotation class ApercusComposantClair200

/** Un composant sans texte : sombre et clair. */
@Preview(name = "Sombre", group = "Composants", locale = "fr", widthDp = LARGEUR_PLANCHE, uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(name = "Clair", group = "Composants", locale = "fr", widthDp = LARGEUR_PLANCHE, uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL)
annotation class ApercusComposant

/** Un composant avec du texte : sombre, clair, et sombre à 200 %. */
@Preview(name = "Sombre", group = "Composants", locale = "fr", widthDp = LARGEUR_PLANCHE, uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(name = "Clair", group = "Composants", locale = "fr", widthDp = LARGEUR_PLANCHE, uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(name = "Sombre 200 %", group = "Composants 200 %", locale = "fr", widthDp = LARGEUR_PLANCHE, heightDp = HAUTEUR_200, showBackground = true, backgroundColor = FOND_SOMBRE, fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
annotation class ApercusTexte

/**
 * Zone utile de l'écran des maquettes (`Ecran.dc.html`, 390 × 844) : sans la barre d'état (44) ni la
 * barre de navigation (24), que les aperçus n'ont pas.
 */
const val HAUTEUR_ECRAN = 844 - 44 - 24

/** Un gabarit, dans la zone utile de l'écran des maquettes : sombre, clair, sombre à 200 %. */
@Preview(name = "Sombre", group = "Gabarits", locale = "fr", widthDp = 390, heightDp = HAUTEUR_ECRAN, uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(name = "Clair", group = "Gabarits", locale = "fr", widthDp = 390, heightDp = HAUTEUR_ECRAN, uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(name = "Sombre 200 %", group = "Gabarits 200 %", locale = "fr", widthDp = 390, heightDp = HAUTEUR_ECRAN, fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
annotation class ApercusGabarit

/** Un composant et ses états, sur le fond, marges de 16, comme sur la planche. */
@Composable
fun ApercuComposant(contenu: @Composable ColumnScope.() -> Unit) {
    EruditusTheme(sombre = isSystemInDarkTheme()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Eruditus.couleurs.fond)
                .padding(Eruditus.espacements.groupe),
            verticalArrangement = Arrangement.spacedBy(Eruditus.espacements.blocs),
            content = contenu,
        )
    }
}

/** Un gabarit, au thème de l'aperçu. */
@Composable
fun ApercuGabarit(contenu: @Composable () -> Unit) {
    EruditusTheme(sombre = isSystemInDarkTheme(), content = contenu)
}

/** Légende d'un état, comme sur les planches (12, 600, `texte-secondaire`). */
@Composable
fun Legende(texte: String, premiere: Boolean = false) {
    Text(
        texte,
        modifier = if (premiere) Modifier else Modifier.padding(top = Eruditus.espacements.icone),
        style = Eruditus.typo.petit.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.02.em),
        color = Eruditus.couleurs.texteSecondaire,
    )
}

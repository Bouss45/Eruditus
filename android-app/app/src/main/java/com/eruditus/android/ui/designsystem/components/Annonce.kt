package com.eruditus.android.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eruditus.android.ui.designsystem.theme.Eruditus
import com.eruditus.android.ui.designsystem.theme.IconeTrait
import com.eruditus.android.ui.designsystem.theme.Icones

/**
 * `Annonce` (DESIGN.md § 7) : `accent-teinte`, rayon 20, marge 16, écart 12 ; titre : icône de
 * mise à jour 20 `texte` + `corps` 600, annoncé comme titre ; [texte] en `corps`
 * `texte-secondaire` ; [actions] : deux petits `BoutonSecondaire` dans une `RangeeBoutons`,
 * l'action sûre à gauche. Titre et texte forment une zone annoncée à l'arrivée. Mise à jour
 * disponible sur l'Accueil (SPEC D62) ; son entrée et sa sortie sont celles du message en haut.
 */
@Composable
fun Annonce(
    titre: String,
    texte: String,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Eruditus.couleurs
    val styleTitre = Eruditus.typo.corps.copy(fontWeight = FontWeight.SemiBold)
    val hauteurLigne = with(LocalDensity.current) { styleTitre.lineHeight.toDp() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.accentTeinte, Eruditus.rayons.groupe)
            .padding(Eruditus.espacements.groupe),
        verticalArrangement = Arrangement.spacedBy(Eruditus.espacements.blocs),
    ) {
        Column(
            Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(Eruditus.espacements.decalage),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Eruditus.espacements.icone)) {
                Box(Modifier.height(hauteurLigne), contentAlignment = Alignment.Center) {
                    IconeTrait(Icones.MiseAJour, 20.dp, c.texte)
                }
                Text(titre, Modifier.weight(1f).semantics { heading() }, style = styleTitre, color = c.texte)
            }
            Text(texte, style = Eruditus.typo.corps, color = c.texteSecondaire)
        }
        RangeeBoutons(content = actions)
    }
}

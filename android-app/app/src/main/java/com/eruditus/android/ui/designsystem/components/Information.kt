package com.eruditus.android.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eruditus.android.R
import com.eruditus.android.ui.designsystem.theme.Eruditus
import com.eruditus.android.ui.designsystem.theme.IconeTrait
import com.eruditus.android.ui.designsystem.theme.Icones

/** Une valeur de [Statistiques] ; [libelle] facultatif (Résultat : « +2 points » seul). */
@Immutable
data class Statistique(val valeur: String, val libelle: String? = null)

/**
 * `Statistiques` (DESIGN.md § 7) : libellé `petit` 600 `texte-secondaire` + valeur `chiffre`,
 * écart 4 ; 40 entre les paires, 16 quand elles passent à la ligne.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Statistiques(statistiques: List<Statistique>, modifier: Modifier = Modifier) {
    val c = Eruditus.couleurs
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(40.dp),
        verticalArrangement = Arrangement.spacedBy(Eruditus.espacements.groupe),
    ) {
        statistiques.forEach { statistique ->
            Column(
                Modifier.semantics(mergeDescendants = true) { },
                verticalArrangement = Arrangement.spacedBy(Eruditus.espacements.decalage),
            ) {
                statistique.libelle?.let {
                    Text(it, style = Eruditus.typo.petit.copy(fontWeight = FontWeight.SemiBold), color = c.texteSecondaire)
                }
                Text(statistique.valeur, style = Eruditus.typo.chiffre, color = c.texte)
            }
        }
    }
}

/** `Marque` (DESIGN.md § 7) : « Eruditus » en `marque`, [phrase] en `corps` `texte-secondaire`, écart 14. */
@Composable
fun Marque(phrase: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            stringResource(R.string.ds_marque),
            modifier = Modifier.semantics { heading() },
            style = Eruditus.typo.marque,
            color = Eruditus.couleurs.texte,
        )
        Text(phrase, style = Eruditus.typo.corps, color = Eruditus.couleurs.texteSecondaire)
    }
}

/** Icônes d'état du gabarit Information (DESIGN.md § 8). */
enum class EtatSysteme { Reseau, Serveur, MiseAJour }

/** `IconeEtat` (DESIGN.md § 7) : 48, trait 1,5, `texte-secondaire`, décorative. */
@Composable
fun IconeEtat(etat: EtatSysteme, modifier: Modifier = Modifier) {
    val image = when (etat) {
        EtatSysteme.Reseau -> Icones.Reseau
        EtatSysteme.Serveur -> Icones.Serveur
        EtatSysteme.MiseAJour -> Icones.MiseAJour
    }
    IconeTrait(image, 48.dp, Eruditus.couleurs.texteSecondaire, modifier)
}

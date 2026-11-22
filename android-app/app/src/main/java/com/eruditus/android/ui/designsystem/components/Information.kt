package com.eruditus.android.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eruditus.android.R
import com.eruditus.android.ui.designsystem.theme.Eruditus
import com.eruditus.android.ui.designsystem.theme.IconeTrait
import com.eruditus.android.ui.designsystem.theme.Icones

/**
 * Une valeur de [Statistiques] ; [libelle] facultatif (Résultat : la variation de l'Elo seule) ;
 * [lecture] : ce que TalkBack lit à la place du libellé et de la valeur (« Elo, plus 14 »).
 */
@Immutable
data class Statistique(val valeur: String, val libelle: String? = null, val lecture: String? = null)

/**
 * `Statistiques` (DESIGN.md § 7) : libellé `petit` 600 `texte-secondaire` + valeur `chiffre`,
 * écart 4 ; 40 entre les paires, 16 quand elles passent à la ligne. [premiereEnGrand] (D117) : la
 * première valeur seule sur sa ligne, en `chiffre` ; les suivantes dessous, côte à côte, en
 * `choix` 600.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Statistiques(statistiques: List<Statistique>, modifier: Modifier = Modifier, premiereEnGrand: Boolean = false) {
    val c = Eruditus.couleurs
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(40.dp),
        verticalArrangement = Arrangement.spacedBy(Eruditus.espacements.groupe),
    ) {
        statistiques.forEachIndexed { i, statistique ->
            val grande = !premiereEnGrand || i == 0
            Column(
                (if (premiereEnGrand && i == 0) Modifier.fillMaxWidth() else Modifier).let {
                    // Lecture donnée : elle remplace le libellé et la valeur.
                    if (statistique.lecture != null) it.clearAndSetSemantics { contentDescription = statistique.lecture } else it.semantics(mergeDescendants = true) { }
                },
                verticalArrangement = Arrangement.spacedBy(Eruditus.espacements.decalage),
            ) {
                statistique.libelle?.let {
                    Text(it, style = Eruditus.typo.petit.copy(fontWeight = FontWeight.SemiBold), color = c.texteSecondaire)
                }
                Text(
                    statistique.valeur,
                    style = if (grande) Eruditus.typo.chiffre else Eruditus.typo.choix.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "'tnum'"),
                    color = c.texte,
                )
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

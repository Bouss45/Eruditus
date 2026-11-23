package com.eruditus.android.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eruditus.android.ui.designsystem.theme.Eruditus
import com.eruditus.android.ui.designsystem.theme.IconeTrait
import com.eruditus.android.ui.designsystem.theme.Icones

/** Genres de verdict (spec/ecrans.md § 9, raisons du serveur, SPEC D32). */
enum class GenreResultat {
    /** `gagne`, `deja_reussie` : ✓ `vert`. */
    Bonne,

    /** `fausse` : ✗ `rouge`. */
    Fausse,

    /** `sans_reponse`, `hors_delai` : horloge `texte`. */
    TempsEcoule,

    /** « Question non répondue » (détail d'une partie, D132) : `texte`, sans icône. */
    NonRepondue,
}

/**
 * Un résultat à afficher : [verdict] seul sur sa ligne, [detail] dessous. [annonce] : ce que lit
 * TalkBack, s'il diffère du texte (« … La bonne réponse était C, Salvador Dalí. »).
 */
@Immutable
data class Resultat(
    val genre: GenreResultat,
    val verdict: String,
    val detail: String? = null,
    val annonce: String? = null,
)

/**
 * `LigneResultat` (DESIGN.md § 7) : icône de 20 + verdict (`verdict` 700) seul sur sa ligne ;
 * détail (`verdict` 400, `texte-secondaire`) dessous, sur toute la largeur ; écart 2. Zone
 * annoncée à son apparition, sauf [annoncee] faux (le détail d'une partie, déjà à l'écran). L'écran
 * la fait suivre d'un `BoutonSecondaire`.
 */
@Composable
fun LigneResultat(resultat: Resultat, modifier: Modifier = Modifier, annoncee: Boolean = true) {
    val c = Eruditus.couleurs
    val style = Eruditus.typo.verdict
    val (icone, couleur) = when (resultat.genre) {
        GenreResultat.Bonne -> Icones.CocheVerdict to c.vert
        GenreResultat.Fausse -> Icones.CroixVerdict to c.rouge
        GenreResultat.TempsEcoule -> Icones.Horloge to c.texte
        GenreResultat.NonRepondue -> null to c.texte
    }
    val annonce = resultat.annonce ?: listOfNotNull(resultat.verdict, resultat.detail).joinToString(" ")
    val hauteurLigne = with(LocalDensity.current) { style.lineHeight.toDp() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                if (annoncee) liveRegion = LiveRegionMode.Polite
                contentDescription = annonce
            },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Eruditus.espacements.icone)) {
            if (icone != null) {
                Box(Modifier.height(hauteurLigne), contentAlignment = Alignment.Center) {
                    IconeTrait(icone, 20.dp, couleur)
                }
            }
            Text(resultat.verdict, Modifier.weight(1f), style = style, color = couleur)
        }
        if (resultat.detail != null) {
            Text(
                resultat.detail,
                style = style.copy(fontWeight = FontWeight.Normal),
                color = c.texteSecondaire,
            )
        }
    }
}

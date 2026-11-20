package com.eruditus.android.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eruditus.android.R
import com.eruditus.android.ui.designsystem.theme.Eruditus

/**
 * `CodeRecuperation` (DESIGN.md § 7) : grille 2 × 2 à toutes les tailles de texte, écarts 14 × 12,
 * marges 22 × 16, rayon 20, `surface`, contour `separateur` ; groupes en `code` centrés, sans
 * tiret. Une seule description pour TalkBack, lue caractère par caractère.
 *
 * [code] : tel que le serveur le renvoie, `XXXX-XXXX-XXXX-XXXX` (SPEC D35).
 */
@Composable
fun CodeRecuperation(code: String, modifier: Modifier = Modifier) {
    val groupes = groupesDuCode(code)
    val description = stringResource(R.string.ds_code_recuperation, codeEpele(groupes))
    val forme = Eruditus.rayons.groupe
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Eruditus.couleurs.surface, forme)
            .border(Eruditus.tailles.contour, Eruditus.couleurs.separateur, forme)
            .padding(horizontal = 16.dp, vertical = 22.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        groupes.chunked(2).forEach { ligne ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ligne.forEach { groupe ->
                    Text(
                        groupe,
                        modifier = Modifier.weight(1f),
                        style = Eruditus.typo.code,
                        color = Eruditus.couleurs.texte,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Les groupes de 4 du code, sans tiret ni espace, en majuscules. Vérifié par `CodeRecuperationTest`. */
fun groupesDuCode(code: String): List<String> =
    code.filter { it.isLetterOrDigit() }.uppercase().chunked(4)

/** « 7 K 3 M, Q 9 X A, … » : TalkBack lit chaque caractère, groupe par groupe. */
fun codeEpele(groupes: List<String>): String =
    groupes.joinToString(", ") { groupe -> groupe.toList().joinToString(" ") }

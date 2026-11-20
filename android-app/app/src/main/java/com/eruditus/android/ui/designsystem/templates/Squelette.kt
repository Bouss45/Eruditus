package com.eruditus.android.ui.designsystem.templates

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eruditus.android.ui.designsystem.components.Message
import com.eruditus.android.ui.designsystem.components.ZoneMessage
import com.eruditus.android.ui.designsystem.components.ZoneTirage
import com.eruditus.android.ui.designsystem.theme.Eruditus

/**
 * Squelette commun des gabarits (DESIGN.md § 5 et § 8), bord à bord : barre facultative sous la
 * barre d'état ; message en haut ; contenu qui défile ; zone du bas fixe, à 16 au-dessus de la
 * barre de navigation ou du clavier. À 200 %, seul le contenu défile.
 *
 * Le contenu a au moins la hauteur visible : un `Spacer(Modifier.weight(1f))` y fait « l'espace »
 * des gabarits. Marges : 20 sur les côtés ; contenu à 12 sous la barre, 24 sans barre.
 */
/** Tirer vers le bas pour recharger (Classement). */
data class Tirage(val enCours: Boolean, val onTirage: () -> Unit)

@Composable
internal fun Squelette(
    modifier: Modifier = Modifier,
    barre: (@Composable () -> Unit)? = null,
    message: Message? = null,
    defilement: ScrollState = rememberScrollState(),
    margeHautContenu: Dp? = null,
    tirage: Tirage? = null,
    bas: (@Composable ColumnScope.() -> Unit)? = null,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    val e = Eruditus.espacements
    Column(
        modifier
            .fillMaxSize()
            .background(Eruditus.couleurs.fond)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
            barre?.invoke()
            ZoneMessage(
                message,
                if (barre != null) PaddingValues(start = e.ecran, end = e.ecran, top = e.icone) else PaddingValues(horizontal = e.ecran),
            )
        }
        val zone: @Composable (@Composable () -> Unit) -> Unit = { interieur ->
            if (tirage == null) {
                interieur()
            } else {
                ZoneTirage(tirage.enCours, tirage.onTirage, Modifier.fillMaxSize()) { interieur() }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val hauteurVisible = maxHeight
            zone {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(defilement),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = hauteurVisible)
                            .padding(
                                start = e.ecran,
                                end = e.ecran,
                                top = margeHautContenu ?: if (barre != null) e.blocs else e.boite,
                                bottom = if (bas == null) e.groupe else 0.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(e.blocs),
                        content = contenu,
                    )
                }
            }
        }
        if (bas != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom))
                    .padding(start = e.ecran, end = e.ecran, top = e.blocs, bottom = e.groupe),
                verticalArrangement = Arrangement.spacedBy(e.blocs),
                content = bas,
            )
        } else {
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)))
        }
    }
}

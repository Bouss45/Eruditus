package com.eruditus.android.ui.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.dp
import com.eruditus.android.R
import com.eruditus.android.ui.designsystem.theme.Eruditus

/**
 * `ChargementEcran` (DESIGN.md § 7) : `Indicateur` de 40, trait 4, `texte-secondaire`, centré dans
 * la zone de contenu, sous la barre du haut. Premier chargement d'un écran ; TalkBack : « Chargement ».
 */
@Composable
fun ChargementEcran(modifier: Modifier = Modifier) {
    val chargement = stringResource(R.string.ds_chargement)
    Box(
        modifier
            .fillMaxSize()
            .clearAndSetSemantics {
                contentDescription = chargement
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
        contentAlignment = Alignment.Center,
    ) {
        Indicateur(Eruditus.couleurs.texteSecondaire, taille = 40.dp, trait = 4.dp)
    }
}

/**
 * Tirage (`ChargementEcran`, DESIGN.md § 7) : tirer vers le bas recharge (Classement) ; indicateur
 * de tirage Material 3, fond `surface-haute`, arc `texte-secondaire`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneTirage(
    enCours: Boolean,
    onTirage: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val etat = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = enCours,
        onRefresh = onTirage,
        modifier = modifier,
        state = etat,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = etat,
                isRefreshing = enCours,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = Eruditus.couleurs.surfaceHaute,
                color = Eruditus.couleurs.texteSecondaire,
            )
        },
        content = content,
    )
}

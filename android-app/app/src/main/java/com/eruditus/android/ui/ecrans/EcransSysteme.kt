package com.eruditus.android.ui.ecrans

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.eruditus.android.R
import com.eruditus.android.ui.designsystem.components.BoutonPrincipal
import com.eruditus.android.ui.designsystem.components.EtatSysteme
import com.eruditus.android.ui.designsystem.templates.GabaritInformation
import com.eruditus.android.ui.designsystem.theme.Eruditus

/*
 * États du système (spec/ecrans.md § 13, § 14) : gabarit Information avec son icône d'état.
 */

/**
 * § 13 Pas de connexion (D20, D53) : icône réseau ou serveur et son texte ; « Réessayer » relance
 * l'aiguillage, indicateur dans le bouton ([enCours]).
 */
@Composable
fun EcranPasDeConnexion(serveur: Boolean, enCours: Boolean, onReessayer: () -> Unit) {
    GabaritInformation(
        icone = if (serveur) EtatSysteme.Serveur else EtatSysteme.Reseau,
        titre = stringResource(R.string.pas_de_connexion_titre),
        principal = { BoutonPrincipal(stringResource(R.string.reessayer), onReessayer, chargement = enCours) },
    ) {
        Text(
            stringResource(if (serveur) R.string.pas_de_connexion_serveur else R.string.pas_de_connexion_reseau),
            style = Eruditus.typo.corps,
            color = Eruditus.couleurs.texteSecondaire,
        )
    }
}

/** § 14 Mise à jour nécessaire (D21, D60) : rien d'autre n'est accessible. */
@Composable
fun EcranMiseAJour(onMettreAJour: () -> Unit) {
    GabaritInformation(
        icone = EtatSysteme.MiseAJour,
        titre = stringResource(R.string.mise_a_jour_titre),
        principal = { BoutonPrincipal(stringResource(R.string.mise_a_jour_action), onMettreAJour) },
    ) {
        Text(
            stringResource(R.string.mise_a_jour_texte),
            style = Eruditus.typo.corps,
            color = Eruditus.couleurs.texteSecondaire,
        )
    }
}

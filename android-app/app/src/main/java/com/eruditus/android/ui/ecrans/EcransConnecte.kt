package com.eruditus.android.ui.ecrans

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.eruditus.android.R
import com.eruditus.android.donnees.api.Moi
import com.eruditus.android.misesajour.Release
import com.eruditus.android.ui.designsystem.components.Annonce
import com.eruditus.android.ui.designsystem.components.Boite
import com.eruditus.android.ui.designsystem.components.BoutonDestructeur
import com.eruditus.android.ui.designsystem.components.BoutonPrincipal
import com.eruditus.android.ui.designsystem.components.BoutonSecondaire
import com.eruditus.android.ui.designsystem.components.Champ
import com.eruditus.android.ui.designsystem.components.LigneInfo
import com.eruditus.android.ui.designsystem.components.ListeGroupee
import com.eruditus.android.ui.designsystem.components.Statistique
import com.eruditus.android.ui.designsystem.templates.GabaritAccueil
import com.eruditus.android.ui.designsystem.templates.GabaritListe

/*
 * Écrans d'un joueur connecté (spec/ecrans.md § 8, § 12) : Accueil et Compte. Partie et Résultat :
 * `EcransPartie.kt`.
 */

/** « 12 points », « 1 point », « 0 point ». */
@Composable
fun textePoints(points: Int): String = pluralStringResource(R.plurals.accueil_points, points, points)

/** « 3e sur 48 », « 1er sur 48 » ou « Pas encore classé ». */
@Composable
fun texteRang(rang: Int?, classes: Int): String = when (rang) {
    null -> stringResource(R.string.accueil_pas_classe)
    1 -> stringResource(R.string.accueil_rang_premier, classes)
    else -> stringResource(R.string.accueil_rang_suivant, rang, classes)
}

/**
 * § 8 Accueil (D57, D65) : le pseudo, le score, le rang, le petit bouton « Compte » et « Jouer » ;
 * pas de « Classement » tant que son sujet n'est pas fait. [pseudo] : celui de la session, remplacé
 * par celui de `/moi` dès sa réponse. [version] : celle de l'app installée, pour l'annonce d'une
 * nouvelle version (D62). [creation] : « Jouer » attend le serveur, « Compte » est désactivé.
 */
@Composable
fun EcranAccueil(
    etat: EtatAccueil,
    pseudo: String,
    version: String,
    onCompte: () -> Unit,
    onJouer: () -> Unit,
    creation: Boolean = false,
    onPlusTard: () -> Unit = {},
    onTelecharger: () -> Unit = {},
) {
    val moi: Moi? = etat.moi
    GabaritAccueil(
        titre = moi?.pseudo ?: pseudo,
        statistiques = if (moi == null) {
            emptyList()
        } else {
            listOf(
                Statistique(textePoints(moi.score), stringResource(R.string.accueil_score)),
                Statistique(texteRang(moi.rang, moi.classes), stringResource(R.string.accueil_rang)),
            )
        },
        message = etat.message.enMessage(),
        chargement = etat.chargement && moi == null,
        petitsBoutons = { BoutonSecondaire(stringResource(R.string.accueil_compte), onCompte, petit = true, actif = !creation) },
        jouer = { BoutonPrincipal(stringResource(R.string.accueil_jouer), onJouer, grand = true, chargement = creation) },
        annonce = etat.annonce?.let { release -> { AnnonceMiseAJour(release, version, onPlusTard, onTelecharger) } },
    )
}

/** L'`Annonce` d'une nouvelle version (D62) : « Plus tard » à gauche, « Télécharger » à droite. */
@Composable
fun AnnonceMiseAJour(release: Release, versionInstallee: String, onPlusTard: () -> Unit, onTelecharger: () -> Unit) {
    Annonce(
        titre = stringResource(R.string.annonce_titre),
        texte = stringResource(R.string.annonce_texte, release.version, versionInstallee),
        actions = {
            BoutonSecondaire(stringResource(R.string.annonce_plus_tard), onPlusTard, petit = true)
            BoutonSecondaire(stringResource(R.string.annonce_telecharger), onTelecharger, petit = true)
        },
    )
}

/**
 * § 12 Compte (D13, D14, D59) : le pseudo, « Se déconnecter », « Supprimer mon compte » et la
 * version. Les comptes d'essai (debug, preview) se suppriment ici.
 */
@Composable
fun EcranCompte(
    etat: EtatCompte,
    pseudo: String,
    version: String,
    onRetour: () -> Unit,
    onOuvrir: (BoiteCompte) -> Unit,
    onFermer: () -> Unit,
    onDeconnecter: () -> Unit,
    onMotDePasse: (String) -> Unit,
    onSupprimer: () -> Unit,
) {
    GabaritListe(
        titre = stringResource(R.string.compte_titre),
        onRetour = onRetour,
        pied = stringResource(R.string.compte_version, version),
        bas = {
            BoutonSecondaire(stringResource(R.string.compte_deconnecter), { onOuvrir(BoiteCompte.Deconnexion) })
            BoutonDestructeur(stringResource(R.string.compte_supprimer), { onOuvrir(BoiteCompte.Suppression) })
        },
    ) {
        ListeGroupee { LigneInfo(stringResource(R.string.compte_pseudo), pseudo) }
    }

    val fermer = { if (!etat.envoi) onFermer() }
    Boite(
        visible = etat.boite == BoiteCompte.Deconnexion,
        onFermer = fermer,
        titre = stringResource(R.string.compte_deconnecter_question),
        actions = {
            BoutonSecondaire(stringResource(R.string.annuler), fermer, actif = !etat.envoi)
            BoutonPrincipal(stringResource(R.string.compte_deconnecter), onDeconnecter, chargement = etat.envoi)
        },
    )
    val supprimable = etat.motDePasse.isNotEmpty() && !etat.envoi
    Boite(
        visible = etat.boite == BoiteCompte.Suppression,
        onFermer = fermer,
        texte = stringResource(R.string.compte_supprimer_texte),
        champ = {
            Champ(
                etat.motDePasse,
                onMotDePasse,
                stringResource(R.string.champ_mot_de_passe),
                erreur = etat.erreur?.texte(),
                motDePasse = true,
                actif = !etat.envoi,
                typeContenu = ContentType.Password,
                optionsClavier = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                actionsClavier = KeyboardActions(onDone = { if (supprimable) onSupprimer() }),
            )
        },
        actions = {
            BoutonSecondaire(stringResource(R.string.annuler), fermer, actif = !etat.envoi)
            BoutonDestructeur(
                stringResource(R.string.compte_supprimer_action),
                onSupprimer,
                chargement = etat.envoi,
                actif = supprimable || etat.envoi,
            )
        },
    )
}

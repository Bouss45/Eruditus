package com.eruditus.android.ui.ecrans

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.eruditus.android.R
import com.eruditus.android.classement.EtatClassement
import com.eruditus.android.classement.LigneVue
import com.eruditus.android.classement.TextesClassement
import com.eruditus.android.classement.VueClassement
import com.eruditus.android.donnees.api.Moi
import com.eruditus.android.misesajour.Release
import com.eruditus.android.ui.designsystem.components.Annonce
import com.eruditus.android.ui.designsystem.components.BoutonPrincipal
import com.eruditus.android.ui.designsystem.components.BoutonSecondaire
import com.eruditus.android.ui.designsystem.components.Encadre
import com.eruditus.android.ui.designsystem.components.LigneClassement
import com.eruditus.android.ui.designsystem.components.ListeGroupee
import com.eruditus.android.ui.designsystem.components.SeparateurPoints
import com.eruditus.android.ui.designsystem.components.Statistique
import com.eruditus.android.ui.designsystem.templates.GabaritAccueil
import com.eruditus.android.ui.designsystem.templates.GabaritListe
import com.eruditus.android.ui.designsystem.templates.Tirage

/*
 * Écrans d'un joueur connecté (spec/ecrans.md § 8, § 11) : Accueil et Classement. Paramètres :
 * `EcransParametres.kt` ; Partie et Résultat : `EcransPartie.kt`.
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
 * § 8 Accueil (D57, D65, D71, D76) : le pseudo, le score, le rang, les petits boutons
 * « Classement » et « Paramètres », et « Jouer ». [pseudo] : celui de la session, remplacé par celui de `/moi` dès sa
 * réponse. [version] : celle de l'app installée, pour l'annonce d'une nouvelle version (D62).
 * [creation] : « Jouer » attend le serveur, « Classement » et « Paramètres » sont désactivés.
 */
@Composable
fun EcranAccueil(
    etat: EtatAccueil,
    pseudo: String,
    version: String,
    onClassement: () -> Unit,
    onParametres: () -> Unit,
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
        petitsBoutons = {
            BoutonSecondaire(stringResource(R.string.accueil_classement), onClassement, petit = true, actif = !creation)
            BoutonSecondaire(stringResource(R.string.accueil_parametres), onParametres, petit = true, actif = !creation)
        },
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
 * § 11 Classement (D72, D73) : le top 10 sous « Top 10 » et « Points », ma ligne dedans ou sous le
 * séparateur, l'encadré si je ne suis pas classé, « 48 joueurs classés » ; personne de classé : le
 * texte seul. Premier chargement : indicateur centré ; tirer vers le bas recharge, les lignes
 * restent. Premier chargement refusé (429) : le message seul, tirer réessaie.
 */
@Composable
fun EcranClassement(etat: EtatClassement, onRetour: () -> Unit, onTirage: () -> Unit) {
    val t = textes()
    val vue = etat.vue
    GabaritListe(
        titre = stringResource(R.string.classement_titre),
        onRetour = onRetour,
        message = etat.message.enMessage(),
        chargement = etat.indicateurCentre,
        tirage = Tirage(etat.tirageEnCours, onTirage),
        pied = (vue as? VueClassement.Liste)?.let { TextesClassement.pied(t, it.classes) },
        vide = stringResource(R.string.classement_personne).takeIf { vue == VueClassement.Personne },
    ) {
        if (vue is VueClassement.Liste) {
            if (vue.top.isNotEmpty()) {
                ListeGroupee(enTete = stringResource(R.string.classement_top), enTeteDroite = stringResource(R.string.classement_points)) {
                    vue.top.forEachIndexed { i, ligne -> LigneDuClassement(t, ligne, separateur = i > 0) }
                }
            }
            vue.maLigne?.let { ligne ->
                if (vue.top.isNotEmpty()) SeparateurPoints()
                ListeGroupee(teintee = true) { LigneDuClassement(t, ligne) }
            }
            if (vue.pasClasse) Encadre(stringResource(R.string.classement_pas_classe))
        }
    }
}

/** Une ligne du classement : rang « 1er », pseudo, « 19 » + « points », lue d'un bloc (D74). */
@Composable
private fun LigneDuClassement(t: Textes, ligne: LigneVue, separateur: Boolean = false) {
    LigneClassement(
        rang = TextesClassement.rang(t, ligne.rang),
        pseudo = ligne.pseudo,
        nombre = TextesClassement.nombre(ligne.score),
        unite = TextesClassement.unite(t, ligne.score),
        description = TextesClassement.description(t, ligne),
        moi = ligne.moi,
        separateur = separateur,
    )
}

package com.eruditus.android.ui.ecrans

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.eruditus.android.R
import com.eruditus.android.classement.EtatClassement
import com.eruditus.android.classement.LigneVue
import com.eruditus.android.classement.TextesClassement
import com.eruditus.android.classement.VueClassement
import com.eruditus.android.donnees.api.Moi
import com.eruditus.android.elo.TextesElo
import com.eruditus.android.misesajour.Release
import com.eruditus.android.ui.designsystem.components.Annonce
import com.eruditus.android.ui.designsystem.components.BoutonPrincipal
import com.eruditus.android.ui.designsystem.components.BoutonSecondaire
import com.eruditus.android.ui.designsystem.components.Encadre
import com.eruditus.android.ui.designsystem.components.Lien
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

/**
 * § 8 Accueil (D57, D65, D71, D76, D117) : le pseudo, l'Elo en grand, le rang par Elo (ou la phrase
 * provisoire) et les bonnes réponses, la phrase de la banque épuisée, les petits boutons
 * « Classement » et « Paramètres », et « Jouer » ; le `Lien` « Historique » (D136) sous les
 * statistiques et leurs phrases, avant l'annonce. [pseudo] : celui de la session, remplacé par celui de `/moi` dès sa
 * réponse. [version] : celle de l'app installée, pour l'annonce d'une nouvelle version (D62).
 * [creation] : « Jouer » attend le serveur, « Classement » et « Paramètres » sont désactivés.
 */
@Composable
fun EcranAccueil(
    etat: EtatAccueil,
    pseudo: String,
    version: String,
    onClassement: () -> Unit,
    onHistorique: () -> Unit,
    onParametres: () -> Unit,
    onJouer: () -> Unit,
    creation: Boolean = false,
    onPlusTard: () -> Unit = {},
    onTelecharger: () -> Unit = {},
) {
    val moi: Moi? = etat.moi
    val t = textes()
    GabaritAccueil(
        titre = moi?.pseudo ?: pseudo,
        statistiques = if (moi == null) {
            emptyList()
        } else {
            listOfNotNull(
                Statistique(moi.elo.valeur.toString(), stringResource(R.string.accueil_elo)),
                moi.elo.rang?.takeUnless { moi.elo.provisoire }
                    ?.let { Statistique(TextesElo.rang(t, it, moi.elo.classes), stringResource(R.string.accueil_rang)) },
                Statistique(moi.score.toString(), stringResource(R.string.accueil_bonnes_reponses)),
            )
        },
        phrases = moi?.elo?.let { elo ->
            listOfNotNull(
                TextesElo.provisoire(t, elo.restantes).takeIf { elo.provisoire },
                TextesElo.banqueEpuisee(t).takeIf { elo.revision },
            )
        }.orEmpty(),
        message = etat.message.enMessage(),
        chargement = etat.chargement && moi == null,
        petitsBoutons = {
            BoutonSecondaire(stringResource(R.string.accueil_classement), onClassement, petit = true, actif = !creation)
            BoutonSecondaire(stringResource(R.string.accueil_parametres), onParametres, petit = true, actif = !creation)
        },
        jouer = { BoutonPrincipal(stringResource(R.string.accueil_jouer), onJouer, grand = true, chargement = creation) },
        lien = { Lien(stringResource(R.string.historique_titre), onHistorique, actif = !creation) },
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
 * § 11 Classement (D72, D73, D112) : le top 10 sous « Top 10 » et « Elo », ma ligne dedans ou sous le
 * séparateur, l'encadré « Elo provisoire… » tant que je ne suis pas classé, « 48 joueurs classés » ; personne de classé : le
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
                ListeGroupee(enTete = stringResource(R.string.classement_top), enTeteDroite = stringResource(R.string.classement_elo)) {
                    vue.top.forEachIndexed { i, ligne -> LigneDuClassement(t, ligne, separateur = i > 0) }
                }
            }
            vue.maLigne?.let { ligne ->
                if (vue.top.isNotEmpty()) SeparateurPoints()
                ListeGroupee(teintee = true) { LigneDuClassement(t, ligne) }
            }
            vue.restantes?.let { Encadre(TextesElo.provisoire(t, it)) }
        }
    }
}

/** Une ligne du classement : rang « 1er », pseudo, Elo, lue d'un bloc (D74). */
@Composable
private fun LigneDuClassement(t: Textes, ligne: LigneVue, separateur: Boolean = false) {
    LigneClassement(
        rang = TextesClassement.rang(t, ligne.rang),
        pseudo = ligne.pseudo,
        nombre = TextesClassement.nombre(ligne.elo),
        description = TextesClassement.description(t, ligne),
        moi = ligne.moi,
        separateur = separateur,
    )
}

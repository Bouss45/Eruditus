package com.eruditus.android.ui.ecrans

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.eruditus.android.R
import com.eruditus.android.avis.ReglesAvis
import com.eruditus.android.parametres.Theme
import com.eruditus.android.ui.designsystem.components.Boite
import com.eruditus.android.ui.designsystem.components.BoutonDestructeur
import com.eruditus.android.ui.designsystem.components.BoutonPrincipal
import com.eruditus.android.ui.designsystem.components.BoutonSecondaire
import com.eruditus.android.ui.designsystem.components.Champ
import com.eruditus.android.ui.designsystem.components.Compteur
import com.eruditus.android.ui.designsystem.components.LigneInfo
import com.eruditus.android.ui.designsystem.components.LigneOption
import com.eruditus.android.ui.designsystem.components.ListeGroupee
import com.eruditus.android.ui.designsystem.components.Message
import com.eruditus.android.ui.designsystem.templates.GabaritFormulaire
import com.eruditus.android.ui.designsystem.templates.GabaritListe
import com.eruditus.android.ui.designsystem.theme.Eruditus

/*
 * Paramètres (spec/ecrans.md § 12, D76) et ses formulaires (§ 12.1 à § 12.3, D83, D85) : un
 * gabarit et des composants du design system, sans état propre ; l'état et les actions viennent
 * de `ModeleApp`.
 */

/** Le libellé d'une option du thème. */
@Composable
fun Theme.libelle(): String = stringResource(
    when (this) {
        Theme.Systeme -> R.string.theme_systeme
        Theme.Sombre -> R.string.theme_sombre
        Theme.Clair -> R.string.theme_clair
    },
)

@Composable
private fun InfoParametres.texte(): String = when (this) {
    is InfoParametres.PseudoChange -> stringResource(R.string.parametres_pseudo_change, pseudo)
    InfoParametres.MotDePasseChange -> stringResource(R.string.parametres_mot_de_passe_change)
    InfoParametres.AvisEnvoye -> stringResource(R.string.parametres_avis_envoye)
}

/**
 * § 12 Paramètres (D76, D77) : trois sections. « Compte » : le pseudo, « Changer de pseudo »,
 * « Changer de mot de passe », « Se déconnecter », « Supprimer mon compte » ; « Apparence » : le
 * [theme], une `LigneOption` par choix ; « À propos » : la [version], puis « Envoyer un avis »
 * (D85). Les boîtes de déconnexion
 * et de suppression sont celles de l'ancien écran Compte (D13, D14) ; les comptes d'essai se
 * suppriment ici (D59).
 */
@Composable
fun EcranParametres(
    etat: EtatParametres,
    pseudo: String,
    version: String,
    theme: Theme,
    onRetour: () -> Unit,
    onChangerPseudo: () -> Unit,
    onChangerMotDePasse: () -> Unit,
    onTheme: (Theme) -> Unit,
    onAvis: () -> Unit,
    onOuvrir: (BoiteCompte) -> Unit,
    onFermer: () -> Unit,
    onDeconnecter: () -> Unit,
    onMotDePasse: (String) -> Unit,
    onSupprimer: () -> Unit,
) {
    // 32 entre sections : l'écart de 12 du contenu, plus 20.
    val entreSections = Modifier.padding(top = Eruditus.espacements.section - Eruditus.espacements.blocs)
    GabaritListe(
        titre = stringResource(R.string.parametres_titre),
        onRetour = onRetour,
        message = etat.info?.let { Message(it.texte()) },
    ) {
        ListeGroupee(enTete = stringResource(R.string.parametres_section_compte)) {
            LigneInfo(stringResource(R.string.parametres_pseudo), pseudo)
        }
        BoutonSecondaire(stringResource(R.string.parametres_changer_pseudo), onChangerPseudo)
        BoutonSecondaire(stringResource(R.string.parametres_changer_mot_de_passe), onChangerMotDePasse)
        BoutonSecondaire(stringResource(R.string.compte_deconnecter), { onOuvrir(BoiteCompte.Deconnexion) })
        BoutonDestructeur(stringResource(R.string.compte_supprimer), { onOuvrir(BoiteCompte.Suppression) })

        ListeGroupee(entreSections.selectableGroup(), enTete = stringResource(R.string.parametres_section_apparence)) {
            Theme.entries.forEachIndexed { i, option ->
                LigneOption(option.libelle(), choisie = option == theme, onChoisir = { onTheme(option) }, separateur = i > 0)
            }
        }

        ListeGroupee(entreSections, enTete = stringResource(R.string.parametres_section_a_propos)) {
            LigneInfo(stringResource(R.string.parametres_version), version)
        }
        BoutonSecondaire(stringResource(R.string.parametres_envoyer_avis), onAvis)
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
                actionsClavier = envoiAuClavier(supprimable, onSupprimer),
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

/**
 * § 12.1 Changer de pseudo (D79, D80, D83) : le texte, puis « Nouveau pseudo », prérempli avec
 * l'actuel, vérifié pendant la saisie comme à la création ; « Changer mon pseudo ».
 */
@Composable
fun EcranChangementPseudo(
    etat: EtatChangementPseudo,
    onPseudo: (String) -> Unit,
    onEnvoyer: () -> Unit,
    onRetour: () -> Unit,
) {
    GabaritFormulaire(
        titre = stringResource(R.string.changement_pseudo_titre),
        onRetour = onRetour,
        message = etat.message.enMessage(),
        action = {
            BoutonPrincipal(
                stringResource(R.string.changement_pseudo_action),
                onEnvoyer,
                chargement = etat.envoi,
                actif = etat.actif || etat.envoi,
            )
        },
    ) {
        Text(
            stringResource(R.string.changement_pseudo_texte),
            style = Eruditus.typo.corps,
            color = Eruditus.couleurs.texteSecondaire,
        )
        Champ(
            etat.pseudo,
            onPseudo,
            stringResource(R.string.champ_nouveau_pseudo),
            aide = stringResource(R.string.aide_pseudo),
            erreur = etat.erreurPseudo?.texte(),
            actif = !etat.envoi,
            typeContenu = ContentType.NewUsername,
            optionsClavier = clavierPseudo.copy(imeAction = ImeAction.Done),
            actionsClavier = envoiAuClavier(etat.actif, onEnvoyer),
        )
    }
}

/**
 * § 12.2 Changer de mot de passe (D81, D83) : le texte, « Mot de passe actuel », « Nouveau mot de
 * passe » (vérifié pendant la saisie comme à la création) ; « Changer mon mot de passe ».
 */
@Composable
fun EcranChangementMotDePasse(
    etat: EtatChangementMotDePasse,
    onActuel: (String) -> Unit,
    onNouveau: (String) -> Unit,
    onEnvoyer: () -> Unit,
    onRetour: () -> Unit,
) {
    GabaritFormulaire(
        titre = stringResource(R.string.changement_mot_de_passe_titre),
        onRetour = onRetour,
        message = etat.message.enMessage(),
        action = {
            BoutonPrincipal(
                stringResource(R.string.changement_mot_de_passe_action),
                onEnvoyer,
                chargement = etat.envoi,
                actif = etat.actif || etat.envoi,
            )
        },
    ) {
        Text(
            stringResource(R.string.changement_mot_de_passe_texte),
            style = Eruditus.typo.corps,
            color = Eruditus.couleurs.texteSecondaire,
        )
        Champ(
            etat.actuel,
            onActuel,
            stringResource(R.string.champ_mot_de_passe_actuel),
            erreur = etat.erreurActuel?.texte(),
            motDePasse = true,
            actif = !etat.envoi,
            typeContenu = ContentType.Password,
            optionsClavier = clavierMotDePasse(dernier = false),
        )
        Champ(
            etat.nouveau,
            onNouveau,
            stringResource(R.string.champ_nouveau_mot_de_passe),
            aide = stringResource(R.string.aide_mot_de_passe),
            erreur = etat.erreurNouveau?.texte(),
            motDePasse = true,
            actif = !etat.envoi,
            typeContenu = ContentType.NewPassword,
            optionsClavier = clavierMotDePasse(dernier = true),
            actionsClavier = envoiAuClavier(etat.actif, onEnvoyer),
        )
    }
}

/**
 * § 12.3 Envoyer un avis (D85 – D94) : le texte, « Ton avis » sur plusieurs lignes avec son
 * compteur, « Envoyer » ; au retour avec du texte, la boîte « Abandonner ton avis ? »
 * ([Continuer], action sûre, à gauche).
 */
@Composable
fun EcranAvis(
    etat: EtatAvis,
    onTexte: (String) -> Unit,
    onEnvoyer: () -> Unit,
    onRetour: () -> Unit,
    onContinuer: () -> Unit,
    onAbandonner: () -> Unit,
) {
    GabaritFormulaire(
        titre = stringResource(R.string.avis_titre),
        onRetour = onRetour,
        message = etat.message.enMessage(),
        action = {
            BoutonPrincipal(
                stringResource(R.string.avis_action),
                onEnvoyer,
                chargement = etat.envoi,
                actif = etat.actif || etat.envoi,
            )
        },
    ) {
        Text(
            stringResource(R.string.avis_texte),
            style = Eruditus.typo.corps,
            color = Eruditus.couleurs.texteSecondaire,
        )
        Champ(
            etat.texte,
            onTexte,
            stringResource(R.string.champ_avis),
            erreur = etat.erreur?.texte(),
            actif = !etat.envoi,
            plusieursLignes = true,
            compteur = Compteur(etat.longueur, ReglesAvis.MAX),
        )
    }
    Boite(
        visible = etat.abandon,
        onFermer = onContinuer,
        titre = stringResource(R.string.avis_abandon_titre),
        texte = stringResource(R.string.avis_abandon_texte),
        actions = {
            BoutonSecondaire(stringResource(R.string.partie_continuer), onContinuer)
            BoutonDestructeur(stringResource(R.string.partie_abandonner), onAbandonner)
        },
    )
}

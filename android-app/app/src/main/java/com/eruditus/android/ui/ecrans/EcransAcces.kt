package com.eruditus.android.ui.ecrans

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.eruditus.android.R
import com.eruditus.android.compte.TexteErreur
import com.eruditus.android.ui.designsystem.components.Boite
import com.eruditus.android.ui.designsystem.components.BoutonPrincipal
import com.eruditus.android.ui.designsystem.components.BoutonSecondaire
import com.eruditus.android.ui.designsystem.components.Champ
import com.eruditus.android.ui.designsystem.components.CodeRecuperation
import com.eruditus.android.ui.designsystem.components.ErreurFormulaire
import com.eruditus.android.ui.designsystem.components.GenreMessage
import com.eruditus.android.ui.designsystem.components.Lien
import com.eruditus.android.ui.designsystem.components.Message
import com.eruditus.android.ui.designsystem.templates.GabaritFormulaire
import com.eruditus.android.ui.designsystem.templates.GabaritInformation
import com.eruditus.android.ui.designsystem.theme.Eruditus
import com.eruditus.android.ui.designsystem.theme.Icones

/*
 * Écrans d'accès au compte (spec/ecrans.md § 3 à § 7) : un gabarit et des composants du design
 * system, sans état propre ; l'état et les actions viennent de `ModeleApp`.
 */

@Composable
fun TexteErreur.texte(): String = stringResource(ressource)

/** Un message en haut d'écran, de genre alerte pour 429 et l'erreur générique (DESIGN.md § 8). */
@Composable
fun TexteErreur?.enMessage(): Message? =
    this?.let { Message(it.texte(), if (it.alerte) GenreMessage.Alerte else GenreMessage.Info) }

private val clavierPseudo = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Ascii,
    imeAction = ImeAction.Next,
)

private fun clavierMotDePasse(dernier: Boolean) = KeyboardOptions(
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Password,
    imeAction = if (dernier) ImeAction.Done else ImeAction.Next,
)

/** Le dernier champ envoie le formulaire, si son bouton est actif. */
private fun envoiAuClavier(actif: Boolean, onEnvoyer: () -> Unit) = KeyboardActions(onDone = { if (actif) onEnvoyer() })

/** § 3 Bienvenue (D19) : la marque, « Créer un compte » et « Se connecter ». */
@Composable
fun EcranBienvenue(arrivee: ArriveeBienvenue?, onCreer: () -> Unit, onConnecter: () -> Unit) {
    val message = when (arrivee) {
        ArriveeBienvenue.SessionExpiree -> Message(TexteErreur.SessionExpiree.texte())
        ArriveeBienvenue.CompteSupprime -> Message(stringResource(R.string.bienvenue_compte_supprime))
        null -> null
    }
    GabaritInformation(
        message = message,
        marque = stringResource(R.string.bienvenue_phrase),
        secondaire = { BoutonSecondaire(stringResource(R.string.bienvenue_connecter), onConnecter) },
        principal = { BoutonPrincipal(stringResource(R.string.bienvenue_creer), onCreer) },
    )
}

/** § 4 Créer un compte. */
@Composable
fun EcranCreation(
    etat: EtatCreation,
    onPseudo: (String) -> Unit,
    onMotDePasse: (String) -> Unit,
    onEnvoyer: () -> Unit,
    onRetour: () -> Unit,
) {
    GabaritFormulaire(
        titre = stringResource(R.string.creation_titre),
        onRetour = onRetour,
        message = etat.message.enMessage(),
        action = {
            BoutonPrincipal(
                stringResource(R.string.creation_action),
                onEnvoyer,
                chargement = etat.envoi,
                actif = etat.actif || etat.envoi,
            )
        },
    ) {
        Champ(
            etat.pseudo,
            onPseudo,
            stringResource(R.string.champ_pseudo),
            aide = stringResource(R.string.aide_pseudo),
            erreur = etat.erreurPseudo?.texte(),
            actif = !etat.envoi,
            typeContenu = ContentType.NewUsername,
            optionsClavier = clavierPseudo,
        )
        Champ(
            etat.motDePasse,
            onMotDePasse,
            stringResource(R.string.champ_mot_de_passe),
            aide = stringResource(R.string.aide_mot_de_passe),
            erreur = etat.erreurMotDePasse?.texte(),
            motDePasse = true,
            actif = !etat.envoi,
            typeContenu = ContentType.NewPassword,
            optionsClavier = clavierMotDePasse(dernier = true),
            actionsClavier = envoiAuClavier(etat.actif, onEnvoyer),
        )
    }
}

/**
 * § 5 Code de récupération (D11) : le code en grand, « Copier », le texte, « J'ai noté mon code ».
 * [EtatCode.confirmation] : la boîte du retour système.
 */
@Composable
fun EcranCode(
    etat: EtatCode,
    onCopier: () -> Unit,
    onNote: () -> Unit,
    onFermerConfirmation: () -> Unit,
) {
    GabaritInformation(
        titre = stringResource(R.string.code_titre),
        message = if (etat.copie) Message(stringResource(R.string.code_copie)) else null,
        principal = { BoutonPrincipal(stringResource(R.string.code_action), onNote) },
    ) {
        CodeRecuperation(etat.code, Modifier.padding(top = Eruditus.espacements.icone))
        BoutonSecondaire(stringResource(R.string.code_copier), onCopier, petit = true, icone = Icones.Copier)
        Text(
            stringResource(R.string.code_texte),
            style = Eruditus.typo.corps,
            color = Eruditus.couleurs.texteSecondaire,
        )
    }
    Boite(
        visible = etat.confirmation,
        onFermer = onFermerConfirmation,
        titre = stringResource(R.string.code_retour_titre),
        texte = stringResource(R.string.code_retour_texte),
        actions = {
            BoutonSecondaire(stringResource(R.string.code_retour_pas_encore), onFermerConfirmation)
            BoutonPrincipal(stringResource(R.string.code_retour_oui), onNote)
        },
    )
}

/** § 6 Se connecter. */
@Composable
fun EcranConnexion(
    etat: EtatConnexion,
    onPseudo: (String) -> Unit,
    onMotDePasse: (String) -> Unit,
    onEnvoyer: () -> Unit,
    onOubli: () -> Unit,
    onRetour: () -> Unit,
) {
    GabaritFormulaire(
        titre = stringResource(R.string.connexion_titre),
        onRetour = onRetour,
        message = etat.message.enMessage(),
        action = {
            BoutonPrincipal(
                stringResource(R.string.connexion_action),
                onEnvoyer,
                chargement = etat.envoi,
                actif = etat.actif || etat.envoi,
            )
        },
    ) {
        Champ(
            etat.pseudo,
            onPseudo,
            stringResource(R.string.champ_pseudo),
            actif = !etat.envoi,
            typeContenu = ContentType.Username,
            optionsClavier = clavierPseudo,
        )
        Champ(
            etat.motDePasse,
            onMotDePasse,
            stringResource(R.string.champ_mot_de_passe),
            motDePasse = true,
            actif = !etat.envoi,
            typeContenu = ContentType.Password,
            optionsClavier = clavierMotDePasse(dernier = true),
            actionsClavier = envoiAuClavier(etat.actif, onEnvoyer),
        )
        etat.erreur?.let { ErreurFormulaire(it.texte()) }
        Lien(stringResource(R.string.connexion_oubli), onOubli, actif = !etat.envoi)
    }
}

/** § 7 Mot de passe oublié. */
@Composable
fun EcranRecuperation(
    etat: EtatRecuperation,
    onPseudo: (String) -> Unit,
    onCode: (String) -> Unit,
    onMotDePasse: (String) -> Unit,
    onEnvoyer: () -> Unit,
    onRetour: () -> Unit,
) {
    GabaritFormulaire(
        titre = stringResource(R.string.oubli_titre),
        onRetour = onRetour,
        message = etat.message.enMessage(),
        action = {
            BoutonPrincipal(
                stringResource(R.string.oubli_action),
                onEnvoyer,
                chargement = etat.envoi,
                actif = etat.actif || etat.envoi,
            )
        },
    ) {
        Text(
            stringResource(R.string.oubli_texte),
            style = Eruditus.typo.corps,
            color = Eruditus.couleurs.texteSecondaire,
        )
        Champ(
            etat.pseudo,
            onPseudo,
            stringResource(R.string.champ_pseudo),
            actif = !etat.envoi,
            typeContenu = ContentType.Username,
            optionsClavier = clavierPseudo,
        )
        Champ(
            etat.code,
            onCode,
            stringResource(R.string.champ_code),
            actif = !etat.envoi,
            optionsClavier = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
            ),
        )
        Champ(
            etat.motDePasse,
            onMotDePasse,
            stringResource(R.string.champ_nouveau_mot_de_passe),
            aide = stringResource(R.string.aide_mot_de_passe),
            erreur = etat.erreurMotDePasse?.texte(),
            motDePasse = true,
            actif = !etat.envoi,
            typeContenu = ContentType.NewPassword,
            optionsClavier = clavierMotDePasse(dernier = true),
            actionsClavier = envoiAuClavier(etat.actif, onEnvoyer),
        )
        etat.erreur?.let { ErreurFormulaire(it.texte()) }
    }
}

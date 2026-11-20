package com.eruditus.android.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.eruditus.android.BuildConfig
import com.eruditus.android.ui.designsystem.theme.Eruditus
import com.eruditus.android.ui.designsystem.theme.MouvementEruditus
import com.eruditus.android.ui.ecrans.EcranAccueil
import com.eruditus.android.ui.ecrans.EcranBienvenue
import com.eruditus.android.ui.ecrans.EcranCode
import com.eruditus.android.ui.ecrans.EcranCompte
import com.eruditus.android.ui.ecrans.EcranConnexion
import com.eruditus.android.ui.ecrans.EcranCreation
import com.eruditus.android.ui.ecrans.EcranMiseAJour
import com.eruditus.android.ui.ecrans.EcranPasDeConnexion
import com.eruditus.android.ui.ecrans.EcranRecuperation

/**
 * L'app : l'écran du sommet de la pile de [modele] (D56), avec la transition `duree.ecran` (fondu
 * et 30 dp, vers la gauche en avançant, vers la droite en revenant ; DESIGN.md § 6). Le retour
 * système dépile, ou quitte l'app depuis un écran racine (Bienvenue, Accueil, Pas de connexion,
 * Mise à jour) ; sur le code de récupération, il ouvre la boîte de confirmation.
 */
@Composable
fun AppEruditus(modele: ModeleApp) {
    val contexte = LocalContext.current
    val mouvement = Eruditus.mouvement
    val decalage = with(LocalDensity.current) { MouvementEruditus.ECRAN_DECALAGE.roundToPx() }

    BackHandler(enabled = modele.retourIntercepte) { modele.retour() }

    AnimatedContent(
        targetState = modele.pile.lastOrNull() ?: Destination.Demarrage,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val sens = if (modele.avant) 1 else -1
            (fadeIn(mouvement.ecranEntree()) + slideInHorizontally(mouvement.ecranEntree()) { sens * decalage }) togetherWith
                fadeOut(mouvement.ecranSortie())
        },
        label = "ecran",
    ) { destination ->
        when (destination) {
            // Caché par l'écran de lancement d'Android (DESIGN.md § 10) le temps de l'aiguillage.
            Destination.Demarrage -> Box(Modifier.fillMaxSize().background(Eruditus.couleurs.fond))

            is Destination.Bienvenue -> EcranBienvenue(
                arrivee = destination.arrivee,
                onCreer = modele::ouvrirCreation,
                onConnecter = modele::ouvrirConnexion,
            )

            Destination.Creation -> EcranCreation(
                etat = modele.creation,
                onPseudo = modele::saisirCreationPseudo,
                onMotDePasse = modele::saisirCreationMotDePasse,
                onEnvoyer = modele::creerCompte,
                onRetour = modele::retour,
            )

            Destination.Code -> modele.code?.let { etat ->
                EcranCode(
                    etat = etat,
                    onCopier = { modele.codeCopie(copierCode(contexte, etat.code)) },
                    onNote = modele::codeNote,
                    onFermerConfirmation = modele::fermerConfirmationCode,
                )
            }

            Destination.Connexion -> EcranConnexion(
                etat = modele.connexion,
                onPseudo = modele::saisirConnexionPseudo,
                onMotDePasse = modele::saisirConnexionMotDePasse,
                onEnvoyer = modele::connecter,
                onOubli = modele::ouvrirRecuperation,
                onRetour = modele::retour,
            )

            Destination.Recuperation -> EcranRecuperation(
                etat = modele.recuperation,
                onPseudo = modele::saisirRecuperationPseudo,
                onCode = modele::saisirRecuperationCode,
                onMotDePasse = modele::saisirRecuperationMotDePasse,
                onEnvoyer = modele::recuperer,
                onRetour = modele::retour,
            )

            Destination.Accueil -> {
                // Rechargé à chaque arrivée sur l'Accueil (spec/ecrans.md § 8).
                LaunchedEffect(Unit) { modele.chargerAccueil() }
                EcranAccueil(etat = modele.accueil, pseudo = modele.pseudo, onCompte = modele::ouvrirCompte)
            }

            Destination.Compte -> EcranCompte(
                etat = modele.compte,
                pseudo = modele.pseudo,
                version = BuildConfig.VERSION_NAME,
                onRetour = modele::retour,
                onOuvrir = modele::ouvrirBoite,
                onFermer = modele::fermerBoite,
                onDeconnecter = modele::deconnecter,
                onMotDePasse = modele::saisirMotDePasseSuppression,
                onSupprimer = modele::supprimer,
            )

            is Destination.PasDeConnexion -> EcranPasDeConnexion(
                serveur = destination.serveur,
                enCours = modele.aiguillageEnCours,
                onReessayer = modele::aiguiller,
            )

            Destination.MiseAJour -> EcranMiseAJour(onMettreAJour = { ouvrirMiseAJour(contexte) })
        }
    }
}

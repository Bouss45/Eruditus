package com.eruditus.android.ui.app

import android.os.SystemClock
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LifecycleStartEffect
import com.eruditus.android.BuildConfig
import com.eruditus.android.partie.EtatPartie
import com.eruditus.android.partie.Phase
import com.eruditus.android.ui.designsystem.theme.Eruditus
import com.eruditus.android.ui.designsystem.theme.MouvementEruditus
import com.eruditus.android.ui.ecrans.EcranAccueil
import com.eruditus.android.ui.ecrans.EcranBienvenue
import com.eruditus.android.ui.ecrans.EcranCode
import com.eruditus.android.ui.ecrans.EcranCompte
import com.eruditus.android.ui.ecrans.EcranConnexion
import com.eruditus.android.ui.ecrans.EcranCreation
import com.eruditus.android.ui.ecrans.EcranMiseAJour
import com.eruditus.android.ui.ecrans.EcranPartie
import com.eruditus.android.ui.ecrans.EcranPasDeConnexion
import com.eruditus.android.ui.ecrans.EcranRecuperation
import com.eruditus.android.ui.ecrans.EcranResultat

/**
 * L'app : l'écran du sommet de la pile de [modele] (D56), avec la transition `duree.ecran` (fondu
 * et 30 dp, vers la gauche en avançant, vers la droite en revenant ; DESIGN.md § 6). Le retour
 * système dépile, ou quitte l'app depuis un écran racine (Bienvenue, Accueil, Pas de connexion,
 * Mise à jour) ; sur le code de récupération et sur la Partie, il ouvre la boîte de confirmation.
 *
 * Premier plan et arrière-plan de l'activité sont transmis à la partie : aucun envoi automatique en
 * arrière-plan, question redemandée au retour ou à la recréation de l'écran (D66).
 */
@Composable
fun AppEruditus(modele: ModeleApp) {
    val contexte = LocalContext.current
    val mouvement = Eruditus.mouvement
    val decalage = with(LocalDensity.current) { MouvementEruditus.ECRAN_DECALAGE.roundToPx() }
    val creationPartie by modele.partie.creation.collectAsState()

    BackHandler(enabled = modele.retourIntercepte) { modele.retour() }
    LifecycleStartEffect(modele) {
        modele.partie.auPremierPlan()
        onStopOrDispose { modele.partie.enArrierePlan() }
    }

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
                EcranAccueil(
                    etat = modele.accueil,
                    pseudo = modele.pseudo,
                    version = BuildConfig.VERSION_NAME,
                    onCompte = modele::ouvrirCompte,
                    onJouer = modele::jouer,
                    creation = creationPartie,
                    onPlusTard = modele::plusTard,
                    onTelecharger = { ouvrirMiseAJour(contexte, modele.accueil.annonce) },
                )
            }

            Destination.Partie -> {
                val etat by modele.partie.etat.collectAsState()
                // La partie quittée reste affichée le temps de la transition de sortie.
                dernierNonNul(etat)?.let { partie ->
                    EcranPartie(
                        etat = partie,
                        restantMs = restantAChaqueImage(partie),
                        abandon = modele.abandon,
                        onChoix = modele.partie::choisir,
                        onBouton = modele.partie::continuer,
                        onFermer = modele::retour,
                        onContinuer = modele::continuerPartie,
                        onAbandonner = modele::abandonner,
                    )
                }
            }

            Destination.Resultat -> dernierNonNul(modele.resultat)?.let { etat ->
                EcranResultat(etat, chargement = creationPartie, onRejouer = modele::rejouer, onAccueil = modele::retour)
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

            Destination.MiseAJour -> EcranMiseAJour(onMettreAJour = { modele.ouvrirMiseAJour { ouvrirMiseAJour(contexte, it) } })
        }
    }
}

/**
 * Temps restant du chrono, recalculé à chaque image tant qu'il décompte, sur l'horloge monotone de
 * la partie : il part du `restantMs` du serveur, jamais de 20 s fixes (D66).
 */
@Composable
private fun restantAChaqueImage(partie: EtatPartie): Long {
    val decompte = partie.phase == Phase.Attente
    val image by produceState(0L, decompte, partie.finMs) {
        if (decompte) while (true) withFrameMillis { value = it }
    }
    return image.let { partie.restantMs(SystemClock.elapsedRealtime()) }
}

private class Dernier<T : Any>(var valeur: T? = null)

/** La dernière valeur non nulle : l'écran quitté garde son contenu pendant sa sortie. */
@Composable
private fun <T : Any> dernierNonNul(valeur: T?): T? {
    val dernier = remember { Dernier<T>() }
    if (valeur != null) dernier.valeur = valeur
    return dernier.valeur
}

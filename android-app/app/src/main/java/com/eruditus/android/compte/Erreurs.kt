package com.eruditus.android.compte

import androidx.annotation.StringRes
import com.eruditus.android.R
import com.eruditus.android.donnees.api.ErreurApi

/**
 * Les messages d'erreur (spec/ecrans.md § 15) ; leurs textes sont confrontés
 * au tableau de la spec par `TextesErreursTest`. [alerte] : genre du message en haut
 * (`DESIGN.md` § 8), alerte pour 429 et l'erreur générique.
 */
enum class TexteErreur(@StringRes val ressource: Int, val alerte: Boolean = false) {
    PseudoInvalide(R.string.erreur_pseudo_invalide),
    PseudoReserve(R.string.erreur_pseudo_reserve),
    PseudoPris(R.string.erreur_pseudo_pris),
    MotDePasseInvalide(R.string.erreur_mot_de_passe_invalide),
    ConnexionEchouee(R.string.erreur_connexion_echouee),
    CodeIncorrect(R.string.erreur_code_incorrect),
    MotDePasseIncorrect(R.string.erreur_mot_de_passe_incorrect),
    SessionExpiree(R.string.erreur_session_expiree),
    TropDeTentatives(R.string.erreur_trop_de_tentatives, alerte = true),
    Generique(R.string.erreur_generique, alerte = true),
    PartiePlusValable(R.string.erreur_partie_plus_valable),
    AucunePartie(R.string.erreur_aucune_partie),
}

/**
 * L'écran sur lequel l'erreur arrive : la même erreur ne s'affiche pas au même endroit partout.
 * [Jouer] : « Jouer » sur l'Accueil ou « Rejouer » sur le Résultat ; [Partie] : question et
 * réponse (D68).
 */
enum class EcranErreur { Creation, Connexion, Recuperation, Accueil, Suppression, Jouer, Partie }

/** Où et comment une erreur s'affiche (spec/ecrans.md § 15, `DESIGN.md` § 8). */
sealed interface Affichage {
    /** Sous le champ Pseudo. */
    data class SousPseudo(val texte: TexteErreur) : Affichage

    /** Sous le champ du mot de passe : celui du formulaire, ou celui de la boîte de suppression. */
    data class SousMotDePasse(val texte: TexteErreur) : Affichage

    /** `ErreurFormulaire`, au-dessus du lien ou de l'action. */
    data class AuDessusAction(val texte: TexteErreur) : Affichage

    /** `MessageHaut` de l'écran en cours. */
    data class EnHaut(val texte: TexteErreur) : Affichage

    /** Écran « Pas de connexion », texte réseau ou serveur (D53). */
    data class PasDeConnexion(val serveur: Boolean) : Affichage

    /** Session effacée, Bienvenue avec « Ta session a expiré, reconnecte-toi. ». */
    data object SessionExpiree : Affichage

    /** La partie s'arrête : retour à l'Accueil, avec ce message en haut (D68). */
    data class VersAccueil(val texte: TexteErreur) : Affichage
}

fun affichage(erreur: ErreurApi, ecran: EcranErreur): Affichage {
    val boite = ecran == EcranErreur.Suppression
    fun ailleurs(texte: TexteErreur): Affichage = if (boite) Affichage.SousMotDePasse(texte) else Affichage.EnHaut(texte)
    return when (erreur) {
        ErreurApi.Reseau -> Affichage.PasDeConnexion(serveur = false)
        ErreurApi.Serveur -> Affichage.PasDeConnexion(serveur = true)
        ErreurApi.TropDeTentatives -> ailleurs(TexteErreur.TropDeTentatives)
        // Seules les routes à jeton (Accueil, suppression, partie) peuvent voir une session
        // expirer : une route publique ne reçoit jamais de jeton (D30).
        ErreurApi.NonAutorise -> when (ecran) {
            EcranErreur.Accueil, EcranErreur.Suppression, EcranErreur.Jouer, EcranErreur.Partie -> Affichage.SessionExpiree
            else -> ailleurs(TexteErreur.Generique)
        }
        is ErreurApi.Refus -> refus(erreur, ecran) ?: ailleurs(TexteErreur.Generique)
    }
}

private fun refus(erreur: ErreurApi.Refus, ecran: EcranErreur): Affichage? = when (ecran) {
    EcranErreur.Creation -> when (erreur.code) {
        "pseudo_invalide" -> Affichage.SousPseudo(TexteErreur.PseudoInvalide)
        "pseudo_reserve" -> Affichage.SousPseudo(TexteErreur.PseudoReserve)
        "pseudo_pris" -> Affichage.SousPseudo(TexteErreur.PseudoPris)
        "mot_de_passe_invalide" -> Affichage.SousMotDePasse(TexteErreur.MotDePasseInvalide)
        else -> null
    }
    // auth-with-password répond 400 sans code Eruditus : une seule erreur, on ne dit pas lequel est faux.
    EcranErreur.Connexion -> if (erreur.statut == 400) Affichage.AuDessusAction(TexteErreur.ConnexionEchouee) else null
    EcranErreur.Recuperation -> when (erreur.code) {
        "mot_de_passe_invalide" -> Affichage.SousMotDePasse(TexteErreur.MotDePasseInvalide)
        "code_incorrect" -> Affichage.AuDessusAction(TexteErreur.CodeIncorrect)
        else -> null
    }
    EcranErreur.Suppression -> if (erreur.code == "mot_de_passe_incorrect") Affichage.SousMotDePasse(TexteErreur.MotDePasseIncorrect) else null
    EcranErreur.Accueil -> null
    // 503 avec son code : un refus, pas une panne (D68).
    EcranErreur.Jouer -> if (erreur.code == "banque_insuffisante") Affichage.VersAccueil(TexteErreur.AucunePartie) else null
    EcranErreur.Partie -> when (erreur.code) {
        "partie_introuvable", "partie_finie", "numero_invalide", "deja_repondu" -> Affichage.VersAccueil(TexteErreur.PartiePlusValable)
        else -> null
    }
}

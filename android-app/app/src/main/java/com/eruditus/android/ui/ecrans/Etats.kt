package com.eruditus.android.ui.ecrans

import com.eruditus.android.compte.EtatSaisie
import com.eruditus.android.compte.ReglesMotDePasse
import com.eruditus.android.compte.ReglesPseudo
import com.eruditus.android.compte.TexteErreur
import com.eruditus.android.donnees.api.Moi
import com.eruditus.android.misesajour.Release

/*
 * États des écrans du compte (spec/ecrans.md § 3 à § 12), en mémoire seulement : jamais dans
 * l'état sauvegardé d'Android (D56). Aucun n'affiche son mot de passe dans `toString`.
 *
 * Erreurs sous un champ : celle du serveur, sinon celle de la vérification pendant la saisie
 * (D55) ; modifier le champ efface celle du serveur.
 */

/** Comment on arrive sur Bienvenue : un message en haut, ou rien. */
enum class ArriveeBienvenue { SessionExpiree, CompteSupprime }

/** Erreur de saisie d'un pseudo : seulement quand il ne peut plus devenir valide (D55). */
fun erreurPseudoSaisi(pseudo: String): TexteErreur? =
    TexteErreur.PseudoInvalide.takeIf { ReglesPseudo.etat(pseudo) == EtatSaisie.Invalide }

/** Erreur de saisie d'un nouveau mot de passe : seulement quand il ne peut plus devenir valide (D55). */
fun erreurMotDePasseSaisi(motDePasse: String): TexteErreur? =
    TexteErreur.MotDePasseInvalide.takeIf { ReglesMotDePasse.etat(motDePasse) == EtatSaisie.Invalide }

data class EtatCreation(
    val pseudo: String = "",
    val motDePasse: String = "",
    val erreurPseudoServeur: TexteErreur? = null,
    val erreurMotDePasseServeur: TexteErreur? = null,
    val message: TexteErreur? = null,
    val envoi: Boolean = false,
) {
    val erreurPseudo: TexteErreur? get() = erreurPseudoServeur ?: erreurPseudoSaisi(pseudo)
    val erreurMotDePasse: TexteErreur? get() = erreurMotDePasseServeur ?: erreurMotDePasseSaisi(motDePasse)
    val actif: Boolean get() = !envoi && ReglesPseudo.etat(pseudo) == EtatSaisie.Valide && ReglesMotDePasse.valide(motDePasse)

    override fun toString() = "EtatCreation(envoi=$envoi)"
}

data class EtatConnexion(
    val pseudo: String = "",
    val motDePasse: String = "",
    /** Au-dessus de l'action : « Pseudo ou mot de passe incorrect. ». */
    val erreur: TexteErreur? = null,
    val message: TexteErreur? = null,
    val envoi: Boolean = false,
) {
    val actif: Boolean get() = !envoi && ReglesPseudo.nettoyer(pseudo).isNotEmpty() && motDePasse.isNotEmpty()

    override fun toString() = "EtatConnexion(envoi=$envoi)"
}

data class EtatRecuperation(
    val pseudo: String = "",
    val code: String = "",
    val motDePasse: String = "",
    /** Sous le nouveau mot de passe : le serveur, ou la vérification avant l'envoi (D10). */
    val erreurMotDePasseEnvoi: TexteErreur? = null,
    /** Au-dessus de l'action : « Pseudo ou code incorrect. ». */
    val erreur: TexteErreur? = null,
    val message: TexteErreur? = null,
    val envoi: Boolean = false,
) {
    val erreurMotDePasse: TexteErreur? get() = erreurMotDePasseEnvoi ?: erreurMotDePasseSaisi(motDePasse)
    val actif: Boolean
        get() = !envoi && ReglesPseudo.nettoyer(pseudo).isNotEmpty() && code.isNotBlank() && motDePasse.isNotEmpty()

    override fun toString() = "EtatRecuperation(envoi=$envoi)"
}

/** Code de récupération affiché une seule fois (D11) ; [copie] : « Code copié. » en haut (Android 12 et moins). */
data class EtatCode(val code: String, val copie: Boolean = false, val confirmation: Boolean = false) {
    override fun toString() = "EtatCode(copie=$copie)"
}

/**
 * Accueil : [moi] nul avant la première réponse ; [chargement] : premier chargement en cours ;
 * [annonce] : nouvelle version à annoncer (D62), release seulement.
 */
data class EtatAccueil(
    val moi: Moi? = null,
    val chargement: Boolean = true,
    val message: TexteErreur? = null,
    val annonce: Release? = null,
)

/** Boîte ouverte sur l'écran Compte. */
enum class BoiteCompte { Deconnexion, Suppression }

data class EtatCompte(
    val boite: BoiteCompte? = null,
    val motDePasse: String = "",
    /** Sous le champ de la boîte de suppression (spec/ecrans.md § 12). */
    val erreur: TexteErreur? = null,
    val envoi: Boolean = false,
) {
    override fun toString() = "EtatCompte(boite=$boite, envoi=$envoi)"
}

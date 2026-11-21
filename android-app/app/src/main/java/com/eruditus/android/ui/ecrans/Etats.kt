package com.eruditus.android.ui.ecrans

import com.eruditus.android.avis.ReglesAvis
import com.eruditus.android.compte.Affichage
import com.eruditus.android.compte.EtatSaisie
import com.eruditus.android.compte.ReglesMotDePasse
import com.eruditus.android.compte.ReglesPseudo
import com.eruditus.android.compte.TexteErreur
import com.eruditus.android.donnees.api.Bilan
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

/** Boîte du compte ouverte sur l'écran Paramètres. */
enum class BoiteCompte { Deconnexion, Suppression }

/** Message info en haut de Paramètres, après un changement réussi (D83) ou un avis envoyé (D85). */
sealed interface InfoParametres {
    data class PseudoChange(val pseudo: String) : InfoParametres
    data object MotDePasseChange : InfoParametres
    data object AvisEnvoye : InfoParametres
}

/** Paramètres (spec/ecrans.md § 12) : boîte de déconnexion ou de suppression, et [info] en haut. */
data class EtatParametres(
    val boite: BoiteCompte? = null,
    val motDePasse: String = "",
    /** Sous le champ de la boîte de suppression (spec/ecrans.md § 12). */
    val erreur: TexteErreur? = null,
    val envoi: Boolean = false,
    val info: InfoParametres? = null,
) {
    override fun toString() = "EtatParametres(boite=$boite, envoi=$envoi, info=$info)"
}

/**
 * Changer de pseudo (§ 12.1, D83) : [pseudo] prérempli avec l'[actuel] ; le bouton s'active
 * quand le pseudo respecte D9 et diffère de l'actuel, casse comprise. Mêmes règles et textes
 * qu'à la création (D55).
 */
data class EtatChangementPseudo(
    val actuel: String,
    val pseudo: String = actuel,
    val erreurServeur: TexteErreur? = null,
    val message: TexteErreur? = null,
    val envoi: Boolean = false,
) {
    val erreurPseudo: TexteErreur? get() = erreurServeur ?: erreurPseudoSaisi(pseudo)
    val actif: Boolean
        get() = !envoi && ReglesPseudo.etat(pseudo) == EtatSaisie.Valide && ReglesPseudo.nettoyer(pseudo) != actuel
}

/**
 * Changer de mot de passe (§ 12.2, D83) : le bouton s'active quand l'[actuel] est rempli et que
 * le [nouveau] respecte D10, vérifié pendant la saisie comme à la création (D55).
 */
data class EtatChangementMotDePasse(
    val actuel: String = "",
    val nouveau: String = "",
    /** Sous « Mot de passe actuel » : « Mot de passe incorrect. ». */
    val erreurActuel: TexteErreur? = null,
    /** Sous « Nouveau mot de passe » : le serveur ; sinon la vérification pendant la saisie. */
    val erreurNouveauServeur: TexteErreur? = null,
    val message: TexteErreur? = null,
    val envoi: Boolean = false,
) {
    val erreurNouveau: TexteErreur? get() = erreurNouveauServeur ?: erreurMotDePasseSaisi(nouveau)
    val actif: Boolean get() = !envoi && actuel.isNotEmpty() && ReglesMotDePasse.valide(nouveau)

    override fun toString() = "EtatChangementMotDePasse(envoi=$envoi)"
}

/**
 * Envoyer un avis (§ 12.3, D85 – D94) : compteur et « Envoyer » suivent le texte tel qu'il
 * partira (D86), la saisie n'est jamais coupée. Pas d'erreur de saisie : au-delà de 1000, seul le
 * compteur passe en rouge. [abandon] : la boîte « Abandonner ton avis ? ». Le texte ne vit qu'ici
 * (spec/donnees.md § 3), jamais dans `toString`.
 */
data class EtatAvis(
    val texte: String = "",
    /** Sous le champ : `avis_invalide`, `avis_trop_recent` (D93). */
    val erreurServeur: TexteErreur? = null,
    val message: TexteErreur? = null,
    val envoi: Boolean = false,
    val abandon: Boolean = false,
) {
    val envoye: String get() = ReglesAvis.normaliser(texte)
    val longueur: Int get() = ReglesAvis.longueur(envoye)
    val tropLong: Boolean get() = longueur > ReglesAvis.MAX
    val actif: Boolean get() = !envoi && longueur in 1..ReglesAvis.MAX
    val erreur: TexteErreur? get() = erreurServeur

    /** Retour avec du texte, même des blancs, envoi en cours compris : la boîte (D85). */
    val confirmerRetour: Boolean get() = texte.isNotEmpty()

    fun saisir(valeur: String) = copy(texte = valeur, erreurServeur = null)

    fun envoyer() = copy(envoi = true, erreurServeur = null, message = null)

    /** Après un refus ou une panne : le texte reste, l'erreur à sa place (D93). */
    fun apresEchec(affichage: Affichage) = copy(
        envoi = false,
        erreurServeur = (affichage as? Affichage.SousAvis)?.texte,
        message = (affichage as? Affichage.EnHaut)?.texte,
    )

    override fun toString() = "EtatAvis(longueur=$longueur, envoi=$envoi, abandon=$abandon)"
}

/**
 * Résultat (D69) : le [bilan] de la dernière correction, sur [total] questions, en mémoire
 * seulement ; [message] : « Rejouer » refusé (429, erreur générique).
 */
data class EtatResultat(val bilan: Bilan, val total: Int, val message: TexteErreur? = null)

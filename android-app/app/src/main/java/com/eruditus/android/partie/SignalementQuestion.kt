package com.eruditus.android.partie

import com.eruditus.android.avis.ReglesAvis
import com.eruditus.android.compte.TexteErreur

/** Les motifs d'un signalement (spec/ecrans.md § 9, D119), dans l'ordre de la boîte. */
enum class MotifSignalement(val code: String) {
    ReponseFausse("reponse_fausse"),
    EnonceAmbigu("enonce_ambigu"),
    ExplicationFausse("explication_fausse"),
    Autre("autre"),
}

/** Le commentaire suit la règle de l'avis (D86) puis tient en [COMMENTAIRE_MAX] caractères (D119). */
object ReglesSignalement {
    const val COMMENTAIRE_MAX = 200
}

/**
 * La boîte « Signaler cette question » et son résultat (D118), pour la question affichée, en
 * mémoire seulement. [erreur] : sous le champ. La saisie n'est jamais coupée ; au-delà de
 * [ReglesSignalement.COMMENTAIRE_MAX] l'envoi est bloqué et le compteur passe en `rouge`.
 */
data class EtatSignalement(
    val ouvert: Boolean = false,
    val motif: MotifSignalement? = null,
    val commentaire: String = "",
    val envoi: Boolean = false,
    val erreur: TexteErreur? = null,
    /** Fait : le lien devient « Question signalée. Merci. » (D118). */
    val envoye: Boolean = false,
) {
    /** Le commentaire tel qu'il partira. */
    val texteEnvoye: String get() = ReglesAvis.normaliser(commentaire)
    val longueur: Int get() = ReglesAvis.longueur(texteEnvoye)
    val tropLong: Boolean get() = longueur > ReglesSignalement.COMMENTAIRE_MAX
    val actif: Boolean get() = motif != null && !envoi && !tropLong

    /** Jamais le commentaire dans un log (D26). */
    override fun toString() = "EtatSignalement(ouvert=$ouvert, motif=$motif, envoi=$envoi, envoye=$envoye)"
}

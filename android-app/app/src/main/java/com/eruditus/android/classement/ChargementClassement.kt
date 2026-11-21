package com.eruditus.android.classement

import com.eruditus.android.compte.Affichage
import com.eruditus.android.compte.EcranErreur
import com.eruditus.android.compte.TexteErreur
import com.eruditus.android.compte.affichage
import com.eruditus.android.donnees.api.Classement
import com.eruditus.android.donnees.api.Resultat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * L'écran Classement, en mémoire seulement (D73) : [classement] nul avant la première réponse ;
 * [chargement] : une demande est en cours ; [message] : 429 ou erreur générique, en haut.
 */
data class EtatClassement(
    val classement: Classement? = null,
    val chargement: Boolean = false,
    val message: TexteErreur? = null,
) {
    /** Premier chargement : `ChargementEcran` centré, à la place du contenu. */
    val indicateurCentre: Boolean get() = chargement && classement == null

    /** Tirage : les lignes restent à l'écran, l'indicateur de tirage tourne. */
    val tirageEnCours: Boolean get() = chargement && classement != null

    /** Ce que montre l'écran, une fois une réponse reçue. */
    val vue: VueClassement? get() = classement?.let(ReglesClassement::vue)
}

/**
 * Chargement du Classement (spec/ecrans.md § 11, D73) : [ouvrir] repart de rien, indicateur centré
 * jusqu'à la première réponse ; [tirer] recharge en gardant les lignes ; un seul chargement à la
 * fois ; [quitter] oublie tout, et une réponse arrivée ensuite est ignorée. Session expirée et
 * pas de connexion passent par [sortir] (la navigation) ; 429 et autre refus : message en haut.
 * [charger] : `DepotCompte.classement`, qui efface la session sur un 401. Vérifié par
 * `ChargementClassementTest`.
 */
class ChargementClassement(
    private val charger: suspend () -> Resultat<Classement>,
    private val portee: CoroutineScope,
    private val sortir: (Affichage) -> Unit,
) {
    private val _etat = MutableStateFlow(EtatClassement())
    val etat: StateFlow<EtatClassement> = _etat.asStateFlow()

    private var demande: Job? = null

    /** Ouverture de l'écran : rien de l'ouverture précédente n'est gardé. */
    fun ouvrir() {
        quitter()
        demander()
    }

    /** Tirer la liste vers le bas : ignoré tant qu'un chargement est en cours. */
    fun tirer() {
        if (!_etat.value.chargement) demander()
    }

    fun quitter() {
        demande?.cancel()
        demande = null
        _etat.value = EtatClassement()
    }

    private fun demander() {
        // Relancer l'action efface le message (spec/ecrans.md § 1).
        _etat.value = _etat.value.copy(chargement = true, message = null)
        demande = portee.launch {
            when (val r = charger.invoke()) {
                is Resultat.Ok -> _etat.value = EtatClassement(classement = r.valeur)
                is Resultat.Echec -> when (val a = affichage(r.erreur, EcranErreur.Classement)) {
                    is Affichage.EnHaut -> _etat.value = _etat.value.copy(chargement = false, message = a.texte)
                    else -> {
                        _etat.value = _etat.value.copy(chargement = false)
                        sortir(a)
                    }
                }
            }
        }
    }
}

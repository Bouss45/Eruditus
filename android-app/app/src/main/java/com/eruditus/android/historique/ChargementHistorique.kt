package com.eruditus.android.historique

import com.eruditus.android.compte.Affichage
import com.eruditus.android.compte.EcranErreur
import com.eruditus.android.compte.TexteErreur
import com.eruditus.android.compte.affichage
import com.eruditus.android.donnees.api.CourbeLue
import com.eruditus.android.donnees.api.DetailPartie
import com.eruditus.android.donnees.api.ErreurApi
import com.eruditus.android.donnees.api.PageHistorique
import com.eruditus.android.donnees.api.PartieListee
import com.eruditus.android.donnees.api.Resultat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * L'écran Historique, en mémoire seulement (D136) : [parties] nul avant la première réponse ;
 * [courbe] nulle si elle manque (échec seul) ; [suivant] : le curseur de la page d'après ;
 * [chargement] : première page ou rechargement ; [suiteEnCours] : « Voir plus » ; [message] : en haut.
 */
data class EtatHistorique(
    val courbe: CourbeLue? = null,
    val parties: List<PartieListee>? = null,
    val suivant: String? = null,
    val chargement: Boolean = false,
    val suiteEnCours: Boolean = false,
    val message: TexteErreur? = null,
) {
    /** Premier chargement : `ChargementEcran` centré, à la place du contenu. */
    val indicateurCentre: Boolean get() = chargement && parties == null

    /** Tirage : le contenu reste à l'écran, l'indicateur de tirage tourne. */
    val tirageEnCours: Boolean get() = chargement && parties != null
}

/**
 * Chargement de l'Historique (spec/ecrans.md § 16, D131, D135) : [ouvrir] repart de rien et demande
 * la courbe et la première page ensemble ; [tirer] les recharge en gardant le contenu ; [voirPlus]
 * ajoute la page suivante, sans doublon et sans tri. Une seule demande à la fois. Session
 * expirée, pas de connexion : [sortir] (la navigation) ; 429 et autre refus : message en haut,
 * contenu gardé. Échec seul de la courbe : la liste s'affiche sans elle, avec un message.
 * Vérifié par `ChargementHistoriqueTest`.
 */
class ChargementHistorique(
    private val chargerPage: suspend (String?) -> Resultat<PageHistorique>,
    private val chargerCourbe: suspend () -> Resultat<CourbeLue>,
    private val portee: CoroutineScope,
    private val sortir: (Affichage) -> Unit,
) {
    private val _etat = MutableStateFlow(EtatHistorique())
    val etat: StateFlow<EtatHistorique> = _etat.asStateFlow()

    private var demande: Job? = null
    private var ouvert = false

    private val occupe: Boolean get() = _etat.value.chargement || _etat.value.suiteEnCours

    fun ouvrir() {
        quitter()
        ouvert = true
        premierePage()
    }

    /** Tirer vers le bas : ignoré tant qu'une demande est en cours. */
    fun tirer() {
        if (ouvert && !occupe) premierePage()
    }

    /** « Voir plus » : la page qui suit le curseur du serveur. */
    fun voirPlus() {
        val curseur = _etat.value.suivant ?: return
        if (ouvert && !occupe) suite(curseur)
    }

    /** Un message en haut de l'écran déjà affiché : le détail d'une partie n'est plus disponible (D134). */
    fun afficher(message: TexteErreur) {
        _etat.value = _etat.value.copy(message = message)
    }

    fun quitter() {
        ouvert = false
        demande?.cancel()
        demande = null
        _etat.value = EtatHistorique()
    }

    private fun premierePage() {
        _etat.value = _etat.value.copy(chargement = true, message = null)
        demande = portee.launch {
            val (page, courbe) = coroutineScope {
                val p = async { chargerPage(null) }
                val c = async { chargerCourbe() }
                p.await() to c.await()
            }
            when (page) {
                is Resultat.Echec -> echec(page.erreur)
                is Resultat.Ok -> _etat.value = EtatHistorique(
                    courbe = (courbe as? Resultat.Ok)?.valeur,
                    parties = page.valeur.parties,
                    suivant = page.valeur.suivant,
                    message = (courbe as? Resultat.Echec)?.let { messageCourbe(it.erreur) },
                )
            }
        }
    }

    private fun suite(curseur: String) {
        _etat.value = _etat.value.copy(suiteEnCours = true, message = null)
        demande = portee.launch {
            when (val page = chargerPage(curseur)) {
                is Resultat.Echec -> echec(page.erreur)
                is Resultat.Ok -> _etat.value = _etat.value.let {
                    it.copy(
                        parties = (it.parties.orEmpty() + page.valeur.parties).distinctBy { p -> p.id },
                        suivant = page.valeur.suivant,
                        suiteEnCours = false,
                    )
                }
            }
        }
    }

    private fun echec(erreur: ErreurApi) {
        val fini = _etat.value.copy(chargement = false, suiteEnCours = false)
        when (val a = affichage(erreur, EcranErreur.Historique)) {
            is Affichage.EnHaut -> _etat.value = fini.copy(message = a.texte)
            else -> {
                _etat.value = fini
                sortir(a)
            }
        }
    }

    /**
     * Échec de la courbe seule : la liste a répondu, donc le réseau marche. Seule une session
     * expirée quitte l'écran ; le reste est un message en haut.
     */
    private fun messageCourbe(erreur: ErreurApi): TexteErreur? = when (val a = affichage(erreur, EcranErreur.Historique)) {
        is Affichage.EnHaut -> a.texte
        Affichage.SessionExpiree -> {
            sortir(a)
            null
        }
        else -> TexteErreur.Generique
    }
}

/** Le détail d'une partie, en mémoire seulement : [detail] nul avant la réponse ; [message] : en haut, la barre reste. */
data class EtatDetail(
    val detail: DetailPartie? = null,
    val chargement: Boolean = false,
    val message: TexteErreur? = null,
) {
    val indicateurCentre: Boolean get() = chargement && detail == null
    val tirageEnCours: Boolean get() = chargement && detail != null
}

/**
 * Chargement du détail d'une partie (spec/ecrans.md § 17) : [ouvrir] demande `GET /historique/{id}` ;
 * `partie_introuvable` retourne à l'Historique, session expirée et pas de connexion quittent
 * l'écran ([sortir] fait la navigation), 429 et autre refus : message en haut, [tirer] réessaie.
 * Vérifié par `ChargementHistoriqueTest`.
 */
class ChargementDetail(
    private val charger: suspend (String) -> Resultat<DetailPartie>,
    private val portee: CoroutineScope,
    private val sortir: (Affichage) -> Unit,
) {
    private val _etat = MutableStateFlow(EtatDetail())
    val etat: StateFlow<EtatDetail> = _etat.asStateFlow()

    private var demande: Job? = null
    private var id: String? = null

    fun ouvrir(partie: String) {
        quitter()
        id = partie
        demander(partie)
    }

    fun tirer() {
        val partie = id ?: return
        if (!_etat.value.chargement) demander(partie)
    }

    fun quitter() {
        id = null
        demande?.cancel()
        demande = null
        _etat.value = EtatDetail()
    }

    private fun demander(partie: String) {
        _etat.value = _etat.value.copy(chargement = true, message = null)
        demande = portee.launch {
            when (val r = charger(partie)) {
                is Resultat.Ok -> _etat.value = EtatDetail(detail = r.valeur)
                is Resultat.Echec -> when (val a = affichage(r.erreur, EcranErreur.DetailPartie)) {
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

package com.eruditus.android.partie

import com.eruditus.android.compte.Affichage
import com.eruditus.android.compte.EcranErreur
import com.eruditus.android.compte.TexteErreur
import com.eruditus.android.compte.affichage
import com.eruditus.android.donnees.api.Bilan
import com.eruditus.android.donnees.api.ClientApi
import com.eruditus.android.donnees.api.PartieCreee
import com.eruditus.android.donnees.api.QuestionServie
import com.eruditus.android.donnees.api.ReponseCorrigee
import com.eruditus.android.donnees.api.Resultat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Où en est la question affichée (D66, D67). */
sealed interface Phase {
    /** Le chrono tourne ; les choix se touchent tant qu'il reste du temps. */
    data object Attente : Phase

    /** [choix] est parti (nul : sans réponse) ; le chrono est arrêté à [restantMs]. */
    data class Envoi(val choix: Int?, val restantMs: Long) : Phase

    /** La correction est là, chrono arrêté à [restantMs] ; [suite] : la question suivante est demandée. */
    data class Corrigee(val reponse: ReponseCorrigee, val restantMs: Long, val suite: Boolean = false) : Phase
}

/**
 * La partie en cours, en mémoire seulement (D65). [finMs] : l'instant, sur l'horloge monotone, où
 * le chrono atteint 0 : réception de la question + son `restantMs` (D66). [message] : 429 ou
 * erreur générique, en haut de la Partie (D68).
 */
data class EtatPartie(
    val id: String,
    val question: QuestionServie,
    val finMs: Long,
    val phase: Phase = Phase.Attente,
    val message: TexteErreur? = null,
    val signalement: EtatSignalement = EtatSignalement(),
) {
    /** Temps restant affiché : il décompte pendant l'attente, arrêté ensuite. */
    fun restantMs(maintenant: Long): Long = when (phase) {
        Phase.Attente -> (finMs - maintenant).coerceIn(0, question.delaiMs)
        is Phase.Envoi -> phase.restantMs
        is Phase.Corrigee -> phase.restantMs
    }
}

/** Ce que la partie demande à l'app hors de son écran (D65, D68). */
sealed interface SortiePartie {
    /** La partie est créée : la Partie s'ouvre sur la question 1. */
    data object Ouverte : SortiePartie

    /** « Voir le résultat », après la dernière correction. */
    data class VoirResultat(val bilan: Bilan, val total: Int) : SortiePartie

    /** Retour à l'Accueil avec ce message en haut : plus valable, aucune partie disponible. */
    data class Accueil(val message: TexteErreur) : SortiePartie

    /** « Jouer » ou « Rejouer » refusé : ce message en haut de l'écran en cours. */
    data class MessageIci(val texte: TexteErreur) : SortiePartie

    data class PasDeConnexion(val serveur: Boolean) : SortiePartie

    /** Session effacée : Bienvenue avec « Ta session a expiré, reconnecte-toi. ». */
    data object SessionExpiree : SortiePartie
}

/**
 * Déroulement d'une partie (spec/ecrans.md § 9, § 10 ; D65 – D68) : le serveur décide de tout,
 * l'app n'envoie que des choix.
 *
 * - Le compte à rebours part toujours du `restantMs` du serveur, jamais de 20 s fixes ; à 0, un
 *   seul envoi « sans réponse » par question, et jamais tant que l'app est en arrière-plan.
 * - Au retour au premier plan (ou à la recréation de l'écran), une question qui attend encore sa
 *   réponse est redemandée et le chrono recalé ; une correction ne l'est jamais.
 * - Un seul envoi par question, une seule demande de question suivante, une seule création.
 * - [quitter] annule tout : une réponse arrivée ensuite est ignorée.
 *
 * [horloge] : horloge monotone en ms (`elapsedRealtime`) ; [portee] : celle du modèle de l'app ;
 * [sortir] : la navigation. Vérifié par `DeroulementPartieTest`.
 */
class DeroulementPartie(
    private val api: ClientApi,
    private val effacerSession: suspend () -> Unit,
    private val horloge: () -> Long,
    private val portee: CoroutineScope,
    private val sortir: (SortiePartie) -> Unit,
) {
    private val _etat = MutableStateFlow<EtatPartie?>(null)
    val etat: StateFlow<EtatPartie?> = _etat.asStateFlow()

    private val _creation = MutableStateFlow(false)

    /** « Jouer » ou « Rejouer » attend le serveur : indicateur dans le bouton. */
    val creation: StateFlow<Boolean> = _creation.asStateFlow()

    /** Appels et minuterie de la partie en cours, annulés ensemble quand on la quitte. */
    private var travaux = SupervisorJob(portee.coroutineContext[Job])
    private var minuterie: Job? = null
    private var envoiAutomatiqueFait = false
    private var redemandeEnCours = false
    private var premierPlan = true
    private var arretee = false

    /** « Jouer » ou « Rejouer » : crée la partie ; la Partie ne s'ouvre qu'au succès. */
    fun jouer() {
        if (_creation.value) return
        fermer()
        _creation.value = true
        portee.launch {
            val r = api.creerPartie()
            _creation.value = false
            when (r) {
                is Resultat.Ok -> {
                    ouvrir(r.valeur)
                    sortir(SortiePartie.Ouverte)
                    armer()
                }
                is Resultat.Echec -> {
                    val a = affichage(r.erreur, EcranErreur.Jouer)
                    if (!quitterSur(a)) sortir(SortiePartie.MessageIci(texteEnHaut(a)))
                }
            }
        }
    }

    /** Toucher un choix : seul le premier appui part, tant que le chrono tourne. */
    fun choisir(index: Int) {
        val e = _etat.value ?: return
        if (e.phase != Phase.Attente || index !in 0..3) return
        val restant = e.restantMs(horloge())
        if (restant <= 0) return
        envoyer(e, index, restant)
    }

    /** « Question suivante », ou « Voir le résultat » après la dernière correction. */
    fun continuer() {
        val e = _etat.value ?: return
        val corrigee = e.phase as? Phase.Corrigee ?: return
        if (corrigee.suite) return
        val bilan = corrigee.reponse.bilan
        if (bilan != null) {
            fermer()
            sortir(SortiePartie.VoirResultat(bilan, e.question.total))
            return
        }
        val demande = e.copy(phase = corrigee.copy(suite = true), message = null)
        _etat.value = demande
        lancer {
            val r = api.question(e.id)
            if (_etat.value !== demande) return@lancer
            when (r) {
                is Resultat.Ok -> nouvelleQuestion(demande, r.valeur)
                is Resultat.Echec -> {
                    val a = affichage(r.erreur, EcranErreur.Partie)
                    if (!quitterSur(a)) _etat.value = demande.copy(phase = corrigee, message = texteEnHaut(a))
                }
            }
        }
    }

    // Signaler la question corrigée (D118, D119) -------------------------------------------------

    /** Le lien « Signaler cette question » : la boîte, sur la correction, tant que ce n'est pas fait. */
    fun ouvrirSignalement() = majSignalement { if (it.envoye) it else it.copy(ouvert = true) }

    /** « Annuler » : la boîte se ferme, la saisie reste ; rien ne se ferme pendant l'envoi. */
    fun fermerSignalement() = majSignalement { if (it.envoi) it else it.copy(ouvert = false) }

    fun choisirMotif(motif: MotifSignalement) =
        majSignalement { if (!it.ouvert || it.envoi) it else it.copy(motif = motif, erreur = null) }

    fun saisirCommentaire(texte: String) =
        majSignalement { if (!it.ouvert || it.envoi) it else it.copy(commentaire = texte, erreur = null) }

    /** « Envoyer » : un seul envoi à la fois, motif obligatoire ; une erreur laisse la boîte ouverte. */
    fun signaler() {
        val e = _etat.value ?: return
        val s = e.signalement
        val motif = s.motif
        if (e.phase !is Phase.Corrigee || !s.ouvert || motif == null || !s.actif) return
        _etat.value = e.copy(signalement = s.copy(envoi = true, erreur = null))
        lancer {
            val r = api.signalerQuestion(e.id, e.question.numero, motif.code, s.texteEnvoye)
            val actuel = _etat.value
            // La question suivante est arrivée (ou la partie est quittée) : cette réponse ne la concerne plus.
            if (actuel == null || actuel.question !== e.question) return@lancer
            when (r) {
                is Resultat.Ok -> _etat.value = actuel.copy(signalement = EtatSignalement(envoye = true))
                is Resultat.Echec -> {
                    val a = affichage(r.erreur, EcranErreur.Signalement)
                    if (!quitterSur(a)) {
                        val texte = (a as? Affichage.SousSignalement)?.texte ?: TexteErreur.Generique
                        _etat.value = actuel.copy(signalement = actuel.signalement.copy(envoi = false, erreur = texte))
                    }
                }
            }
        }
    }

    private fun majSignalement(transformer: (EtatSignalement) -> EtatSignalement) {
        val e = _etat.value ?: return
        if (e.phase !is Phase.Corrigee) return
        _etat.value = e.copy(signalement = transformer(e.signalement))
    }

    /** Abandon, ou départ de la partie : rien n'est envoyé au serveur (pas de route d'abandon). */
    fun quitter() = fermer()

    /** L'app passe en arrière-plan (ou son écran est détruit) : aucun envoi automatique. */
    fun enArrierePlan() {
        premierPlan = false
        arretee = true
        minuterie?.cancel()
        minuterie = null
    }

    /** L'app revient au premier plan : la question qui attend sa réponse est redemandée (D66). */
    fun auPremierPlan() {
        premierPlan = true
        val revenue = arretee
        arretee = false
        if (revenue && _etat.value?.phase == Phase.Attente) redemander() else armer()
    }

    // ------------------------------------------------------------------------------------------

    private fun ouvrir(partie: PartieCreee) {
        envoiAutomatiqueFait = false
        redemandeEnCours = false
        _etat.value = EtatPartie(partie.id, partie.question, horloge() + partie.question.restantMs)
    }

    /** Question servie : le chrono part de son `restantMs` (D66). */
    private fun nouvelleQuestion(e: EtatPartie, q: QuestionServie) {
        envoiAutomatiqueFait = false
        _etat.value = e.copy(question = q, finMs = horloge() + q.restantMs, phase = Phase.Attente, message = null, signalement = EtatSignalement())
        armer()
    }

    private fun envoyer(e: EtatPartie, choix: Int?, restant: Long) {
        minuterie?.cancel()
        minuterie = null
        val envoi = e.copy(phase = Phase.Envoi(choix, restant), message = null)
        _etat.value = envoi
        lancer {
            val r = api.repondre(e.id, e.question.numero, choix)
            if (_etat.value !== envoi) return@lancer
            when (r) {
                is Resultat.Ok ->
                    if (r.valeur.correction.numero != e.question.numero) {
                        quitterSur(Affichage.PasDeConnexion(serveur = true)) // correction illisible (D67)
                    } else {
                        _etat.value = envoi.copy(phase = Phase.Corrigee(r.valeur, restant))
                    }
                is Resultat.Echec -> {
                    val a = affichage(r.erreur, EcranErreur.Partie)
                    if (!quitterSur(a)) {
                        // Les choix reviennent ; l'envoi automatique déjà tenté n'est pas répété.
                        _etat.value = envoi.copy(phase = Phase.Attente, message = texteEnHaut(a))
                        armer()
                    }
                }
            }
        }
    }

    /** Redemande la question en attente, au retour au premier plan ; l'envoi automatique attend. */
    private fun redemander() {
        val e = _etat.value ?: return
        if (e.phase != Phase.Attente || redemandeEnCours) return
        redemandeEnCours = true
        minuterie?.cancel()
        minuterie = null
        lancer {
            val r = api.question(e.id)
            redemandeEnCours = false
            val actuel = _etat.value
            // Un choix touché pendant la redemande est parti : sa réponse l'emporte.
            if (actuel == null || actuel.id != e.id || actuel.phase != Phase.Attente) return@lancer
            when (r) {
                is Resultat.Ok -> nouvelleQuestion(actuel, r.valeur)
                is Resultat.Echec -> {
                    val a = affichage(r.erreur, EcranErreur.Partie)
                    if (!quitterSur(a)) {
                        _etat.value = actuel.copy(message = texteEnHaut(a))
                        armer()
                    }
                }
            }
        }
    }

    /** Minuterie de l'envoi automatique, au premier plan seulement. */
    private fun armer() {
        minuterie?.cancel()
        minuterie = null
        val e = _etat.value ?: return
        if (!premierPlan || e.phase != Phase.Attente || redemandeEnCours || envoiAutomatiqueFait) return
        minuterie = lancer {
            delay((e.finMs - horloge()).coerceAtLeast(0))
            minuterie = null
            expirer()
        }
    }

    /** Chrono à 0 : « sans réponse », une seule fois par question. */
    private fun expirer() {
        val e = _etat.value ?: return
        if (e.phase != Phase.Attente || envoiAutomatiqueFait || redemandeEnCours || !premierPlan) return
        envoiAutomatiqueFait = true
        envoyer(e, null, 0)
    }

    /**
     * Les erreurs qui arrêtent la partie : session expirée, pas de connexion, partie plus valable
     * ou aucune partie disponible. Rend faux pour un message en haut de l'écran en cours.
     */
    private suspend fun quitterSur(a: Affichage): Boolean {
        val sortie = when (a) {
            Affichage.SessionExpiree -> {
                effacerSession()
                SortiePartie.SessionExpiree
            }
            is Affichage.PasDeConnexion -> SortiePartie.PasDeConnexion(a.serveur)
            is Affichage.VersAccueil -> SortiePartie.Accueil(a.texte)
            else -> return false
        }
        fermer()
        sortir(sortie)
        return true
    }

    private fun texteEnHaut(a: Affichage): TexteErreur = (a as? Affichage.EnHaut)?.texte ?: TexteErreur.Generique

    private fun lancer(bloc: suspend CoroutineScope.() -> Unit): Job = portee.launch(travaux, block = bloc)

    private fun fermer() {
        travaux.cancel()
        travaux = SupervisorJob(portee.coroutineContext[Job])
        minuterie = null
        envoiAutomatiqueFait = false
        redemandeEnCours = false
        _etat.value = null
    }
}

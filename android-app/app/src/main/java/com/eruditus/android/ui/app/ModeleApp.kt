package com.eruditus.android.ui.app

import android.app.Application
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eruditus.android.BuildConfig
import com.eruditus.android.classement.ChargementClassement
import com.eruditus.android.historique.ChargementDetail
import com.eruditus.android.historique.ChargementHistorique
import com.eruditus.android.compte.Affichage
import com.eruditus.android.compte.Aiguillage
import com.eruditus.android.compte.DepotCompte
import com.eruditus.android.compte.EcranErreur
import com.eruditus.android.compte.ReglesMotDePasse
import com.eruditus.android.compte.ReglesPseudo
import com.eruditus.android.compte.SaisieCode
import com.eruditus.android.compte.TexteErreur
import com.eruditus.android.compte.affichage
import com.eruditus.android.donnees.api.Resultat
import com.eruditus.android.donnees.api.TransportHttps
import com.eruditus.android.donnees.session.ChiffreurKeystore
import com.eruditus.android.donnees.session.CoffreSession
import com.eruditus.android.misesajour.FichierMisesAJour
import com.eruditus.android.misesajour.Release
import com.eruditus.android.misesajour.SourceGitHub
import com.eruditus.android.misesajour.VerificationMisesAJour
import com.eruditus.android.parametres.FichierPreferences
import com.eruditus.android.parametres.Preferences
import com.eruditus.android.parametres.Theme
import com.eruditus.android.partie.DeroulementPartie
import com.eruditus.android.partie.SortiePartie
import com.eruditus.android.ui.ecrans.ArriveeBienvenue
import com.eruditus.android.ui.ecrans.BoiteCompte
import com.eruditus.android.ui.ecrans.EtatAccueil
import com.eruditus.android.ui.ecrans.EtatAvis
import com.eruditus.android.ui.ecrans.EtatCode
import com.eruditus.android.ui.ecrans.EtatChangementMotDePasse
import com.eruditus.android.ui.ecrans.EtatChangementPseudo
import com.eruditus.android.ui.ecrans.EtatParametres
import com.eruditus.android.ui.ecrans.InfoParametres
import com.eruditus.android.ui.ecrans.EtatConnexion
import com.eruditus.android.ui.ecrans.EtatCreation
import com.eruditus.android.ui.ecrans.EtatRecuperation
import com.eruditus.android.ui.ecrans.EtatResultat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Les écrans de la pile (spec/ecrans.md § 1.1). */
sealed interface Destination {
    data object Demarrage : Destination
    data class Bienvenue(val arrivee: ArriveeBienvenue? = null) : Destination
    data object Creation : Destination
    data object Connexion : Destination
    data object Recuperation : Destination
    data object Code : Destination
    data object Accueil : Destination
    data object Classement : Destination
    data object Historique : Destination
    data class DetailPartie(val id: String) : Destination
    data object Parametres : Destination
    data object ChangementPseudo : Destination
    data object ChangementMotDePasse : Destination
    data object Avis : Destination
    data object Partie : Destination
    data object Resultat : Destination
    data class PasDeConnexion(val serveur: Boolean) : Destination
    data object MiseAJour : Destination
}

/**
 * L'état de l'app et sa pile d'écrans, en mémoire seulement (D56) : il survit à une rotation ou à
 * un changement de thème, pas à la mort du processus, qui fait repartir de l'aiguillage. Mot de
 * passe et code n'existent qu'ici, le temps de leur écran.
 */
class ModeleApp(application: Application) : AndroidViewModel(application) {

    private val depot = DepotCompte(
        TransportHttps(BuildConfig.SERVEUR_URL),
        CoffreSession(File(application.noBackupFilesDir, "session"), ChiffreurKeystore()),
    )

    /** Le thème choisi (D77, D78), gardé sur le téléphone, hors de tout compte. */
    private val preferences = FichierPreferences(File(application.noBackupFilesDir, FichierPreferences.NOM))

    /** Nouvelles versions (D61) : en release seulement, au chargement de l'Accueil. */
    private val misesAJour = VerificationMisesAJour(
        source = SourceGitHub(),
        fichier = FichierMisesAJour(File(application.noBackupFilesDir, "mises-a-jour.json")),
        horloge = System::currentTimeMillis,
        nomInstalle = BuildConfig.VERSION_NAME,
        codeInstalle = BuildConfig.VERSION_CODE,
        active = BuildConfig.VERIFIER_MISES_A_JOUR,
    )

    /**
     * La partie (D65 – D68) : le serveur sert, chronomètre et corrige ; l'horloge est monotone, et
     * [sortiePartie] fait la navigation qu'elle demande.
     */
    val partie = DeroulementPartie(
        api = depot.api,
        effacerSession = depot::effacerSession,
        horloge = SystemClock::elapsedRealtime,
        portee = viewModelScope,
        sortir = ::sortiePartie,
    )

    /**
     * Le Classement (D72, D73) : repart de rien à chaque ouverture, oublié en quittant l'écran ;
     * session expirée et pas de connexion font leur navigation.
     */
    val classement = ChargementClassement(
        charger = depot::classement,
        portee = viewModelScope,
        sortir = { quitterSurErreur(it) },
    )

    /**
     * L'Historique (D136) : repart de rien à chaque ouverture, oublié en quittant l'écran ; le
     * détail d'une partie fait de même. Session expirée et pas de connexion font leur navigation.
     */
    val historique = ChargementHistorique(
        chargerPage = depot::historique,
        chargerCourbe = depot::courbe,
        portee = viewModelScope,
        sortir = { quitterSurErreur(it) },
    )

    val detailPartie = ChargementDetail(depot::detailPartie, viewModelScope, ::sortieDetail)

    val pile = mutableStateListOf<Destination>(Destination.Demarrage)

    /** Sens du dernier changement d'écran : vers l'avant (empiler, remplacer) ou en arrière. */
    var avant by mutableStateOf(true)
        private set

    var aiguillageEnCours by mutableStateOf(false)
        private set

    var creation by mutableStateOf(EtatCreation())
        private set
    var connexion by mutableStateOf(EtatConnexion())
        private set
    var recuperation by mutableStateOf(EtatRecuperation())
        private set
    var code by mutableStateOf<EtatCode?>(null)
        private set
    var accueil by mutableStateOf(EtatAccueil())
        private set
    var parametres by mutableStateOf(EtatParametres())
        private set
    var changementPseudo by mutableStateOf(EtatChangementPseudo(actuel = ""))
        private set
    var changementMotDePasse by mutableStateOf(EtatChangementMotDePasse())
        private set
    var avis by mutableStateOf(EtatAvis())
        private set

    /** Compte les ouvertures d'Envoyer un avis : un envoi abandonné ne touche pas au suivant (D85). */
    private var ouverturesAvis = 0

    /**
     * Le thème de l'app (D77), lu dans le fichier ; il change une fois le nouveau choix écrit,
     * pour que l'activité, recréée à ce moment-là, relise le bon (`MainActivity`).
     */
    var theme by mutableStateOf(preferences.lire().theme)
        private set

    /** Le Résultat de la dernière partie (D69). */
    var resultat by mutableStateOf<EtatResultat?>(null)
        private set

    /** La boîte « Abandonner la partie ? » est ouverte. */
    var abandon by mutableStateOf(false)
        private set

    /** Message à montrer en haut de l'Accueil à son arrivée : partie plus valable, aucune partie (D68). */
    private var messageArrivee: TexteErreur? = null

    /** Le pseudo de la session, pour l'Accueil avant `/moi` et pour l'écran Paramètres. */
    val pseudo: String get() = depot.session?.pseudo.orEmpty()

    /** L'écran de lancement d'Android reste affiché tant que l'aiguillage n'a pas abouti. */
    val demarrage: Boolean get() = pile.lastOrNull() == Destination.Demarrage

    init {
        aiguiller()
    }

    // Navigation ------------------------------------------------------------------------------

    private fun empiler(destination: Destination) {
        avant = true
        pile.add(destination)
    }

    /** Remplace l'écran du sommet : le Résultat remplace la Partie, « Rejouer » le Résultat (D65). */
    private fun remplacerSommet(destination: Destination) {
        avant = true
        pile[pile.lastIndex] = destination
    }

    /** Remplace toute la pile (Bienvenue, Code, Accueil, Pas de connexion, Mise à jour). */
    private fun remplacer(destination: Destination) {
        avant = true
        pile.clear()
        pile.add(destination)
    }

    /**
     * Retour système ou flèche de la barre : dépile, en oubliant la saisie de l'écran quitté. Sur
     * la Partie : la boîte d'abandon ; le chrono continue (spec/ecrans.md § 9). Sur Envoyer un
     * avis avec du texte : la boîte « Abandonner ton avis ? » (D85).
     */
    fun retour() {
        when (pile.lastOrNull()) {
            Destination.Code -> code = code?.copy(confirmation = true)
            Destination.Partie -> abandon = true
            Destination.Avis -> if (avis.confirmerRetour) avis = avis.copy(abandon = true) else depiler()
            else -> depiler()
        }
    }

    private fun depiler() {
        if (pile.size > 1) {
            avant = false
            when (pile.removeAt(pile.lastIndex)) {
                Destination.Creation -> creation = EtatCreation()
                Destination.Connexion -> connexion = EtatConnexion()
                Destination.Recuperation -> recuperation = EtatRecuperation()
                Destination.Parametres -> parametres = EtatParametres()
                Destination.ChangementPseudo -> changementPseudo = EtatChangementPseudo(actuel = "")
                Destination.ChangementMotDePasse -> changementMotDePasse = EtatChangementMotDePasse()
                Destination.Avis -> avis = EtatAvis()
                Destination.Classement -> classement.quitter()
                Destination.Historique -> historique.quitter()
                is Destination.DetailPartie -> detailPartie.quitter()
                else -> Unit
            }
        }
    }

    /** Le retour système est-il pris par l'app (sinon il quitte l'app, depuis un écran racine) ? */
    val retourIntercepte: Boolean get() = pile.size > 1 || pile.lastOrNull() == Destination.Code

    fun ouvrirCreation() {
        creation = EtatCreation()
        empiler(Destination.Creation)
    }

    fun ouvrirConnexion() {
        connexion = EtatConnexion()
        empiler(Destination.Connexion)
    }

    fun ouvrirRecuperation() {
        recuperation = EtatRecuperation()
        empiler(Destination.Recuperation)
    }

    /** « Paramètres » sur l'Accueil (D76). */
    fun ouvrirParametres() {
        parametres = EtatParametres()
        empiler(Destination.Parametres)
    }

    /** « Changer de pseudo » : le formulaire, prérempli avec le pseudo actuel (D83). */
    fun ouvrirChangementPseudo() {
        parametres = parametres.copy(info = null)
        changementPseudo = EtatChangementPseudo(actuel = pseudo)
        empiler(Destination.ChangementPseudo)
    }

    fun ouvrirChangementMotDePasse() {
        parametres = parametres.copy(info = null)
        changementMotDePasse = EtatChangementMotDePasse()
        empiler(Destination.ChangementMotDePasse)
    }

    /** « Envoyer un avis » dans Paramètres (D85) : le formulaire, vide. */
    fun ouvrirAvis() {
        parametres = parametres.copy(info = null)
        avis = EtatAvis()
        ouverturesAvis++
        empiler(Destination.Avis)
    }

    /** « Classement » sur l'Accueil (D71) : indicateur centré jusqu'à la première réponse. */
    fun ouvrirClassement() {
        empiler(Destination.Classement)
        classement.ouvrir()
    }

    /** « Historique » sur l'Accueil (D136) : indicateur centré jusqu'à la première réponse. */
    fun ouvrirHistorique() {
        empiler(Destination.Historique)
        historique.ouvrir()
    }

    /** Une partie de l'Historique (D132) : son détail se charge à l'ouverture. */
    fun ouvrirDetail(id: String) {
        empiler(Destination.DetailPartie(id))
        detailPartie.ouvrir(id)
    }

    /** Partie introuvable : retour à l'Historique avec son message (D134) ; le reste quitte l'écran comme ailleurs. */
    private fun sortieDetail(affichage: Affichage) {
        if (affichage is Affichage.VersHistorique) {
            depiler()
            historique.afficher(affichage.texte)
        } else {
            quitterSurErreur(affichage)
        }
    }

    private fun bienvenue(arrivee: ArriveeBienvenue? = null) {
        oublierSaisies()
        accueil = EtatAccueil()
        remplacer(Destination.Bienvenue(arrivee))
    }

    private fun versAccueil() {
        oublierSaisies()
        remplacer(Destination.Accueil)
    }

    private fun pasDeConnexion(serveur: Boolean) {
        oublierSaisies()
        remplacer(Destination.PasDeConnexion(serveur))
    }

    private fun oublierSaisies() {
        creation = EtatCreation()
        connexion = EtatConnexion()
        recuperation = EtatRecuperation()
        parametres = EtatParametres()
        changementPseudo = EtatChangementPseudo(actuel = "")
        changementMotDePasse = EtatChangementMotDePasse()
        avis = EtatAvis()
        code = null
        classement.quitter()
        historique.quitter()
        detailPartie.quitter()
        partie.quitter()
        resultat = null
        abandon = false
        messageArrivee = null
    }

    // Démarrage -------------------------------------------------------------------------------

    /** Aiguillage du démarrage (§ 1.2), relancé par « Réessayer ». */
    fun aiguiller() {
        if (aiguillageEnCours) return
        aiguillageEnCours = true
        viewModelScope.launch {
            val resultat = depot.aiguiller(BuildConfig.VERSION_CODE)
            aiguillageEnCours = false
            when (resultat) {
                Aiguillage.MiseAJour -> remplacer(Destination.MiseAJour)
                is Aiguillage.PasDeConnexion -> pasDeConnexion(resultat.serveur)
                is Aiguillage.Bienvenue -> bienvenue(if (resultat.sessionExpiree) ArriveeBienvenue.SessionExpiree else null)
                Aiguillage.Accueil -> {
                    accueil = EtatAccueil()
                    versAccueil()
                }
            }
        }
    }

    /** Erreurs qui quittent l'écran : session expirée, pas de connexion. Rend vrai si traitée. */
    private fun quitterSurErreur(affichage: Affichage): Boolean = when (affichage) {
        Affichage.SessionExpiree -> {
            bienvenue(ArriveeBienvenue.SessionExpiree)
            true
        }
        is Affichage.PasDeConnexion -> {
            pasDeConnexion(affichage.serveur)
            true
        }
        else -> false
    }

    // Créer un compte -------------------------------------------------------------------------

    fun saisirCreationPseudo(valeur: String) {
        creation = creation.copy(pseudo = valeur, erreurPseudoServeur = null)
    }

    fun saisirCreationMotDePasse(valeur: String) {
        creation = creation.copy(motDePasse = valeur, erreurMotDePasseServeur = null)
    }

    fun creerCompte() {
        val etat = creation
        if (!etat.actif) return
        creation = etat.copy(envoi = true, message = null, erreurPseudoServeur = null, erreurMotDePasseServeur = null)
        viewModelScope.launch {
            when (val r = depot.creer(etat.pseudo, etat.motDePasse)) {
                is Resultat.Ok -> montrerCode(r.valeur)
                is Resultat.Echec -> {
                    val a = affichage(r.erreur, EcranErreur.Creation)
                    if (!quitterSurErreur(a)) {
                        creation = creation.copy(
                            envoi = false,
                            erreurPseudoServeur = (a as? Affichage.SousPseudo)?.texte,
                            erreurMotDePasseServeur = (a as? Affichage.SousMotDePasse)?.texte,
                            message = (a as? Affichage.EnHaut)?.texte,
                        )
                    }
                }
            }
        }
    }

    // Code de récupération --------------------------------------------------------------------

    private fun montrerCode(valeur: String) {
        oublierSaisies()
        accueil = EtatAccueil()
        code = EtatCode(valeur)
        remplacer(Destination.Code)
    }

    /** « Copier » ; [confirmationVisible] : Android 12 et moins n'affichent pas de confirmation. */
    fun codeCopie(confirmationVisible: Boolean) {
        if (!confirmationVisible) code = code?.copy(copie = true)
    }

    fun fermerConfirmationCode() {
        code = code?.copy(confirmation = false)
    }

    /** « J'ai noté mon code » ou « Oui » : le code est oublié. */
    fun codeNote() = versAccueil()

    // Se connecter ----------------------------------------------------------------------------

    fun saisirConnexionPseudo(valeur: String) {
        connexion = connexion.copy(pseudo = valeur)
    }

    fun saisirConnexionMotDePasse(valeur: String) {
        connexion = connexion.copy(motDePasse = valeur)
    }

    fun connecter() {
        val etat = connexion
        if (!etat.actif) return
        connexion = etat.copy(envoi = true, erreur = null, message = null)
        viewModelScope.launch {
            when (val r = depot.connecter(etat.pseudo, etat.motDePasse)) {
                is Resultat.Ok -> {
                    accueil = EtatAccueil()
                    versAccueil()
                }
                is Resultat.Echec -> {
                    val a = affichage(r.erreur, EcranErreur.Connexion)
                    if (!quitterSurErreur(a)) {
                        connexion = connexion.copy(
                            envoi = false,
                            erreur = (a as? Affichage.AuDessusAction)?.texte,
                            message = (a as? Affichage.EnHaut)?.texte,
                        )
                    }
                }
            }
        }
    }

    // Mot de passe oublié ---------------------------------------------------------------------

    fun saisirRecuperationPseudo(valeur: String) {
        recuperation = recuperation.copy(pseudo = valeur)
    }

    fun saisirRecuperationCode(valeur: String) {
        recuperation = recuperation.copy(code = valeur)
    }

    fun saisirRecuperationMotDePasse(valeur: String) {
        recuperation = recuperation.copy(motDePasse = valeur, erreurMotDePasseEnvoi = null)
    }

    /**
     * Le nouveau mot de passe d'abord (D10), puis pseudo et code bien formés (D55) : sinon, aucun
     * appel au serveur.
     */
    fun recuperer() {
        val etat = recuperation
        if (!etat.actif) return
        if (!ReglesMotDePasse.valide(etat.motDePasse)) {
            recuperation = etat.copy(erreurMotDePasseEnvoi = TexteErreur.MotDePasseInvalide, erreur = null, message = null)
            return
        }
        val pseudo = ReglesPseudo.nettoyer(etat.pseudo)
        val codeNormalise = SaisieCode.normaliser(etat.code)
        if (!ReglesPseudo.valide(pseudo) || codeNormalise == null) {
            recuperation = etat.copy(erreur = TexteErreur.CodeIncorrect, message = null)
            return
        }
        recuperation = etat.copy(envoi = true, erreur = null, message = null)
        viewModelScope.launch {
            when (val r = depot.recuperer(pseudo, codeNormalise, etat.motDePasse)) {
                is Resultat.Ok -> montrerCode(r.valeur)
                is Resultat.Echec -> {
                    val a = affichage(r.erreur, EcranErreur.Recuperation)
                    if (!quitterSurErreur(a)) {
                        recuperation = recuperation.copy(
                            envoi = false,
                            erreurMotDePasseEnvoi = (a as? Affichage.SousMotDePasse)?.texte,
                            erreur = (a as? Affichage.AuDessusAction)?.texte,
                            message = (a as? Affichage.EnHaut)?.texte,
                        )
                    }
                }
            }
        }
    }

    // Accueil ---------------------------------------------------------------------------------

    /**
     * `GET /moi` à chaque arrivée sur l'Accueil ; les valeurs déjà là restent affichées (§ 1). En
     * même temps, sans l'attendre, la vérification des nouvelles versions si elle est due (D61) :
     * elle ne lève rien et n'ouvre jamais « Pas de connexion ».
     */
    fun chargerAccueil() {
        // Un message d'arrivée (partie plus valable, aucune partie) reste pendant le rechargement.
        accueil = accueil.copy(chargement = true, message = messageArrivee)
        messageArrivee = null
        viewModelScope.launch {
            val annonce = misesAJour.verifierSiDue()
            if (pile.lastOrNull() == Destination.Accueil) accueil = accueil.copy(annonce = annonce)
        }
        viewModelScope.launch {
            when (val r = depot.moi()) {
                is Resultat.Ok -> accueil = accueil.copy(moi = r.valeur, chargement = false)
                is Resultat.Echec -> {
                    val a = affichage(r.erreur, EcranErreur.Accueil)
                    if (!quitterSurErreur(a)) {
                        accueil = accueil.copy(chargement = false, message = (a as? Affichage.EnHaut)?.texte)
                    }
                }
            }
        }
    }

    /** « Plus tard » : l'annonce disparaît, cette version n'est plus jamais annoncée (D62). */
    fun plusTard() {
        val version = accueil.annonce?.version ?: return
        accueil = accueil.copy(annonce = null)
        viewModelScope.launch { withContext(Dispatchers.IO) { misesAJour.ignorer(version) } }
    }

    /** La Release vers laquelle « Mettre à jour » envoie (D63), lue sur le disque, puis [ouvrir]. */
    fun ouvrirMiseAJour(ouvrir: (Release?) -> Unit) {
        viewModelScope.launch { ouvrir(withContext(Dispatchers.IO) { misesAJour.pourMettreAJour() }) }
    }

    // Partie et Résultat ------------------------------------------------------------------------

    /** « Jouer » : la Partie s'ouvre quand le serveur a créé la partie (D65). */
    fun jouer() {
        accueil = accueil.copy(message = null)
        partie.jouer()
    }

    /** « Rejouer » : une nouvelle Partie remplace le Résultat. */
    fun rejouer() {
        resultat = resultat?.copy(message = null)
        partie.jouer()
    }

    /** « Continuer » dans la boîte d'abandon. */
    fun continuerPartie() {
        abandon = false
    }

    /** « Abandonner » : retour à l'Accueil, sans appel au serveur. */
    fun abandonner() {
        partie.quitter()
        revenirAccueil(null)
    }

    /** La navigation que demande la partie (D65, D68). */
    private fun sortiePartie(sortie: SortiePartie) {
        when (sortie) {
            SortiePartie.Ouverte -> {
                abandon = false
                if (pile.lastOrNull() == Destination.Resultat) remplacerSommet(Destination.Partie) else empiler(Destination.Partie)
            }
            is SortiePartie.VoirResultat -> {
                abandon = false
                resultat = EtatResultat(sortie.bilan, sortie.total)
                remplacerSommet(Destination.Resultat)
            }
            is SortiePartie.Accueil -> revenirAccueil(sortie.message)
            is SortiePartie.MessageIci ->
                if (pile.lastOrNull() == Destination.Resultat) {
                    resultat = resultat?.copy(message = sortie.texte)
                } else {
                    accueil = accueil.copy(message = sortie.texte)
                }
            is SortiePartie.PasDeConnexion -> pasDeConnexion(sortie.serveur)
            SortiePartie.SessionExpiree -> bienvenue(ArriveeBienvenue.SessionExpiree)
        }
    }

    /** Retour à l'Accueil, sous la Partie ou le Résultat, avec [message] en haut ou sans. */
    private fun revenirAccueil(message: TexteErreur?) {
        abandon = false
        if (pile.lastOrNull() == Destination.Accueil) {
            accueil = accueil.copy(message = message)
            return
        }
        messageArrivee = message
        val accueilDansLaPile = pile.lastIndexOf(Destination.Accueil)
        if (accueilDansLaPile < 0) {
            remplacer(Destination.Accueil)
        } else {
            avant = false
            while (pile.lastIndex > accueilDansLaPile) pile.removeAt(pile.lastIndex)
        }
    }

    // Paramètres --------------------------------------------------------------------------------

    fun ouvrirBoite(boite: BoiteCompte) {
        parametres = EtatParametres(boite = boite)
    }

    fun fermerBoite() {
        if (!parametres.envoi) parametres = parametres.copy(boite = null, motDePasse = "", erreur = null)
    }

    fun saisirMotDePasseSuppression(valeur: String) {
        parametres = parametres.copy(motDePasse = valeur, erreur = null)
    }

    /**
     * Le thème (D77) : écrit d'abord (D78), puis [theme] change et l'activité l'applique. Le
     * message en haut de Paramètres s'efface, comme à toute action (spec/ecrans.md § 1).
     */
    fun choisirTheme(choisi: Theme) {
        parametres = parametres.copy(info = null)
        if (choisi == theme) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { preferences.ecrire(Preferences(choisi)) }
            theme = choisi
        }
    }

    /** Retour à Paramètres après un changement réussi ou un avis envoyé, avec son message info en haut (D83, D85). */
    private fun revenirParametres(info: InfoParametres) {
        changementPseudo = EtatChangementPseudo(actuel = "")
        changementMotDePasse = EtatChangementMotDePasse()
        avis = EtatAvis()
        parametres = parametres.copy(info = info)
        val ici = pile.lastIndexOf(Destination.Parametres)
        if (ici < 0) return
        avant = false
        while (pile.lastIndex > ici) pile.removeAt(pile.lastIndex)
    }

    fun saisirNouveauPseudo(valeur: String) {
        changementPseudo = changementPseudo.copy(pseudo = valeur, erreurServeur = null)
    }

    /** « Changer mon pseudo » (D79, D83) : erreurs du pseudo sous le champ, comme à la création. */
    fun changerPseudo() {
        val etat = changementPseudo
        if (!etat.actif) return
        changementPseudo = etat.copy(envoi = true, erreurServeur = null, message = null)
        viewModelScope.launch {
            when (val r = depot.changerPseudo(etat.pseudo)) {
                is Resultat.Ok -> revenirParametres(InfoParametres.PseudoChange(r.valeur))
                is Resultat.Echec -> {
                    val a = affichage(r.erreur, EcranErreur.ChangementPseudo)
                    if (!quitterSurErreur(a)) {
                        changementPseudo = changementPseudo.copy(
                            envoi = false,
                            erreurServeur = (a as? Affichage.SousPseudo)?.texte,
                            message = (a as? Affichage.EnHaut)?.texte,
                        )
                    }
                }
            }
        }
    }

    fun saisirMotDePasseActuel(valeur: String) {
        changementMotDePasse = changementMotDePasse.copy(actuel = valeur, erreurActuel = null)
    }

    fun saisirNouveauMotDePasse(valeur: String) {
        changementMotDePasse = changementMotDePasse.copy(nouveau = valeur, erreurNouveauServeur = null)
    }

    /**
     * « Changer mon mot de passe » (D81, D83) : le jeton neuf remplace l'ancien (le dépôt
     * l'enregistre) ; les autres appareils sont déconnectés.
     */
    fun changerMotDePasse() {
        val etat = changementMotDePasse
        if (!etat.actif) return
        changementMotDePasse = etat.copy(envoi = true, erreurActuel = null, erreurNouveauServeur = null, message = null)
        viewModelScope.launch {
            when (val r = depot.changerMotDePasse(etat.actuel, etat.nouveau)) {
                is Resultat.Ok -> revenirParametres(InfoParametres.MotDePasseChange)
                is Resultat.Echec -> {
                    val a = affichage(r.erreur, EcranErreur.ChangementMotDePasse)
                    if (!quitterSurErreur(a)) {
                        changementMotDePasse = changementMotDePasse.copy(
                            envoi = false,
                            erreurActuel = (a as? Affichage.SousMotDePasseActuel)?.texte,
                            erreurNouveauServeur = (a as? Affichage.SousMotDePasse)?.texte,
                            message = (a as? Affichage.EnHaut)?.texte,
                        )
                    }
                }
            }
        }
    }

    fun saisirAvis(valeur: String) {
        avis = avis.saisir(valeur)
    }

    /**
     * « Envoyer » (D87, D93) : le texte passé par la règle, la version de l'app et celle d'Android.
     * Succès : Paramètres et son message ; échec : le texte reste, l'erreur à sa place. Abandonné
     * pendant l'envoi (D85) : un succès met le message sur Paramètres si l'on y est encore, un échec
     * est ignoré, sauf la session expirée, déjà effacée : Bienvenue.
     */
    fun envoyerAvis() {
        val etat = avis
        if (!etat.actif) return
        val ouverture = ouverturesAvis
        avis = etat.envoyer()
        viewModelScope.launch {
            val r = depot.envoyerAvis(etat.texte, BuildConfig.VERSION_NAME, Build.VERSION.RELEASE)
            val ouvert = ouverture == ouverturesAvis && pile.lastOrNull() == Destination.Avis
            when (r) {
                is Resultat.Ok -> when {
                    ouvert -> revenirParametres(InfoParametres.AvisEnvoye)
                    pile.lastOrNull() == Destination.Parametres -> parametres = parametres.copy(info = InfoParametres.AvisEnvoye)
                }
                is Resultat.Echec -> {
                    val a = affichage(r.erreur, EcranErreur.Avis)
                    if (!quitterSurErreur(a) && ouvert) avis = avis.apresEchec(a)
                }
            }
        }
    }

    /** « Continuer » dans la boîte d'abandon : elle se ferme, le texte reste. */
    fun continuerAvis() {
        avis = avis.copy(abandon = false)
    }

    /** « Abandonner » : retour à Paramètres, texte oublié. */
    fun abandonnerAvis() {
        if (pile.lastOrNull() == Destination.Avis) depiler()
    }

    /** Déconnexion de tous les appareils ; Bienvenue sans message, quoi qu'il arrive (D13). */
    fun deconnecter() {
        if (parametres.envoi) return
        parametres = parametres.copy(envoi = true)
        viewModelScope.launch {
            depot.deconnecter()
            bienvenue()
        }
    }

    fun supprimer() {
        val etat = parametres
        if (etat.envoi || etat.motDePasse.isEmpty()) return
        parametres = etat.copy(envoi = true, erreur = null)
        viewModelScope.launch {
            when (val r = depot.supprimer(etat.motDePasse)) {
                is Resultat.Ok -> bienvenue(ArriveeBienvenue.CompteSupprime)
                is Resultat.Echec -> {
                    val a = affichage(r.erreur, EcranErreur.Suppression)
                    if (!quitterSurErreur(a)) {
                        parametres = parametres.copy(envoi = false, erreur = (a as? Affichage.SousMotDePasse)?.texte ?: TexteErreur.Generique)
                    }
                }
            }
        }
    }
}

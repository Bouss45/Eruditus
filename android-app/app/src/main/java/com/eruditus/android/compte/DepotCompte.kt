package com.eruditus.android.compte

import com.eruditus.android.avis.ReglesAvis
import com.eruditus.android.donnees.api.Classement
import com.eruditus.android.donnees.api.CourbeLue
import com.eruditus.android.donnees.api.DetailPartie
import com.eruditus.android.donnees.api.PageHistorique
import com.eruditus.android.donnees.api.ClientApi
import com.eruditus.android.donnees.api.ErreurApi
import com.eruditus.android.donnees.api.Moi
import com.eruditus.android.donnees.api.Resultat
import com.eruditus.android.donnees.api.SessionOuverte
import com.eruditus.android.donnees.api.Transport
import com.eruditus.android.donnees.session.CoffreSession
import com.eruditus.android.donnees.session.Session
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Où mène l'aiguillage du démarrage (spec/ecrans.md § 1.2). */
sealed interface Aiguillage {
    data object MiseAJour : Aiguillage
    data class PasDeConnexion(val serveur: Boolean) : Aiguillage
    data class Bienvenue(val sessionExpiree: Boolean) : Aiguillage
    data object Accueil : Aiguillage
}

/**
 * Le compte du joueur : la session (en mémoire et dans le [coffre]) et les appels qui l'ouvrent, la
 * renouvellent et la ferment (spec/regles.md § 1, spec/donnees.md § 5). Tout 401 sur une route à
 * jeton efface la session. Le fichier est lu et écrit sur [disque].
 */
class DepotCompte(
    transport: Transport,
    private val coffre: CoffreSession,
    private val disque: CoroutineDispatcher = Dispatchers.IO,
) {
    /** La session en cours, en mémoire ; null hors connexion. */
    @Volatile
    var session: Session? = null
        private set

    /** Le client de l'API, avec le jeton de la session en cours ; aussi pour la partie. */
    val api = ClientApi(transport) { session?.jeton }

    /** `/info` sans jeton, puis la session locale et son renouvellement (D21, D30, D53). */
    suspend fun aiguiller(versionCode: Int): Aiguillage {
        when (val info = api.info()) {
            is Resultat.Echec -> return Aiguillage.PasDeConnexion(serveur = info.erreur != ErreurApi.Reseau)
            is Resultat.Ok -> if (versionCode < info.valeur) return Aiguillage.MiseAJour
        }
        val locale = withContext(disque) { coffre.lire() } ?: run {
            session = null
            return Aiguillage.Bienvenue(sessionExpiree = false)
        }
        session = locale
        return when (val renouvele = api.renouveler()) {
            is Resultat.Ok -> {
                enregistrer(locale.copy(jeton = renouvele.valeur.jeton, idJoueur = renouvele.valeur.idJoueur))
                Aiguillage.Accueil
            }
            is Resultat.Echec -> when (renouvele.erreur) {
                ErreurApi.NonAutorise -> {
                    effacerSession()
                    Aiguillage.Bienvenue(sessionExpiree = true)
                }
                ErreurApi.Reseau -> Aiguillage.PasDeConnexion(serveur = false)
                else -> Aiguillage.PasDeConnexion(serveur = true)
            }
        }
    }

    /** Crée le compte et ouvre sa session ; rend le code de récupération. */
    suspend fun creer(pseudo: String, motDePasse: String): Resultat<String> =
        ouvrir(api.creerCompte(ReglesPseudo.nettoyer(pseudo), motDePasse), pseudoSaisi = null)

    /** Connexion : le pseudo saisi reste celui de la session jusqu'au premier `/moi` (D54). */
    suspend fun connecter(pseudo: String, motDePasse: String): Resultat<Unit> {
        val nettoye = ReglesPseudo.nettoyer(pseudo)
        return when (val r = ouvrir(api.connexion(nettoye, motDePasse), pseudoSaisi = nettoye)) {
            is Resultat.Ok -> Resultat.Ok(Unit)
            is Resultat.Echec -> r
        }
    }

    /** Nouveau mot de passe par le code ; rend le nouveau code. [code] : déjà normalisé. */
    suspend fun recuperer(pseudo: String, code: String, nouveauMotDePasse: String): Resultat<String> =
        ouvrir(api.recuperation(ReglesPseudo.nettoyer(pseudo), code, nouveauMotDePasse), pseudoSaisi = null)

    /** Déconnexion de tous les appareils ; la session locale est effacée quoi qu'il arrive (D13). */
    suspend fun deconnecter() {
        try {
            api.deconnexion()
        } finally {
            effacerSession()
        }
    }

    /** Suppression du compte (D14) : session effacée après succès ou 401, gardée après un refus. */
    suspend fun supprimer(motDePasse: String): Resultat<Unit> =
        api.suppression(motDePasse).also { r ->
            if (r is Resultat.Ok || (r as? Resultat.Echec)?.erreur == ErreurApi.NonAutorise) effacerSession()
        }

    /** Nouveau pseudo (D79) : celui du serveur remplace celui de la session ; 401 l'efface. */
    suspend fun changerPseudo(pseudo: String): Resultat<String> =
        api.changerPseudo(ReglesPseudo.nettoyer(pseudo)).also { r ->
            when (r) {
                is Resultat.Ok -> session?.let { s -> enregistrer(s.copy(pseudo = r.valeur)) }
                is Resultat.Echec -> if (r.erreur == ErreurApi.NonAutorise) effacerSession()
            }
        }

    /**
     * Nouveau mot de passe (D81) : toutes les sessions du compte tombent, celle-ci comprise, et le
     * jeton neuf de la réponse la remplace aussitôt (mémoire et fichier). Refus : ancien jeton
     * gardé ; 401 : session effacée.
     */
    suspend fun changerMotDePasse(motDePasse: String, nouveauMotDePasse: String): Resultat<Unit> =
        when (val r = api.changerMotDePasse(motDePasse, nouveauMotDePasse)) {
            is Resultat.Ok -> {
                val ouverte = r.valeur
                enregistrer(Session(ouverte.jeton, ouverte.idJoueur, ouverte.pseudo ?: session?.pseudo.orEmpty()))
                Resultat.Ok(Unit)
            }
            is Resultat.Echec -> {
                if (r.erreur == ErreurApi.NonAutorise) effacerSession()
                r
            }
        }

    /** `GET /moi` ; son pseudo remplace celui de la session (casse de création, D54). */
    suspend fun moi(): Resultat<Moi> = api.moi().also { r ->
        when (r) {
            is Resultat.Ok -> session?.let { s -> if (s.pseudo != r.valeur.pseudo) enregistrer(s.copy(pseudo = r.valeur.pseudo)) }
            is Resultat.Echec -> if (r.erreur == ErreurApi.NonAutorise) effacerSession()
        }
    }

    /** `GET /classement` ; un 401 efface la session, comme pour `/moi`. */
    suspend fun classement(): Resultat<Classement> = api.classement().also { r ->
        if ((r as? Resultat.Echec)?.erreur == ErreurApi.NonAutorise) effacerSession()
    }

    /** Une page de `GET /historique` (D131) ; un 401 efface la session. */
    suspend fun historique(avant: String?): Resultat<PageHistorique> = api.historique(avant).effacerSiNonAutorise()

    /** `GET /historique/{id}` (D132) ; un 401 efface la session. */
    suspend fun detailPartie(id: String): Resultat<DetailPartie> = api.detailPartie(id).effacerSiNonAutorise()

    /** `GET /elo/courbe` (D135) ; un 401 efface la session. */
    suspend fun courbe(): Resultat<CourbeLue> = api.courbe().effacerSiNonAutorise()

    private suspend fun <T> Resultat<T>.effacerSiNonAutorise(): Resultat<T> = also {
        if ((it as? Resultat.Echec)?.erreur == ErreurApi.NonAutorise) effacerSession()
    }

    /** Un avis (D85 – D87) : le texte passe par la règle (D86) ; un 401 efface la session. */
    suspend fun envoyerAvis(texte: String, versionApp: String, android: String): Resultat<Unit> =
        api.envoyerAvis(ReglesAvis.normaliser(texte), versionApp, android).also { r ->
            if ((r as? Resultat.Echec)?.erreur == ErreurApi.NonAutorise) effacerSession()
        }

    /** Efface la session : mémoire et fichier (spec/donnees.md § 5). */
    suspend fun effacerSession() {
        session = null
        withContext(disque) { coffre.effacer() }
    }

    private suspend fun ouvrir(resultat: Resultat<SessionOuverte>, pseudoSaisi: String?): Resultat<String> =
        when (resultat) {
            is Resultat.Echec -> resultat
            is Resultat.Ok -> {
                val ouverte = resultat.valeur
                enregistrer(Session(ouverte.jeton, ouverte.idJoueur, ouverte.pseudo ?: pseudoSaisi.orEmpty()))
                Resultat.Ok(ouverte.code.orEmpty())
            }
        }

    /**
     * Garde la session en mémoire et dans le fichier. Si le Keystore ou le disque refuse l'écriture,
     * elle reste en mémoire : le joueur est connecté jusqu'à la fermeture de l'app.
     */
    private suspend fun enregistrer(nouvelle: Session) {
        session = nouvelle
        withContext(disque) {
            try {
                coffre.ecrire(nouvelle)
            } catch (e: Exception) {
                coffre.effacer()
            }
        }
    }
}

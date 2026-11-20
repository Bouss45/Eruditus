package com.eruditus.android.donnees.api

import java.io.IOException

/** Accès d'une route (D30) : publique (jamais de jeton) ou à jeton. */
enum class Acces { Public, Jeton }

/**
 * Les routes que l'app appelle (spec/serveur.md § 5.1), seule liste du client (D52) : l'en-tête
 * `Authorization` n'est posé que sur les routes [Acces.Jeton].
 */
enum class Route(val methode: String, val chemin: String, val acces: Acces) {
    Info("GET", "/api/eruditus/v1/info", Acces.Public),
    CreerCompte("POST", "/api/eruditus/v1/comptes", Acces.Public),
    Connexion("POST", "/api/collections/eruditus_joueurs/auth-with-password", Acces.Public),
    Renouvellement("POST", "/api/collections/eruditus_joueurs/auth-refresh", Acces.Jeton),
    Recuperation("POST", "/api/eruditus/v1/comptes/recuperation", Acces.Public),
    Deconnexion("POST", "/api/eruditus/v1/comptes/deconnexion", Acces.Jeton),
    Suppression("POST", "/api/eruditus/v1/comptes/suppression", Acces.Jeton),
    Moi("GET", "/api/eruditus/v1/moi", Acces.Jeton),
}

/** Une requête : [corps] JSON facultatif, [jeton] seulement pour une route à jeton. */
class Requete(val methode: String, val chemin: String, val corps: String?, val jeton: String?) {
    /** Jamais le jeton ni le corps (mot de passe) : rien de sensible ne doit finir dans un log (D26). */
    override fun toString() = "Requete($methode $chemin)"
}

class ReponseHttp(val statut: Int, val corps: String)

/**
 * L'appel n'a pas abouti. [connexionEtablie] faux : pas de réseau, nom introuvable, connexion
 * refusée, trop longue ou TLS refusé (texte réseau) ; vrai : connecté, mais pas de réponse dans le
 * délai (texte serveur, D53).
 */
class TransportEchoue(val connexionEtablie: Boolean, cause: Throwable? = null) : IOException(cause)

/** Envoie une requête au serveur et rend sa réponse, ou lève [TransportEchoue]. */
fun interface Transport {
    suspend fun envoyer(requete: Requete): ReponseHttp
}

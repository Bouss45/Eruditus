package com.eruditus.android.donnees.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.io.IOException

/** Ce que l'app retient d'une erreur (spec/serveur.md § 5.3, D53). */
sealed interface ErreurApi {
    /** Pas de connexion établie : « Pas de connexion », texte réseau. */
    data object Reseau : ErreurApi

    /** 5xx sans code, pas de réponse dans le délai, ou réponse qui n'est pas le JSON attendu. */
    data object Serveur : ErreurApi

    /** 401 : jeton absent, invalide ou expiré. */
    data object NonAutorise : ErreurApi

    /** 429 : limiteur (D23). */
    data object TropDeTentatives : ErreurApi

    /** Tout autre refus : [code] de `data.code`, ou null (connexion échouée, erreur standard). */
    data class Refus(val statut: Int, val code: String?) : ErreurApi
}

sealed interface Resultat<out T> {
    data class Ok<T>(val valeur: T) : Resultat<T>
    data class Echec(val erreur: ErreurApi) : Resultat<Nothing>
}

/**
 * Jeton et joueur rendus par la création, la connexion, le renouvellement ou la récupération. Des
 * routes standard de PocketBase, seuls `token` et `record.id` sont lus ([pseudo] nul,
 * spec/serveur.md § 5.1) ; [code] : le code de récupération, création et récupération seulement.
 */
data class SessionOuverte(val jeton: String, val idJoueur: String, val pseudo: String?, val code: String?) {
    override fun toString() = "SessionOuverte($idJoueur, $pseudo)"
}

/** `GET /moi` : [rang] nul tant que le score est 0. */
data class Moi(val pseudo: String, val score: Int, val rang: Int?, val classes: Int)

/**
 * Client de l'API Eruditus (D52), sans SDK. [jeton] donne le jeton de la session en cours ; il
 * n'est lu que pour une route à jeton, jamais pour une route publique (D30). Les champs inconnus
 * des réponses sont ignorés (D22).
 */
class ClientApi(private val transport: Transport, private val jeton: () -> String?) {

    suspend fun info(): Resultat<Int> = appeler(Route.Info) { entier(it, "versionMinimale") }

    suspend fun creerCompte(pseudo: String, motDePasse: String): Resultat<SessionOuverte> =
        appeler(Route.CreerCompte, buildJsonObject { put("pseudo", pseudo); put("motDePasse", motDePasse) }) {
            sessionEruditus(it)
        }

    suspend fun connexion(pseudo: String, motDePasse: String): Resultat<SessionOuverte> =
        appeler(Route.Connexion, buildJsonObject { put("identity", pseudo); put("password", motDePasse) }) {
            sessionStandard(it)
        }

    suspend fun renouveler(): Resultat<SessionOuverte> = appeler(Route.Renouvellement) { sessionStandard(it) }

    suspend fun recuperation(pseudo: String, code: String, nouveauMotDePasse: String): Resultat<SessionOuverte> =
        appeler(
            Route.Recuperation,
            buildJsonObject { put("pseudo", pseudo); put("code", code); put("nouveauMotDePasse", nouveauMotDePasse) },
        ) { sessionEruditus(it) }

    suspend fun deconnexion(): Resultat<Unit> = appeler(Route.Deconnexion) { }

    suspend fun suppression(motDePasse: String): Resultat<Unit> =
        appeler(Route.Suppression, buildJsonObject { put("motDePasse", motDePasse) }) { }

    suspend fun moi(): Resultat<Moi> = appeler(Route.Moi) { reponse ->
        val joueur = objet(reponse, "joueur")
        val rang = reponse?.get("rang")
        Moi(
            pseudo = texte(joueur, "pseudo"),
            score = entier(reponse, "score"),
            rang = if (rang == null || rang is JsonNull) null else entierDe(rang),
            classes = entier(reponse, "classes"),
        )
    }

    /**
     * Envoie la requête de [route] et lit sa réponse avec [lire] ; un champ attendu absent ou du
     * mauvais type rend [ErreurApi.Serveur] (réponse illisible, D53).
     */
    private suspend fun <T> appeler(route: Route, corps: JsonObject? = null, lire: (JsonObject?) -> T): Resultat<T> {
        val requete = Requete(
            route.methode,
            route.chemin,
            corps?.toString(),
            if (route.acces == Acces.Jeton) jeton() else null,
        )
        val reponse = try {
            transport.envoyer(requete)
        } catch (e: TransportEchoue) {
            return Resultat.Echec(if (e.connexionEtablie) ErreurApi.Serveur else ErreurApi.Reseau)
        } catch (e: IOException) {
            return Resultat.Echec(ErreurApi.Reseau)
        }
        return when (val lue = interpreter(reponse)) {
            is Resultat.Echec -> lue
            is Resultat.Ok -> try {
                Resultat.Ok(lire(lue.valeur))
            } catch (e: ReponseIllisible) {
                Resultat.Echec(ErreurApi.Serveur)
            }
        }
    }

    private class ReponseIllisible : Exception()

    private fun objet(parent: JsonObject?, nom: String): JsonObject =
        parent?.get(nom) as? JsonObject ?: throw ReponseIllisible()

    private fun texte(parent: JsonObject?, nom: String): String =
        (parent?.get(nom) as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotEmpty() }?.content
            ?: throw ReponseIllisible()

    private fun entier(parent: JsonObject?, nom: String): Int = entierDe(parent?.get(nom))

    private fun entierDe(element: JsonElement?): Int =
        (element as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull ?: throw ReponseIllisible()

    /** `{ token, joueur: { id, pseudo }, codeRecuperation }` des routes Eruditus. */
    private fun sessionEruditus(reponse: JsonObject?): SessionOuverte {
        val joueur = objet(reponse, "joueur")
        return SessionOuverte(texte(reponse, "token"), texte(joueur, "id"), texte(joueur, "pseudo"), texte(reponse, "codeRecuperation"))
    }

    /** `{ token, record }` des routes standard : seuls `token` et `record.id` sont lus. */
    private fun sessionStandard(reponse: JsonObject?): SessionOuverte =
        SessionOuverte(texte(reponse, "token"), texte(objet(reponse, "record"), "id"), null, null)

    internal companion object {
        /**
         * Statut et corps → succès (objet JSON, ou rien pour un 204) ou erreur (D53) : 401 et 429 par
         * leur statut ; un `data.code` donne un refus précis ; sinon 4xx → refus sans code, tout le
         * reste (3xx, 5xx, 2xx qui n'est pas un objet JSON) → serveur.
         */
        fun interpreter(reponse: ReponseHttp): Resultat<JsonObject?> {
            val statut = reponse.statut
            return when {
                statut == 204 -> Resultat.Ok(null)
                statut in 200..299 -> objetJson(reponse.corps)?.let { Resultat.Ok(it) } ?: Resultat.Echec(ErreurApi.Serveur)
                statut == 401 -> Resultat.Echec(ErreurApi.NonAutorise)
                statut == 429 -> Resultat.Echec(ErreurApi.TropDeTentatives)
                else -> {
                    val code = ((objetJson(reponse.corps)?.get("data") as? JsonObject)?.get("code") as? JsonPrimitive)
                        ?.takeIf { it.isString && it.content.isNotEmpty() }?.content
                    when {
                        code != null -> Resultat.Echec(ErreurApi.Refus(statut, code))
                        statut in 400..499 -> Resultat.Echec(ErreurApi.Refus(statut, null))
                        else -> Resultat.Echec(ErreurApi.Serveur)
                    }
                }
            }
        }

        private fun objetJson(texte: String): JsonObject? = try {
            Json.parseToJsonElement(texte) as? JsonObject
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

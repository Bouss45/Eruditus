package com.eruditus.android.donnees.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
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

/** Une ligne du classement : rang, pseudo, score, rien d'autre (spec/regles.md § 4, T30). */
data class Place(val rang: Int, val pseudo: String, val score: Int)

/**
 * `GET /classement` : [top] dans l'ordre et avec les rangs du serveur, 10 lignes au plus ; [moi]
 * nul tant que le score est 0 ; [classes] : nombre de joueurs classés.
 */
data class Classement(val top: List<Place>, val moi: Place?, val classes: Int)

/**
 * Une question servie (spec/serveur.md § 5.2), jamais avec sa bonne réponse (D16) : [numero] de 1
 * à [total], 4 [choix] dans l'ordre de la banque, [restantMs] de 0 à [delaiMs], d'où part le
 * compte à rebours de l'app (D66).
 */
data class QuestionServie(
    val numero: Int,
    val total: Int,
    val enonce: String,
    val choix: List<String>,
    val delaiMs: Long,
    val restantMs: Long,
)

/** `POST /parties` : l'identifiant de la partie et sa question 1. */
data class PartieCreee(val id: String, val question: QuestionServie)

/** Raison de la correction (D32) ; [Inconnue] : ajoutée par un serveur plus récent (D22, D67). */
enum class Raison(val code: String?) {
    Gagne("gagne"),
    DejaReussie("deja_reussie"),
    Fausse("fausse"),
    SansReponse("sans_reponse"),
    HorsDelai("hors_delai"),
    Inconnue(null),
}

/** Correction d'une réponse : [choix] et [bonneReponse] de 0 à 3, [choix] nul = sans réponse. */
data class Correction(
    val numero: Int,
    val choix: Int?,
    val correcte: Boolean,
    val bonneReponse: Int,
    val horsDelai: Boolean,
    val point: Int,
    val raison: Raison,
    val score: Int,
)

/** Bilan de la partie, après la dernière réponse. */
data class Bilan(val bonnes: Int, val points: Int, val score: Int)

/** `POST /parties/{id}/reponse` : [bilan] nul avant la dernière question. */
data class ReponseCorrigee(val correction: Correction, val bilan: Bilan?)

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

    /** Nouveau pseudo (D79) ; rend celui que le serveur a enregistré. */
    suspend fun changerPseudo(pseudo: String): Resultat<String> =
        appeler(Route.ChangementPseudo, buildJsonObject { put("pseudo", pseudo) }) { texte(objet(it, "joueur"), "pseudo") }

    /** Nouveau mot de passe (D81) ; rend le jeton neuf et le joueur, sans code (il ne change pas). */
    suspend fun changerMotDePasse(motDePasse: String, nouveauMotDePasse: String): Resultat<SessionOuverte> =
        appeler(
            Route.ChangementMotDePasse,
            buildJsonObject { put("motDePasse", motDePasse); put("nouveauMotDePasse", nouveauMotDePasse) },
        ) { reponse ->
            val joueur = objet(reponse, "joueur")
            SessionOuverte(texte(reponse, "token"), texte(joueur, "id"), texte(joueur, "pseudo"), null)
        }

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
     * Le classement (D72). Seules les 10 premières lignes de `top` sont lues : un serveur plus
     * récent peut en envoyer plus (D22). Illisible seulement si un champ obligatoire manque ou a
     * le mauvais type.
     */
    suspend fun classement(): Resultat<Classement> = appeler(Route.Classement) { reponse ->
        val corps = reponse ?: throw ReponseIllisible()
        val top = corps["top"] as? JsonArray ?: throw ReponseIllisible()
        val moi = corps["moi"] ?: throw ReponseIllisible()
        Classement(
            top = top.take(TOP).map { place(it as? JsonObject ?: throw ReponseIllisible()) },
            moi = if (moi is JsonNull) null else place(moi as? JsonObject ?: throw ReponseIllisible()),
            classes = entier(corps, "classes"),
        )
    }

    /** Crée une partie (abandonne la précédente) et sert sa question 1. */
    suspend fun creerPartie(): Resultat<PartieCreee> = appeler(Route.CreerPartie) { reponse ->
        val partie = objet(reponse, "partie")
        val id = texte(partie, "id").takeIf { ID_PARTIE.matches(it) } ?: throw ReponseIllisible()
        PartieCreee(id, question(objet(reponse, "question")))
    }

    /** La question servie pas encore répondue, avec son temps restant, sinon la suivante. */
    suspend fun question(partie: String): Resultat<QuestionServie> =
        appeler(Route.Question, id = partie) { question(objet(it, "question")) }

    /** Envoie le choix (0 à 3) ou « sans réponse » ([choix] nul) pour la question [numero]. */
    suspend fun repondre(partie: String, numero: Int, choix: Int?): Resultat<ReponseCorrigee> =
        appeler(Route.Reponse, buildJsonObject { put("numero", numero); put("choix", choix) }, id = partie) { reponse ->
            val c = objet(reponse, "correction")
            val choisi = c["choix"]
            val bilan = reponse?.get("bilan")
            ReponseCorrigee(
                Correction(
                    numero = entier(c, "numero"),
                    choix = if (choisi == null || choisi is JsonNull) null else lettre(entierDe(choisi)),
                    correcte = booleen(c, "correcte"),
                    bonneReponse = lettre(entier(c, "bonneReponse")),
                    horsDelai = booleen(c, "horsDelai"),
                    point = entier(c, "point"),
                    raison = texteBrut(c, "raison").let { code -> Raison.entries.firstOrNull { it.code == code } ?: Raison.Inconnue },
                    score = entier(c, "score"),
                ),
                if (bilan == null || bilan is JsonNull) {
                    null
                } else {
                    val b = bilan as? JsonObject ?: throw ReponseIllisible()
                    Bilan(entier(b, "bonnes"), entier(b, "points"), entier(b, "score"))
                },
            )
        }

    /**
     * Envoie la requête de [route] et lit sa réponse avec [lire] ; un champ attendu absent ou du
     * mauvais type rend [ErreurApi.Serveur] (réponse illisible, D53). [id] remplace `{id}` dans le
     * chemin : un identifiant qui n'est pas fait de lettres et de chiffres n'est jamais envoyé.
     */
    private suspend fun <T> appeler(
        route: Route,
        corps: JsonObject? = null,
        id: String? = null,
        lire: (JsonObject?) -> T,
    ): Resultat<T> {
        val chemin = if (id == null) {
            route.chemin
        } else {
            if (!ID_PARTIE.matches(id)) return Resultat.Echec(ErreurApi.Serveur)
            route.chemin.replace("{id}", id)
        }
        val requete = Requete(
            route.methode,
            chemin,
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

    /** Texte présent, vide permis (une raison inconnue reste lisible). */
    private fun texteBrut(parent: JsonObject?, nom: String): String =
        (parent?.get(nom) as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw ReponseIllisible()

    private fun booleen(parent: JsonObject?, nom: String): Boolean =
        (parent?.get(nom) as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: throw ReponseIllisible()

    /** Une ligne de classement : `rang` et `score` entiers, `pseudo` texte ; les autres champs ignorés. */
    private fun place(ligne: JsonObject): Place = Place(entier(ligne, "rang"), texteBrut(ligne, "pseudo"), entier(ligne, "score"))

    /** Un indice de choix, de 0 à 3. */
    private fun lettre(valeur: Int): Int = valeur.takeIf { it in 0..3 } ?: throw ReponseIllisible()

    /** L'objet `question` : 4 choix non vides, numéro de 1 au total, temps restant de 0 au délai. */
    private fun question(q: JsonObject): QuestionServie {
        val total = entier(q, "total").takeIf { it >= 1 } ?: throw ReponseIllisible()
        val numero = entier(q, "numero").takeIf { it in 1..total } ?: throw ReponseIllisible()
        val choix = (q["choix"] as? JsonArray)?.takeIf { it.size == 4 } ?: throw ReponseIllisible()
        val delai = entier(q, "delaiMs").takeIf { it > 0 } ?: throw ReponseIllisible()
        val restant = entier(q, "restantMs").takeIf { it >= 0 } ?: throw ReponseIllisible()
        return QuestionServie(
            numero = numero,
            total = total,
            enonce = texte(q, "enonce"),
            choix = choix.map { element ->
                (element as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotEmpty() }?.content ?: throw ReponseIllisible()
            },
            delaiMs = delai.toLong(),
            restantMs = restant.coerceAtMost(delai).toLong(),
        )
    }

    /** `{ token, joueur: { id, pseudo }, codeRecuperation }` des routes Eruditus. */
    private fun sessionEruditus(reponse: JsonObject?): SessionOuverte {
        val joueur = objet(reponse, "joueur")
        return SessionOuverte(texte(reponse, "token"), texte(joueur, "id"), texte(joueur, "pseudo"), texte(reponse, "codeRecuperation"))
    }

    /** `{ token, record }` des routes standard : seuls `token` et `record.id` sont lus. */
    private fun sessionStandard(reponse: JsonObject?): SessionOuverte =
        SessionOuverte(texte(reponse, "token"), texte(objet(reponse, "record"), "id"), null, null)

    internal companion object {
        /** Lignes du classement affichées : le top 10 (spec/regles.md § 4). */
        const val TOP = 10

        /** Identifiant de partie de PocketBase : lettres et chiffres seulement (D67). */
        private val ID_PARTIE = Regex("^[A-Za-z0-9]{1,64}$")

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

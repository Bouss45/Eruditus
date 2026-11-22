package com.eruditus.servertests

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Textes qui ne doivent paraître dans aucune réponse faite à l'app ni dans aucun journal : le
 * secret de l'adresse du faux webhook (T44). Le client des tests les cherche dans chaque réponse.
 */
object TextesInterdits {
    val liste = CopyOnWriteArrayList<String>()
}

/**
 * Faux webhook Discord des tests (spec/serveur.md § 10, D91), sur 127.0.0.1 : jamais Discord.
 * Son adresse porte un faux secret ; il garde chaque message reçu et répond selon [mode].
 */
object FauxWebhook {
    /** Ce que le faux webhook répond au prochain message. */
    sealed interface Mode {
        /** Répond [statut] avec [corps] (vide pour 204). */
        data class Statut(val statut: Int, val corps: String = "") : Mode

        /** Garde le message, puis ne répond rien avant [ms] : Discord qui publie mais répond tard. */
        data class Silence(val ms: Long) : Mode
    }

    /** Un message reçu : méthode, chemin, `Content-Type` et corps JSON. */
    data class Message(val methode: String, val chemin: String, val typeContenu: String?, val corps: JsonObject) {
        val embed: JsonObject get() = corps.tableau("embeds").single().jsonObject
    }

    val secret: String = "faux-secret-" + Serveur.aleatoire(40)
    private const val ID = "1234567890123456789"
    val chemin = "/api/webhooks/$ID/$secret"

    @Volatile
    var mode: Mode = Mode.Statut(204)

    private val recus = CopyOnWriteArrayList<Message>()

    private val serveur: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        executor = Executors.newCachedThreadPool { tache -> Thread(tache, "faux-webhook").apply { isDaemon = true } }
        createContext("/") { echange -> traiter(echange) }
        start()
    }

    val adresse: String = "http://127.0.0.1:${serveur.address.port}$chemin"

    init {
        TextesInterdits.liste += secret
    }

    /** Les messages reçus dont le titre est « Avis de [pseudo] ». */
    fun messagesDe(pseudo: String): List<Message> =
        recus.filter { m -> runCatching { m.embed.str("title") }.getOrNull() == "Avis de $pseudo" }

    /** Les messages reçus dont le titre est « Question signalée · [ref] » (D120). */
    fun messagesQuestion(ref: String): List<Message> =
        recus.filter { m -> runCatching { m.embed.str("title") }.getOrNull() == "Question signalée · $ref" }

    /** Tous les messages reçus (chemin et corps compris), pour y chercher un texte. */
    fun tousLesMessages(): List<Message> = recus.toList()

    private fun traiter(echange: HttpExchange) {
        echange.use {
            val corps = echange.requestBody.readBytes().toString(Charsets.UTF_8)
            val objet = runCatching { Json.parseToJsonElement(corps).jsonObject }.getOrElse { JsonObject(emptyMap()) }
            recus += Message(
                echange.requestMethod,
                echange.requestURI.toString(),
                echange.requestHeaders.getFirst("Content-Type"),
                objet,
            )
            when (val m = mode) {
                is Mode.Statut -> repondre(echange, m.statut, m.corps)
                is Mode.Silence -> {
                    Thread.sleep(m.ms)
                    runCatching { repondre(echange, 204, "") } // le serveur a déjà abandonné
                }
            }
        }
    }

    private fun repondre(echange: HttpExchange, statut: Int, corps: String) {
        val octets = corps.toByteArray()
        if (octets.isEmpty()) {
            echange.sendResponseHeaders(statut, -1)
        } else {
            echange.responseHeaders.add("Content-Type", "application/json")
            echange.sendResponseHeaders(statut, octets.size.toLong())
            echange.responseBody.write(octets)
        }
    }

    /** Réponses de Discord imitées : message publié (200 avec `?wait=true`), limite atteinte. */
    const val CORPS_200 = """{"id":"1","type":0,"content":"","channel_id":"2"}"""
    const val CORPS_429 = """{"message":"You are being rate limited.","retry_after":1.5,"global":false}"""
}

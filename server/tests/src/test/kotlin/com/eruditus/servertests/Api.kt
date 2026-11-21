package com.eruditus.servertests

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail

/**
 * Adresses IP simulées dans X-Forwarded-For : le limiteur compte par IP (spec/serveur.md § 10).
 * Chaque appel prend une adresse neuve, sauf quand un test fixe la sienne.
 */
object Ips {
    private val compteur = AtomicInteger(1)

    fun nouvelle(): String {
        val n = compteur.getAndIncrement()
        return "10.${(n shr 16) and 255}.${(n shr 8) and 255}.${n and 255}"
    }
}

class Reponse(val status: Int, val corps: String) {
    val json: JsonElement? by lazy {
        if (corps.isBlank()) null else runCatching { Json.parseToJsonElement(corps) }.getOrNull()
    }

    val obj: JsonObject
        get() = json as? JsonObject ?: throw AssertionError("Corps JSON objet attendu : $this")

    /** Code d'erreur Eruditus (`data.code`), null s'il n'y en a pas. */
    val code: String?
        get() = ((json as? JsonObject)?.get("data") as? JsonObject)?.get("code")?.let {
            (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content
        }

    override fun toString(): String = "$status $corps"
}

/** Client HTTP des tests : passe par HTTP, comme l'app. */
class Api(
    private val base: String,
    private val jetonParDefaut: String? = null,
    private val superutilisateur: Boolean = false,
) {
    fun requete(
        methode: String,
        chemin: String,
        corps: String? = null,
        jeton: String? = jetonParDefaut,
        ip: String? = Ips.nouvelle(),
        typeContenu: String? = if (corps != null) "application/json" else null,
        verifier: Boolean = true,
    ): Reponse {
        val constructeur = HttpRequest.newBuilder(URI.create(base + chemin))
            .timeout(Duration.ofSeconds(30))
            .method(
                methode,
                if (corps == null) HttpRequest.BodyPublishers.noBody()
                else HttpRequest.BodyPublishers.ofString(corps, Charsets.UTF_8),
            )
        if (jeton != null) constructeur.header("Authorization", jeton)
        if (ip != null) constructeur.header("X-Forwarded-For", ip)
        if (typeContenu != null) constructeur.header("Content-Type", typeContenu)
        val reponse = client.send(constructeur.build(), HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        val resultat = Reponse(reponse.statusCode(), reponse.body() ?: "")
        if (verifier) verifierToujours(resultat, "$methode $chemin")
        return resultat
    }

    fun get(chemin: String, jeton: String? = jetonParDefaut, ip: String? = Ips.nouvelle()): Reponse =
        requete("GET", chemin, null, jeton, ip)

    fun post(
        chemin: String,
        corps: JsonElement? = null,
        jeton: String? = jetonParDefaut,
        ip: String? = Ips.nouvelle(),
        verifier: Boolean = true,
    ): Reponse = requete("POST", chemin, corps?.toString(), jeton, ip, verifier = verifier)

    fun patch(chemin: String, corps: JsonElement, jeton: String? = jetonParDefaut, ip: String? = Ips.nouvelle()): Reponse =
        requete("PATCH", chemin, corps.toString(), jeton, ip)

    fun delete(chemin: String, jeton: String? = jetonParDefaut, ip: String? = Ips.nouvelle()): Reponse =
        requete("DELETE", chemin, null, jeton, ip)

    /**
     * Vérifications faites sur **toute** réponse : jamais de 500 (T32), jamais le secret du
     * webhook des avis (T44), et pour tout client autre que le superutilisateur, jamais de champ
     * sensible (T7, T18, T44).
     */
    private fun verifierToujours(reponse: Reponse, appel: String) {
        assertNotEquals("Erreur 500 sur $appel : $reponse", 500, reponse.status)
        for (interdit in TextesInterdits.liste) {
            if (interdit in reponse.corps) fail("Secret du webhook dans la réponse de $appel : ${reponse.status}")
        }
        if (!superutilisateur) {
            reponse.json?.let { chercherChampsInterdits(it, appel, reponse) }
        }
    }

    private fun chercherChampsInterdits(element: JsonElement, appel: String, reponse: Reponse) {
        when (element) {
            is JsonObject -> element.forEach { (cle, valeur) ->
                if (cle in CHAMPS_INTERDITS) fail("Champ « $cle » présent dans la réponse de $appel : $reponse")
                chercherChampsInterdits(valeur, appel, reponse)
            }
            is JsonArray -> element.forEach { chercherChampsInterdits(it, appel, reponse) }
            else -> Unit
        }
    }

    companion object {
        val CHAMPS_INTERDITS = setOf(
            "password", "passwordHash", "tokenKey", "code_recuperation", "pseudo_modifie_le", "avis_envoye_le",
            "email", "emailVisibility", "verified", "bonne_reponse",
        )

        private val client: HttpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build()
    }
}

// --- Lecture des réponses JSON -------------------------------------------------------------

fun JsonObject.champ(nom: String): JsonElement =
    this[nom] ?: throw AssertionError("Champ « $nom » absent de $this")

fun JsonObject.str(nom: String): String {
    val p = champ(nom) as? JsonPrimitive
    if (p == null || !p.isString) throw AssertionError("« $nom » devrait être un texte : $this")
    return p.content
}

fun JsonObject.int(nom: String): Int {
    val p = champ(nom) as? JsonPrimitive
    val valeur = p?.takeIf { !it.isString }?.intOrNull
    if (valeur == null || p.content.contains('.')) throw AssertionError("« $nom » devrait être un entier : $this")
    return valeur
}

fun JsonObject.long(nom: String): Long {
    val p = champ(nom) as? JsonPrimitive
    return p?.takeIf { !it.isString }?.longOrNull ?: throw AssertionError("« $nom » devrait être un entier : $this")
}

fun JsonObject.intOuNull(nom: String): Int? = if (champ(nom) is JsonNull) null else int(nom)

fun JsonObject.bool(nom: String): Boolean {
    val p = champ(nom) as? JsonPrimitive
    return p?.takeIf { !it.isString }?.booleanOrNull ?: throw AssertionError("« $nom » devrait être un booléen : $this")
}

fun JsonObject.objet(nom: String): JsonObject =
    champ(nom) as? JsonObject ?: throw AssertionError("« $nom » devrait être un objet : $this")

fun JsonObject.objetOuNull(nom: String): JsonObject? = if (champ(nom) is JsonNull) null else objet(nom)

fun JsonObject.tableau(nom: String): JsonArray =
    champ(nom) as? JsonArray ?: throw AssertionError("« $nom » devrait être un tableau : $this")

fun JsonElement.texte(): String? = (this as? JsonPrimitive)?.contentOrNull

/** Erreur Eruditus attendue : statut et `data.code`. */
fun Reponse.doitEtreErreur(status: Int, code: String?) {
    assertEquals("Statut de $this", status, this.status)
    assertEquals("data.code de $this", code, this.code)
}

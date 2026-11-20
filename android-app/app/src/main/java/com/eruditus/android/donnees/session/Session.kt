package com.eruditus.android.donnees.session

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Ce que le téléphone garde pour rester connecté (spec/donnees.md § 1) : rien d'autre. */
data class Session(val jeton: String, val idJoueur: String, val pseudo: String) {
    /** Jamais le jeton : rien de sensible ne doit finir dans un log (D26). */
    override fun toString() = "Session($idJoueur, $pseudo)"
}

/** Le JSON du fichier de session, version 1 (D54). */
object FormatSession {
    private const val VERSION = 1

    fun encoder(session: Session): ByteArray = buildJsonObject {
        put("version", VERSION)
        put("jeton", session.jeton)
        put("idJoueur", session.idJoueur)
        put("pseudo", session.pseudo)
    }.toString().toByteArray(Charsets.UTF_8)

    /** La session, ou null si le JSON est illisible, d'une autre version ou incomplet. */
    fun decoder(octets: ByteArray): Session? {
        val objet = try {
            Json.parseToJsonElement(String(octets, Charsets.UTF_8)) as? JsonObject
        } catch (e: IllegalArgumentException) {
            null
        } ?: return null
        if ((objet["version"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull != VERSION) return null
        fun texte(nom: String) = (objet[nom] as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotEmpty() }?.content
        return Session(texte("jeton") ?: return null, texte("idJoueur") ?: return null, texte("pseudo") ?: return null)
    }
}

/**
 * Chiffre le fichier de session (AES-256-GCM, IV de 12 octets en tête, D54). [dechiffrer] lève
 * une exception si la clé manque ou si les données ont été altérées.
 */
interface Chiffreur {
    fun chiffrer(clair: ByteArray): ByteArray
    fun dechiffrer(donnees: ByteArray): ByteArray

    /** Efface la clé : la prochaine écriture en crée une neuve. */
    fun oublierCle()
}

/**
 * Le fichier de session (spec/donnees.md § 1, § 2, § 5) : chiffré, écrit de façon atomique ; tout
 * fichier illisible (tronqué, altéré, clé perdue, autre version) est effacé avec sa clé.
 */
class CoffreSession(private val fichier: File, private val chiffreur: Chiffreur) {

    fun lire(): Session? {
        if (!fichier.exists()) return null
        val session = try {
            FormatSession.decoder(chiffreur.dechiffrer(fichier.readBytes()))
        } catch (e: Exception) {
            null
        }
        if (session == null) {
            effacer()
            chiffreur.oublierCle()
        }
        return session
    }

    fun ecrire(session: Session) {
        val donnees = chiffreur.chiffrer(FormatSession.encoder(session))
        val temporaire = File(fichier.parentFile, "${fichier.name}.tmp")
        try {
            temporaire.writeBytes(donnees)
            Files.move(temporaire.toPath(), fichier.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            temporaire.delete()
            throw e
        }
    }

    fun effacer() {
        fichier.delete()
        File(fichier.parentFile, "${fichier.name}.tmp").delete()
    }
}

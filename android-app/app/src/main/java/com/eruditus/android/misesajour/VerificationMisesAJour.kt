package com.eruditus.android.misesajour

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Rend le corps de la réponse de `releases/latest`, ou lève une exception. */
fun interface SourceReleases {
    suspend fun derniere(): String
}

/**
 * `mises-a-jour.json` (D64, spec/donnees.md § 1) : en clair, écrit de façon atomique ; illisible
 * ou d'une autre version, effacé et relu comme vide.
 */
class FichierMisesAJour(private val fichier: File) {

    fun lire(): EtatMisesAJour {
        if (!fichier.exists()) return EtatMisesAJour()
        val etat = try {
            decoder(fichier.readText())
        } catch (e: IOException) {
            null
        }
        if (etat == null) fichier.delete()
        return etat ?: EtatMisesAJour()
    }

    fun ecrire(etat: EtatMisesAJour) {
        val temporaire = File(fichier.parentFile, "${fichier.name}.tmp")
        try {
            temporaire.writeText(encoder(etat))
            Files.move(temporaire.toPath(), fichier.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            temporaire.delete()
        }
    }

    private fun encoder(etat: EtatMisesAJour): String = buildJsonObject {
        put("version", VERSION)
        put("reussieLe", etat.reussieLe)
        put("echecLe", etat.echecLe)
        etat.derniereVersion?.let { put("derniereVersion", it) }
        etat.apk?.let { put("apk", it) }
        etat.page?.let { put("page", it) }
        etat.versionIgnoree?.let { put("versionIgnoree", it) }
    }.toString()

    private fun decoder(texte: String): EtatMisesAJour? {
        val objet = try {
            Json.parseToJsonElement(texte) as? JsonObject
        } catch (e: IllegalArgumentException) {
            null
        } ?: return null
        fun nombre(nom: String) = (objet[nom] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
        fun texte(nom: String) = (objet[nom] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (nombre("version") != VERSION.toLong()) return null
        return EtatMisesAJour(
            reussieLe = nombre("reussieLe") ?: 0,
            echecLe = nombre("echecLe") ?: 0,
            derniereVersion = texte("derniereVersion"),
            apk = texte("apk"),
            page = texte("page"),
            versionIgnoree = texte("versionIgnoree"),
        )
    }

    private companion object {
        const val VERSION = 1
    }
}

/**
 * Vérification des nouvelles versions au chargement de l'Accueil (D61, D62) : seulement si
 * [active] (build release), seulement si elle est due, une à la fois. Un échec ne lève jamais
 * rien et ne change pas l'annonce : il retarde seulement le prochain essai d'une heure.
 */
class VerificationMisesAJour(
    private val source: SourceReleases,
    private val fichier: FichierMisesAJour,
    private val horloge: () -> Long,
    private val nomInstalle: String,
    private val codeInstalle: Int,
    private val active: Boolean,
    private val disque: CoroutineDispatcher = Dispatchers.IO,
) {
    private val verrou = Mutex()

    /** L'annonce d'après ce qui est gardé, sans requête. */
    fun annonce(): Release? =
        if (active) ReglesMisesAJour.annonce(fichier.lire(), nomInstalle, codeInstalle) else null

    /** Release vers laquelle « Mettre à jour » envoie (D63), même ignorée. */
    fun pourMettreAJour(): Release? =
        if (active) ReglesMisesAJour.pourMettreAJour(fichier.lire(), nomInstalle, codeInstalle) else null

    /** Vérifie si c'est dû, puis rend l'annonce. Jamais d'exception, jamais « Pas de connexion ». */
    suspend fun verifierSiDue(): Release? {
        if (!active) return null
        if (!verrou.tryLock()) return withContext(disque) { annonce() }
        try {
            val etat = withContext(disque) { fichier.lire() }
            val maintenant = horloge()
            if (!ReglesMisesAJour.due(etat, maintenant)) return ReglesMisesAJour.annonce(etat, nomInstalle, codeInstalle)
            val release = try {
                ReglesMisesAJour.lireRelease(source.derniere())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val suivant = if (release == null) {
                etat.copy(echecLe = maintenant)
            } else {
                etat.copy(reussieLe = maintenant, echecLe = 0, derniereVersion = release.version, apk = release.apk, page = release.page)
            }
            withContext(disque) { fichier.ecrire(suivant) }
            return ReglesMisesAJour.annonce(suivant, nomInstalle, codeInstalle)
        } finally {
            verrou.unlock()
        }
    }

    /** « Plus tard » : cette version n'est plus jamais annoncée. */
    fun ignorer(version: String) {
        if (!active) return
        fichier.ecrire(fichier.lire().copy(versionIgnoree = version))
    }
}

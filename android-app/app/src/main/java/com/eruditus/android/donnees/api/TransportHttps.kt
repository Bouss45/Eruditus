package com.eruditus.android.donnees.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import javax.net.ssl.HttpsURLConnection

/**
 * Transport réel (D52) : `HttpsURLConnection` du système, sans bibliothèque. [base] est
 * `BuildConfig.SERVEUR_URL`, toujours en `https://` (D24, D43) ; une adresse en clair ne donnerait
 * pas une `HttpsURLConnection` et l'appel échouerait. Délais de 10 s pour se connecter et pour la
 * réponse (spec/ecrans.md § 1) ; pas de redirection, pas de cache, aucune journalisation.
 */
class TransportHttps(private val base: String) : Transport {

    override suspend fun envoyer(requete: Requete): ReponseHttp = withContext(Dispatchers.IO) {
        val connexion = try {
            URI(base + requete.chemin).toURL().openConnection() as HttpsURLConnection
        } catch (e: Exception) {
            throw TransportEchoue(connexionEtablie = false, e)
        }
        try {
            connexion.requestMethod = requete.methode
            connexion.connectTimeout = DELAI_MS
            connexion.readTimeout = DELAI_MS
            connexion.instanceFollowRedirects = false
            connexion.useCaches = false
            connexion.setRequestProperty("Accept", "application/json")
            requete.jeton?.let { connexion.setRequestProperty("Authorization", it) }
            val corps = requete.corps?.toByteArray(Charsets.UTF_8)
            if (requete.methode == "POST") {
                connexion.doOutput = true
                if (corps != null) connexion.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connexion.setFixedLengthStreamingMode(corps?.size ?: 0)
            }

            try {
                connexion.connect()
            } catch (e: IOException) {
                throw TransportEchoue(connexionEtablie = false, e)
            }

            try {
                if (connexion.doOutput) connexion.outputStream.use { sortie -> corps?.let { sortie.write(it) } }
                val statut = connexion.responseCode
                val flux = if (statut >= 400) connexion.errorStream else connexion.inputStream
                val texte = flux?.use { String(it.readBytes(), Charsets.UTF_8) }.orEmpty()
                ReponseHttp(statut, texte)
            } catch (e: IOException) {
                throw TransportEchoue(connexionEtablie = true, e)
            }
        } finally {
            connexion.disconnect()
        }
    }

    private companion object {
        const val DELAI_MS = 10_000
    }
}

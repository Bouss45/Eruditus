package com.eruditus.android.misesajour

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import javax.net.ssl.HttpsURLConnection

/**
 * Dernière Release lue sur l'API publique de GitHub (D61) : un `GET` anonyme en HTTPS, sans jeton,
 * sans cookie, sans aucune donnée de l'app ni du joueur ; seuls les en-têtes [ENTETES]. Délais de
 * 10 s, réponse de 1 Mo au plus, aucune journalisation.
 */
class SourceGitHub : SourceReleases {

    override suspend fun derniere(): String = withContext(Dispatchers.IO) {
        val connexion = URI(ADRESSE).toURL().openConnection() as HttpsURLConnection
        try {
            connexion.connectTimeout = DELAI_MS
            connexion.readTimeout = DELAI_MS
            connexion.useCaches = false
            ENTETES.forEach { (nom, valeur) -> connexion.setRequestProperty(nom, valeur) }
            val statut = connexion.responseCode
            if (statut != HttpsURLConnection.HTTP_OK) throw IOException("HTTP $statut")
            connexion.inputStream.use { entree ->
                val sortie = ByteArrayOutputStream()
                val tampon = ByteArray(16 * 1024)
                while (true) {
                    val n = entree.read(tampon)
                    if (n < 0) break
                    sortie.write(tampon, 0, n)
                    if (sortie.size() > TAILLE_MAX) throw IOException("réponse trop volumineuse")
                }
                sortie.toByteArray().decodeToString()
            }
        } finally {
            connexion.disconnect()
        }
    }

    companion object {
        const val ADRESSE = ReglesMisesAJour.API

        /** `User-Agent` est exigé par GitHub : le nom de l'app seul, ni version ni appareil. */
        val ENTETES = mapOf(
            "Accept" to "application/vnd.github+json",
            "X-GitHub-Api-Version" to "2022-11-28",
            "User-Agent" to "Eruditus-Android",
        )

        private const val DELAI_MS = 10_000
        private const val TAILLE_MAX = 1024 * 1024
    }
}

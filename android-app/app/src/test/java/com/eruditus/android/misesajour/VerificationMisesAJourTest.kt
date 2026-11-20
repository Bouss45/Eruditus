package com.eruditus.android.misesajour

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.net.UnknownHostException

/**
 * Vérification au chargement de l'Accueil (D61, D62, D64) : délais, échec silencieux, version
 * ignorée, fichier gardé ; rien en debug et preview.
 */
class VerificationMisesAJourTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private val heure = 3_600_000L
    private var maintenant = 100 * heure
    private var appels = 0
    private var reponse: () -> String = { RELEASE_13 }
    private val source = SourceReleases { appels++; reponse() }
    private val fichier by lazy { File(dossier.root, "mises-a-jour.json") }
    private val stockage by lazy { FichierMisesAJour(fichier) }

    private fun verification(active: Boolean = true, nom: String = "1.0.12", code: Int = 1012) =
        VerificationMisesAJour(source, stockage, { maintenant }, nom, code, active)

    private fun verifier(v: VerificationMisesAJour = verification()) = runBlocking { v.verifierSiDue() }

    @Test
    fun `une version plus recente est annoncee et gardee`() {
        assertEquals(Release("1.0.13", PAGE_13, APK_13), verifier())
        assertEquals(1, appels)
        val etat = stockage.lire()
        assertEquals(maintenant, etat.reussieLe)
        assertEquals("1.0.13", etat.derniereVersion)
        // Relue par une nouvelle instance (redémarrage de l'app), sans nouvel appel.
        assertEquals(Release("1.0.13", PAGE_13, APK_13), verification().annonce())
    }

    @Test
    fun `pas de nouvelle requete avant 6 heures apres une reussite`() {
        verifier()
        maintenant += 6 * heure - 1
        assertEquals(Release("1.0.13", PAGE_13, APK_13), verifier())
        assertEquals(1, appels)
        maintenant += 1
        verifier()
        assertEquals(2, appels)
    }

    @Test
    fun `echec reseau, rien d'affiche de plus, aucune exception, nouvel essai 1 heure apres`() {
        reponse = { throw UnknownHostException("api.github.com") }
        assertNull(verifier())
        assertEquals(maintenant, stockage.lire().echecLe)
        assertEquals(0L, stockage.lire().reussieLe)
        maintenant += heure - 1
        verifier()
        assertEquals(1, appels)
        maintenant += 1
        reponse = { RELEASE_13 }
        assertEquals(Release("1.0.13", PAGE_13, APK_13), verifier())
        assertEquals(2, appels)
        assertEquals(0L, stockage.lire().echecLe)
    }

    @Test
    fun `echec apres une reussite, l'annonce connue reste`() {
        verifier()
        maintenant += 7 * heure
        for (echec in listOf<() -> String>(
            { throw IOException("HTTP 403") },
            { """{ "message": "API rate limit exceeded" }""" },
            { "<html>Portail Wi-Fi</html>" },
            { throw IllegalStateException("inattendu") },
        )) {
            reponse = echec
            assertEquals(Release("1.0.13", PAGE_13, APK_13), verifier())
            assertEquals("1.0.13", stockage.lire().derniereVersion)
            maintenant += heure
        }
    }

    @Test
    fun `plus tard, cette version n'est plus annoncee, une plus recente l'est`() {
        val v = verification()
        runBlocking { v.verifierSiDue() }
        v.ignorer("1.0.13")
        assertNull(v.annonce())
        assertNull(verification().annonce())
        maintenant += 6 * heure
        reponse = { RELEASE_14 }
        assertEquals("1.0.14", verifier()?.version)
    }

    @Test
    fun `l'app deja a jour, rien n'est annonce`() {
        assertNull(verifier(verification(nom = "1.0.13", code = 1013)))
        assertNull(verifier(verification(nom = "1.0.20", code = 1020)))
    }

    @Test
    fun `debug et preview ne verifient rien et n'ecrivent rien`() {
        assertNull(verifier(verification(active = false)))
        assertEquals(0, appels)
        assertFalse(fichier.exists())
    }

    @Test
    fun `fichier illisible, efface, comme si rien n'avait ete verifie`() {
        fichier.writeText("pas du json")
        assertEquals(EtatMisesAJour(), stockage.lire())
        assertFalse(fichier.exists())
        fichier.writeText("""{"version":2,"reussieLe":5}""")
        assertEquals(EtatMisesAJour(), stockage.lire())
        assertFalse(fichier.exists())
    }

    @Test
    fun `fichier ecrit puis relu, version 1, sans fichier temporaire laisse`() {
        val etat = EtatMisesAJour(reussieLe = 5, echecLe = 7, derniereVersion = "1.0.13", apk = "a", page = "p", versionIgnoree = "1.0.12")
        stockage.ecrire(etat)
        assertEquals(etat, stockage.lire())
        assertTrue(fichier.readText().contains("\"version\":1"))
        assertEquals(listOf("mises-a-jour.json"), dossier.root.list()!!.toList())
    }

    @Test
    fun `la requete vers GitHub ne porte ni jeton, ni cookie, ni donnee de l'app`() {
        assertEquals("https://api.github.com/repos/Bouss45/Eruditus/releases/latest", SourceGitHub.ADRESSE)
        val noms = SourceGitHub.ENTETES.keys.map { it.lowercase() }.toSet()
        assertEquals(setOf("accept", "x-github-api-version", "user-agent"), noms)
        assertEquals("Eruditus-Android", SourceGitHub.ENTETES["User-Agent"])
    }

    private companion object {
        const val PAGE_13 = "https://github.com/Bouss45/Eruditus/releases/tag/v1.0.13"
        const val APK_13 = "https://github.com/Bouss45/Eruditus/releases/download/v1.0.13/Eruditus-v1.0.13.apk"
        val RELEASE_13 = """
            {"tag_name":"v1.0.13","html_url":"$PAGE_13","draft":false,"prerelease":false,
             "assets":[{"name":"Eruditus-v1.0.13.apk","browser_download_url":"$APK_13"}]}
        """.trimIndent()
        val RELEASE_14 = """
            {"tag_name":"v1.0.14","html_url":"https://github.com/Bouss45/Eruditus/releases/tag/v1.0.14",
             "assets":[{"name":"Eruditus-v1.0.14.apk","browser_download_url":"https://exemple.test/14.apk"}]}
        """.trimIndent()
    }
}

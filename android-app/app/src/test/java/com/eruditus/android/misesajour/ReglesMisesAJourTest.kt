package com.eruditus.android.misesajour

import com.eruditus.android.misesajour.ReglesMisesAJour.Cible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nouvelles versions (spec/regles.md § 6, D61 – D63), règles reprises de MuscleNote
 * (`UpdateRulesTest`) : versions et versionCode, réponse de l'API GitHub, délais, version
 * ignorée, Obtainium ou navigateur.
 */
class ReglesMisesAJourTest {

    private val heure = 3_600_000L

    // --- Versions -----------------------------------------------------------------------------

    @Test
    fun `les nombres d'une version sont lus quel que soit le prefixe ou le suffixe`() {
        assertEquals(listOf(1, 0, 12), ReglesMisesAJour.nombres("v1.0.12"))
        assertEquals(listOf(1, 0, 0), ReglesMisesAJour.nombres("1.0.0-preview"))
        assertEquals(emptyList<Int>(), ReglesMisesAJour.nombres("latest"))
    }

    @Test
    fun `une version est plus recente nombre par nombre`() {
        assertTrue(ReglesMisesAJour.plusRecente("1.0.10", "1.0.9"))
        assertTrue(ReglesMisesAJour.plusRecente("1.1", "1.0.42"))
        assertTrue(ReglesMisesAJour.plusRecente("1.0.1", "1.0.0-preview"))
        assertFalse(ReglesMisesAJour.plusRecente("1.0.9", "1.0.10"))
        assertFalse(ReglesMisesAJour.plusRecente("1.0.4", "1.0.4"))
        assertFalse(ReglesMisesAJour.plusRecente("1.0", "1.0.0"))
        assertFalse(ReglesMisesAJour.plusRecente("latest", "1.0.4"))
    }

    @Test
    fun `le tag v1 0 n donne le versionCode 1000 plus n de release yml`() {
        assertEquals(1011, ReglesMisesAJour.versionCodeDeRelease("1.0.11"))
        assertEquals(1000, ReglesMisesAJour.versionCodeDeRelease("1.0.0"))
        assertEquals(1142, ReglesMisesAJour.versionCodeDeRelease("1.0.142"))
        assertNull(ReglesMisesAJour.versionCodeDeRelease("1.1.0"))
        assertNull(ReglesMisesAJour.versionCodeDeRelease("1.0"))
        assertNull(ReglesMisesAJour.versionCodeDeRelease("1.0.11-beta"))
        assertNull(ReglesMisesAJour.versionCodeDeRelease("2.0.3"))
    }

    @Test
    fun `une Release v1 0 n se compare au versionCode installe`() {
        assertTrue(ReglesMisesAJour.plusRecenteQueInstallee("1.0.12", nomInstalle = "1.0.11", codeInstalle = 1011))
        assertTrue(ReglesMisesAJour.plusRecenteQueInstallee("1.0.10", nomInstalle = "1.0.9", codeInstalle = 1009))
        assertFalse(ReglesMisesAJour.plusRecenteQueInstallee("1.0.11", nomInstalle = "1.0.11", codeInstalle = 1011))
        assertFalse(ReglesMisesAJour.plusRecenteQueInstallee("1.0.10", nomInstalle = "1.0.11", codeInstalle = 1011))
        // Le versionCode l'emporte sur le nom.
        assertFalse(ReglesMisesAJour.plusRecenteQueInstallee("1.0.12", nomInstalle = "1.0.0", codeInstalle = 1012))
    }

    @Test
    fun `une Release d'une autre forme se compare au nom installe, un tag sans nombre jamais`() {
        assertTrue(ReglesMisesAJour.plusRecenteQueInstallee("1.1.0", nomInstalle = "1.0.42", codeInstalle = 1042))
        assertFalse(ReglesMisesAJour.plusRecenteQueInstallee("1.1.0", nomInstalle = "1.1.0", codeInstalle = 1042))
        assertFalse(ReglesMisesAJour.plusRecenteQueInstallee("latest", nomInstalle = "1.0.42", codeInstalle = 1042))
    }

    // --- Réponse de l'API ---------------------------------------------------------------------

    @Test
    fun `la reponse de l'API donne la version, la page et le premier APK`() {
        val corps = """
            {
              "url": "https://api.github.com/repos/Bouss45/Eruditus/releases/1",
              "html_url": "https://github.com/Bouss45/Eruditus/releases/tag/v1.0.13",
              "tag_name": "v1.0.13",
              "name": "Eruditus v1.0.13",
              "draft": false,
              "prerelease": false,
              "body": "Eruditus 1.0.13 (versionCode 1013).",
              "assets": [
                { "name": "notes.txt", "browser_download_url": "https://exemple.test/notes.txt", "size": 3 },
                { "name": "Eruditus-v1.0.13.apk", "browser_download_url": "https://exemple.test/Eruditus-v1.0.13.apk", "size": 9 },
                { "name": "autre.APK", "browser_download_url": "https://exemple.test/autre.APK", "size": 9 }
              ]
            }
        """.trimIndent()
        assertEquals(
            Release("1.0.13", "https://github.com/Bouss45/Eruditus/releases/tag/v1.0.13", "https://exemple.test/Eruditus-v1.0.13.apk"),
            ReglesMisesAJour.lireRelease(corps),
        )
    }

    @Test
    fun `sans APK, la page de la Release reste`() {
        val corps = """{ "html_url": "https://github.com/o/r/releases/tag/v1.0.8", "tag_name": "v1.0.8", "assets": [] }"""
        assertEquals(Release("1.0.8", "https://github.com/o/r/releases/tag/v1.0.8", null), ReglesMisesAJour.lireRelease(corps))
        val sansListe = """{ "html_url": "p", "tag_name": "v1.0.8" }"""
        assertEquals(Release("1.0.8", "p", null), ReglesMisesAJour.lireRelease(sansListe))
    }

    @Test
    fun `une reponse invalide, un brouillon, une preversion ou la limite de requetes ne donnent rien`() {
        for (corps in listOf(
            """{ "html_url": "p", "tag_name": "v1.0.9", "draft": true }""",
            """{ "html_url": "p", "tag_name": "v1.0.9", "prerelease": true }""",
            """{ "html_url": "p", "tag_name": "latest" }""",
            """{ "tag_name": "v1.0.9" }""",
            """{ "html_url": "p" }""",
            """{ "html_url": 3, "tag_name": "v1.0.9" }""",
            """{ "message": "API rate limit exceeded for 1.2.3.4.", "documentation_url": "https://docs.github.com" }""",
            """[]""",
            "<html>",
            "",
        )) {
            assertNull(corps, ReglesMisesAJour.lireRelease(corps))
        }
    }

    // --- Quand vérifier -----------------------------------------------------------------------

    @Test
    fun `jamais verifie, la verification est due`() {
        assertTrue(ReglesMisesAJour.due(EtatMisesAJour(), maintenant = 1_000))
    }

    @Test
    fun `6 heures apres une reussite`() {
        val etat = EtatMisesAJour(reussieLe = 10 * heure)
        assertFalse(ReglesMisesAJour.due(etat, 10 * heure))
        assertFalse(ReglesMisesAJour.due(etat, 16 * heure - 1))
        assertTrue(ReglesMisesAJour.due(etat, 16 * heure))
    }

    @Test
    fun `1 heure apres un echec, meme si la derniere reussite est ancienne`() {
        val etat = EtatMisesAJour(reussieLe = 1 * heure, echecLe = 20 * heure)
        assertFalse(ReglesMisesAJour.due(etat, 20 * heure + 59 * 60_000))
        assertTrue(ReglesMisesAJour.due(etat, 21 * heure))
        // Échec sans aucune réussite : 1 h aussi.
        assertFalse(ReglesMisesAJour.due(EtatMisesAJour(echecLe = 5 * heure), 5 * heure + 1))
        assertTrue(ReglesMisesAJour.due(EtatMisesAJour(echecLe = 5 * heure), 6 * heure))
    }

    @Test
    fun `apres un echec, une reussite recente attend toujours ses 6 heures`() {
        val etat = EtatMisesAJour(reussieLe = 10 * heure, echecLe = 11 * heure)
        assertFalse(ReglesMisesAJour.due(etat, 13 * heure))
        assertTrue(ReglesMisesAJour.due(etat, 16 * heure))
    }

    @Test
    fun `horloge reculee, une date dans le futur rend la verification due`() {
        assertTrue(ReglesMisesAJour.due(EtatMisesAJour(reussieLe = 50 * heure), 10 * heure))
        assertTrue(ReglesMisesAJour.due(EtatMisesAJour(echecLe = 50 * heure), 10 * heure))
    }

    // --- Annonce et version ignorée -----------------------------------------------------------

    private val connue = EtatMisesAJour(reussieLe = 1, derniereVersion = "1.0.13", apk = "apk", page = "page")

    @Test
    fun `une Release plus recente que l'app installee est annoncee`() {
        assertEquals(Release("1.0.13", "page", "apk"), ReglesMisesAJour.annonce(connue, "1.0.12", 1012))
        assertNull(ReglesMisesAJour.annonce(connue, "1.0.13", 1013))
        assertNull(ReglesMisesAJour.annonce(connue, "1.0.14", 1014))
        assertNull(ReglesMisesAJour.annonce(EtatMisesAJour(), "1.0.12", 1012))
    }

    @Test
    fun `la version ignoree n'est plus annoncee, une plus recente l'est`() {
        val ignoree = connue.copy(versionIgnoree = "1.0.13")
        assertNull(ReglesMisesAJour.annonce(ignoree, "1.0.12", 1012))
        val suivante = ignoree.copy(derniereVersion = "1.0.14", apk = "apk14", page = "page14")
        assertEquals(Release("1.0.14", "page14", "apk14"), ReglesMisesAJour.annonce(suivante, "1.0.12", 1012))
    }

    @Test
    fun `pour Mettre a jour, la version ignoree compte quand meme`() {
        val ignoree = connue.copy(versionIgnoree = "1.0.13")
        assertEquals(Release("1.0.13", "page", "apk"), ReglesMisesAJour.pourMettreAJour(ignoree, "1.0.12", 1012))
        assertNull(ReglesMisesAJour.pourMettreAJour(connue, "1.0.13", 1013))
    }

    // --- Obtainium ou navigateur --------------------------------------------------------------

    private val release = Release("1.0.13", "https://github.com/o/r/releases/tag/v1.0.13", "https://exemple.test/Eruditus-v1.0.13.apk")

    @Test
    fun `installee par Obtainium, GitHub ou F-Droid`() {
        assertTrue(ReglesMisesAJour.installeeParObtainium(listOf("dev.imranr.obtainium")))
        assertTrue(ReglesMisesAJour.installeeParObtainium(listOf("dev.imranr.obtainium.fdroid")))
        // Initiateur Obtainium, installateur du système (installation par Obtainium sans Shizuku).
        assertTrue(ReglesMisesAJour.installeeParObtainium(listOf("com.google.android.packageinstaller", "dev.imranr.obtainium")))
    }

    @Test
    fun `installee au nom du Play Store, c'est Obtainium par Shizuku`() {
        assertTrue(ReglesMisesAJour.installeeParObtainium(listOf("com.android.vending")))
        assertEquals(
            listOf(
                Cible.Obtainium("dev.imranr.obtainium"),
                Cible.Obtainium("dev.imranr.obtainium.fdroid"),
                Cible.Navigateur("https://exemple.test/Eruditus-v1.0.13.apk"),
            ),
            ReglesMisesAJour.cibles(listOf("com.android.vending"), release),
        )
    }

    @Test
    fun `installee a la main ou par un autre, le navigateur seulement`() {
        assertFalse(ReglesMisesAJour.installeeParObtainium(emptyList()))
        assertFalse(ReglesMisesAJour.installeeParObtainium(listOf("com.google.android.packageinstaller")))
        assertFalse(ReglesMisesAJour.installeeParObtainium(listOf("org.fdroid.fdroid")))
        assertEquals(
            listOf(Cible.Navigateur("https://exemple.test/Eruditus-v1.0.13.apk")),
            ReglesMisesAJour.cibles(listOf("com.google.android.packageinstaller"), release),
        )
    }

    @Test
    fun `Obtainium d'abord dans la variante qui a installe l'app, puis l'autre, puis le navigateur`() {
        assertEquals(
            listOf(
                Cible.Obtainium("dev.imranr.obtainium.fdroid"),
                Cible.Obtainium("dev.imranr.obtainium"),
                Cible.Navigateur("https://exemple.test/Eruditus-v1.0.13.apk"),
            ),
            ReglesMisesAJour.cibles(listOf("dev.imranr.obtainium.fdroid"), release),
        )
    }

    @Test
    fun `sans APK, la page de la Release, sans Release connue, la derniere Release du depot`() {
        assertEquals(
            listOf(Cible.Navigateur("https://github.com/o/r/releases/tag/v1.0.13")),
            ReglesMisesAJour.cibles(emptyList(), release.copy(apk = null)),
        )
        assertEquals(
            listOf(Cible.Navigateur("https://github.com/Bouss45/Eruditus/releases/latest")),
            ReglesMisesAJour.cibles(emptyList(), null),
        )
    }
}

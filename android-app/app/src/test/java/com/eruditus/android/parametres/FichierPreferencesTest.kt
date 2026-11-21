package com.eruditus.android.parametres

import com.eruditus.android.compte.DepotCompte
import com.eruditus.android.donnees.api.Resultat
import com.eruditus.android.donnees.api.Route
import com.eruditus.android.donnees.api.TransportFactice
import com.eruditus.android.donnees.api.TransportFactice.Companion.CONNEXION
import com.eruditus.android.donnees.session.ChiffreurLogiciel
import com.eruditus.android.donnees.session.CoffreSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Le thème gardé sur le téléphone (D78, spec/donnees.md § 1, § 5) : `preferences.json`, relu
 * tel quel, Système s'il manque ou ne se lit pas, gardé à la déconnexion et à la suppression.
 */
class FichierPreferencesTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private val fichier by lazy { File(dossier.root, "preferences.json") }
    private val preferences by lazy { FichierPreferences(fichier) }

    @Test
    fun `rien d'ecrit, Systeme, aucun fichier cree`() {
        assertEquals(Theme.Systeme, preferences.lire().theme)
        assertFalse(fichier.exists())
    }

    @Test
    fun `chaque theme ecrit puis relu, par un autre lecteur aussi`() {
        for (theme in Theme.entries) {
            preferences.ecrire(Preferences(theme))
            assertEquals(theme, preferences.lire().theme)
            assertEquals(theme, FichierPreferences(fichier).lire().theme)
        }
    }

    @Test
    fun `format du fichier, version 1, en clair, ecriture atomique`() {
        preferences.ecrire(Preferences(Theme.Clair))
        assertEquals("""{"version":1,"theme":"clair"}""", fichier.readText())
        preferences.ecrire(Preferences(Theme.Sombre))
        assertEquals("""{"version":1,"theme":"sombre"}""", fichier.readText())
        assertEquals(listOf("preferences.json"), dossier.root.list()!!.toList())
    }

    @Test
    fun `theme inconnu, absent ou mal type, Systeme et fichier garde`() {
        for (contenu in listOf(
            """{"version":1,"theme":"sepia"}""",
            """{"version":1}""",
            """{"version":1,"theme":2}""",
            """{"version":1,"theme":"sombre","champFutur":true}""",
        )) {
            fichier.writeText(contenu)
            val attendu = if ("\"sombre\"" in contenu) Theme.Sombre else Theme.Systeme
            assertEquals(contenu, attendu, preferences.lire().theme)
            assertTrue(contenu, fichier.exists())
        }
    }

    @Test
    fun `fichier illisible ou d'une autre version, efface, Systeme`() {
        for (contenu in listOf("", "pas du json", "[]", """{"theme":"sombre"}""", """{"version":2,"theme":"sombre"}""", """{"version":"1","theme":"clair"}""")) {
            fichier.writeText(contenu)
            assertEquals(contenu, Theme.Systeme, preferences.lire().theme)
            assertFalse(contenu, fichier.exists())
        }
    }

    @Test
    fun `codes des themes, ceux de la spec`() {
        assertEquals(listOf("systeme", "sombre", "clair"), Theme.entries.map { it.code })
    }

    @Test
    fun `le theme reste a la deconnexion et a la suppression du compte`() = runBlocking {
        // Le fichier de session et les préférences sont dans le même dossier (noBackupFilesDir).
        val transport = TransportFactice()
        val session = File(dossier.root, "session")
        val coffre = CoffreSession(session, ChiffreurLogiciel())
        val depot = DepotCompte(transport, coffre)
        preferences.ecrire(Preferences(Theme.Clair))

        transport.repondre(Route.Connexion, 200, CONNEXION)
        depot.connecter("Hibou_42", "nuit-blanche-42")
        transport.repondre(Route.Deconnexion, 204)
        depot.deconnecter()
        assertFalse(session.exists())
        assertEquals(Theme.Clair, preferences.lire().theme)

        depot.connecter("Hibou_42", "nuit-blanche-42")
        transport.repondre(Route.Suppression, 204)
        assertEquals(Resultat.Ok(Unit), depot.supprimer("nuit-blanche-42"))
        assertNull(coffre.lire())
        assertEquals(Theme.Clair, preferences.lire().theme)
    }
}

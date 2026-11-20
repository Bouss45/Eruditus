package com.eruditus.android.donnees.session

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Fichier de session (D54, spec/donnees.md § 1, § 2, § 5) : chiffré, relu, effacé dès qu'il est
 * illisible, sans jamais laisser le jeton en clair sur le disque.
 */
class CoffreSessionTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private val chiffreur = ChiffreurLogiciel()
    private val fichier: File by lazy { File(dossier.root, "session") }
    private val coffre by lazy { CoffreSession(fichier, chiffreur) }
    private val session = Session(jeton = "jeton-tres-secret", idJoueur = "id-hibou", pseudo = "Hibou_42")

    @Test
    fun `rien d'ecrit, aucune session`() {
        assertNull(coffre.lire())
        assertFalse(fichier.exists())
    }

    @Test
    fun `ecrite puis relue`() {
        coffre.ecrire(session)
        assertEquals(session, coffre.lire())
        assertEquals(session, CoffreSession(fichier, chiffreur).lire())
    }

    @Test
    fun `le fichier ne contient ni le jeton ni le pseudo en clair`() {
        coffre.ecrire(session)
        val octets = fichier.readBytes()
        val texte = String(octets, Charsets.ISO_8859_1)
        assertFalse("jeton-tres-secret" in texte)
        assertFalse("Hibou_42" in texte)
        assertFalse("version" in texte)
    }

    @Test
    fun `chaque ecriture tire un nouvel IV`() {
        coffre.ecrire(session)
        val premier = fichier.readBytes()
        coffre.ecrire(session)
        val second = fichier.readBytes()
        assertFalse(premier.copyOf(12).contentEquals(second.copyOf(12)))
    }

    @Test
    fun `ecriture atomique, aucun fichier temporaire laisse`() {
        coffre.ecrire(session)
        coffre.ecrire(session.copy(jeton = "jeton-neuf"))
        assertEquals(listOf("session"), dossier.root.list()!!.toList())
        assertEquals("jeton-neuf", coffre.lire()!!.jeton)
    }

    @Test
    fun `effacer supprime le fichier`() {
        coffre.ecrire(session)
        coffre.effacer()
        assertFalse(fichier.exists())
        assertNull(coffre.lire())
    }

    @Test
    fun `fichier tronque, efface avec sa cle`() {
        coffre.ecrire(session)
        fichier.writeBytes(fichier.readBytes().copyOf(20))
        assertNull(coffre.lire())
        assertFalse(fichier.exists())
        assertEquals(1, chiffreur.clesOubliees)
    }

    @Test
    fun `fichier altere, efface`() {
        coffre.ecrire(session)
        val octets = fichier.readBytes()
        octets[octets.size - 1] = (octets[octets.size - 1] + 1).toByte()
        fichier.writeBytes(octets)
        assertNull(coffre.lire())
        assertFalse(fichier.exists())
    }

    @Test
    fun `cle perdue (restauration, verrouillage change), fichier efface`() {
        coffre.ecrire(session)
        chiffreur.perdreCle()
        assertNull(coffre.lire())
        assertFalse(fichier.exists())
        // Une nouvelle session s'écrit ensuite avec une nouvelle clé.
        coffre.ecrire(session)
        assertEquals(session, coffre.lire())
    }

    @Test
    fun `version inconnue ou JSON illisible, efface`() {
        for (json in listOf(
            """{"version":2,"jeton":"j","idJoueur":"i","pseudo":"p"}""",
            """{"jeton":"j","idJoueur":"i","pseudo":"p"}""",
            """{"version":1,"jeton":"j","idJoueur":"i"}""",
            """{"version":1,"jeton":"","idJoueur":"i","pseudo":"p"}""",
            "pas du json",
        )) {
            fichier.writeBytes(chiffreur.chiffrer(json.toByteArray()))
            assertNull(json, coffre.lire())
            assertFalse(json, fichier.exists())
        }
    }

    @Test
    fun `format du fichier, version 1`() {
        assertEquals(session, FormatSession.decoder(FormatSession.encoder(session)))
        val json = String(FormatSession.encoder(session))
        assertTrue(json, "\"version\":1" in json)
        assertArrayEquals(FormatSession.encoder(session), FormatSession.encoder(session.copy()))
    }

    @Test
    fun `une session affichee ne montre pas son jeton`() {
        assertFalse("jeton-tres-secret" in session.toString())
    }
}

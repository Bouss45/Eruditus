package com.eruditus.releasenotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Une seule règle « touche l'app » (D49) : les motifs qui déclenchent `release.yml` sont ceux qui
 * choisissent les commits des notes de version.
 */
class WorkflowReleaseTest {

    private val workflow = File(System.getProperty("eruditus.depot"), ".github/workflows/release.yml")

    /** Liste `paths` du déclencheur `on: push:` (lignes `- "motif"`), lue sans bibliothèque YAML. */
    private fun cheminsDuPush(lignes: List<String>): List<String> {
        fun indentation(ligne: String) = ligne.length - ligne.trimStart().length
        fun utile(ligne: String) = ligne.isNotBlank() && !ligne.trimStart().startsWith("#")

        val on = lignes.indexOfFirst { it.trimEnd() == "on:" }
        check(on >= 0) { "pas de bloc on: dans release.yml" }
        val push = (on + 1 until lignes.size).first { lignes[it].trim() == "push:" }
        val paths = (push + 1 until lignes.size).first { lignes[it].trim() == "paths:" }
        val niveau = indentation(lignes[paths])
        return (paths + 1 until lignes.size)
            .map { lignes[it] }
            .filter(::utile)
            .takeWhile { indentation(it) > niveau && it.trimStart().startsWith("- ") }
            .map { it.trimStart().removePrefix("- ").trim().removeSurrounding("\"") }
    }

    @Test
    fun `release yml se déclenche sur les mêmes motifs que les notes de version`() {
        assertTrue("$workflow introuvable", workflow.isFile)
        assertEquals(NotesDeVersion.MOTIFS_APP, cheminsDuPush(workflow.readLines()))
    }

    @Test
    fun `la liste des motifs commence par android-app puis n'exclut que dans android-app`() {
        val motifs = NotesDeVersion.MOTIFS_APP
        assertEquals("android-app/**", motifs.first())
        motifs.drop(1).forEach { assertTrue(it, it.startsWith("!android-app/")) }
    }
}

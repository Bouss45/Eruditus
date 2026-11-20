package com.eruditus.releasenotes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Motifs de chemins dans la syntaxe de `paths` de GitHub Actions (spec/publication.md § 3). */
class MotifsTest {

    @Test
    fun `une étoile ne traverse pas les dossiers`() {
        assertTrue(Motifs.correspond(listOf("docs/*.md"), "docs/a.md"))
        assertFalse(Motifs.correspond(listOf("docs/*.md"), "docs/x/a.md"))
        assertTrue(Motifs.correspond(listOf("src/test*/**"), "src/testDebug/A.kt"))
        assertTrue(Motifs.correspond(listOf("src/test*/**"), "src/test/A.kt"))
    }

    @Test
    fun `deux étoiles traversent les dossiers, zéro dossier compris`() {
        assertTrue(Motifs.correspond(listOf("android-app/**"), "android-app/app/src/main/A.kt"))
        assertTrue(Motifs.correspond(listOf("android-app/**"), "android-app/gradlew"))
        assertTrue(Motifs.correspond(listOf("a/**/*.md"), "a/README.md"))
        assertTrue(Motifs.correspond(listOf("a/**/*.md"), "a/b/c/NOTES.md"))
        assertTrue(Motifs.correspond(listOf("a/**/src/test*/**"), "a/src/test/A.kt"))
        assertTrue(Motifs.correspond(listOf("a/**/src/test*/**"), "a/app/src/test/java/A.kt"))
    }

    @Test
    fun `un motif ne correspond qu'au chemin entier`() {
        assertFalse(Motifs.correspond(listOf("android-app/**"), "server/android-app/x"))
        assertFalse(Motifs.correspond(listOf("android-app/**"), "android-app-bis/x"))
        assertFalse(Motifs.correspond(listOf("a/*.md"), "a/b.mdx"))
    }

    @Test
    fun `le point et les autres caractères sont littéraux`() {
        assertFalse(Motifs.correspond(listOf("a/*.md"), "a/bxmd"))
        assertTrue(Motifs.correspond(listOf("a/(b)\$c^.kt"), "a/(b)\$c^.kt"))
        assertFalse(Motifs.correspond(listOf("a/(b)\$c^.kt"), "a/b"))
    }

    @Test
    fun `le dernier motif qui correspond l'emporte`() {
        val motifs = listOf("a/**", "!a/**/*.md", "a/garde/*.md")
        assertTrue(Motifs.correspond(motifs, "a/b.kt"))
        assertFalse(Motifs.correspond(motifs, "a/b.md"))
        assertFalse(Motifs.correspond(motifs, "a/x/b.md"))
        assertTrue(Motifs.correspond(motifs, "a/garde/b.md"))
        assertFalse(Motifs.correspond(motifs, "b/c.kt"))
    }

    @Test
    fun `sans motif positif rien ne correspond`() {
        assertFalse(Motifs.correspond(emptyList(), "a/b.kt"))
        assertFalse(Motifs.correspond(listOf("!a/*.md"), "a/b.kt"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un motif avec un point d'interrogation est refusé`() {
        Motifs.correspond(listOf("a/b?.kt"), "a/b.kt")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un motif avec un plus est refusé`() {
        Motifs.correspond(listOf("a/b+.kt"), "a/b.kt")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un motif avec des crochets est refusé`() {
        Motifs.correspond(listOf("a/[bc].kt"), "a/b.kt")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un motif avec une barre oblique inverse est refusé`() {
        Motifs.correspond(listOf("a/\\*.kt"), "a/*.kt")
    }
}

package com.eruditus.releasenotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Notes de version (spec/publication.md § 3, D49) : lecture de `git log`, commits retenus, texte. */
class NotesDeVersionTest {

    private fun commit(titre: String, vararg fichiers: String, parents: Int = 1) = Commit(
        empreinte = "h-$titre",
        parents = List(parents) { "p$it" },
        titre = titre,
        fichiers = fichiers.toList(),
    )

    private val entete = NotesDeVersion.entete("1.0.12", "1012", "1a2b3c4d5e6f")

    @Test
    fun `la sortie de git log est lue commit par commit`() {
        val log = "\u001eaaa\u001fppp\u001fClassement : ma position sous le top 10\n\n" +
            "android-app/app/src/main/A.kt\nSPEC.md\n" +
            "\u001ebbb\u001fppp qqq\u001fMerge pull request #4 from Bouss45/feat/classement\n" +
            "\u001eccc\u001fbbb\u001fPartie : « É » et \"guillemets\"\n\n\"spec/é t.md\"\n"
        val commits = NotesDeVersion.lireGitLog(log)
        assertEquals(3, commits.size)
        assertEquals(
            Commit("aaa", listOf("ppp"), "Classement : ma position sous le top 10", listOf("android-app/app/src/main/A.kt", "SPEC.md")),
            commits[0],
        )
        assertEquals(listOf("ppp", "qqq"), commits[1].parents)
        assertEquals(emptyList<String>(), commits[1].fichiers)
        assertEquals("Partie : « É » et \"guillemets\"", commits[2].titre)
        assertEquals(listOf("spec/é t.md"), commits[2].fichiers)
    }

    @Test
    fun `une sortie vide ne donne aucun commit`() {
        assertEquals(emptyList<Commit>(), NotesDeVersion.lireGitLog(""))
        assertEquals(emptyList<Commit>(), NotesDeVersion.lireGitLog("\n"))
    }

    @Test
    fun `le code et le build de l'app touchent l'app`() {
        listOf(
            "android-app/app/src/main/java/com/eruditus/android/MainActivity.kt",
            "android-app/app/src/main/res/values/strings.xml",
            "android-app/app/src/main/AndroidManifest.xml",
            "android-app/app/src/release/java/com/eruditus/android/PremierEcran.kt",
            "android-app/app/build.gradle.kts",
            "android-app/app/proguard-rules.pro",
            "android-app/build.gradle.kts",
            "android-app/settings.gradle.kts",
            "android-app/gradle.properties",
            "android-app/gradle/libs.versions.toml",
            "android-app/gradle/wrapper/gradle-wrapper.properties",
            "android-app/gradlew",
        ).forEach { assertTrue(it, NotesDeVersion.toucheLApp(it)) }
    }

    @Test
    fun `les tests, la doc, le catalogue et les notes de version ne touchent pas l'app`() {
        listOf(
            "android-app/app/src/test/java/com/eruditus/android/ui/designsystem/theme/CouleursDesignTest.kt",
            "android-app/app/src/androidTest/java/A.kt",
            "android-app/app/src/testDebug/java/A.kt",
            "android-app/app/src/androidTestDebug/java/A.kt",
            "android-app/app/src/catalogue/java/com/eruditus/android/catalogue/Catalogue.kt",
            "android-app/app/src/debug/AndroidManifest.xml",
            "android-app/app/src/preview/res/values/strings.xml",
            "android-app/release-notes/src/main/kotlin/com/eruditus/releasenotes/Main.kt",
            "android-app/release-notes/build.gradle.kts",
            "android-app/README.md",
            "android-app/app/NOTES.md",
        ).forEach { assertFalse(it, NotesDeVersion.toucheLApp(it)) }
    }

    @Test
    fun `rien hors d'android-app ne touche l'app`() {
        listOf(
            "server/pb_hooks/eruditus.pb.js",
            "server/pb_migrations/1763500000_eruditus_collections.js",
            "server/tests/build.gradle.kts",
            "server/tests/src/test/kotlin/com/eruditus/servertests/PartieTest.kt",
            "spec/regles.md",
            "SPEC.md",
            "DESIGN.md",
            "CLAUDE.md",
            "design/icone/premier-plan.svg",
            ".github/workflows/release.yml",
            ".gitignore",
            "android-app-bis/app/src/main/A.kt",
        ).forEach { assertFalse(it, NotesDeVersion.toucheLApp(it)) }
    }

    @Test
    fun `les commits qui ne touchent que les tests, server, spec ou design ne sont pas retenus`() {
        val tests = commit("Tests du classement : ex æquo", "android-app/app/src/test/java/ClassementTest.kt")
        val serveur = commit("Serveur : route /moi", "server/pb_hooks/eruditus.pb.js", "server/tests/src/test/kotlin/MoiTest.kt")
        val spec = commit("Spec : écran Compte", "SPEC.md", "spec/ecrans.md")
        val design = commit("Design : icône", "design/icone/fond.svg", "DESIGN.md")
        val catalogue = commit("Catalogue : boîte avec champ", "android-app/app/src/catalogue/java/Boites.kt")
        val notes = commit("Notes de version : 60 lignes", "android-app/release-notes/src/main/kotlin/N.kt")
        val ci = commit("CI : cache Gradle", ".github/workflows/checks.yml")
        val app = commit("Classement : ma position sous le top 10", "android-app/app/src/main/java/Classement.kt", "android-app/app/src/test/java/ClassementTest.kt", "SPEC.md")
        assertEquals(listOf(app), NotesDeVersion.retenus(listOf(tests, serveur, spec, design, catalogue, notes, ci, app)))
    }

    @Test
    fun `les fusions et les commits sans fichier ne sont pas retenus`() {
        val app = commit("Compte : supprimer mon compte", "android-app/app/src/main/C.kt")
        val fusion = commit("Merge pull request #4", "android-app/app/src/main/C.kt", parents = 2)
        val vide = commit("Commit vide")
        val racine = commit("Premier commit", "android-app/build.gradle.kts", parents = 0)
        assertEquals(listOf(app, racine), NotesDeVersion.retenus(listOf(app, fusion, vide, racine)))
    }

    @Test
    fun `le texte liste les titres du plus ancien au plus récent avec le lien de comparaison`() {
        val texte = NotesDeVersion.texte(
            entete,
            listOf(
                commit("Compte : créer un compte", "android-app/a.kt"),
                commit("Spec : écran Partie", "SPEC.md"),
                commit("Partie : 5 questions, 20 s chacune", "android-app/b.kt"),
            ),
            "https://github.com/Bouss45/Eruditus/compare/v1.0.11...v1.0.12",
        )
        assertEquals(
            """
            Eruditus 1.0.12 (versionCode 1012), commit 1a2b3c4.

            ## Changements
            - Compte : créer un compte
            - Partie : 5 questions, 20 s chacune

            Tous les commits : https://github.com/Bouss45/Eruditus/compare/v1.0.11...v1.0.12

            """.trimIndent(),
            texte,
        )
    }

    @Test
    fun `sans commit retenu le texte le dit`() {
        val texte = NotesDeVersion.texte(entete, listOf(commit("CI", ".github/workflows/release.yml")), null)
        assertEquals(
            "Eruditus 1.0.12 (versionCode 1012), commit 1a2b3c4.\n\nAucun changement dans l'app : nouvelle compilation.\n",
            texte,
        )
    }

    @Test
    fun `sans Release précédente la liste est indisponible, sans lien de comparaison`() {
        val texte = NotesDeVersion.texte(entete, null, "https://example.invalid/compare")
        assertEquals("Eruditus 1.0.12 (versionCode 1012), commit 1a2b3c4.\n\nListe des changements indisponible.\n", texte)
    }

    @Test
    fun `au-delà de 50 commits les suivants sont comptés`() {
        val beaucoup = (1..53).map { commit("Changement $it", "android-app/f$it.kt") }
        val lignes = NotesDeVersion.texte(entete, beaucoup, null).lines()
        assertTrue("- Changement 50" in lignes)
        assertFalse("- Changement 51" in lignes)
        assertTrue("- … et 3 autres changements" in lignes)

        val unDePlus = NotesDeVersion.texte(entete, beaucoup.take(51), null).lines()
        assertTrue("- … et 1 autre changement" in unDePlus)
        val pile = NotesDeVersion.texte(entete, beaucoup.take(50), null)
        assertFalse("autre" in pile)
    }
}

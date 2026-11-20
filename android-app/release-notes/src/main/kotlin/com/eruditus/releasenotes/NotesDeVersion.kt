package com.eruditus.releasenotes

/** Un commit lu dans la sortie de `git log` : empreinte, parents, titre et fichiers modifiés. */
data class Commit(
    val empreinte: String,
    val parents: List<String>,
    val titre: String,
    val fichiers: List<String>,
)

/**
 * Notes de version des Releases (spec/publication.md § 3, D49), en fonctions pures testables sur
 * la JVM : titres des commits qui touchent l'app depuis la Release précédente, sans les fusions.
 */
object NotesDeVersion {
    /**
     * Ce qui touche l'app (D49) : ce qui entre dans le build de l'APK publié. Même liste, dans le
     * même ordre, que `paths` du déclencheur de `.github/workflows/release.yml` (vérifié par
     * `WorkflowReleaseTest`).
     */
    val MOTIFS_APP: List<String> = listOf(
        "android-app/**",
        "!android-app/**/*.md",
        "!android-app/release-notes/**",
        "!android-app/**/src/test*/**",
        "!android-app/**/src/androidTest*/**",
        "!android-app/**/src/catalogue/**",
        "!android-app/**/src/debug/**",
        "!android-app/**/src/preview/**",
    )

    /** Au-delà, une dernière ligne « … et N autres changements ». */
    const val MAX_LIGNES = 50

    /** Séparateurs de commits (`%x1e`) et de champs (`%x1f`) du format passé à `git log`. */
    private const val SEPARATEUR_COMMITS = '\u001e'
    private const val SEPARATEUR_CHAMPS = '\u001f'

    /**
     * Format attendu : `git -c core.quotePath=false log --reverse --name-only
     * --format=%x1e%H%x1f%P%x1f%s <tag>..<commit>` : pour chaque commit, une ligne « empreinte,
     * parents, titre », puis ses fichiers modifiés, un par ligne.
     */
    const val FORMAT_GIT_LOG = "%x1e%H%x1f%P%x1f%s"

    fun lireGitLog(texte: String): List<Commit> =
        texte.split(SEPARATEUR_COMMITS)
            .filter { it.isNotBlank() }
            .map { bloc ->
                val lignes = bloc.lines()
                val champs = lignes.first().split(SEPARATEUR_CHAMPS, limit = 3)
                Commit(
                    empreinte = champs[0].trim(),
                    parents = champs.getOrElse(1) { "" }.split(' ').filter { it.isNotBlank() },
                    titre = champs.getOrElse(2) { "" }.trim(),
                    fichiers = lignes.drop(1).map { sansGuillemets(it.trim()) }.filter { it.isNotEmpty() },
                )
            }

    /** Git entoure de guillemets un chemin aux caractères spéciaux. */
    private fun sansGuillemets(chemin: String): String =
        if (chemin.length >= 2 && chemin.startsWith('"') && chemin.endsWith('"')) chemin.substring(1, chemin.length - 1) else chemin

    /** Vrai si [chemin] (relatif à la racine du dépôt) entre dans le build de l'APK publié. */
    fun toucheLApp(chemin: String): Boolean = Motifs.correspond(MOTIFS_APP, chemin)

    /** Commit retenu : pas une fusion (au plus un parent) et au moins un fichier qui touche l'app. */
    fun estRetenu(commit: Commit): Boolean =
        commit.parents.size <= 1 && commit.fichiers.any(::toucheLApp)

    /** Commits retenus, dans l'ordre reçu (du plus ancien au plus récent). */
    fun retenus(commits: List<Commit>): List<Commit> = commits.filter(::estRetenu)

    /** Première ligne du texte : version, `versionCode` et commit construit. */
    fun entete(versionName: String, versionCode: String, commit: String): String =
        "Eruditus $versionName (versionCode $versionCode), commit ${commit.take(7)}."

    /**
     * Texte de la Release. [commits] = commits depuis la Release précédente, `null` s'il n'y en a
     * pas ou si son tag est introuvable ; [urlComparaison] = page de comparaison des deux tags.
     */
    fun texte(entete: String, commits: List<Commit>?, urlComparaison: String?): String = buildString {
        append(entete).append("\n\n")
        if (commits == null) {
            append("Liste des changements indisponible.\n")
            return@buildString
        }
        val liste = retenus(commits)
        if (liste.isEmpty()) {
            append("Aucun changement dans l'app : nouvelle compilation.\n")
        } else {
            append("## Changements\n")
            liste.take(MAX_LIGNES).forEach { append("- ").append(it.titre).append('\n') }
            val reste = liste.size - MAX_LIGNES
            if (reste > 0) {
                append(if (reste == 1) "- … et 1 autre changement\n" else "- … et $reste autres changements\n")
            }
        }
        if (urlComparaison != null) append("\nTous les commits : ").append(urlComparaison).append('\n')
    }
}

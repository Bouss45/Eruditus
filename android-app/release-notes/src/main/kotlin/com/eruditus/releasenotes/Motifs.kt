package com.eruditus.releasenotes

/**
 * Motifs de chemins dans la syntaxe de `paths` de GitHub Actions, réduite à ce dont la liste
 * [NotesDeVersion.MOTIFS_APP] a besoin (spec/publication.md § 3) :
 *
 * - `*` : zéro ou plusieurs caractères, sauf `/` ;
 * - `**` : zéro ou plusieurs caractères, `/` compris ; `**` suivi de `/` couvre aussi zéro dossier ;
 * - `!` en tête : le motif exclut ;
 * - pour chaque chemin, le dernier motif qui correspond l'emporte.
 *
 * `?`, `+`, `[ ]`, `{ }` et `\` ont un sens particulier pour GitHub : ils sont refusés plutôt
 * qu'imités à moitié, pour que la règle d'ici reste celle de `release.yml`.
 */
object Motifs {
    private val INTERDITS = setOf('?', '+', '[', ']', '{', '}', '\\')

    /** Vrai si [chemin] est retenu par la liste [motifs], lue dans l'ordre. */
    fun correspond(motifs: List<String>, chemin: String): Boolean {
        var retenu = false
        for (motif in motifs) {
            val exclut = motif.startsWith('!')
            if (regex(motif.removePrefix("!")).matches(chemin)) retenu = !exclut
        }
        return retenu
    }

    /** Expression régulière d'un motif, sans son `!`. */
    fun regex(motif: String): Regex {
        motif.firstOrNull { it in INTERDITS }?.let {
            throw IllegalArgumentException("caractère « $it » non pris en charge dans le motif $motif")
        }
        val sortie = StringBuilder()
        var i = 0
        while (i < motif.length) {
            when {
                motif.startsWith("**/", i) -> { sortie.append("(?:.*/)?"); i += 3 }
                motif.startsWith("**", i) -> { sortie.append(".*"); i += 2 }
                motif[i] == '*' -> { sortie.append("[^/]*"); i += 1 }
                else -> {
                    // Littéral : un passage ininterrompu jusqu'à la prochaine étoile.
                    val fin = motif.indexOf('*', i).let { if (it < 0) motif.length else it }
                    sortie.append(Regex.escape(motif.substring(i, fin)))
                    i = fin
                }
            }
        }
        return Regex(sortie.toString())
    }
}

package com.eruditus.android.elo

import com.eruditus.android.R
import com.eruditus.android.donnees.api.EloQuestion
import com.eruditus.android.donnees.api.Enjeu
import com.eruditus.android.ui.ecrans.Textes
import kotlin.math.abs

/** La ligne d'enjeu : son [texte] affiché et sa [lue] pour TalkBack (D129). */
data class LigneEnjeu(val texte: String, val lue: String)

/**
 * La ligne d'enjeu de la Partie (spec/ecrans.md § 9, D126 – D129) : le niveau en mots, puis le gain
 * et la perte annoncés pendant la question, la variation obtenue à la correction. Un cas sans
 * ligne (`enjeu` ou `elo` absent, ou chiffres illisibles sans niveau) rend null. Vérifiée sur
 * `strings.xml` par `TextesEnjeuTest`.
 */
object TextesEnjeu {

    /** « Difficile » ; null hors de 1 à 5. */
    fun niveau(t: Textes, niveau: Int?): String? = when (niveau) {
        1 -> t.texte(R.string.enjeu_niveau_1)
        2 -> t.texte(R.string.enjeu_niveau_2)
        3 -> t.texte(R.string.enjeu_niveau_3)
        4 -> t.texte(R.string.enjeu_niveau_4)
        5 -> t.texte(R.string.enjeu_niveau_5)
        else -> null
    }

    /** « Difficile · Réussir +12 · Rater −28 », « Difficile · Déjà vue : sans effet sur l'Elo », « Sans effet sur l'Elo ». */
    fun question(t: Textes, e: Enjeu?): LigneEnjeu? {
        if (e == null) return null
        val reste = when {
            e.illisible -> emptyList()
            e.gain != null && e.perte != null -> listOf(
                t.texte(R.string.enjeu_reussir, signe(t, e.gain)) to t.texte(R.string.enjeu_reussir_lu, lu(t, e.gain)),
                t.texte(R.string.enjeu_rater, signe(t, -e.perte)) to t.texte(R.string.enjeu_rater_lu, lu(t, -e.perte)),
            )
            else -> listOf(sansEffet(t, niveau(t, e.niveau) != null))
        }
        return ligne(niveau(t, e.niveau), reste)
    }

    /** « Difficile · +12 », « Difficile · −28 », « Difficile · 0 », ou la ligne sans effet. */
    fun correction(t: Textes, e: EloQuestion?): LigneEnjeu? {
        if (e == null) return null
        val reste = when {
            e.illisible -> emptyList()
            e.variation != null -> listOf(signe(t, e.variation) to t.texte(R.string.enjeu_elo_lu, lu(t, e.variation)))
            else -> listOf(sansEffet(t, niveau(t, e.niveau) != null))
        }
        return ligne(niveau(t, e.niveau), reste)
    }

    /** Question déjà vue (le niveau précède) ou sans niveau ; le même texte affiché et lu. */
    private fun sansEffet(t: Textes, niveauConnu: Boolean): Pair<String, String> =
        t.texte(if (niveauConnu) R.string.enjeu_deja_vue else R.string.enjeu_sans_effet).let { it to it }

    /** Parties séparées par « · » à l'écran, et par des phrases pour TalkBack ; aucune partie, aucune ligne. */
    private fun ligne(niveau: String?, reste: List<Pair<String, String>>): LigneEnjeu? {
        val parties = listOfNotNull(niveau?.let { it to it }) + reste
        if (parties.isEmpty()) return null
        return LigneEnjeu(parties.joinToString(" · ") { it.first }, parties.joinToString(" ") { it.second + "." })
    }

    /** « +12 », « −28 » (vrai signe moins), « 0 » jamais « +0 » ni « −0 ». */
    private fun signe(t: Textes, v: Int): String = when {
        v > 0 -> t.texte(R.string.elo_variation_hausse, v)
        v < 0 -> t.texte(R.string.elo_variation_baisse, abs(v))
        else -> "0"
    }

    /** « plus 12 », « moins 28 », « 0 ». */
    private fun lu(t: Textes, v: Int): String = when {
        v > 0 -> t.texte(R.string.enjeu_plus_lu, v)
        v < 0 -> t.texte(R.string.enjeu_moins_lu, abs(v))
        else -> "0"
    }
}

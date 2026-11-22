package com.eruditus.android.elo

import com.eruditus.android.TextesRessources
import com.eruditus.android.donnees.api.EloQuestion
import com.eruditus.android.donnees.api.Enjeu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La ligne d'enjeu telle que le téléphone l'affiche et la lit (spec/ecrans.md § 9, D126, D127,
 * D129) : lue dans `strings.xml`, comme `TextesEloTest`.
 */
class TextesEnjeuTest {

    private val t = TextesRessources()
    private val m = "−"
    private val nb = "\u00A0"

    private fun question(e: Enjeu?) = TextesEnjeu.question(t, e)
    private fun correction(e: EloQuestion?) = TextesEnjeu.correction(t, e)

    @Test
    fun `les cinq niveaux en mots`() {
        val attendus = listOf("Très facile", "Facile", "Moyen", "Difficile", "Expert")
        for (n in 1..5) {
            assertEquals(attendus[n - 1], TextesEnjeu.niveau(t, n))
            assertEquals("${attendus[n - 1]} · Réussir +12 · Rater ${m}28", question(Enjeu(n, 12, 28))!!.texte)
            assertEquals("${attendus[n - 1]} · +12", correction(EloQuestion(n, 12))!!.texte)
        }
    }

    @Test
    fun `question comptee, le texte et la lecture TalkBack`() {
        val ligne = question(Enjeu(4, 12, 28))!!
        assertEquals("Difficile · Réussir +12 · Rater ${m}28", ligne.texte)
        assertEquals("Difficile. Réussir$nb: plus 12. Rater$nb: moins 28.", ligne.lue)
    }

    @Test
    fun `gain nul ou perte nulle, 0 jamais +0 ni moins 0`() {
        val gainNul = question(Enjeu(4, 0, 28))!!
        assertEquals("Difficile · Réussir 0 · Rater ${m}28", gainNul.texte)
        assertEquals("Difficile. Réussir$nb: 0. Rater$nb: moins 28.", gainNul.lue)
        val perteNulle = question(Enjeu(4, 12, 0))!!
        assertEquals("Difficile · Réussir +12 · Rater 0", perteNulle.texte)
        assertEquals("Difficile. Réussir$nb: plus 12. Rater$nb: 0.", perteNulle.lue)
        for (ligne in listOf(gainNul, perteNulle, question(Enjeu(4, 0, 0))!!)) {
            assertEquals(false, ligne.texte.contains("+0"))
            assertEquals(false, ligne.texte.contains("${m}0"))
        }
    }

    @Test
    fun `question deja vue, le niveau et pas de chiffres`() {
        val ligne = question(Enjeu(4, null, null))!!
        assertEquals("Difficile · Déjà vue$nb: sans effet sur l’Elo", ligne.texte)
        assertEquals("Difficile. Déjà vue$nb: sans effet sur l’Elo.", ligne.lue)
    }

    @Test
    fun `question sans niveau`() {
        val ligne = question(Enjeu(null, null, null))!!
        assertEquals("Sans effet sur l’Elo", ligne.texte)
        assertEquals("Sans effet sur l’Elo.", ligne.lue)
    }

    @Test
    fun `correction juste, fausse, variation zero`() {
        val juste = correction(EloQuestion(4, 12))!!
        assertEquals("Difficile · +12", juste.texte)
        assertEquals("Difficile. Elo, plus 12.", juste.lue)
        val fausse = correction(EloQuestion(4, -28))!!
        assertEquals("Difficile · ${m}28", fausse.texte)
        assertEquals("Difficile. Elo, moins 28.", fausse.lue)
        val zero = correction(EloQuestion(4, 0))!!
        assertEquals("Difficile · 0", zero.texte)
        assertEquals("Difficile. Elo, 0.", zero.lue)
    }

    @Test
    fun `correction sans reponse ou hors delai, la perte annoncee, meme ligne que fausse`() {
        // Le serveur renvoie la variation appliquée : la perte déjà comptée pendant le chrono.
        assertEquals("Difficile · ${m}28", correction(EloQuestion(4, -28))!!.texte)
    }

    @Test
    fun `correction deja vue ou sans niveau`() {
        assertEquals("Difficile · Déjà vue$nb: sans effet sur l’Elo", correction(EloQuestion(4, null))!!.texte)
        assertEquals("Difficile. Déjà vue$nb: sans effet sur l’Elo.", correction(EloQuestion(4, null))!!.lue)
        assertEquals("Sans effet sur l’Elo", correction(EloQuestion(null, null))!!.texte)
    }

    @Test
    fun `enjeu ou elo absent, pas de ligne`() {
        assertNull(question(null))
        assertNull(correction(null))
    }

    @Test
    fun `niveau hors de 1 a 5, la ligne sans niveau`() {
        for (n in listOf(0, 6, -1, 99)) {
            assertEquals("Réussir +12 · Rater ${m}28", question(Enjeu(n, 12, 28))!!.texte)
            assertEquals("+12", correction(EloQuestion(n, 12))!!.texte)
            assertEquals("Sans effet sur l’Elo", question(Enjeu(n, null, null))!!.texte)
        }
    }

    @Test
    fun `chiffres illisibles, la ligne sans chiffres, jamais Deja vue`() {
        assertEquals("Difficile", question(Enjeu(4, null, null, illisible = true))!!.texte)
        assertEquals("Difficile.", question(Enjeu(4, null, null, illisible = true))!!.lue)
        assertEquals("Difficile", correction(EloQuestion(4, null, illisible = true))!!.texte)
        // Sans niveau non plus : rien d'honnête à dire, donc pas de ligne.
        assertNull(question(Enjeu(null, null, null, illisible = true)))
        assertNull(correction(EloQuestion(null, null, illisible = true)))
    }
}

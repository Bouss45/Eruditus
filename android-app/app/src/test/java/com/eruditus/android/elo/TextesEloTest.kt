package com.eruditus.android.elo

import com.eruditus.android.PlurielsTest.Companion.NB
import com.eruditus.android.R
import com.eruditus.android.TextesRessources
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Textes de l'Elo tels que le téléphone les affiche et les lit (spec/ecrans.md § 8, § 10, § 11,
 * D112, D115) : lus dans `strings.xml` avec les pluriels d'Android, comme D72.
 */
class TextesEloTest {

    private val t = TextesRessources()
    private val moins = "−"

    @Test
    fun `variation, le signe seul, le vrai signe moins`() {
        assertEquals("+14", TextesElo.variation(t, 14, revision = false))
        assertEquals("${moins}8", TextesElo.variation(t, -8, revision = false))
        assertEquals("+1", TextesElo.variation(t, 1, revision = false))
        assertEquals("${moins}120", TextesElo.variation(t, -120, revision = false))
    }

    @Test
    fun `variation nulle, Elo inchange, jamais +0`() {
        assertEquals("Elo inchangé", TextesElo.variation(t, 0, revision = false))
    }

    @Test
    fun `revision, sans effet sur l'Elo, a la place de toute variation`() {
        val revision = "Révision${NB}: sans effet sur l’Elo"
        assertEquals(revision, TextesElo.variation(t, 0, revision = true))
        assertEquals(revision, TextesElo.variation(t, 14, revision = true))
    }

    @Test
    fun `TalkBack lit la variation, plus 14, moins 8`() {
        assertEquals("Elo, plus 14", TextesElo.variationLue(t, 14, revision = false))
        assertEquals("Elo, moins 8", TextesElo.variationLue(t, -8, revision = false))
        assertEquals("Elo inchangé", TextesElo.variationLue(t, 0, revision = false))
        assertEquals("Révision${NB}: sans effet sur l’Elo", TextesElo.variationLue(t, 14, revision = true))
    }

    @Test
    fun `Elo apres la partie`() {
        assertEquals("Elo${NB}: 1261", TextesElo.apres(t, 1261))
        assertEquals("Elo${NB}: 100", TextesElo.apres(t, 100))
    }

    @Test
    fun `rang de l'Accueil, memes accords que le Classement`() {
        assertEquals("3e sur 18", TextesElo.rang(t, 3, 18))
        assertEquals("1er sur 18", TextesElo.rang(t, 1, 18))
        assertEquals("2e sur 2", TextesElo.rang(t, 2, 2))
        assertEquals("11e sur 48", TextesElo.rang(t, 11, 48))
    }

    @Test
    fun `phrase provisoire, encore 1 reponse, encore 12 reponses`() {
        assertEquals("Elo provisoire${NB}: encore 1 réponse avant d’entrer au classement.", TextesElo.provisoire(t, 1))
        assertEquals("Elo provisoire${NB}: encore 12 réponses avant d’entrer au classement.", TextesElo.provisoire(t, 12))
        assertEquals("Elo provisoire${NB}: encore 30 réponses avant d’entrer au classement.", TextesElo.provisoire(t, 30))
    }

    @Test
    fun `banque epuisee`() {
        assertEquals("Tu as vu toutes les questions${NB}: tes parties sont des révisions, sans effet sur l’Elo.", TextesElo.banqueEpuisee(t))
    }

    @Test
    fun `verdict sans point, Bonne reponse seul (D116)`() {
        assertEquals("Bonne réponse\u202F!", t.texte(R.string.partie_bonne_reponse))
    }

    @Test
    fun `plus aucun point ni score dans les textes affiches (D108, D116)`() {
        val ecrans = Regex("^(accueil|classement|partie|resultat|elo)_")
        val tous = t.chaines.filterKeys(ecrans::containsMatchIn).values + t.pluriels.filterKeys(ecrans::containsMatchIn).values.flatMap { it.values }
        assertEquals(emptyList<String>(), tous.filter { Regex("point|score", RegexOption.IGNORE_CASE).containsMatchIn(it) })
    }
}

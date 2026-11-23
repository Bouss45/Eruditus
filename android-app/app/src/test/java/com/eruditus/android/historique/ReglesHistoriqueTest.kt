package com.eruditus.android.historique

import com.eruditus.android.donnees.api.DetailQuestion
import com.eruditus.android.donnees.api.EloQuestion
import com.eruditus.android.donnees.api.QuestionRelue
import com.eruditus.android.donnees.api.StatutQuestion
import com.eruditus.android.ui.designsystem.components.ArriveeBonne
import com.eruditus.android.ui.designsystem.components.EtatChoix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Ce que le détail d'une partie montre de chaque question (spec/ecrans.md § 17, D132, D134), selon
 * `statut` et `detail` : les états des quatre choix (✓ sur la bonne réponse, ✗ sur le choix du
 * joueur s'il est faux), le verdict, l'explication et la source, ou le verdict seul.
 */
class ReglesHistoriqueTest {

    private val choix = listOf("René Magritte", "Max Ernst", "Salvador Dalí", "Joan Miró")
    private val neutre = EtatChoix.Neutre
    private val bonne = EtatChoix.Bonne(ArriveeBonne.Choisie)
    private val mauvaise = EtatChoix.Mauvaise

    private fun complet(choixJoueur: Int?, bonneReponse: Int? = 2, explication: String? = "Dalí, en 1931.", source: String? = "MoMA") =
        DetailQuestion.Complet("Qui a peint La Persistance de la mémoire ?", choix, choixJoueur, bonneReponse, explication, source)

    private fun vue(statut: StatutQuestion?, detail: DetailQuestion, elo: EloQuestion = EloQuestion(4, 12), numero: Int = 2) =
        ReglesHistorique.vue(QuestionRelue(numero, statut, elo, detail))

    private fun complet(v: VueQuestion): CorpsQuestion.Complet = v.corps as CorpsQuestion.Complet

    @Test
    fun `juste, la bonne reponse en vert, aucun rouge, explication et source`() {
        val v = vue(StatutQuestion.Juste, complet(choixJoueur = 2))
        assertEquals(2, v.numero)
        assertEquals(EloQuestion(4, 12), v.enjeu)
        assertEquals(CorpsQuestion.Complet("Qui a peint La Persistance de la mémoire ?", listOf(
            ChoixRelu("René Magritte", neutre), ChoixRelu("Max Ernst", neutre), ChoixRelu("Salvador Dalí", bonne), ChoixRelu("Joan Miró", neutre),
        ), VerdictRelu.Bonne, "Dalí, en 1931.", "MoMA"), v.corps)
    }

    @Test
    fun `fausse, le choix du joueur en rouge et la bonne reponse en vert`() {
        val c = complet(vue(StatutQuestion.Fausse, complet(choixJoueur = 0)))
        assertEquals(listOf(mauvaise, neutre, bonne, neutre), c.choix.map { it.etat })
        assertEquals(VerdictRelu.Fausse, c.verdict)
    }

    @Test
    fun `sans reponse, la bonne reponse en vert seulement, temps ecoule`() {
        val c = complet(vue(StatutQuestion.SansReponse, complet(choixJoueur = null, bonneReponse = 1)))
        assertEquals(listOf(neutre, bonne, neutre, neutre), c.choix.map { it.etat })
        assertEquals(VerdictRelu.TempsEcoule, c.verdict)
    }

    @Test
    fun `hors delai, le choix tardif en rouge, temps ecoule`() {
        val c = complet(vue(StatutQuestion.HorsDelai, complet(choixJoueur = 1)))
        assertEquals(listOf(neutre, mauvaise, bonne, neutre), c.choix.map { it.etat })
        assertEquals(VerdictRelu.TempsEcoule, c.verdict)
    }

    @Test
    fun `non repondue, les choix neutres, ni coche, ni explication, ni source`() {
        val c = complet(vue(StatutQuestion.NonRepondue, complet(choixJoueur = null, bonneReponse = null, explication = null, source = null)))
        assertEquals(List(4) { neutre }, c.choix.map { it.etat })
        assertEquals(VerdictRelu.NonRepondue, c.verdict)
        assertNull(c.explication)
        assertNull(c.source)
    }

    @Test
    fun `non repondue, meme si un serveur envoyait la reponse, rien n'est revele`() {
        val c = complet(vue(StatutQuestion.NonRepondue, complet(choixJoueur = 1, bonneReponse = 2)))
        assertEquals(List(4) { neutre }, c.choix.map { it.etat })
        assertNull(c.explication)
        assertNull(c.source)
    }

    @Test
    fun `sans explication ni source, rien a afficher dessous`() {
        val c = complet(vue(StatutQuestion.Juste, complet(choixJoueur = 2, explication = null, source = null)))
        assertNull(c.explication)
        assertNull(c.source)
        // Une explication blanche n'est pas une explication.
        assertNull(complet(vue(StatutQuestion.Juste, complet(choixJoueur = 2, explication = "  ", source = ""))).explication)
    }

    @Test
    fun `une explication sans source, ou une source sans explication`() {
        assertEquals("Dalí, en 1931." to null, complet(vue(StatutQuestion.Juste, complet(2, source = null))).let { it.explication to it.source })
        assertEquals(null to "MoMA", complet(vue(StatutQuestion.Juste, complet(2, explication = null))).let { it.explication to it.source })
    }

    @Test
    fun `sans effet sur l'Elo, la ligne d'enjeu garde son niveau`() {
        val v = vue(StatutQuestion.Juste, complet(2), elo = EloQuestion(4, null))
        assertEquals(EloQuestion(4, null), v.enjeu)
    }

    @Test
    fun `modifiee et retiree, le verdict seulement et la note`() {
        assertEquals(
            CorpsQuestion.VerdictSeul(VerdictRelu.Fausse, DetailQuestion.Modifiee),
            vue(StatutQuestion.Fausse, DetailQuestion.Modifiee).corps,
        )
        assertEquals(
            CorpsQuestion.VerdictSeul(VerdictRelu.Bonne, DetailQuestion.Retiree),
            vue(StatutQuestion.Juste, DetailQuestion.Retiree).corps,
        )
        assertEquals(CorpsQuestion.VerdictSeul(VerdictRelu.TempsEcoule, DetailQuestion.Modifiee), vue(StatutQuestion.HorsDelai, DetailQuestion.Modifiee).corps)
        assertEquals(CorpsQuestion.VerdictSeul(VerdictRelu.TempsEcoule, DetailQuestion.Retiree), vue(StatutQuestion.SansReponse, DetailQuestion.Retiree).corps)
        assertEquals(CorpsQuestion.VerdictSeul(VerdictRelu.NonRepondue, DetailQuestion.Modifiee), vue(StatutQuestion.NonRepondue, DetailQuestion.Modifiee).corps)
    }

    @Test
    fun `modifiee et retiree gardent la ligne d'enjeu`() {
        assertEquals(EloQuestion(4, 12), vue(StatutQuestion.Fausse, DetailQuestion.Modifiee).enjeu)
    }

    @Test
    fun `statut ou detail inconnu, seulement le numero et detail indisponible, sans ligne d'enjeu`() {
        for (v in listOf(vue(null, DetailQuestion.Indisponible), vue(StatutQuestion.Juste, DetailQuestion.Indisponible), vue(null, complet(2)))) {
            assertEquals(CorpsQuestion.Indisponible, v.corps)
            assertNull(v.enjeu)
            assertEquals(2, v.numero)
        }
    }

    @Test
    fun `les questions gardent l'ordre du serveur, aucun tri`() {
        val q = { n: Int -> QuestionRelue(n, StatutQuestion.Juste, EloQuestion(null, null), DetailQuestion.Modifiee) }
        assertEquals(listOf(3, 1, 2), ReglesHistorique.vues(listOf(q(3), q(1), q(2))).map { it.numero })
    }
}

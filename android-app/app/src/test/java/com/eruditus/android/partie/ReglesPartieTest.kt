package com.eruditus.android.partie

import com.eruditus.android.donnees.api.Correction
import com.eruditus.android.donnees.api.Raison
import com.eruditus.android.ui.designsystem.components.ArriveeBonne
import com.eruditus.android.ui.designsystem.components.EtatChoix
import com.eruditus.android.ui.designsystem.components.EtatChoix.Bonne
import com.eruditus.android.ui.designsystem.components.EtatChoix.Mauvaise
import com.eruditus.android.ui.designsystem.components.EtatChoix.Verrouille
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Traduction de la correction (spec/ecrans.md § 9, D67) : le verdict et son détail selon `raison`,
 * avec un repli pour une raison inconnue (D22) ; les états des 4 choix selon `correcte`, `choix`
 * et `bonneReponse`, qui donnent le mouvement et la vibration (DESIGN.md § 6).
 */
class ReglesPartieTest {

    private fun correction(
        choix: Int?,
        bonne: Int,
        raison: Raison,
        correcte: Boolean = raison == Raison.Gagne || raison == Raison.DejaReussie,
        point: Int = if (raison == Raison.Gagne) 1 else 0,
    ) = Correction(1, choix, correcte, bonne, raison == Raison.HorsDelai, point, raison, score = 12)

    private fun lire(c: Correction) = ReglesPartie.lire(c)

    @Test
    fun `gagne, bonne reponse +1 point, sans detail, la bonne choisie`() {
        val l = lire(correction(choix = 2, bonne = 2, raison = Raison.Gagne))
        assertEquals(Verdict.Gagne, l.verdict)
        assertEquals(null, l.lettreDetail)
        assertEquals(listOf(Verrouille, Verrouille, Bonne(ArriveeBonne.Choisie), Verrouille), l.etats)
    }

    @Test
    fun `deja reussie, comme gagne mais sans point`() {
        val l = lire(correction(choix = 0, bonne = 0, raison = Raison.DejaReussie))
        assertEquals(Verdict.DejaReussie, l.verdict)
        assertEquals(null, l.lettreDetail)
        assertEquals(listOf(Bonne(ArriveeBonne.Choisie), Verrouille, Verrouille, Verrouille), l.etats)
    }

    @Test
    fun `fausse, mauvaise reponse, la bonne etait C, revelee apres le non`() {
        val l = lire(correction(choix = 0, bonne = 2, raison = Raison.Fausse))
        assertEquals(Verdict.Fausse, l.verdict)
        assertEquals('C', l.lettreDetail)
        assertEquals(listOf(Mauvaise, Verrouille, Bonne(ArriveeBonne.ApresErreur), Verrouille), l.etats)
    }

    @Test
    fun `sans reponse, temps ecoule, la bonne en fondu`() {
        val l = lire(correction(choix = null, bonne = 3, raison = Raison.SansReponse))
        assertEquals(Verdict.TempsEcoule, l.verdict)
        assertEquals('D', l.lettreDetail)
        assertEquals(listOf(Verrouille, Verrouille, Verrouille, Bonne(ArriveeBonne.TempsEcoule)), l.etats)
    }

    @Test
    fun `hors delai sans choix, comme sans reponse`() {
        val l = lire(correction(choix = null, bonne = 1, raison = Raison.HorsDelai))
        assertEquals(Verdict.TempsEcoule, l.verdict)
        assertEquals('B', l.lettreDetail)
        assertEquals(listOf(Verrouille, Bonne(ArriveeBonne.TempsEcoule), Verrouille, Verrouille), l.etats)
    }

    @Test
    fun `hors delai sur la bonne reponse, temps ecoule, la bonne en fondu, pas de rouge`() {
        val l = lire(correction(choix = 1, bonne = 1, raison = Raison.HorsDelai))
        assertEquals(Verdict.TempsEcoule, l.verdict)
        assertEquals('B', l.lettreDetail)
        assertEquals(listOf(Verrouille, Bonne(ArriveeBonne.TempsEcoule), Verrouille, Verrouille), l.etats)
    }

    @Test
    fun `hors delai sur un autre choix, temps ecoule, mais rouge et non comme une erreur`() {
        val l = lire(correction(choix = 3, bonne = 0, raison = Raison.HorsDelai))
        assertEquals(Verdict.TempsEcoule, l.verdict)
        assertEquals('A', l.lettreDetail)
        assertEquals(listOf(Bonne(ArriveeBonne.ApresErreur), Verrouille, Verrouille, Mauvaise), l.etats)
    }

    @Test
    fun `raison inconnue correcte avec un point, bonne reponse +1 point`() {
        val l = lire(correction(choix = 2, bonne = 2, raison = Raison.Inconnue, correcte = true, point = 1))
        assertEquals(Verdict.Gagne, l.verdict)
        assertEquals(null, l.lettreDetail)
        assertEquals(listOf(Verrouille, Verrouille, Bonne(ArriveeBonne.Choisie), Verrouille), l.etats)
    }

    @Test
    fun `raison inconnue correcte sans point, bonne reponse seule`() {
        val l = lire(correction(choix = 2, bonne = 2, raison = Raison.Inconnue, correcte = true, point = 0))
        assertEquals(Verdict.BonneReponse, l.verdict)
        assertEquals(null, l.lettreDetail)
    }

    @Test
    fun `raison inconnue fausse, mauvaise reponse et la bonne reponse etait X`() {
        val l = lire(correction(choix = 1, bonne = 3, raison = Raison.Inconnue, correcte = false))
        assertEquals(Verdict.Fausse, l.verdict)
        assertEquals('D', l.lettreDetail)
        assertEquals(listOf(Verrouille, Mauvaise, Verrouille, Bonne(ArriveeBonne.ApresErreur)), l.etats)
        val sansChoix = lire(correction(choix = null, bonne = 0, raison = Raison.Inconnue, correcte = false))
        assertEquals(Verdict.Fausse, sansChoix.verdict)
        assertEquals('A', sansChoix.lettreDetail)
    }

    @Test
    fun `une seule bonne reponse verte, au plus un choix rouge`() {
        for (raison in Raison.entries) {
            for (bonne in 0..3) {
                for (choix in listOf(null, 0, 1, 2, 3)) {
                    val correcte = choix == bonne && raison in listOf(Raison.Gagne, Raison.DejaReussie, Raison.Inconnue)
                    val etats = lire(correction(choix, bonne, raison, correcte = correcte)).etats
                    assertEquals(4, etats.size)
                    assertEquals("$raison $bonne $choix", 1, etats.count { it is Bonne })
                    assertEquals("$raison $bonne $choix", etats[bonne] is Bonne, true)
                    assertEquals("$raison $bonne $choix", if (choix != null && choix != bonne) 1 else 0, etats.count { it == Mauvaise })
                    assertEquals(4, etats.count { it is Bonne || it == Mauvaise || it == EtatChoix.Verrouille })
                }
            }
        }
    }
}

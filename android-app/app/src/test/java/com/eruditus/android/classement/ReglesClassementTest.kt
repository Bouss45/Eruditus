package com.eruditus.android.classement

import com.eruditus.android.donnees.api.Classement
import com.eruditus.android.donnees.api.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce que montre l'écran Classement (spec/ecrans.md § 11, spec/regles.md § 4, D72) : rangs, ordre
 * et scores du serveur tels quels ; ma ligne reconnue à mon pseudo, dans le top 10 ou sous le
 * séparateur ; pas encore classé ; personne de classé.
 */
class ReglesClassementTest {

    private val pseudos = listOf(
        "Hypatie", "LeBaronPerche", "MarieCurieDuDimanche", "Albatros", "tartine42",
        "Vercingetorix", "Zoe_L", "Quiz_Kid", "Penelope", "LaChouette",
    )

    /** Un top 10 sans ex æquo, rangs 1 à 10, scores 19 à 10 ; [moi] remplace le pseudo de ce rang. */
    private fun top(moi: Int? = null, pseudo: String = "Hibou_42") = pseudos.mapIndexed { i, p ->
        Place(rang = i + 1, pseudo = if (moi == i + 1) pseudo else p, score = 19 - i)
    }

    private fun liste(c: Classement): VueClassement.Liste =
        ReglesClassement.vue(c) as? VueClassement.Liste ?: throw AssertionError("une liste attendue : ${ReglesClassement.vue(c)}")

    private fun VueClassement.Liste.marquees() = top.filter { it.moi }

    // Ex æquo (spec/regles.md § 4) ----------------------------------------------------------------

    @Test
    fun `ex aequo, rangs et ordre du serveur gardes tels quels, 1 2 2 4`() {
        val recu = listOf(
            Place(1, "alice", 5), Place(2, "bernard", 3), Place(2, "Bob", 3), Place(4, "Dora", 2),
            Place(5, "eve", 1), Place(5, "Fred", 1), Place(5, "gina", 1), Place(5, "Hugo", 1),
            Place(5, "ines", 1), Place(5, "Jules", 1),
        )
        val vue = liste(Classement(recu, moi = Place(1, "alice", 5), classes = 13))
        assertEquals(recu.map { it.rang }, vue.top.map { it.rang })
        assertEquals(recu.map { it.pseudo }, vue.top.map { it.pseudo })
        assertEquals(recu.map { it.score }, vue.top.map { it.score })
    }

    @Test
    fun `ex aequo au meme rang que moi, seule ma ligne est marquee, par le pseudo`() {
        val recu = listOf(Place(1, "Hypatie", 19), Place(2, "bernard", 12), Place(2, "Hibou_42", 12), Place(4, "Dora", 9))
        val vue = liste(Classement(recu, moi = Place(2, "Hibou_42", 12), classes = 4))
        assertEquals(listOf(false, false, true, false), vue.top.map { it.moi })
        assertNull(vue.maLigne)
    }

    @Test
    fun `ex aequo de la 10e ligne coupe par le top 10, sous le separateur avec son rang`() {
        // Comme le test serveur : Zoe a 1 point, rang 5, mais 6 autres ex æquo la précèdent dans l'ordre alphabétique.
        val recu = listOf(
            Place(1, "alice", 5), Place(2, "bernard", 3), Place(2, "Bob", 3), Place(4, "Dora", 2),
            Place(5, "eve", 1), Place(5, "Fred", 1), Place(5, "gina", 1), Place(5, "Hugo", 1),
            Place(5, "ines", 1), Place(5, "Jules", 1),
        )
        val vue = liste(Classement(recu, moi = Place(5, "Zoe", 1), classes = 13))
        assertTrue(vue.marquees().isEmpty())
        assertEquals(LigneVue(5, "Zoe", 1, moi = true), vue.maLigne)
        assertFalse(vue.pasClasse)
    }

    @Test
    fun `ex aequo a la 10e place coupe, rang 10 sous le separateur`() {
        val recu = top() // la 10e ligne : LaChouette, rang 10, 10 points
        val vue = liste(Classement(recu, moi = Place(10, "Zebre", 10), classes = 48))
        assertTrue(vue.marquees().isEmpty())
        assertEquals(LigneVue(10, "Zebre", 10, moi = true), vue.maLigne)
    }

    // Ma ligne ------------------------------------------------------------------------------------

    @Test
    fun `ma ligne dans le top 10, mise en avant, rien sous le top`() {
        val vue = liste(Classement(top(moi = 3), moi = Place(3, "Hibou_42", 17), classes = 48))
        assertEquals(listOf(LigneVue(3, "Hibou_42", 17, moi = true)), vue.marquees())
        assertEquals(3, vue.top.indexOfFirst { it.moi } + 1)
        assertNull(vue.maLigne)
        assertFalse(vue.pasClasse)
        assertEquals(48, vue.classes)
    }

    @Test
    fun `ma ligne a la 10e place, derniere du top et marquee`() {
        val vue = liste(Classement(top(moi = 10), moi = Place(10, "Hibou_42", 10), classes = 48))
        assertEquals(10, vue.top.size)
        assertTrue(vue.top.last().moi)
        assertEquals(1, vue.marquees().size)
        assertNull(vue.maLigne)
    }

    @Test
    fun `juste apres le top 10, rang 11 sous le separateur`() {
        val vue = liste(Classement(top(), moi = Place(11, "Hibou_42", 9), classes = 48))
        assertEquals(10, vue.top.size)
        assertTrue(vue.marquees().isEmpty())
        assertEquals(LigneVue(11, "Hibou_42", 9, moi = true), vue.maLigne)
    }

    @Test
    fun `loin derriere, rang 27 sous le separateur`() {
        val vue = liste(Classement(top(), moi = Place(27, "Hibou_42", 4), classes = 48))
        assertTrue(vue.marquees().isEmpty())
        assertEquals(LigneVue(27, "Hibou_42", 4, moi = true), vue.maLigne)
        assertFalse(vue.pasClasse)
    }

    @Test
    fun `ma ligne reconnue a mon pseudo sans tenir compte de la casse`() {
        val vue = liste(Classement(top(moi = 4, pseudo = "Hibou_42"), moi = Place(4, "hibou_42", 16), classes = 48))
        assertEquals(4, vue.top.indexOfFirst { it.moi } + 1)
        assertNull(vue.maLigne)
    }

    @Test
    fun `un autre joueur au meme rang que moi ne me marque pas`() {
        // Le rang seul ne suffit pas : moi au rang 3 mais hors des lignes reçues (réponse lue entre deux réponses).
        val vue = liste(Classement(top(), moi = Place(3, "Hibou_42", 17), classes = 48))
        assertTrue(vue.marquees().isEmpty())
        assertEquals(LigneVue(3, "Hibou_42", 17, moi = true), vue.maLigne)
    }

    // Pas classé, personne -------------------------------------------------------------------------

    @Test
    fun `pas encore classe, le top 10 sans ligne marquee, puis l'encadre`() {
        val vue = liste(Classement(top(), moi = null, classes = 48))
        assertEquals(10, vue.top.size)
        assertTrue(vue.marquees().isEmpty())
        assertNull(vue.maLigne)
        assertTrue(vue.pasClasse)
        assertEquals(48, vue.classes)
    }

    @Test
    fun `personne de classe, le texte seul`() {
        assertEquals(VueClassement.Personne, ReglesClassement.vue(Classement(emptyList(), moi = null, classes = 0)))
    }

    @Test
    fun `un seul classe, moi`() {
        val vue = liste(Classement(listOf(Place(1, "Hibou_42", 1)), moi = Place(1, "Hibou_42", 1), classes = 1))
        assertEquals(listOf(LigneVue(1, "Hibou_42", 1, moi = true)), vue.top)
        assertNull(vue.maLigne)
        assertEquals(1, vue.classes)
    }
}

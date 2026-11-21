package com.eruditus.android.classement

import com.eruditus.android.PlurielsTest.Companion.NB
import com.eruditus.android.R
import com.eruditus.android.TextesRessources
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Textes du Classement tels que le téléphone les affiche et les lit (spec/ecrans.md § 11, D72) :
 * accords du rang, des points et du pied, lus dans `strings.xml` avec les pluriels d'Android ;
 * chaque ligne lue d'un seul bloc par TalkBack ; textes fixes de la spec.
 */
class TextesClassementTest {

    private val t = TextesRessources()

    @Test
    fun `rang, 1er puis 2e, 3e, 10e, 27e`() {
        assertEquals("1er", TextesClassement.rang(t, 1))
        assertEquals("2e", TextesClassement.rang(t, 2))
        assertEquals("3e", TextesClassement.rang(t, 3))
        assertEquals("10e", TextesClassement.rang(t, 10))
        assertEquals("11e", TextesClassement.rang(t, 11))
        assertEquals("21e", TextesClassement.rang(t, 21))
        assertEquals("27e", TextesClassement.rang(t, 27))
    }

    @Test
    fun `points de la ligne, le nombre puis son unite, 1 point, 2 points`() {
        assertEquals("1", TextesClassement.nombre(1))
        assertEquals("${NB}point", TextesClassement.unite(t, 1))
        assertEquals("${NB}points", TextesClassement.unite(t, 2))
        assertEquals("${NB}points", TextesClassement.unite(t, 19))
    }

    @Test
    fun `pied, 1 joueur classe, 2 joueurs classes, 48 joueurs classes`() {
        assertEquals("1 joueur classé", TextesClassement.pied(t, 1))
        assertEquals("2 joueurs classés", TextesClassement.pied(t, 2))
        assertEquals("48 joueurs classés", TextesClassement.pied(t, 48))
    }

    @Test
    fun `TalkBack lit chaque ligne d'un seul bloc`() {
        assertEquals("rang 27, Hibou_42, 4${NB}points, toi", TextesClassement.description(t, LigneVue(27, "Hibou_42", 4, moi = true)))
        assertEquals("rang 1, Hypatie, 19${NB}points", TextesClassement.description(t, LigneVue(1, "Hypatie", 19, moi = false)))
        assertEquals("rang 2, Bob, 1${NB}point", TextesClassement.description(t, LigneVue(2, "Bob", 1, moi = false)))
        assertEquals("rang 3, Hibou_42, 1${NB}point, toi", TextesClassement.description(t, LigneVue(3, "Hibou_42", 1, moi = true)))
    }

    @Test
    fun `textes fixes de la spec`() {
        assertEquals("Classement", t.texte(R.string.accueil_classement))
        assertEquals("Classement", t.texte(R.string.classement_titre))
        assertEquals("Top 10", t.texte(R.string.classement_top))
        assertEquals("Points", t.texte(R.string.classement_points))
        assertEquals("Réussis une question pour entrer au classement.", t.texte(R.string.classement_pas_classe))
        assertEquals("Personne n’est encore classé. Lance une partie !", t.texte(R.string.classement_personne))
    }
}

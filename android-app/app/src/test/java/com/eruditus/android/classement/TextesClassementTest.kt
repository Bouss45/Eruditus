package com.eruditus.android.classement

import com.eruditus.android.R
import com.eruditus.android.TextesRessources
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Textes du Classement tels que le téléphone les affiche et les lit (spec/ecrans.md § 11, D72) :
 * accords du rang et du pied, lus dans `strings.xml` avec les pluriels d'Android ;
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
    fun `Elo de la ligne, le nombre seul, sans unite ni espace des milliers`() {
        assertEquals("1180", TextesClassement.nombre(1180))
        assertEquals("2140", TextesClassement.nombre(2140))
        assertEquals("100", TextesClassement.nombre(100))
    }

    @Test
    fun `pied, 1 joueur classe, 2 joueurs classes, 48 joueurs classes`() {
        assertEquals("1 joueur classé", TextesClassement.pied(t, 1))
        assertEquals("2 joueurs classés", TextesClassement.pied(t, 2))
        assertEquals("48 joueurs classés", TextesClassement.pied(t, 48))
    }

    @Test
    fun `TalkBack lit chaque ligne d'un seul bloc`() {
        assertEquals("rang 27, Hibou_42, Elo 1180, toi", TextesClassement.description(t, LigneVue(27, "Hibou_42", 1180, moi = true)))
        assertEquals("rang 1, Hypatie, Elo 2140", TextesClassement.description(t, LigneVue(1, "Hypatie", 2140, moi = false)))
        assertEquals("rang 2, Bob, Elo 1000", TextesClassement.description(t, LigneVue(2, "Bob", 1000, moi = false)))
    }

    @Test
    fun `textes fixes de la spec`() {
        assertEquals("Classement", t.texte(R.string.accueil_classement))
        assertEquals("Classement", t.texte(R.string.classement_titre))
        assertEquals("Top 10", t.texte(R.string.classement_top))
        assertEquals("Elo", t.texte(R.string.classement_elo))
        assertEquals("Personne n’est encore classé. Lance une partie !", t.texte(R.string.classement_personne))
    }
}

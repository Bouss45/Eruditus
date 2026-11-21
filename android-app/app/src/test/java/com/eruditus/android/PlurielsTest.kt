package com.eruditus.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les pluriels des tests sont ceux du téléphone (SPEC D72). Android choisit la catégorie avec
 * `android.icu.text.PluralRules.forLocale(locale).select(n)` (`ResourcesImpl`, d'Android 8 à
 * aujourd'hui), puis `other` si la ressource ne l'a pas : `TextesRessources` fait de même avec
 * ICU4J. En français (CLDR) : 0 et 1 au singulier ; `many` pour les millions, seulement depuis
 * CLDR 38 (Android 12), d'où son texte identique à `other` pour que toutes les versions disent la
 * même chose.
 */
class PlurielsTest {

    private val textes = TextesRessources()

    @Test
    fun `regle du francais, 0 et 1 au singulier, many pour les millions`() {
        assertEquals("one", TextesRessources.categorie(0))
        assertEquals("one", TextesRessources.categorie(1))
        for (n in listOf(2, 3, 10, 11, 21, 48, 100, 999_999)) assertEquals("$n", "other", TextesRessources.categorie(n))
        assertEquals("many", TextesRessources.categorie(1_000_000))
        assertEquals("many", TextesRessources.categorie(2_000_000))
    }

    @Test
    fun `0 point, 1 point, 2 points`() {
        assertEquals("0${NB}point", textes.pluriel(R.plurals.accueil_points, 0, 0))
        assertEquals("1${NB}point", textes.pluriel(R.plurals.accueil_points, 1, 1))
        assertEquals("2${NB}points", textes.pluriel(R.plurals.accueil_points, 2, 2))
    }

    @Test
    fun `chaque pluriel a one et other, et many dit comme other`() {
        assertTrue(textes.pluriels.isNotEmpty())
        for ((nom, items) in textes.pluriels) {
            assertTrue("$nom : one", "one" in items)
            assertTrue("$nom : other", "other" in items)
            items["many"]?.let { assertEquals("$nom : many comme other (Android 8 à 11 n'ont pas many)", items["other"], it) }
            assertTrue("$nom : catégories du français seulement", items.keys.all { it in setOf("one", "many", "other") })
        }
    }

    companion object {
        /** Espace insécable entre un nombre et son unité (`strings.xml`). */
        const val NB = " "
    }
}

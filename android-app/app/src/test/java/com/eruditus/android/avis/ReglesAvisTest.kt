package com.eruditus.android.avis

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La règle du texte d'un avis est **exactement** celle des hooks (D86, spec/regles.md § 7.1) :
 * les cas de `server/tests/cas-avis.json`, que les tests serveur jouent aussi à travers la route,
 * et les constantes relues dans `avis.js`, qui fait foi. Un changement de l'un ou de l'autre
 * relance ce test sur la PR (D47, D86).
 */
class ReglesAvisTest {

    private val depot = File(requireNotNull(System.getProperty("eruditus.depot")))
    private val avisJs: String by lazy { File(depot, "server/pb_hooks/eruditus/avis.js").readText() }

    private val cas: List<JsonObject> by lazy {
        Json.parseToJsonElement(File(depot, "server/tests/cas-avis.json").readText())
            .jsonObject.getValue("cas").jsonArray.map { it.jsonObject }
    }

    /** Une entrée du fichier : un texte, ou des morceaux (texte, ou { repete, fois }). */
    private fun deplier(element: JsonElement): String = when (element) {
        is JsonPrimitive -> element.content
        is JsonArray -> element.joinToString("") { morceau ->
            if (morceau is JsonPrimitive) {
                morceau.content
            } else {
                val m = morceau.jsonObject
                m.getValue("repete").jsonPrimitive.content.repeat(m.getValue("fois").jsonPrimitive.int)
            }
        }
        else -> error("entrée inattendue : $element")
    }

    @Test
    fun `les cas partages avec le serveur`() {
        assertTrue("trop peu de cas : ${cas.size}", cas.size >= 30)
        for (c in cas) {
            val nom = c.getValue("cas").jsonPrimitive.content
            val entree = deplier(c.getValue("entree"))
            val texte = ReglesAvis.normaliser(entree)
            if (c["refus"]?.jsonPrimitive?.boolean == true) {
                assertFalse("« $nom » devrait être refusé", ReglesAvis.valide(entree))
            } else {
                assertEquals("« $nom »", deplier(c.getValue("attendu")), texte)
                assertEquals("« $nom » : longueur", c.getValue("longueur").jsonPrimitive.int, ReglesAvis.longueur(texte))
                assertTrue("« $nom » devrait être accepté", ReglesAvis.valide(entree))
            }
        }
    }

    @Test
    fun `la regle est stable, l'appliquer deux fois ne change rien`() {
        for (c in cas) {
            val une = ReglesAvis.normaliser(deplier(c.getValue("entree")))
            assertEquals(c.getValue("cas").jsonPrimitive.content, une, ReglesAvis.normaliser(une))
        }
    }

    @Test
    fun `longueur maximale relue dans les hooks`() {
        val trouve = Regex("""const LONGUEUR_MAX = (\d+)""").find(avisJs)
        assertTrue("avis.js ne contient plus LONGUEUR_MAX", trouve != null)
        assertEquals(trouve!!.groupValues[1].toInt(), ReglesAvis.MAX)
        assertEquals(1000, ReglesAvis.MAX)
    }

    @Test
    fun `controles et blancs relus dans les hooks`() {
        val controles = Regex("""const CONTROLES = /\[([^\]]+)\]/g""").find(avisJs)
        assertTrue("avis.js ne contient plus CONTROLES", controles != null)
        assertEquals(deplier(controles!!.groupValues[1]), ReglesAvis.CONTROLES)
        val blancs = Regex("""const BLANCS = "\[([^\]]+)\]"""").find(avisJs)
        assertTrue("avis.js ne contient plus BLANCS", blancs != null)
        assertEquals(deplier(blancs!!.groupValues[1].replace("\\\\", "\\")), ReglesAvis.BLANCS)
        // Ceux de trim() en JavaScript, hors contrôles (retirés avant) : spec/regles.md § 7.1.
        val attendus = setOf(' ', '\n', ' ', ' ', ' ', ' ', ' ', ' ', '　', '﻿') +
            (' '..' ').toSet()
        assertEquals(attendus, ReglesAvis.BLANCS)
        assertEquals(('\u0000'..'\u001F').toSet() - '\n' + ('\u007F'..'\u009F').toSet(), ReglesAvis.CONTROLES)
    }

    /**
     * Classe de caractères d'une expression régulière JavaScript, dépliée : `\uXXXX`, `\n`, un
     * caractère seul, et les plages `a-b`.
     */
    private fun deplier(classe: String): Set<Char> {
        val unites = mutableListOf<Char>()
        var i = 0
        while (i < classe.length) {
            val c = classe[i]
            when {
                c == '\\' && classe[i + 1] == 'u' -> { unites += classe.substring(i + 2, i + 6).toInt(16).toChar(); i += 6 }
                c == '\\' && classe[i + 1] == 'n' -> { unites += '\n'; i += 2 }
                c == '-' && unites.isNotEmpty() && i + 1 < classe.length -> { unites += '￿'; i++ } // marque de plage
                else -> { unites += c; i++ }
            }
        }
        val resultat = mutableSetOf<Char>()
        var j = 0
        while (j < unites.size) {
            if (j + 2 < unites.size && unites[j + 1] == '￿') {
                resultat += (unites[j]..unites[j + 2]).toSet()
                j += 3
            } else {
                resultat += unites[j]
                j++
            }
        }
        return resultat
    }

    @Test
    fun `les reperes de la spec`() {
        assertEquals("Ligne 1\nLigne 2", ReglesAvis.normaliser(" Ligne 1\r\nLigne 2\t "))
        assertEquals(1, ReglesAvis.longueur("😀"))
        assertEquals(5, ReglesAvis.longueur("👨‍👩‍👧"))
        assertTrue(ReglesAvis.valide("a".repeat(1000)))
        assertFalse(ReglesAvis.valide("a".repeat(1001)))
        assertFalse(ReglesAvis.valide(" \n\t "))
    }
}

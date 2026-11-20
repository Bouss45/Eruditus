package com.eruditus.android.compte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * L'app vérifie le pseudo, le mot de passe et le code avec **exactement** les règles du serveur
 * (D9, D10, D55, spec/regles.md § 1) : ce test les relit dans les hooks, qui font foi. Un
 * changement de `comptes.js` relance ce test sur la PR (D47).
 */
class ReglesServeurTest {

    private val comptes: String by lazy {
        File(System.getProperty("eruditus.depot"), "server/pb_hooks/eruditus/comptes.js").readText()
    }

    private fun capture(motif: String, groupes: Int = 1): List<String> {
        val trouve = Regex(motif).find(comptes)
        assertTrue("comptes.js ne contient plus : $motif", trouve != null)
        return (1..groupes).map { trouve!!.groupValues[it] }
    }

    @Test
    fun `motif du pseudo`() {
        val (motif) = capture("""const MOTIF_PSEUDO = /(.+)/""")
        assertEquals(motif, ReglesPseudo.MOTIF.pattern)
    }

    @Test
    fun `longueurs et octets du mot de passe`() {
        val (min, max, octets) = capture(
            """caracteres >= (\d+) && caracteres <= (\d+) && toBytes\(motDePasse\)\.length <= (\d+)""",
            groupes = 3,
        )
        assertEquals(min.toInt(), ReglesMotDePasse.MIN)
        assertEquals(max.toInt(), ReglesMotDePasse.MAX)
        assertEquals(octets.toInt(), ReglesMotDePasse.OCTETS_MAX)
    }

    @Test
    fun `alphabet et motif du code`() {
        val (alphabet) = capture("""const ALPHABET_CODE = "([0-9A-Z]+)"""")
        assertEquals(alphabet, SaisieCode.ALPHABET)
        val (classe, longueur) = capture("""const MOTIF_CODE = /\^\[([^\]]+)\]\{(\d+)\}\$/""", groupes = 2)
        assertEquals(deplier(classe), SaisieCode.ALPHABET)
        assertEquals(longueur.toInt(), SaisieCode.LONGUEUR)
    }

    /** Classe de caractères d'une expression régulière, ses plages dépliées : `0-9AB` → `0123456789AB`. */
    private fun deplier(classe: String): String =
        Regex(""".-.|.""").findAll(classe).joinToString("") { m ->
            val v = m.value
            if (v.length == 3) (v[0]..v[2]).joinToString("") else v
        }

    @Test
    fun `longueur maximale de la saisie du code`() {
        val (limite) = capture("""if \(saisie\.length > (\d+)\) return null""")
        assertEquals(limite.toInt(), SaisieCode.SAISIE_MAX)
    }

    @Test
    fun `normalisation du code, dans le meme ordre`() {
        // L'app fait les mêmes remplacements dans le même ordre (SaisieCode.normaliser) : majuscules,
        // blancs et tirets retirés, O lu 0, I et L lus 1.
        val attendue = """saisie.toUpperCase().replace(/[\s-]/g, "").replace(/O/g, "0").replace(/[IL]/g, "1")"""
        assertTrue("normalisation du code changée dans comptes.js", comptes.contains("const code = $attendue"))
    }
}

package com.eruditus.android.compte

import com.eruditus.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Le texte de chaque message d'erreur, dans `strings.xml`, est celui du tableau de
 * spec/ecrans.md § 15, qui fait foi. Espaces insécables et apostrophes typographiques des
 * ressources sont ramenés à ceux de la spec avant la comparaison.
 */
class TextesErreursTest {

    private val depot = File(requireNotNull(System.getProperty("eruditus.depot")))

    /** La ligne du tableau § 15 de chaque texte : le début de sa colonne « cas ». */
    private val casDuTableau = mapOf(
        TexteErreur.PseudoInvalide to "`pseudo_invalide`",
        TexteErreur.PseudoReserve to "`pseudo_reserve`",
        TexteErreur.PseudoPris to "`pseudo_pris`",
        TexteErreur.MotDePasseInvalide to "`mot_de_passe_invalide`",
        TexteErreur.ConnexionEchouee to "connexion échouée",
        TexteErreur.CodeIncorrect to "`code_incorrect`",
        TexteErreur.MotDePasseIncorrect to "`mot_de_passe_incorrect`",
        TexteErreur.SessionExpiree to "401",
        TexteErreur.TropDeTentatives to "429",
        TexteErreur.Generique to "`requete_invalide`",
    )

    @Test
    fun `chaque texte d'erreur a sa ligne dans la spec`() {
        assertEquals(TexteErreur.entries.toSet(), casDuTableau.keys)
    }

    @Test
    fun `les textes des ressources sont ceux de la spec`() {
        val tableau = tableauDeLaSpec()
        val ressources = chainesDesRessources()
        for ((texte, cas) in casDuTableau) {
            val attendu = tableau.entries.firstOrNull { it.key.startsWith(cas) }?.value
            assertTrue("ligne « $cas » absente du § 15", attendu != null)
            val nom = nomDeRessource(texte.ressource)
            val reel = ressources[nom]
            assertTrue("chaîne $nom absente de strings.xml", reel != null)
            assertEquals("$texte ($nom)", normaliser(attendu!!), normaliser(reel!!))
        }
    }

    /** Colonne « cas » → texte entre « », pour chaque ligne du tableau du § 15. */
    private fun tableauDeLaSpec(): Map<String, String> {
        val spec = File(depot, "spec/ecrans.md").readText()
        val section = spec.substringAfter("## 15. Messages d'erreur").substringBefore("\n## ")
        return section.lines()
            .filter { it.startsWith("| ") && "«" in it }
            .associate { ligne ->
                val colonnes = ligne.split("|").map { it.trim() }
                colonnes[1] to colonnes[2].substringAfter("« ").substringBefore(" »")
            }
    }

    private fun chainesDesRessources(): Map<String, String> {
        val fichier = File(depot, "android-app/app/src/main/res/values/strings.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(fichier)
        val chaines = document.getElementsByTagName("string")
        return (0 until chaines.length).associate { i ->
            val element = chaines.item(i)
            element.attributes.getNamedItem("name").nodeValue to element.textContent
        }
    }

    private fun nomDeRessource(id: Int): String =
        R.string::class.java.fields.first { it.getInt(null) == id }.name

    private fun normaliser(texte: String) = texte
        .replace(' ', ' ')
        .replace(' ', ' ')
        .replace('’', '\'')
        .replace("\\'", "'")
}

package com.eruditus.android

import com.eruditus.android.ui.ecrans.Textes
import com.ibm.icu.text.PluralRules
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Les textes de l'app comme le téléphone les donne, pour les tests JVM : lus dans `strings.xml`
 * même, mis en forme comme `Resources.getString` (`String.format` en français), et les pluriels
 * choisis comme `Resources.getQuantityString` : catégorie d'ICU pour le français
 * (`PluralRules.forLocale(…).select(n)`, que `ResourcesImpl` d'Android appelle aussi), puis
 * `other` si la ressource n'a pas cette catégorie (SPEC D72).
 */
class TextesRessources : Textes {

    private val fichier = File(requireNotNull(System.getProperty("eruditus.depot")), "android-app/app/src/main/res/values/strings.xml")
    private val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(fichier)

    /** Nom → texte de chaque `<string>`. */
    val chaines: Map<String, String> = elements("string").associate { it.getAttribute("name") to brut(it.textContent) }

    /** Nom → (catégorie → texte) de chaque `<plurals>`. */
    val pluriels: Map<String, Map<String, String>> = elements("plurals").associate { p ->
        val items = p.getElementsByTagName("item")
        p.getAttribute("name") to (0 until items.length).map { items.item(it) as Element }
            .associate { it.getAttribute("quantity") to brut(it.textContent) }
    }

    override fun texte(id: Int, vararg args: Any): String {
        val nom = nom(R.string::class.java, id)
        val texte = requireNotNull(chaines[nom]) { "chaîne $nom absente de strings.xml" }
        return if (args.isEmpty()) texte else String.format(FRANCAIS, texte, *args)
    }

    override fun pluriel(id: Int, n: Int, vararg args: Any): String {
        val nom = nom(R.plurals::class.java, id)
        val items = requireNotNull(pluriels[nom]) { "pluriel $nom absent de strings.xml" }
        val texte = items[categorie(n)] ?: requireNotNull(items["other"]) { "pluriel $nom sans other" }
        return String.format(FRANCAIS, texte, *args)
    }

    private fun elements(balise: String): List<Element> {
        val liste = document.getElementsByTagName(balise)
        return (0 until liste.length).map { liste.item(it) as Element }
    }

    private fun nom(classe: Class<*>, id: Int): String = classe.fields.first { it.getInt(null) == id }.name

    /** Échappements d'aapt utilisés dans nos ressources : `\'`, `\"`, `\n`, `\\`. */
    private fun brut(texte: String): String = texte
        .replace("\\'", "'")
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\\\", "\\")

    companion object {
        val FRANCAIS: Locale = Locale.FRENCH

        /** La catégorie de pluriel que choisit Android pour [n] en français. */
        fun categorie(n: Int): String = PluralRules.forLocale(FRANCAIS).select(n.toDouble())
    }
}

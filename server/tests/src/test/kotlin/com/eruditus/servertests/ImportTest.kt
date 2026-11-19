package com.eruditus.servertests

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** Commande d'import `eruditus-questions` (spec/serveur.md § 8), sur un serveur sans questions. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ImportTest {

    private fun ecrire(contenu: String): Path {
        val fichier = Files.createTempFile(serveur.dossier, "banque", ".json")
        Files.writeString(fichier, contenu)
        return fichier
    }

    private fun importer(questions: JsonArray): ResultatCommande = serveur.importer(ecrire(questions.toString()))

    private fun question(n: Int, modifier: (MutableMap<String, JsonElement>) -> Unit = {}): JsonObject {
        val champs = linkedMapOf<String, JsonElement>(
            "ref" to JsonPrimitive("q-%04d".format(n)),
            "enonce" to JsonPrimitive("Énoncé de la question numéro $n ?"),
            "choix" to buildJsonArray { listOf("Un", "Deux", "Trois", "Quatre").forEach { add(JsonPrimitive("$it $n")) } },
            "bonneReponse" to JsonPrimitive(n % 4),
            "active" to JsonPrimitive(true),
        )
        modifier(champs)
        return JsonObject(champs)
    }

    private fun questionsEnBase(): Map<String, JsonObject> =
        serveur.lister("eruditus_questions").associateBy { it.str("ref") }

    @Test
    fun `a import, reimport inchange, mise a jour par ref`() {
        val six = buildJsonArray { (1..6).forEach { add(question(it)) } }
        val premier = importer(six)
        assertEquals(premier.sortie, 0, premier.code)
        assertTrue(premier.sortie, premier.sortie.contains("6 créées, 0 mises à jour, 0 inchangées"))
        val enBase = questionsEnBase()
        assertEquals(6, enBase.size)
        val q3 = enBase.getValue("q-0003")
        assertEquals("Énoncé de la question numéro 3 ?", q3.str("enonce"))
        assertEquals(4, q3.int("bonne_reponse")) // 3 dans le fichier, de 1 à 4 en base
        assertEquals(true, q3.bool("active"))
        assertEquals(listOf("Un 3", "Deux 3", "Trois 3", "Quatre 3"), q3.tableau("choix").map { it.texte() })
        assertEquals(1, enBase.getValue("q-0004").int("bonne_reponse"))

        val second = importer(six)
        assertEquals(second.sortie, 0, second.code)
        assertTrue(second.sortie, second.sortie.contains("0 créées, 0 mises à jour, 6 inchangées"))

        // Mises à jour par ref ; une question absente du fichier n'est pas touchée.
        val modifie = buildJsonArray {
            add(question(1) { it["enonce"] = JsonPrimitive("Énoncé corrigé de la question 1 ?") })
            add(question(2) { it["bonneReponse"] = JsonPrimitive(3) })
            add(question(3) { it["active"] = JsonPrimitive(false) })
            add(question(4) { it["choix"] = buildJsonArray { listOf("A", "B", "C", "D").forEach { c -> add(JsonPrimitive(c)) } } })
            add(question(5))
            add(question(7))
        }
        val troisieme = importer(modifie)
        assertEquals(troisieme.sortie, 0, troisieme.code)
        assertTrue(troisieme.sortie, troisieme.sortie.contains("1 créées, 4 mises à jour, 1 inchangées"))
        val apres = questionsEnBase()
        assertEquals(7, apres.size)
        assertEquals("Énoncé corrigé de la question 1 ?", apres.getValue("q-0001").str("enonce"))
        assertEquals(4, apres.getValue("q-0002").int("bonne_reponse"))
        assertEquals(false, apres.getValue("q-0003").bool("active"))
        assertEquals(enBase.getValue("q-0006"), apres.getValue("q-0006"))
        assertEquals(enBase.getValue("q-0001").str("id"), apres.getValue("q-0001").str("id"))
    }

    @Test
    fun `b une seule entree invalide et rien n'est importe`() {
        val avant = questionsEnBase()
        val invalides: List<Pair<String, JsonElement>> = listOf(
            "ref avec espace" to question(20) { it["ref"] = JsonPrimitive("q 20") },
            "ref vide" to question(20) { it["ref"] = JsonPrimitive("") },
            "ref trop longue" to question(20) { it["ref"] = JsonPrimitive("q".repeat(51)) },
            "ref nombre" to question(20) { it["ref"] = JsonPrimitive(20) },
            "ref absente" to question(20) { it.remove("ref") },
            "énoncé de 9 caractères" to question(20) { it["enonce"] = JsonPrimitive("Trop cour") },
            "énoncé de 301 caractères" to question(20) { it["enonce"] = JsonPrimitive("a".repeat(301)) },
            "énoncé nombre" to question(20) { it["enonce"] = JsonPrimitive(12) },
            "3 choix" to question(20) { it["choix"] = buildJsonArray { add(JsonPrimitive("A")); add(JsonPrimitive("B")); add(JsonPrimitive("C")) } },
            "5 choix" to question(20) { it["choix"] = buildJsonArray { listOf("A", "B", "C", "D", "E").forEach { c -> add(JsonPrimitive(c)) } } },
            "choix vide" to question(20) { it["choix"] = buildJsonArray { listOf("A", "B", "  ", "D").forEach { c -> add(JsonPrimitive(c)) } } },
            "choix en double" to question(20) { it["choix"] = buildJsonArray { listOf("Paris", "Lyon", " paris ", "Nice").forEach { c -> add(JsonPrimitive(c)) } } },
            "choix trop long" to question(20) { it["choix"] = buildJsonArray { listOf("A", "B", "C", "d".repeat(121)).forEach { c -> add(JsonPrimitive(c)) } } },
            "choix nombre" to question(20) { it["choix"] = buildJsonArray { add(JsonPrimitive("A")); add(JsonPrimitive("B")); add(JsonPrimitive("C")); add(JsonPrimitive(4)) } },
            "choix pas un tableau" to question(20) { it["choix"] = JsonPrimitive("A,B,C,D") },
            "bonneReponse 4" to question(20) { it["bonneReponse"] = JsonPrimitive(4) },
            "bonneReponse -1" to question(20) { it["bonneReponse"] = JsonPrimitive(-1) },
            "bonneReponse 1.5" to question(20) { it["bonneReponse"] = JsonPrimitive(1.5) },
            "bonneReponse texte" to question(20) { it["bonneReponse"] = JsonPrimitive("2") },
            "active absente" to question(20) { it.remove("active") },
            "active texte" to question(20) { it["active"] = JsonPrimitive("true") },
            "champ en plus" to question(20) { it["source"] = JsonPrimitive("Wikipédia") },
            "pas un objet" to JsonPrimitive("question"),
        )
        for ((cas, entree) in invalides) {
            // Une entrée valide nouvelle, une mise à jour valide, puis l'entrée invalide.
            val fichier = buildJsonArray {
                add(question(21))
                add(question(1) { it["enonce"] = JsonPrimitive("Mise à jour qui ne doit pas passer ?") })
                add(entree)
            }
            val r = importer(fichier)
            assertNotEquals("$cas : ${r.sortie}", 0, r.code)
            assertEquals(cas, avant, questionsEnBase())
        }

        val refEnDouble = buildJsonArray { add(question(22)); add(question(22) { it["enonce"] = JsonPrimitive("Autre énoncé pour la même ref ?") }) }
        assertNotEquals(0, importer(refEnDouble).code)
        for (contenu in listOf("pas du json", "{}", "{\"ref\": \"q-1\"}", "", "null")) {
            assertNotEquals(contenu, 0, serveur.importer(ecrire(contenu)).code)
        }
        assertNotEquals(0, serveur.importer(serveur.dossier.resolve("absent.json")).code)
        assertNotEquals(0, serveur.commande("eruditus-questions").code)
        assertEquals(avant, questionsEnBase())
    }

    @Test
    fun `c limites acceptees`() {
        val limites = buildJsonArray {
            add(question(30) { it["ref"] = JsonPrimitive("Q_" + "x".repeat(48)) })
            add(question(31) { it["enonce"] = JsonPrimitive("a".repeat(10)) })
            add(question(32) { it["enonce"] = JsonPrimitive("é".repeat(300)) })
            add(question(33) { it["choix"] = buildJsonArray { listOf("A", "B", "C", "ü".repeat(120)).forEach { c -> add(JsonPrimitive(c)) } } })
            add(question(34) { it["bonneReponse"] = JsonPrimitive(0) })
        }
        val r = importer(limites)
        assertEquals(r.sortie, 0, r.code)
        assertTrue(r.sortie, r.sortie.contains("5 créées"))
        assertEquals(1, questionsEnBase().getValue("q-0034").int("bonne_reponse"))
    }

    companion object {
        private val serveur: Serveur by lazy { Serveur("import", questions = false).demarrer() }

        @AfterClass
        @JvmStatic
        fun arreter() {
            serveur.arreter()
        }
    }
}

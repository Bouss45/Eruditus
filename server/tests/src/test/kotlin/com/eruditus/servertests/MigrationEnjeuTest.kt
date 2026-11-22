package com.eruditus.servertests

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Migration de l'enjeu (D130, T71) sur une base créée sans elle, qui a déjà joueurs, parties et
 * réponses comptées ou non : lignes d'avant intactes (empreinte vide), rien de rejoué ; puis une
 * empreinte par ligne servie, comptée ou non, et une difficulté figée sur une ligne non comptée à
 * niveau ; puis son retour arrière.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MigrationEnjeuTest {

    private fun champs(collection: String): Map<String, JsonObject> =
        serveur.su.get("/api/collections/$collection").obj.tableau("fields").map { it.jsonObject }.associateBy { it.str("name") }

    private fun index(collection: String): List<String> =
        serveur.su.get("/api/collections/$collection").obj.tableau("indexes").map { it.texte()!! }

    private fun creer(collection: String, corps: JsonObject): String {
        val r = serveur.su.post("/api/collections/$collection/records", corps)
        assertEquals("$collection : $r", 200, r.status)
        return r.obj.str("id")
    }

    @Test
    fun `a lignes d'avant intactes, une empreinte par service, rien de rejoue`() {
        serveur.demarrer()
        assertFalse("question_empreinte" in champs("eruditus_reponses"))

        val niveaux = listOf(1, 3, 0, 5, 2, 0)
        val questions = niveaux.mapIndexed { i, niveau ->
            creer("eruditus_questions", buildJsonObject {
                put("ref", "av-%04d".format(i + 1))
                put("enonce", "Question d'avant l'enjeu ${i + 1} : quel choix ?")
                put("choix", buildJsonArray { "ABCD".forEach { add(JsonPrimitive("Choix $it d'avant ${i + 1}")) } })
                put("bonne_reponse", 1)
                put("active", true)
                if (niveau > 0) put("niveau", niveau)
                put("difficulte", Elo.DIFFICULTES[niveau])
                put("difficulte_reponses", 0)
            })
        }
        val pseudo = "ancien"
        val motDePasse = "motdepasse-ancien"
        val perte = Elo.perte(1000, 800, 40)
        val joueur = creer("eruditus_joueurs", buildJsonObject {
            put("pseudo", pseudo)
            put("password", motDePasse)
            put("passwordConfirm", motDePasse)
            put("code_recuperation", Binaire.sha256("code"))
            put("elo", perte)
            put("elo_reponses", 1)
        })
        val partie = creer("eruditus_parties", buildJsonObject {
            put("joueur", joueur)
            put("questions", buildJsonArray { questions.take(5).forEach { add(JsonPrimitive(it)) } })
            put("etat", "abandonnee")
        })
        // Une ligne comptée (niveau 1, 800) et une non comptée (niveau 5) : vues avant l'enjeu.
        val comptee = creer("eruditus_reponses", buildJsonObject {
            put("partie", partie); put("joueur", joueur); put("question", questions[0]); put("numero", 1)
            put("servie_le", "2026-11-01 10:00:00.000Z"); put("repondue_le", "2026-11-01 10:00:05.000Z")
            put("choix", 2); put("correcte", false); put("points", 0)
            put("elo_avant", 1000); put("elo_apres", perte); put("elo_difficulte", 800); put("elo_k", 40)
        })
        val nonComptee = creer("eruditus_reponses", buildJsonObject {
            put("partie", partie); put("joueur", joueur); put("question", questions[3]); put("numero", 2)
            put("servie_le", "2026-11-02 10:00:00.000Z"); put("repondue_le", "2026-11-02 10:00:05.000Z")
            put("choix", 1); put("correcte", true); put("points", 1)
        })

        // La migration s'applique au démarrage suivant.
        serveur.arreter()
        serveur.migrations = Chemins.migrations
        serveur.demarrer()

        val champ = champs("eruditus_reponses").getValue("question_empreinte")
        assertEquals("text", champ.str("type"))
        assertEquals(true, champ.bool("hidden"))
        assertEquals(64, champ.int("max"))
        assertTrue(index("eruditus_parties").any { it.contains("joueur") && it.contains("created") })
        for (ligne in listOf(comptee, nonComptee)) assertEquals("", serveur.fiche("eruditus_reponses", ligne).str("question_empreinte"))
        assertEquals(perte, serveur.fiche("eruditus_joueurs", joueur).int("elo"))
        assertEquals(1, serveur.fiche("eruditus_joueurs", joueur).int("elo_reponses"))

        // Le joueur d'avant joue : 4 neuves (niveaux 3, 0, 2, 0), puis la plus anciennement vue.
        val connexion = serveur.connexion(pseudo, motDePasse)
        assertEquals(200, connexion.status)
        val compte = Compte(serveur, pseudo, motDePasse, connexion.obj.str("token"), joueur, "")
        val creation = compte.nouvellePartie()
        assertEquals(200, creation.status)
        val id = creation.obj.objet("partie").str("id")
        var question = creation.obj.objet("question")
        val annonces = mutableListOf(question.objet("enjeu"))
        for (numero in 1..5) {
            // Dix redemandes : la même question, le même enjeu, la même ligne.
            val avant = serveur.ligne(id, numero)
            repeat(10) {
                val r = compte.question(id)
                assertEquals(question.str("enonce"), r.obj.objet("question").str("enonce"))
                assertEquals(annonces.last(), r.obj.objet("question").objet("enjeu"))
            }
            assertEquals(avant, serveur.ligne(id, numero))
            assertEquals(64, avant.str("question_empreinte").length)
            compte.repondre(id, numero, 0)
            if (numero < 5) {
                question = compte.question(id).obj.objet("question")
                annonces += question.objet("enjeu")
            }
        }
        val lignes = serveur.lignes(id)
        assertEquals(5, lignes.map { it.str("question_empreinte") }.distinct().size)
        // La question déjà vue (niveau 1, la plus anciennement vue des deux) : non comptée, mais sa difficulté est figée.
        val revue = lignes.last()
        assertEquals(questions[0], revue.str("question"))
        assertEquals(0, revue.int("elo_avant"))
        assertEquals(800, revue.int("elo_difficulte"))
        // Les neuves sans niveau n'ont aucune difficulté, mais une empreinte.
        for (l in lignes.filter { it.str("question") in listOf(questions[2], questions[5]) }) {
            assertEquals(0, l.int("elo_difficulte"))
            assertEquals(64, l.str("question_empreinte").length)
        }
        serveur.verifierChaine(compte, avantElo = setOf(nonComptee))

        // Redémarrer ne rejoue rien.
        val avant = serveur.lister("eruditus_joueurs") + serveur.lister("eruditus_reponses")
        serveur.redemarrer()
        assertEquals(avant, serveur.lister("eruditus_joueurs") + serveur.lister("eruditus_reponses"))
    }

    @Test
    fun `b retour arriere, champ et index retires, rien d'autre`() {
        val reponses = serveur.lister("eruditus_reponses").size
        serveur.arreter()
        val r = serveur.commande("migrate", "down", "1", entree = "y\n")
        assertEquals(r.sortie, 0, r.code)
        serveur.migrations = sansEnjeu
        serveur.demarrer()
        assertFalse("question_empreinte" in champs("eruditus_reponses"))
        assertTrue(index("eruditus_parties").none { it.contains("created") })
        assertEquals(reponses, serveur.lister("eruditus_reponses").size)
    }

    companion object {
        /** Les migrations du dépôt, sans celle de l'enjeu. */
        private val sansEnjeu: Path by lazy {
            val dossier = Files.createTempDirectory("eruditus-migrations-sans-enjeu-")
            Files.list(Chemins.migrations).use { fichiers ->
                fichiers.filter { !it.fileName.toString().endsWith("_eruditus_enjeu.js") }
                    .forEach { Files.copy(it, dossier.resolve(it.fileName)) }
            }
            dossier
        }

        private val serveur: Serveur by lazy { Serveur("migration-enjeu", questions = false, migrations = sansEnjeu) }

        @AfterClass
        @JvmStatic
        fun arreter() {
            serveur.arreter()
        }
    }
}

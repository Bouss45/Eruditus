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
 * Migration de l'Elo (D110) sur une base créée sans elle, qui a déjà joueurs, parties et
 * réponses : 1000 et 0 réponse comptée, rien de rejoué, une question déjà vue ne compte pas ;
 * puis son retour arrière. Avant la migration, tout est écrit par le superutilisateur : les hooks
 * du dépôt attendent déjà les champs de l'Elo.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MigrationEloTest {

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
    fun `a joueurs existants a 1000, rien de rejoue, questions deja vues sans effet`() {
        serveur.demarrer()
        assertFalse("elo" in champs("eruditus_joueurs"))

        // Avant l'Elo : 6 questions (2 sans niveau), un joueur qui en a vu 2 dans une partie.
        val niveaux = listOf(1, 3, 0, 5, 2, 0)
        val questions = niveaux.mapIndexed { i, niveau ->
            creer("eruditus_questions", buildJsonObject {
                put("ref", "av-%04d".format(i + 1))
                put("enonce", "Question d'avant l'Elo ${i + 1} : quel choix ?")
                put("choix", buildJsonArray { "ABCD".forEach { add(JsonPrimitive("Choix $it d'avant ${i + 1}")) } })
                put("bonne_reponse", 1)
                put("active", true)
                if (niveau > 0) put("niveau", niveau)
            })
        }
        val pseudo = "ancien"
        val motDePasse = "motdepasse-ancien"
        val joueur = creer("eruditus_joueurs", buildJsonObject {
            put("pseudo", pseudo)
            put("password", motDePasse)
            put("passwordConfirm", motDePasse)
            put("code_recuperation", Binaire.sha256("code"))
        })
        val partie = creer("eruditus_parties", buildJsonObject {
            put("joueur", joueur)
            put("questions", buildJsonArray { questions.take(5).forEach { add(JsonPrimitive(it)) } })
            put("etat", "abandonnee")
        })
        val anciennes = listOf(0, 3).mapIndexed { i, q ->
            creer("eruditus_reponses", buildJsonObject {
                put("partie", partie)
                put("joueur", joueur)
                put("question", questions[q])
                put("numero", i + 1)
                put("servie_le", "2026-11-0${i + 1} 10:00:00.000Z")
                put("repondue_le", "2026-11-0${i + 1} 10:00:05.000Z")
                put("choix", 1)
                put("correcte", true)
                put("points", 1)
            })
        }

        // La migration s'applique au démarrage suivant.
        serveur.arreter()
        serveur.migrations = Chemins.migrations
        serveur.demarrer()

        val joueurs = champs("eruditus_joueurs")
        val questionsChamps = champs("eruditus_questions")
        val reponses = champs("eruditus_reponses")
        for ((collection, noms) in listOf(
            joueurs to listOf("elo", "elo_reponses"),
            questionsChamps to listOf("difficulte", "difficulte_reponses"),
            reponses to listOf("elo_avant", "elo_apres", "elo_difficulte", "elo_k"),
        )) {
            for (nom in noms) {
                val champ = collection.getValue(nom)
                assertEquals(nom, "number", champ.str("type"))
                assertEquals(nom, true, champ.bool("hidden"))
                assertEquals(nom, true, champ.bool("onlyInt"))
            }
        }
        assertEquals(100, joueurs.getValue("elo").int("min"))
        assertTrue(index("eruditus_joueurs").any { it.contains("(`elo`)") || it.contains("(elo)") })

        val enBase = serveur.fiche("eruditus_joueurs", joueur)
        assertEquals(1000, enBase.int("elo"))
        assertEquals(0, enBase.int("elo_reponses"))
        for (ligne in anciennes) {
            val l = serveur.fiche("eruditus_reponses", ligne)
            for (champ in listOf("elo_avant", "elo_apres", "elo_difficulte", "elo_k")) assertEquals(0, l.int(champ))
        }
        questions.forEachIndexed { i, id ->
            val q = serveur.fiche("eruditus_questions", id)
            assertEquals(Elo.DIFFICULTES[niveaux[i]], q.int("difficulte"))
            assertEquals(0, q.int("difficulte_reponses"))
        }

        // Le joueur d'avant se connecte et joue : les 4 jamais vues d'abord, puis la plus
        // anciennement vue, qui ne compte pas ; seules les jamais vues à niveau comptent.
        val connexion = serveur.connexion(pseudo, motDePasse)
        assertEquals(200, connexion.status)
        val compte = Compte(serveur, pseudo, motDePasse, connexion.obj.str("token"), joueur, "")
        val bonnes = (0..5).associate { "Question d'avant l'Elo ${it + 1} : quel choix ?" to 0 }
        compte.jouer { enonce, _ -> bonnes.getValue(enonce) }
        val lignes = serveur.lignesDuJoueur(compte)
        assertEquals(questions[0], lignes.last().str("question"))
        assertEquals(0, lignes.last().int("elo_avant"))
        serveur.verifierChaine(compte, avantElo = anciennes.toSet())
        assertEquals(2, serveur.joueurBase(compte).int("elo_reponses")) // niveaux 3 et 2 ; 5 déjà vue

        // Redémarrer ne rejoue rien.
        val avant = serveur.lister("eruditus_joueurs") + serveur.lister("eruditus_reponses")
        serveur.redemarrer()
        assertEquals(avant, serveur.lister("eruditus_joueurs") + serveur.lister("eruditus_reponses"))
    }

    @Test
    fun `b retour arriere, champs et index retires, rien d'autre`() {
        val joueurs = serveur.lister("eruditus_joueurs").size
        val reponses = serveur.lister("eruditus_reponses").size
        serveur.arreter()
        val r = serveur.commande("migrate", "down", "1", entree = "y\n")
        assertEquals(r.sortie, 0, r.code)
        serveur.migrations = sansElo
        serveur.demarrer()
        assertFalse("elo" in champs("eruditus_joueurs"))
        assertFalse("elo_reponses" in champs("eruditus_joueurs"))
        assertFalse("difficulte" in champs("eruditus_questions"))
        assertFalse("elo_avant" in champs("eruditus_reponses"))
        assertTrue(index("eruditus_joueurs").none { it.contains("elo") })
        assertEquals(joueurs, serveur.lister("eruditus_joueurs").size)
        assertEquals(reponses, serveur.lister("eruditus_reponses").size)
    }

    companion object {
        /** Les migrations du dépôt, sans celle de l'Elo. */
        private val sansElo: Path by lazy {
            val dossier = Files.createTempDirectory("eruditus-migrations-sans-elo-")
            Files.list(Chemins.migrations).use { fichiers ->
                fichiers.filter { !it.fileName.toString().endsWith("_eruditus_elo.js") }
                    .forEach { Files.copy(it, dossier.resolve(it.fileName)) }
            }
            dossier
        }

        private val serveur: Serveur by lazy { Serveur("migration-elo", questions = false, migrations = sansElo) }

        @AfterClass
        @JvmStatic
        fun arreter() {
            serveur.arreter()
        }
    }
}

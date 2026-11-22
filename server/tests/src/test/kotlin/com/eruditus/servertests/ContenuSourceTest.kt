package com.eruditus.servertests

import org.junit.Assert.assertEquals
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contenu sourcé (D96 – D99) : explication et source dans la seule correction d'une réponse
 * enregistrée (T45) ; le `niveau` stocké jamais dans une réponse (T46, vérifié aussi sur toute
 * réponse par le client des tests) : l'app ne reçoit que le niveau dérivé de la difficulté, dans
 * `enjeu` et `correction.elo` (D126).
 */
class ContenuSourceTest {
    private val serveur = Serveur.principal

    /** Ni la clé, ni le texte de l'explication ou de la source de [enonce] dans [r]. */
    private fun sansContenu(r: Reponse, appel: String, enonce: String) {
        for (cle in listOf("\"explication\"", "\"source\"")) {
            assertFalse("$cle dans $appel : $r", r.corps.contains(cle))
        }
        for (texte in listOfNotNull(Banque.explication(enonce), Banque.source(enonce))) {
            assertFalse("Texte de « $enonce » dans $appel : $r", r.corps.contains(texte))
        }
    }

    @Test
    fun `T45 explication et source seulement dans la correction de la question repondue`() {
        val compte = Compte.creer(serveur)
        val autre = Compte.creer(serveur)
        val vues = mutableMapOf<String, String?>()
        // Trois parties : les 12 questions de la banque d'exemple, avec et sans contenu.
        repeat(3) {
            val creation = compte.nouvellePartie()
            assertEquals("$creation", 200, creation.status)
            val id = creation.obj.objet("partie").str("id")
            var question = creation.obj.objet("question")
            sansContenu(creation, "création", question.str("enonce"))
            for (numero in 1..5) {
                val enonce = question.str("enonce")
                sansContenu(compte.question(id), "question redemandée $numero", enonce)
                compte.repondre(id, numero + 1, 0).also { sansContenu(it, "numéro suivant", enonce) }.doitEtreErreur(409, "numero_invalide")
                compte.repondre(id, numero, 7).also { sansContenu(it, "choix 7", enonce) }.doitEtreErreur(400, "choix_invalide")
                autre.repondre(id, numero, 0).also { sansContenu(it, "autre joueur", enonce) }.doitEtreErreur(404, "partie_introuvable")

                // Bonne, fausse, puis sans réponse : chaque réponse enregistrée a sa correction.
                val choix = when (numero % 3) {
                    1 -> Banque.bonne(enonce)
                    2 -> Banque.fausse(enonce)
                    else -> null
                }
                val r = compte.repondre(id, numero, choix)
                assertEquals("$r", 200, r.status)
                val correction = r.obj.objet("correction")
                assertEquals(enonce, Banque.explication(enonce), correction.strOuNull("explication"))
                assertEquals(enonce, Banque.source(enonce), correction.strOuNull("source"))
                vues[enonce] = correction.strOuNull("explication")

                // Après la 5e, la partie est terminée : `partie_finie` l'emporte (§ 5.3).
                compte.repondre(id, numero, choix).also { sansContenu(it, "deuxième réponse", enonce) }
                    .doitEtreErreur(409, if (numero < 5) "deja_repondu" else "partie_finie")
                if (numero < 5) {
                    val suivante = compte.question(id)
                    assertEquals("$suivante", 200, suivante.status)
                    question = suivante.obj.objet("question")
                    sansContenu(suivante, "question suivante", question.str("enonce"))
                }
            }
        }
        assertEquals(Banque.questions.size, vues.size)
        assertTrue("Questions avec et sans explication : $vues", vues.values.any { it == null } && vues.values.any { it != null })
    }

    @Test
    fun `T46 le niveau stocke n'est jamais dans une reponse, seul le niveau derive de l'enjeu`() {
        // En base : le niveau du fichier, 0 quand il n'y en a pas (D98).
        val niveaux = serveur.lister("eruditus_questions").associate { it.str("ref") to it.int("niveau") }
        assertEquals(1, niveaux["ex-0001"])
        assertEquals(0, niveaux["ex-0010"])
        val compte = Compte.creer(serveur)
        // Le niveau dérivé de la difficulté n'est permis que dans `enjeu` et `elo` d'une partie (D126).
        fun horsEnjeu(e: JsonElement, parent: String? = null): Boolean = when (e) {
            is JsonObject -> e.any { (cle, v) -> (cle == "niveau" && parent != "enjeu" && parent != "elo") || horsEnjeu(v, cle) }
            is JsonArray -> e.any { horsEnjeu(it, parent) }
            else -> false
        }
        compte.jouerAvec(2).forEach { assertFalse("$it", horsEnjeu(it)) }
        assertFalse(compte.moi().corps.contains("\"niveau\""))
    }
}

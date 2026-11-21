package com.eruditus.servertests

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paramètres (spec/regles.md § 1.8, § 1.9) : changer de pseudo et de mot de passe, et les
 * tentatives T34 – T38 (spec/serveur.md § 7).
 */
class ParametresTest {
    private val serveur = Serveur.principal
    private val api = serveur.api

    /** L'enregistrement du joueur, lu par le superutilisateur (champs cachés compris). */
    private fun enBase(s: Serveur, id: String): JsonObject = s.su.get("/api/collections/eruditus_joueurs/records/$id").obj

    private fun pseudoEnBase(id: String, s: Serveur = serveur): String = enBase(s, id).str("pseudo")

    // --- Changer de pseudo --------------------------------------------------------------------

    @Test
    fun `changement de pseudo, le nouveau partout, l'ancien libre, score et jetons gardes`() {
        val compte = Compte.creer(serveur)
        compte.jouerAvec(2)
        val autreAppareil = serveur.connexion(compte.pseudo, compte.motDePasse).obj.str("token")
        val ancien = compte.pseudo
        val nouveau = Compte.pseudoUnique("Neuf")

        val r = serveur.changerPseudo(compte.jeton, nouveau)
        assertEquals("$r", 200, r.status)
        assertEquals(compte.id, r.obj.objet("joueur").str("id"))
        assertEquals(nouveau, r.obj.objet("joueur").str("pseudo"))
        assertEquals(nouveau, pseudoEnBase(compte.id))

        // /moi, le classement et l'autre appareil (jetons inchangés) ont le nouveau, même score.
        val moi = compte.moi().obj
        assertEquals(nouveau, moi.objet("joueur").str("pseudo"))
        assertEquals(2, moi.int("score"))
        val ligne = api.get("/api/eruditus/v1/classement", compte.jeton).obj.objet("moi")
        assertEquals(nouveau, ligne.str("pseudo"))
        assertEquals(2, ligne.int("score"))
        val ailleurs = api.get("/api/eruditus/v1/moi", autreAppareil)
        assertEquals(200, ailleurs.status)
        assertEquals(nouveau, ailleurs.obj.objet("joueur").str("pseudo"))

        // Connexion et récupération avec le nouveau seulement ; le record ne montre rien de caché.
        assertEquals(400, serveur.connexion(ancien, compte.motDePasse).status)
        val connexion = serveur.connexion(nouveau.lowercase(), compte.motDePasse)
        assertEquals(200, connexion.status)
        assertTrue("pseudo_modifie_le" !in connexion.obj.objet("record"))
        serveur.recuperer(ancien, compte.code, "nouveau-mdp-pseudo").doitEtreErreur(400, "code_incorrect")
        assertEquals(200, serveur.recuperer(nouveau, compte.code, "nouveau-mdp-pseudo").status)

        // L'ancien pseudo est libre aussitôt, et qui le reprend n'hérite de rien (T31).
        val repreneur = Compte.creer(serveur, ancien.uppercase())
        assertEquals(0, repreneur.score())
    }

    @Test
    fun `changer seulement la casse est permis, un pseudo identique ne change rien`() {
        val pseudo = Compte.pseudoUnique("casse").lowercase()
        val compte = Compte.creer(serveur, pseudo)

        assertEquals(200, serveur.changerPseudo(compte.jeton, pseudo).status)
        assertEquals("", enBase(serveur, compte.id).str("pseudo_modifie_le"))

        val r = serveur.changerPseudo(compte.jeton, pseudo.uppercase())
        assertEquals("$r", 200, r.status)
        assertEquals(pseudo.uppercase(), pseudoEnBase(compte.id))
        assertNotEquals("", enBase(serveur, compte.id).str("pseudo_modifie_le"))

        // Identique : succès sans rien changer, même pendant le délai ; la casse seule compte.
        assertEquals(200, serveur.changerPseudo(compte.jeton, pseudo.uppercase()).status)
        serveur.changerPseudo(compte.jeton, pseudo).doitEtreErreur(409, "pseudo_trop_recent")
        assertEquals(pseudo.uppercase(), pseudoEnBase(compte.id))
    }

    @Test
    fun `T34 prendre le pseudo d'un autre, un pseudo reserve ou invalide`() {
        val compte = Compte.creer(serveur)
        val autre = Compte.creer(serveur, Compte.pseudoUnique("Autre"))

        serveur.changerPseudo(compte.jeton, autre.pseudo).doitEtreErreur(400, "pseudo_pris")
        serveur.changerPseudo(compte.jeton, autre.pseudo.uppercase()).doitEtreErreur(400, "pseudo_pris")
        serveur.changerPseudo(compte.jeton, autre.pseudo.lowercase()).doitEtreErreur(400, "pseudo_pris")
        for (reserve in listOf("Admin", "ROOT", "Eruditus_1", "eruditusFan", "Test", "Moderateur")) {
            serveur.changerPseudo(compte.jeton, reserve).doitEtreErreur(400, "pseudo_reserve")
        }
        val invalides = listOf("ab", "a".repeat(21), "hibou 42", "hibôu42", "hibou-42", "hibou😀", "", " hibou42", "hibou\n")
        for (invalide in invalides) {
            serveur.changerPseudo(compte.jeton, invalide).doitEtreErreur(400, "pseudo_invalide")
        }
        for (valeur in listOf(JsonPrimitive(123456), JsonNull, JsonPrimitive(true))) {
            api.post("/api/eruditus/v1/comptes/pseudo", buildJsonObject { put("pseudo", valeur) }, jeton = compte.jeton)
                .doitEtreErreur(400, "requete_invalide")
        }
        api.post("/api/eruditus/v1/comptes/pseudo", buildJsonObject { }, jeton = compte.jeton)
            .doitEtreErreur(400, "requete_invalide")
        api.requete("POST", "/api/eruditus/v1/comptes/pseudo", """{"pseudo":"Texte_brut"}""", compte.jeton, typeContenu = "text/plain")
            .doitEtreErreur(400, "requete_invalide")

        assertEquals(compte.pseudo, pseudoEnBase(compte.id))
        assertEquals(autre.pseudo, pseudoEnBase(autre.id))
        // Aucun de ces refus n'a lancé le délai : le premier vrai changement passe.
        assertEquals(200, serveur.changerPseudo(compte.jeton, Compte.pseudoUnique("Libre")).status)
    }

    @Test
    fun `T35 un seul changement par delai, essais limites par IP`() {
        val compte = Compte.creer(serveur)
        val premier = Compte.pseudoUnique("Un")
        assertEquals(200, serveur.changerPseudo(compte.jeton, premier).status)
        serveur.changerPseudo(compte.jeton, Compte.pseudoUnique("Deux")).doitEtreErreur(409, "pseudo_trop_recent")
        assertEquals(premier, pseudoEnBase(compte.id))

        // 10 essais par heure et par IP, refus compris ; une autre IP a son propre compteur.
        val essayeur = Compte.creer(serveur)
        val ip = Ips.nouvelle()
        repeat(10) { serveur.changerPseudo(essayeur.jeton, premier, ip).doitEtreErreur(400, "pseudo_pris") }
        assertEquals(429, serveur.changerPseudo(essayeur.jeton, Compte.pseudoUnique("Trois"), ip).status)
        assertEquals(essayeur.pseudo, pseudoEnBase(essayeur.id))
        assertEquals(200, serveur.changerPseudo(essayeur.jeton, Compte.pseudoUnique("Quatre")).status)
    }

    @Test
    fun `changement de pseudo permis de nouveau apres le delai`() {
        val court = Serveur.chronoCourt // délai du pseudo : 1 000 ms
        val compte = Compte.creer(court)
        assertEquals(200, court.changerPseudo(compte.jeton, Compte.pseudoUnique("Avant")).status)
        val apres = Compte.pseudoUnique("Apres")
        court.changerPseudo(compte.jeton, apres).doitEtreErreur(409, "pseudo_trop_recent")
        Thread.sleep(1_300)
        assertEquals(200, court.changerPseudo(compte.jeton, apres).status)
        assertEquals(apres, pseudoEnBase(compte.id, court))
    }

    // --- Changer de mot de passe --------------------------------------------------------------

    @Test
    fun `T37 changement de mot de passe, ce telephone garde sa session par le jeton neuf, les autres tombent`() {
        val compte = Compte.creer(serveur)
        val autreAppareil = serveur.connexion(compte.pseudo, compte.motDePasse).obj.str("token")

        val r = serveur.changerMotDePasse(compte.jeton, compte.motDePasse, "nouveau-mdp-t37")
        assertEquals("$r", 200, r.status)
        val neuf = r.obj.str("token")
        assertEquals(compte.id, r.obj.objet("joueur").str("id"))
        assertEquals(compte.pseudo, r.obj.objet("joueur").str("pseudo"))

        for (ancien in listOf(compte.jeton, autreAppareil)) {
            assertEquals(401, api.get("/api/eruditus/v1/moi", ancien).status)
            assertEquals(401, api.post("/api/collections/eruditus_joueurs/auth-refresh", jeton = ancien).status)
            assertEquals(401, api.post("/api/eruditus/v1/parties", jeton = ancien).status)
        }
        assertEquals(200, api.get("/api/eruditus/v1/moi", neuf).status)
        assertEquals(200, api.post("/api/collections/eruditus_joueurs/auth-refresh", jeton = neuf).status)
        assertEquals(400, serveur.connexion(compte.pseudo, compte.motDePasse).status)
        assertEquals(200, serveur.connexion(compte.pseudo, "nouveau-mdp-t37").status)
        // Le code de récupération n'a pas changé.
        assertEquals(200, serveur.recuperer(compte.pseudo, compte.code, "troisieme-mdp-t37").status)
    }

    @Test
    fun `T36 mot de passe actuel faux ou d'un autre joueur, force brute limitee`() {
        val compte = Compte.creer(serveur)
        val autre = Compte.creer(serveur)
        serveur.changerMotDePasse(compte.jeton, "pas-le-bon-mot-de-passe", "nouveau-mdp-t36").doitEtreErreur(400, "mot_de_passe_incorrect")
        serveur.changerMotDePasse(compte.jeton, autre.motDePasse, "nouveau-mdp-t36").doitEtreErreur(400, "mot_de_passe_incorrect")
        serveur.changerMotDePasse(compte.jeton, "", "nouveau-mdp-t36").doitEtreErreur(400, "mot_de_passe_incorrect")
        assertEquals(200, compte.moi().status)
        assertEquals(200, serveur.connexion(compte.pseudo, compte.motDePasse).status)
        assertEquals(200, serveur.connexion(autre.pseudo, autre.motDePasse).status)
        assertEquals(200, autre.moi().status)

        // 5 essais par 10 minutes et par IP : le bon mot de passe est refusé ensuite (429).
        val ip = Ips.nouvelle()
        repeat(5) { serveur.changerMotDePasse(compte.jeton, "mauvais-$it-t36", "nouveau-mdp-t36", ip).doitEtreErreur(400, "mot_de_passe_incorrect") }
        assertEquals(429, serveur.changerMotDePasse(compte.jeton, compte.motDePasse, "nouveau-mdp-t36", ip).status)
        assertEquals(200, compte.moi().status)
        assertEquals(200, serveur.connexion(compte.pseudo, compte.motDePasse).status)
        assertEquals(200, serveur.changerMotDePasse(compte.jeton, compte.motDePasse, "nouveau-mdp-t36").status)
    }

    @Test
    fun `mot de passe, le nouveau verifie d'abord, puis l'actuel, requetes mal formees`() {
        val compte = Compte.creer(serveur)
        serveur.changerMotDePasse(compte.jeton, "faux", "court").doitEtreErreur(400, "mot_de_passe_invalide")
        serveur.changerMotDePasse(compte.jeton, compte.motDePasse, "a".repeat(7)).doitEtreErreur(400, "mot_de_passe_invalide")
        serveur.changerMotDePasse(compte.jeton, compte.motDePasse, "a".repeat(72)).doitEtreErreur(400, "mot_de_passe_invalide")
        serveur.changerMotDePasse(compte.jeton, compte.motDePasse, "é".repeat(37)).doitEtreErreur(400, "mot_de_passe_invalide")
        val chemin = "/api/eruditus/v1/comptes/mot-de-passe"
        api.post(chemin, buildJsonObject { put("motDePasse", compte.motDePasse) }, jeton = compte.jeton)
            .doitEtreErreur(400, "requete_invalide")
        api.post(chemin, buildJsonObject { put("nouveauMotDePasse", "nouveau-mdp-ordre") }, jeton = compte.jeton)
            .doitEtreErreur(400, "requete_invalide")
        api.post(chemin, buildJsonObject { put("motDePasse", compte.motDePasse); put("nouveauMotDePasse", 12345678) }, jeton = compte.jeton)
            .doitEtreErreur(400, "requete_invalide")
        api.requete("POST", chemin, "[]", compte.jeton).doitEtreErreur(400, "requete_invalide")
        assertEquals(200, compte.moi().status)
        assertEquals(200, serveur.connexion(compte.pseudo, compte.motDePasse).status)

        // 8, 71 caractères et 36 « é » (72 octets) passent ; le même mot de passe aussi.
        var actuel = compte.motDePasse
        var jeton = compte.jeton
        for (nouveau in listOf("a".repeat(8), "a".repeat(71), "é".repeat(36), "é".repeat(36))) {
            val r = serveur.changerMotDePasse(jeton, actuel, nouveau)
            assertEquals("« $nouveau » : $r", 200, r.status)
            assertEquals(401, api.get("/api/eruditus/v1/moi", jeton).status)
            jeton = r.obj.str("token")
            actuel = nouveau
            assertEquals(200, serveur.connexion(compte.pseudo, nouveau).status)
        }
    }

    // --- Comptes des autres et jetons -----------------------------------------------------------

    @Test
    fun `T38 changer le pseudo ou le mot de passe d'un autre joueur, champs en plus ignores`() {
        val compte = Compte.creer(serveur)
        val autre = Compte.creer(serveur)
        val nouveau = Compte.pseudoUnique("Moi")
        val r = api.post(
            "/api/eruditus/v1/comptes/pseudo",
            buildJsonObject {
                put("pseudo", nouveau)
                put("id", autre.id)
                put("joueur", autre.id)
                put("tokenKey", "cle-choisie-par-le-tricheur-1234567890")
                put("password", "mot-de-passe-impose")
                put("passwordConfirm", "mot-de-passe-impose")
                put("code_recuperation", "0".repeat(64))
                put("pseudo_modifie_le", "2000-01-01 00:00:00.000Z")
                put("email", "triche@exemple.fr")
                put("verified", true)
                put("score", 999)
            },
            jeton = compte.jeton,
        )
        assertEquals("$r", 200, r.status)
        assertEquals(compte.id, r.obj.objet("joueur").str("id"))
        val enregistre = enBase(serveur, compte.id)
        assertEquals(nouveau, enregistre.str("pseudo"))
        assertEquals("", enregistre.str("email"))
        assertEquals(false, enregistre.bool("verified"))
        assertEquals(Binaire.sha256(compte.code.replace("-", "")), enregistre.str("code_recuperation"))
        assertTrue(!enregistre.str("pseudo_modifie_le").startsWith("2000"))
        assertEquals(autre.pseudo, pseudoEnBase(autre.id))
        assertEquals(200, compte.moi().status)
        assertEquals(200, autre.moi().status)
        assertEquals(400, serveur.connexion(nouveau, "mot-de-passe-impose").status)
        assertEquals(0, compte.score())

        val m = api.post(
            "/api/eruditus/v1/comptes/mot-de-passe",
            buildJsonObject {
                put("motDePasse", compte.motDePasse)
                put("nouveauMotDePasse", "nouveau-mdp-t38")
                put("id", autre.id)
                put("pseudo", autre.pseudo)
                put("tokenKey", "cle-choisie-par-le-tricheur-1234567890")
                put("code_recuperation", "0".repeat(64))
            },
            jeton = compte.jeton,
        )
        assertEquals("$m", 200, m.status)
        assertEquals(compte.id, m.obj.objet("joueur").str("id"))
        assertEquals(200, serveur.connexion(nouveau, "nouveau-mdp-t38").status)
        assertEquals(200, serveur.connexion(autre.pseudo, autre.motDePasse).status)
        assertEquals(200, autre.moi().status)
        assertEquals(200, serveur.recuperer(nouveau, compte.code, "encore-un-mdp-t38").status)
    }

    @Test
    fun `T38 sans jeton, jeton de superutilisateur ou de users sur les routes des Parametres`() {
        val compte = Compte.creer(serveur)
        for (jeton in listOf(null, serveur.jetonSu, jetonUsers())) {
            assertEquals(401, serveur.changerPseudo(jeton, Compte.pseudoUnique("Vol")).status)
            assertEquals(401, serveur.changerMotDePasse(jeton, compte.motDePasse, "nouveau-mdp-vol").status)
        }
        assertEquals(compte.pseudo, pseudoEnBase(compte.id))
        assertEquals(200, serveur.connexion(compte.pseudo, compte.motDePasse).status)
        assertEquals(200, compte.moi().status)
    }

    /** Jeton d'un compte de la collection `users`, créé par le superutilisateur (T14). */
    private fun jetonUsers(): String {
        val email = "param-${Serveur.aleatoire(6).lowercase()}@exemple.fr"
        val creation = serveur.su.post(
            "/api/collections/users/records",
            buildJsonObject {
                put("email", email)
                put("password", "motdepasse-users")
                put("passwordConfirm", "motdepasse-users")
            },
        )
        assertEquals("$creation", 200, creation.status)
        val connexion = serveur.su.post(
            "/api/collections/users/auth-with-password",
            buildJsonObject {
                put("identity", email)
                put("password", "motdepasse-users")
            },
            jeton = null,
        )
        assertEquals("$connexion", 200, connexion.status)
        return connexion.obj.str("token")
    }
}

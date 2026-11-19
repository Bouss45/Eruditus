package com.eruditus.servertests

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Routes de compte (spec/regles.md § 1) et tentatives T1 – T15, plus D30. */
class ComptesTest {
    private val serveur = Serveur.principal
    private val api = serveur.api

    private fun nombreDeJoueurs() = serveur.compter("eruditus_joueurs")

    // --- Création -------------------------------------------------------------------------

    @Test
    fun `creation puis connexion insensible a la casse, pseudo affiche tel que cree`() {
        val pseudo = Compte.pseudoUnique("Hibou")
        val compte = Compte.creer(serveur, pseudo)
        assertEquals(pseudo, compte.pseudo)

        val connexion = serveur.connexion(pseudo.lowercase(), compte.motDePasse)
        assertEquals(200, connexion.status)
        assertEquals(compte.id, connexion.obj.objet("record").str("id"))
        assertEquals(pseudo, connexion.obj.objet("record").str("pseudo"))
        assertEquals(pseudo, compte.moi().obj.objet("joueur").str("pseudo"))
    }

    @Test
    fun `T1 creer un compte par l'API standard est refuse`() {
        val pseudo = Compte.pseudoUnique("T1_")
        val corps = buildJsonObject {
            put("pseudo", pseudo)
            put("password", "motdepasse-t1")
            put("passwordConfirm", "motdepasse-t1")
            put("code_recuperation", "0".repeat(64))
        }
        val sansJeton = api.post("/api/collections/eruditus_joueurs/records", corps)
        assertTrue("$sansJeton", sansJeton.status in 400..403)
        val autre = Compte.creer(serveur)
        val avecJeton = api.post("/api/collections/eruditus_joueurs/records", corps, jeton = autre.jeton)
        assertTrue("$avecJeton", avecJeton.status in 400..403)
        assertEquals(0, serveur.compter("eruditus_joueurs", "pseudo = '$pseudo'"))
    }

    @Test
    fun `T2 les champs en plus sont ignores a la creation`() {
        val pseudo = Compte.pseudoUnique("T2_")
        val r = api.post(
            "/api/eruditus/v1/comptes",
            buildJsonObject {
                put("pseudo", pseudo)
                put("motDePasse", "motdepasse-t2")
                put("email", "triche@exemple.fr")
                put("verified", true)
                put("emailVisibility", true)
                put("code_recuperation", "0".repeat(64))
                put("codeRecuperation", "0000-0000-0000-0000")
                put("score", 999)
                put("id", "abcdefghijklmno")
                put("password", "\$2a\$10\$abcdefghijklmnopqrstuuJ0fd4Z1kD3Nqk1u1x1x1x1x1x1x1x1x")
                put("tokenKey", "cle-choisie-par-le-tricheur-1234567890")
            },
        )
        assertEquals("$r", 200, r.status)
        val id = r.obj.objet("joueur").str("id")
        assertNotEquals("abcdefghijklmno", id)
        val code = r.obj.str("codeRecuperation")
        assertNotEquals("0000-0000-0000-0000", code)

        val enregistre = serveur.su.get("/api/collections/eruditus_joueurs/records/$id").obj
        assertEquals("", enregistre.str("email"))
        // Seule l'empreinte SHA-256 du code normalisé est gardée (spec/regles.md § 1.6).
        assertEquals(Binaire.sha256(code.replace("-", "")), enregistre.str("code_recuperation"))
        assertEquals(false, enregistre.bool("verified"))
        assertEquals(200, serveur.connexion(pseudo, "motdepasse-t2").status)
        assertEquals(400, serveur.connexion(pseudo, "\$2a\$10\$abcdefghijklmnopqrstuuJ0fd4Z1kD3Nqk1u1x1x1x1x1x1x1x1x").status)
        val compte = Compte(serveur, pseudo, "motdepasse-t2", r.obj.str("token"), id, code)
        assertEquals(0, compte.score())
        // Le code enregistré est celui renvoyé, pas celui envoyé.
        serveur.recuperer(pseudo, "0000-0000-0000-0000", "nouveau-t2-1").doitEtreErreur(400, "code_incorrect")
        assertEquals(200, serveur.recuperer(pseudo, code, "nouveau-t2-2").status)
    }

    @Test
    fun `T3 pseudo invalide refuse`() {
        val avant = nombreDeJoueurs()
        val invalides = listOf(
            "ab", "a".repeat(21), "hibou 42", "hibôu42", "hibou-42", "hibou😀", "hibou\u0001", "", " hibou42",
            "hibou42 ", "hib.ou", "hibou\n",
        )
        for (pseudo in invalides) {
            serveur.creerCompte(texte(pseudo), texte("motdepasse-t3")).doitEtreErreur(400, "pseudo_invalide")
        }
        serveur.creerCompte(JsonPrimitive(123456), texte("motdepasse-t3")).doitEtreErreur(400, "requete_invalide")
        serveur.creerCompte(JsonNull, texte("motdepasse-t3")).doitEtreErreur(400, "requete_invalide")
        assertEquals(avant, nombreDeJoueurs())
        // Limites acceptées : 3 et 20 caractères.
        Compte.creer(serveur, "a" + Serveur.aleatoire(2))
        Compte.creer(serveur, Compte.pseudoUnique("v").padEnd(20, '9'))
    }

    @Test
    fun `T4 pseudo reserve ou deja pris en changeant la casse`() {
        val avant = nombreDeJoueurs()
        for (pseudo in listOf("ADMIN", "Administrateur", "root", "SuperUser", "Eruditus_1", "eruditus", "ERUDITUSfan",
            "Test", "null", "Guest", "Moderateur", "Joueur", "anonyme", "Systeme")) {
            serveur.creerCompte(texte(pseudo), texte("motdepasse-t4")).doitEtreErreur(400, "pseudo_reserve")
        }
        assertEquals(avant, nombreDeJoueurs())

        val pris = Compte.pseudoUnique("hibou")
        Compte.creer(serveur, pris)
        serveur.creerCompte(texte(pris.uppercase()), texte("motdepasse-t4")).doitEtreErreur(400, "pseudo_pris")
        serveur.creerCompte(texte(pris), texte("autre-mot-de-passe")).doitEtreErreur(400, "pseudo_pris")
        assertEquals(avant + 1, nombreDeJoueurs())
    }

    @Test
    fun `T5 mot de passe de 8 a 71 caracteres et 72 octets au plus`() {
        fun essai(motDePasse: String) = serveur.creerCompte(texte(Compte.pseudoUnique("m")), texte(motDePasse))
        essai("a".repeat(7)).doitEtreErreur(400, "mot_de_passe_invalide")
        essai("a".repeat(72)).doitEtreErreur(400, "mot_de_passe_invalide")
        essai("é".repeat(37)).doitEtreErreur(400, "mot_de_passe_invalide") // 37 caractères, 74 octets
        essai("é".repeat(7)).doitEtreErreur(400, "mot_de_passe_invalide") // 14 octets mais 7 caractères
        essai("🙂".repeat(19)).doitEtreErreur(400, "mot_de_passe_invalide") // 76 octets
        serveur.creerCompte(texte(Compte.pseudoUnique("m")), JsonPrimitive(12345678)).doitEtreErreur(400, "requete_invalide")

        for (motDePasse in listOf("a".repeat(8), "a".repeat(71), "é".repeat(36), "é".repeat(8), "🙂".repeat(18), "mot de passe à espaces")) {
            val compte = Compte.creer(serveur, motDePasse = motDePasse)
            assertEquals("Connexion avec « $motDePasse »", 200, serveur.connexion(compte.pseudo, motDePasse).status)
        }
    }

    @Test
    fun `T6 creer un compte dans la collection users est refuse`() {
        val avant = serveur.compter("users")
        val corps = buildJsonObject {
            put("email", "t6-${Serveur.aleatoire(6)}@exemple.fr")
            put("password", "motdepasse-t6")
            put("passwordConfirm", "motdepasse-t6")
        }
        val r = api.post("/api/collections/users/records", corps)
        assertTrue("$r", r.status in 400..403)
        val compte = Compte.creer(serveur)
        val avecJeton = api.post("/api/collections/users/records", corps, jeton = compte.jeton)
        assertTrue("$avecJeton", avecJeton.status in 400..403)
        assertEquals(avant, serveur.compter("users"))
    }

    @Test
    fun `T7 lister ou lire des joueurs par l'API standard est refuse, rien de sensible nulle part`() {
        val compte = Compte.creer(serveur)
        for (jeton in listOf(null, compte.jeton)) {
            val liste = api.get("/api/collections/eruditus_joueurs/records", jeton)
            assertTrue("$liste", liste.status in 400..404)
            val fiche = api.get("/api/collections/eruditus_joueurs/records/${compte.id}", jeton)
            assertTrue("$fiche", fiche.status in 400..404)
            assertTrue(!fiche.corps.contains(compte.pseudo))
        }
        // Connexion et renouvellement : record sans email, emailVisibility ni verified (D33) ;
        // le client Api échoue de lui-même si un champ interdit apparaît dans une réponse.
        val connexion = serveur.connexion(compte.pseudo, compte.motDePasse)
        assertEquals(200, connexion.status)
        val record = connexion.obj.objet("record")
        Api.CHAMPS_INTERDITS.forEach { assertTrue("$it dans $record", it !in record) }
        val renouvellement = api.post("/api/collections/eruditus_joueurs/auth-refresh", jeton = compte.jeton)
        assertEquals(200, renouvellement.status)
        Api.CHAMPS_INTERDITS.forEach { assertTrue(it !in renouvellement.obj.objet("record")) }
    }

    @Test
    fun `T8 modifier son compte par l'API standard est refuse`() {
        val compte = Compte.creer(serveur)
        val modifications = listOf(
            buildJsonObject { put("pseudo", Compte.pseudoUnique("T8_")) },
            buildJsonObject {
                put("password", "nouveau-mdp-t8")
                put("passwordConfirm", "nouveau-mdp-t8")
                put("oldPassword", compte.motDePasse)
            },
            buildJsonObject { put("code_recuperation", "0".repeat(64)) },
            buildJsonObject { put("tokenKey", "cle-choisie-par-le-tricheur-1234567890") },
            buildJsonObject { put("verified", true) },
            buildJsonObject { put("email", "t8@exemple.fr") },
        )
        for (corps in modifications) {
            val r = api.patch("/api/collections/eruditus_joueurs/records/${compte.id}", corps, compte.jeton)
            assertTrue("$corps → $r", r.status in 400..404)
        }
        val enregistre = serveur.su.get("/api/collections/eruditus_joueurs/records/${compte.id}").obj
        assertEquals(compte.pseudo, enregistre.str("pseudo"))
        assertEquals("", enregistre.str("email"))
        assertEquals(200, serveur.connexion(compte.pseudo, compte.motDePasse).status)
        assertEquals(200, compte.moi().status)
    }

    @Test
    fun `T9 supprimer un compte par l'API standard ou avec un mauvais mot de passe est refuse`() {
        val compte = Compte.creer(serveur)
        val autre = Compte.creer(serveur)
        for (cible in listOf(compte.id, autre.id)) {
            val r = api.delete("/api/collections/eruditus_joueurs/records/$cible", compte.jeton)
            assertTrue("$r", r.status in 400..404)
            val sansJeton = api.delete("/api/collections/eruditus_joueurs/records/$cible", null)
            assertTrue("$sansJeton", sansJeton.status in 400..404)
        }
        api.post(
            "/api/eruditus/v1/comptes/suppression",
            buildJsonObject { put("motDePasse", "pas-le-bon-mot-de-passe") },
            jeton = compte.jeton,
        ).doitEtreErreur(400, "mot_de_passe_incorrect")
        assertEquals(1, serveur.compter("eruditus_joueurs", "id = '${compte.id}'"))
        assertEquals(1, serveur.compter("eruditus_joueurs", "id = '${autre.id}'"))
        assertEquals(200, serveur.connexion(compte.pseudo, compte.motDePasse).status)
    }

    @Test
    fun `T10 force brute de la connexion limitee a 5 essais par minute et par IP`() {
        val compte = Compte.creer(serveur)
        val ip = Ips.nouvelle()
        repeat(5) { assertEquals(400, serveur.connexion(compte.pseudo, "mauvais-mdp-$it", ip).status) }
        assertEquals(429, serveur.connexion(compte.pseudo, "mauvais-mdp-6", ip).status)
        assertEquals(429, serveur.connexion(compte.pseudo, compte.motDePasse, ip).status)
        // Autre IP : compteur distinct.
        assertEquals(400, serveur.connexion(compte.pseudo, "mauvais-mdp-7", Ips.nouvelle()).status)
        assertEquals(200, serveur.connexion(compte.pseudo, compte.motDePasse, Ips.nouvelle()).status)
    }

    @Test
    fun `T11 force brute du code de recuperation, pseudo inconnu et code faux indiscernables`() {
        val compte = Compte.creer(serveur)
        val ip = Ips.nouvelle()
        val inconnu = serveur.recuperer(Compte.pseudoUnique("inconnu"), "7K3M-Q9XA-2HDE-F8RT", "nouveau-mdp-t11", ip)
        val codeFaux = serveur.recuperer(compte.pseudo, "7K3M-Q9XA-2HDE-F8RT", "nouveau-mdp-t11", ip)
        inconnu.doitEtreErreur(400, "code_incorrect")
        assertEquals(inconnu.status, codeFaux.status)
        assertEquals(inconnu.obj, codeFaux.obj)
        repeat(3) { serveur.recuperer(compte.pseudo, "AAAA-AAAA-AAAA-AAA$it", "nouveau-mdp-t11", ip).doitEtreErreur(400, "code_incorrect") }
        assertEquals(429, serveur.recuperer(compte.pseudo, compte.code, "nouveau-mdp-t11", ip).status)
        assertEquals(200, serveur.connexion(compte.pseudo, compte.motDePasse).status)
        // Autre IP : le bon code passe.
        assertEquals(200, serveur.recuperer(compte.pseudo, compte.code, "nouveau-mdp-t11").status)
    }

    @Test
    fun `T12 code deja utilise, ancien jeton et ancien mot de passe refuses apres une recuperation`() {
        val compte = Compte.creer(serveur)
        val ancienJeton = serveur.connexion(compte.pseudo, compte.motDePasse).obj.str("token")
        val r = serveur.recuperer(compte.pseudo, compte.code, "nouveau-mdp-t12")
        assertEquals(200, r.status)
        val nouveauCode = r.obj.str("codeRecuperation")
        assertNotEquals(compte.code, nouveauCode)

        serveur.recuperer(compte.pseudo, compte.code, "encore-un-mdp-t12").doitEtreErreur(400, "code_incorrect")
        assertEquals(401, api.get("/api/eruditus/v1/moi", compte.jeton).status)
        assertEquals(401, api.get("/api/eruditus/v1/moi", ancienJeton).status)
        assertEquals(400, serveur.connexion(compte.pseudo, compte.motDePasse).status)
        assertEquals(200, serveur.connexion(compte.pseudo, "nouveau-mdp-t12").status)
        assertEquals(200, api.get("/api/eruditus/v1/moi", r.obj.str("token")).status)
        assertEquals(200, serveur.recuperer(compte.pseudo, nouveauCode, "troisieme-mdp-t12").status)
    }

    @Test
    fun `recuperation tolerante a la saisie du code`() {
        // Un code avec un 0 ou un 1 montre aussi la lecture de O, I et L.
        var compte = Compte.creer(serveur)
        repeat(12) { if (!compte.code.any { it == '0' || it == '1' }) compte = Compte.creer(serveur) }
        val saisi = compte.code.replace("-", "").lowercase()
            .mapIndexed { i, c ->
                when (c) {
                    '0' -> 'o'
                    '1' -> if (i % 2 == 0) 'i' else 'L'
                    else -> c
                }
            }
            .joinToString("").chunked(3).joinToString(" - ")
        val r = serveur.recuperer(compte.pseudo.uppercase(), "  $saisi  ", "nouveau-mdp-saisie")
        assertEquals("Code saisi « $saisi » pour ${compte.code} : $r", 200, r.status)
        assertEquals(compte.pseudo, r.obj.objet("joueur").str("pseudo"))
    }

    @Test
    fun `recuperation verifie le nouveau mot de passe d'abord, puis le pseudo et le code`() {
        val compte = Compte.creer(serveur)
        serveur.recuperer(compte.pseudo, "AAAA-AAAA-AAAA-AAAA", "court").doitEtreErreur(400, "mot_de_passe_invalide")
        serveur.recuperer(compte.pseudo, compte.code, "é".repeat(37)).doitEtreErreur(400, "mot_de_passe_invalide")
        serveur.recuperer(compte.pseudo, "abc", "nouveau-mdp-ordre").doitEtreErreur(400, "code_incorrect")
        serveur.recuperer(compte.pseudo, compte.code + "A", "nouveau-mdp-ordre").doitEtreErreur(400, "code_incorrect")
        serveur.recuperer(compte.pseudo, "UUUU-UUUU-UUUU-UUUU", "nouveau-mdp-ordre").doitEtreErreur(400, "code_incorrect")
        serveur.recuperer("a b", compte.code, "nouveau-mdp-ordre").doitEtreErreur(400, "code_incorrect")
        api.post(
            "/api/eruditus/v1/comptes/recuperation",
            buildJsonObject {
                put("pseudo", compte.pseudo)
                put("code", 1234)
                put("nouveauMotDePasse", "nouveau-mdp-ordre")
            },
        ).doitEtreErreur(400, "requete_invalide")
        api.post(
            "/api/eruditus/v1/comptes/recuperation",
            buildJsonObject {
                put("pseudo", compte.pseudo)
                put("code", compte.code)
            },
        ).doitEtreErreur(400, "requete_invalide")
        // Rien n'a changé : le code et le mot de passe d'origine marchent toujours.
        assertEquals(200, serveur.connexion(compte.pseudo, compte.motDePasse).status)
        assertEquals(200, serveur.recuperer(compte.pseudo, compte.code, "nouveau-mdp-ordre").status)
    }

    // --- Jetons ---------------------------------------------------------------------------

    @Test
    fun `T13 jeton refuse apres deconnexion, sur tous les appareils`() {
        val compte = Compte.creer(serveur)
        val telephone2 = serveur.connexion(compte.pseudo, compte.motDePasse).obj.str("token")
        assertEquals(204, api.post("/api/eruditus/v1/comptes/deconnexion", jeton = compte.jeton).status)
        assertEquals(401, api.get("/api/eruditus/v1/moi", compte.jeton).status)
        assertEquals(401, api.get("/api/eruditus/v1/moi", telephone2).status)
        assertEquals(401, api.post("/api/collections/eruditus_joueurs/auth-refresh", jeton = telephone2).status)
        val nouveau = serveur.connexion(compte.pseudo, compte.motDePasse)
        assertEquals(200, nouveau.status)
        assertEquals(200, api.get("/api/eruditus/v1/moi", nouveau.obj.str("token")).status)
    }

    @Test
    fun `T13 jeton refuse apres suppression du compte`() {
        val compte = Compte.creer(serveur)
        assertEquals(
            204,
            api.post(
                "/api/eruditus/v1/comptes/suppression",
                buildJsonObject { put("motDePasse", compte.motDePasse) },
                jeton = compte.jeton,
            ).status,
        )
        assertEquals(401, api.get("/api/eruditus/v1/moi", compte.jeton).status)
        assertEquals(401, api.post("/api/eruditus/v1/parties", jeton = compte.jeton).status)
    }

    @Test
    fun `T13 jeton expire, altere ou signe par une autre cle`() {
        val compte = Compte.creer(serveur)
        val expire = jetonQuiExpire(serveur, compte.id)
        assertEquals(401, api.get("/api/eruditus/v1/moi", expire).status)

        val dernier = compte.jeton.last()
        val altere = compte.jeton.dropLast(1) + (if (dernier == 'A') 'B' else 'A')
        assertEquals(401, api.get("/api/eruditus/v1/moi", altere).status)
        val milieu = compte.jeton.split('.')
        val chargeAlteree = milieu[0] + "." + milieu[1].replaceRange(5, 6, if (milieu[1][5] == 'a') "b" else "a") + "." + milieu[2]
        assertEquals(401, api.get("/api/eruditus/v1/moi", chargeAlteree).status)

        assertEquals(401, api.get("/api/eruditus/v1/moi", resigner(compte.jeton, "une-autre-cle-de-signature")).status)
        assertEquals(200, compte.moi().status)
    }

    @Test
    fun `T14 jeton de superutilisateur ou de users refuse sur les routes Eruditus`() {
        assertEquals(401, api.get("/api/eruditus/v1/moi", serveur.jetonSu).status)
        assertEquals(401, api.get("/api/eruditus/v1/classement", serveur.jetonSu).status)
        assertEquals(401, api.post("/api/eruditus/v1/parties", jeton = serveur.jetonSu).status)

        val email = "t14-${Serveur.aleatoire(6).lowercase()}@exemple.fr"
        val creation = serveur.su.post(
            "/api/collections/users/records",
            buildJsonObject {
                put("email", email)
                put("password", "motdepasse-t14")
                put("passwordConfirm", "motdepasse-t14")
            },
        )
        assertEquals("$creation", 200, creation.status)
        val connexion = serveur.su.post(
            "/api/collections/users/auth-with-password",
            buildJsonObject {
                put("identity", email)
                put("password", "motdepasse-t14")
            },
            jeton = null,
        )
        assertEquals("$connexion", 200, connexion.status)
        val jetonUsers = connexion.obj.str("token")
        assertEquals(401, api.get("/api/eruditus/v1/moi", jetonUsers).status)
        assertEquals(401, api.post("/api/eruditus/v1/parties", jeton = jetonUsers).status)
        assertEquals(401, api.post("/api/eruditus/v1/comptes/deconnexion", jeton = jetonUsers).status)
    }

    @Test
    fun `T15 route protegee sans jeton`() {
        val compte = Compte.creer(serveur)
        val (partie, _) = compte.commencer()
        val reponses = listOf(
            api.get("/api/eruditus/v1/moi", null),
            api.get("/api/eruditus/v1/classement", null),
            api.post("/api/eruditus/v1/parties", jeton = null),
            api.post("/api/eruditus/v1/parties/$partie/question", jeton = null),
            api.post("/api/eruditus/v1/parties/$partie/reponse", buildJsonObject { put("numero", 1); put("choix", 0) }, jeton = null),
            api.post("/api/eruditus/v1/comptes/deconnexion", jeton = null),
            api.post("/api/eruditus/v1/comptes/suppression", buildJsonObject { put("motDePasse", compte.motDePasse) }, jeton = null),
        )
        reponses.forEach { assertEquals("$it", 401, it.status) }
        assertEquals(0, serveur.compter("eruditus_reponses", "partie = '$partie' && repondue_le != ''"))
        assertEquals(200, compte.moi().status)
    }

    @Test
    fun `D30 connexion reussie sans le vieux jeton, 401 si on l'envoie`() {
        val compte = Compte.creer(serveur)
        val vieux = jetonQuiExpire(serveur, compte.id)

        assertEquals(401, serveur.connexion(compte.pseudo, compte.motDePasse, jeton = vieux).status)
        assertEquals(200, serveur.connexion(compte.pseudo, compte.motDePasse).status)

        assertEquals(401, api.get("/api/eruditus/v1/info", vieux).status)
        assertEquals(200, api.get("/api/eruditus/v1/info", null).status)

        val pseudo = Compte.pseudoUnique("d30_")
        val creation = api.post(
            "/api/eruditus/v1/comptes",
            buildJsonObject {
                put("pseudo", pseudo)
                put("motDePasse", "motdepasse-d30")
            },
            jeton = vieux,
        )
        assertEquals(401, creation.status)
        assertEquals(0, serveur.compter("eruditus_joueurs", "pseudo = '$pseudo'"))
    }

    // --- Déconnexion et suppression ---------------------------------------------------------

    @Test
    fun `suppression efface le compte, ses parties et ses reponses, et libere le pseudo`() {
        val compte = Compte.creer(serveur)
        compte.jouerAvec(2)
        compte.commencer()
        assertTrue(serveur.compter("eruditus_parties", "joueur = '${compte.id}'") == 2)
        assertTrue(serveur.compter("eruditus_reponses", "joueur = '${compte.id}'") >= 6)

        val r = api.post(
            "/api/eruditus/v1/comptes/suppression",
            buildJsonObject { put("motDePasse", compte.motDePasse) },
            jeton = compte.jeton,
        )
        assertEquals(204, r.status)
        assertEquals(0, serveur.compter("eruditus_joueurs", "id = '${compte.id}'"))
        assertEquals(0, serveur.compter("eruditus_parties", "joueur = '${compte.id}'"))
        assertEquals(0, serveur.compter("eruditus_reponses", "joueur = '${compte.id}'"))
        assertEquals(400, serveur.connexion(compte.pseudo, compte.motDePasse).status)
        Compte.creer(serveur, compte.pseudo.uppercase())
    }

    @Test
    fun `suppression avec une requete mal formee`() {
        val compte = Compte.creer(serveur)
        api.post("/api/eruditus/v1/comptes/suppression", buildJsonObject { }, jeton = compte.jeton)
            .doitEtreErreur(400, "requete_invalide")
        api.post("/api/eruditus/v1/comptes/suppression", buildJsonObject { put("motDePasse", 12345678) }, jeton = compte.jeton)
            .doitEtreErreur(400, "requete_invalide")
        api.post("/api/eruditus/v1/comptes/suppression", buildJsonObject { put("motDePasse", "") }, jeton = compte.jeton)
            .doitEtreErreur(400, "mot_de_passe_incorrect")
        assertEquals(200, compte.moi().status)
    }

    @Test
    fun `creation avec une requete mal formee`() {
        val avant = nombreDeJoueurs()
        api.post("/api/eruditus/v1/comptes", buildJsonObject { put("pseudo", Compte.pseudoUnique()) })
            .doitEtreErreur(400, "requete_invalide")
        api.post("/api/eruditus/v1/comptes", buildJsonObject { put("motDePasse", "motdepasse") })
            .doitEtreErreur(400, "requete_invalide")
        serveur.creerCompte(texte(Compte.pseudoUnique()), JsonNull).doitEtreErreur(400, "requete_invalide")
        assertEquals(avant, nombreDeJoueurs())
    }

    companion object {
        /** Jeton valable une seconde, obtenu par le superutilisateur, puis attendu jusqu'à expiration. */
        fun jetonQuiExpire(serveur: Serveur, id: String): String {
            val r = serveur.su.post(
                "/api/collections/eruditus_joueurs/impersonate/$id",
                buildJsonObject { put("duration", 1) },
            )
            assertEquals("Emprunt d'identité : $r", 200, r.status)
            val jeton = r.obj.str("token")
            Thread.sleep(2_100)
            return jeton
        }

        /** Même contenu, signé HS256 avec une autre clé. */
        fun resigner(jeton: String, cle: String): String {
            val parties = jeton.split('.')
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(cle.toByteArray(), "HmacSHA256"))
            val signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal("${parties[0]}.${parties[1]}".toByteArray()))
            return "${parties[0]}.${parties[1]}.$signature"
        }
    }
}

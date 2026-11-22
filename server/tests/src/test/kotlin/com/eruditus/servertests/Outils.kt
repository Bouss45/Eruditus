package com.eruditus.servertests

import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals

/** Les 12 questions factices : énoncé → bonne réponse (0 – 3). */
object Banque {
    val questions: List<JsonObject> by lazy {
        Json.parseToJsonElement(Files.readString(Chemins.questionsExemple)).jsonArray.map { it.jsonObject }
    }

    private val bonnes: Map<String, Int> by lazy {
        questions.associate { it.str("enonce") to it.int("bonneReponse") }
    }

    fun bonne(enonce: String): Int = bonnes[enonce] ?: throw AssertionError("Énoncé inconnu : $enonce")

    fun fausse(enonce: String): Int = (bonne(enonce) + 1) % 4

    private fun question(enonce: String): JsonObject =
        questions.firstOrNull { it.str("enonce") == enonce } ?: throw AssertionError("Énoncé inconnu : $enonce")

    /** L'explication de la question dans le fichier, ou null (D96). */
    fun explication(enonce: String): String? = question(enonce)["explication"]?.texte()

    /** La source de la question dans le fichier, ou null (D96). */
    fun source(enonce: String): String? = question(enonce)["source"]?.texte()
}

/** Un compte créé par la route, avec ce que l'app en garde (et le mot de passe, pour les tests). */
class Compte(
    val serveur: Serveur,
    val pseudo: String,
    var motDePasse: String,
    var jeton: String,
    val id: String,
    var code: String,
) {
    val api: Api get() = serveur.api

    fun moi(): Reponse = api.get("/api/eruditus/v1/moi", jeton)

    fun score(): Int {
        val r = moi()
        assertEquals("GET /moi : $r", 200, r.status)
        return r.obj.int("score")
    }

    fun nouvellePartie(ip: String? = Ips.nouvelle()): Reponse =
        api.post("/api/eruditus/v1/parties", jeton = jeton, ip = ip)

    fun question(partie: String, jeton: String? = this.jeton): Reponse =
        api.post("/api/eruditus/v1/parties/$partie/question", jeton = jeton)

    fun repondre(partie: String, numero: Int, choix: Int?, jeton: String? = this.jeton): Reponse =
        api.post(
            "/api/eruditus/v1/parties/$partie/reponse",
            buildJsonObject {
                put("numero", numero)
                put("choix", choix?.let { JsonPrimitive(it) } ?: JsonNull)
            },
            jeton = jeton,
        )

    fun repondreBrut(partie: String, corps: JsonElement): Reponse =
        api.post("/api/eruditus/v1/parties/$partie/reponse", corps, jeton = jeton)

    /** Crée une partie et renvoie (id, question 1). */
    fun commencer(): Pair<String, JsonObject> {
        val r = nouvellePartie()
        assertEquals("POST /parties : $r", 200, r.status)
        return r.obj.objet("partie").str("id") to r.obj.objet("question")
    }

    /**
     * Joue une partie entière : [choix] décide du choix (0 – 3 ou null) selon l'énoncé et le
     * numéro. Renvoie les 5 réponses de la route (correction et bilan).
     */
    fun jouer(choix: (enonce: String, numero: Int) -> Int?): List<JsonObject> {
        val (id, premiere) = commencer()
        var question = premiere
        val reponses = mutableListOf<JsonObject>()
        for (numero in 1..5) {
            assertEquals(numero, question.int("numero"))
            val r = repondre(id, numero, choix(question.str("enonce"), numero))
            assertEquals("Réponse $numero : $r", 200, r.status)
            reponses += r.obj
            if (numero < 5) {
                val suivante = question(id)
                assertEquals("Question ${numero + 1} : $suivante", 200, suivante.status)
                question = suivante.obj.objet("question")
            }
        }
        return reponses
    }

    /** Joue une partie en ne réussissant que [bonnes] questions (les premières). */
    fun jouerAvec(bonnes: Int): List<JsonObject> =
        jouer { enonce, numero -> if (numero <= bonnes) Banque.bonne(enonce) else Banque.fausse(enonce) }

    companion object {
        private val compteur = AtomicInteger()

        /** Pseudo valide, unique dans la série de tests, jamais réservé. */
        fun pseudoUnique(prefixe: String = "j"): String {
            val n = compteur.incrementAndGet()
            return "${prefixe}${n}_${Serveur.aleatoire(5)}".take(20)
        }

        fun creer(
            serveur: Serveur,
            pseudo: String = pseudoUnique(),
            motDePasse: String = "mdp-${Serveur.aleatoire(12)}",
        ): Compte {
            val r = serveur.api.post(
                "/api/eruditus/v1/comptes",
                buildJsonObject {
                    put("pseudo", pseudo)
                    put("motDePasse", motDePasse)
                },
            )
            assertEquals("POST /comptes ($pseudo) : $r", 200, r.status)
            val joueur = r.obj.objet("joueur")
            return Compte(serveur, joueur.str("pseudo"), motDePasse, r.obj.str("token"), joueur.str("id"), r.obj.str("codeRecuperation"))
        }
    }
}

/** Connexion par l'API standard, sans jeton (D30). */
fun Serveur.connexion(identite: String, motDePasse: String, ip: String? = Ips.nouvelle(), jeton: String? = null): Reponse =
    api.post(
        "/api/collections/eruditus_joueurs/auth-with-password",
        buildJsonObject {
            put("identity", identite)
            put("password", motDePasse)
        },
        jeton = jeton,
        ip = ip,
    )

fun Serveur.creerCompte(pseudo: JsonElement, motDePasse: JsonElement, ip: String? = Ips.nouvelle()): Reponse =
    api.post(
        "/api/eruditus/v1/comptes",
        buildJsonObject {
            put("pseudo", pseudo)
            put("motDePasse", motDePasse)
        },
        ip = ip,
    )

fun Serveur.recuperer(pseudo: String, code: String, nouveau: String, ip: String? = Ips.nouvelle()): Reponse =
    api.post(
        "/api/eruditus/v1/comptes/recuperation",
        buildJsonObject {
            put("pseudo", pseudo)
            put("code", code)
            put("nouveauMotDePasse", nouveau)
        },
        ip = ip,
    )

/** Changement de pseudo (D79), avec le jeton du joueur. */
fun Serveur.changerPseudo(jeton: String?, pseudo: String, ip: String? = Ips.nouvelle()): Reponse =
    api.post("/api/eruditus/v1/comptes/pseudo", buildJsonObject { put("pseudo", pseudo) }, jeton = jeton, ip = ip)

/** Changement de mot de passe (D81), avec le jeton du joueur. */
fun Serveur.changerMotDePasse(jeton: String?, actuel: String, nouveau: String, ip: String? = Ips.nouvelle()): Reponse =
    api.post(
        "/api/eruditus/v1/comptes/mot-de-passe",
        buildJsonObject {
            put("motDePasse", actuel)
            put("nouveauMotDePasse", nouveau)
        },
        jeton = jeton,
        ip = ip,
    )

/**
 * Avis (D87) avec le jeton du joueur : [texte], puis les versions de l'app et d'Android, omises
 * quand elles sont nulles ; [extra] ajoute des champs au corps (T39).
 */
fun Serveur.avis(
    jeton: String?,
    texte: String,
    versionApp: JsonElement? = JsonPrimitive("1.0.6"),
    android: JsonElement? = JsonPrimitive("15"),
    ip: String? = Ips.nouvelle(),
    extra: Map<String, JsonElement> = emptyMap(),
): Reponse =
    api.post(
        "/api/eruditus/v1/avis",
        buildJsonObject {
            put("texte", texte)
            versionApp?.let { put("versionApp", it) }
            android?.let { put("android", it) }
            extra.forEach { (cle, valeur) -> put(cle, valeur) }
        },
        jeton = jeton,
        ip = ip,
    )

/** Signalement (D119) : [partie], [numero], [motif], commentaire facultatif ; [extra] ajoute des champs au corps. */
fun Serveur.signaler(
    jeton: String?,
    partie: String,
    numero: Int = 1,
    motif: String = "reponse_fausse",
    commentaire: String? = null,
    ip: String? = Ips.nouvelle(),
    extra: Map<String, JsonElement> = emptyMap(),
): Reponse =
    api.post(
        "/api/eruditus/v1/signalement",
        buildJsonObject {
            put("partie", partie)
            put("numero", numero)
            put("motif", motif)
            commentaire?.let { put("commentaire", it) }
            extra.forEach { (cle, valeur) -> put(cle, valeur) }
        },
        jeton = jeton,
        ip = ip,
    )

/** Jeton d'un compte de la collection `users`, créé par le superutilisateur (T14). */
fun Serveur.jetonUsers(): String {
    val email = "u-${Serveur.aleatoire(8).lowercase()}@exemple.fr"
    val creation = su.post(
        "/api/collections/users/records",
        buildJsonObject {
            put("email", email)
            put("password", "motdepasse-users")
            put("passwordConfirm", "motdepasse-users")
        },
    )
    assertEquals("$creation", 200, creation.status)
    val connexion = su.post(
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

/** Nombre d'enregistrements d'une collection qui répondent au filtre (lu par le superutilisateur). */
fun Serveur.compter(collection: String, filtre: String = ""): Int {
    val parametre = if (filtre.isEmpty()) "" else "&filter=" + java.net.URLEncoder.encode(filtre, Charsets.UTF_8)
    val r = su.get("/api/collections/$collection/records?perPage=1$parametre")
    assertEquals("Lecture de $collection : $r", 200, r.status)
    return r.obj.int("totalItems")
}

/** Enregistrements d'une collection (superutilisateur), 500 au plus. */
fun Serveur.lister(collection: String, filtre: String = "", tri: String = ""): List<JsonObject> {
    val parametres = buildString {
        append("?perPage=500")
        if (filtre.isNotEmpty()) append("&filter=").append(java.net.URLEncoder.encode(filtre, Charsets.UTF_8))
        if (tri.isNotEmpty()) append("&sort=").append(java.net.URLEncoder.encode(tri, Charsets.UTF_8))
    }
    val r = su.get("/api/collections/$collection/records$parametres")
    assertEquals("Lecture de $collection : $r", 200, r.status)
    return r.obj.tableau("items").map { it.jsonObject }
}

fun texte(valeur: String): JsonElement = JsonPrimitive(valeur)

package com.eruditus.servertests

import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Chemins du dépôt, passés par Gradle (server/tests/build.gradle.kts). */
object Chemins {
    val serveur: Path = Path.of(
        System.getProperty("eruditus.serveur") ?: error("Propriété système eruditus.serveur absente"),
    )
    val migrations: Path = serveur.resolve("pb_migrations")
    val hooks: Path = serveur.resolve("pb_hooks")
    val version: Path = serveur.resolve("pocketbase.version")
    val questionsExemple: Path = serveur.resolve("tests/questions-exemple.json")
    val casAvis: Path = serveur.resolve("tests/cas-avis.json")
}

/**
 * Binaire PocketBase épinglé par server/pocketbase.version : téléchargé une fois dans
 * ~/.cache/eruditus/pocketbase/<version>/, zip vérifié par SHA-256 à chaque lancement (différent :
 * arrêt), binaire toujours ré-extrait de ce zip vérifié.
 */
object Binaire {
    val chemin: Path by lazy { preparer() }

    private fun preparer(): Path {
        val proprietes = Files.readAllLines(Chemins.version)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
        val version = proprietes["version"] ?: error("version absente de pocketbase.version")
        val url = proprietes["url"] ?: error("url absente de pocketbase.version")
        val sha256 = proprietes["sha256"] ?: error("sha256 absent de pocketbase.version")
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        check(os.contains("linux") && (arch == "amd64" || arch == "x86_64")) {
            "Les tests serveur utilisent le binaire linux_amd64 épinglé ; système : $os $arch"
        }

        val cache = Path.of(System.getProperty("user.home"), ".cache", "eruditus", "pocketbase", version)
        Files.createDirectories(cache)
        val zip = cache.resolve(url.substringAfterLast('/'))
        if (!Files.exists(zip) || empreinte(zip) != sha256) telecharger(url, zip)
        val obtenue = empreinte(zip)
        check(obtenue == sha256) {
            "SHA-256 du zip PocketBase différent de pocketbase.version : attendu $sha256, obtenu $obtenue"
        }

        val binaire = cache.resolve("pocketbase")
        val temporaire = Files.createTempFile(cache, "pocketbase", ".tmp")
        ZipInputStream(Files.newInputStream(zip)).use { entree ->
            var element = entree.nextEntry
            var trouve = false
            while (element != null) {
                if (element.name == "pocketbase") {
                    Files.copy(entree, temporaire, StandardCopyOption.REPLACE_EXISTING)
                    trouve = true
                    break
                }
                element = entree.nextEntry
            }
            check(trouve) { "Le zip ne contient pas le binaire pocketbase" }
        }
        temporaire.toFile().setExecutable(true)
        Files.move(temporaire, binaire, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        return binaire
    }

    private fun telecharger(url: String, cible: Path) {
        val client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build()
        val temporaire = Files.createTempFile(cible.parent, "telechargement", ".tmp")
        val reponse = client.send(
            HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5)).GET().build(),
            HttpResponse.BodyHandlers.ofFile(temporaire),
        )
        check(reponse.statusCode() == 200) { "Téléchargement de $url : statut ${reponse.statusCode()}" }
        Files.move(temporaire, cible, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun sha256(texte: String): String =
        MessageDigest.getInstance("SHA-256").digest(texte.toByteArray()).joinToString("") { "%02x".format(it) }

    fun empreinte(fichier: Path): String {
        val sha = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(fichier).use { entree ->
            val tampon = ByteArray(1 shl 16)
            while (true) {
                val lus = entree.read(tampon)
                if (lus < 0) break
                sha.update(tampon, 0, lus)
            }
        }
        return sha.digest().joinToString("") { "%02x".format(it) }
    }
}

data class ResultatCommande(val code: Int, val sortie: String)

/**
 * Une instance PocketBase sur une base temporaire vide, avec les migrations et hooks du dépôt.
 * [env] règle les variables ERUDITUS_* (aucune par défaut, comme sur le VPS).
 */
class Serveur(
    val nom: String,
    private val env: Map<String, String> = emptyMap(),
    private val questions: Boolean = true,
    /** Dossier des migrations ; un test de migration démarre d'abord sans la dernière (D110). */
    var migrations: java.nio.file.Path = Chemins.migrations,
) {
    val dossier: Path = Files.createTempDirectory("eruditus-$nom-")
    val donnees: Path = dossier.resolve("pb_data")
    private val journal: Path = dossier.resolve("pocketbase.log")
    private var processus: Process? = null
    private var dejaDemarre = false
    var port = 0
        private set

    private val emailSu = "su-${aleatoire(10).lowercase()}@eruditus.invalid"
    private val motDePasseSu = aleatoire(32)

    val url: String get() = "http://127.0.0.1:$port"

    /** Requêtes d'un client ordinaire (l'app, ou un tricheur). */
    val api: Api get() = Api(url)

    /** Requêtes du superutilisateur des tests, pour inspecter la base. */
    val su: Api get() = Api(url, jetonParDefaut = jetonSu, superutilisateur = true)

    lateinit var jetonSu: String
        private set

    init {
        Runtime.getRuntime().addShutdownHook(Thread { arreter(); supprimer(dossier) })
    }

    fun demarrer(): Serveur {
        port = portLibre()
        val commande = listOf(
            Binaire.chemin.toString(), "serve",
            "--dir", donnees.toString(),
            "--migrationsDir", migrations.toString(),
            "--hooksDir", Chemins.hooks.toString(),
            "--hooksWatch=false",
            "--http", "127.0.0.1:$port",
        )
        val constructeur = ProcessBuilder(commande)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(journal.toFile()))
        preparerEnvironnement(constructeur)
        processus = constructeur.start()
        attendreDemarrage()
        if (!dejaDemarre) {
            dejaDemarre = true
            val creation = commande("superuser", "upsert", emailSu, motDePasseSu)
            check(creation.code == 0) { "Création du superutilisateur : ${creation.sortie}" }
            if (questions) {
                val import = importer(Chemins.questionsExemple)
                check(import.code == 0) { "Import de questions-exemple.json : ${import.sortie}" }
            }
        }
        jetonSu = connecterSu()
        return this
    }

    fun arreter() {
        val p = processus ?: return
        p.destroy()
        if (!p.waitFor(15, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            p.waitFor(5, TimeUnit.SECONDS)
        }
        processus = null
    }

    fun redemarrer(): Serveur {
        arreter()
        return demarrer()
    }

    /** Lance une commande du binaire sur la même base (le service tourne, comme sur le VPS). */
    fun commande(vararg arguments: String, entree: String? = null): ResultatCommande {
        val commande = listOf(Binaire.chemin.toString()) + arguments + listOf(
            "--dir", donnees.toString(),
            "--hooksDir", Chemins.hooks.toString(),
            "--migrationsDir", migrations.toString(),
        )
        val constructeur = ProcessBuilder(commande).redirectErrorStream(true)
        preparerEnvironnement(constructeur)
        val p = constructeur.start()
        p.outputStream.use { flux -> entree?.let { flux.write(it.toByteArray()) } }
        val sortie = p.inputStream.bufferedReader().readText()
        check(p.waitFor(60, TimeUnit.SECONDS)) { "Commande ${arguments.toList()} trop longue" }
        return ResultatCommande(p.exitValue(), sortie)
    }

    fun importer(fichier: Path): ResultatCommande = commande("eruditus-questions", fichier.toString())

    fun journal(): String = if (Files.exists(journal)) Files.readString(journal) else ""

    private fun preparerEnvironnement(constructeur: ProcessBuilder) {
        val environnement = constructeur.environment()
        environnement.keys.filter { it.startsWith("ERUDITUS_") }.forEach { environnement.remove(it) }
        environnement.putAll(env)
    }

    private fun attendreDemarrage() {
        val limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (System.nanoTime() < limite) {
            val p = processus ?: error("Serveur $nom arrêté")
            check(p.isAlive) { "PocketBase ($nom) s'est arrêté au démarrage :\n${journal()}" }
            val pret = runCatching { api.get("/api/health", ip = "127.0.0.1").status == 200 }.getOrDefault(false)
            if (pret) return
            Thread.sleep(100)
        }
        error("PocketBase ($nom) ne répond pas :\n${journal()}")
    }

    private fun connecterSu(): String {
        val reponse = api.post(
            "/api/collections/_superusers/auth-with-password",
            buildJsonObject {
                put("identity", emailSu)
                put("password", motDePasseSu)
            },
            verifier = false,
        )
        check(reponse.status == 200) { "Connexion du superutilisateur : $reponse" }
        return reponse.obj.str("token")
    }

    companion object {
        private val hasard = SecureRandom()

        fun aleatoire(longueur: Int): String {
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            return (1..longueur).map { alphabet[hasard.nextInt(alphabet.length)] }.joinToString("")
        }

        private fun portLibre(): Int = ServerSocket(0).use { it.localPort }

        private fun supprimer(dossier: Path) {
            runCatching { dossier.toFile().deleteRecursively() }
        }

        /**
         * Serveur partagé, délais par défaut (20 000 / 3 000 / 1 800 000 ms), 12 questions. Sans
         * `ERUDITUS_AVIS_WEBHOOK` : les avis y sont `avis_indisponible` (D89).
         */
        val principal: Serveur by lazy { Serveur("principal").demarrer() }

        /**
         * Délais raccourcis : hors délai, abandon par inactivité, changement de pseudo et avis
         * après leur délai, sans attendre (§ 10). Avis vers le faux webhook.
         */
        val chronoCourt: Serveur by lazy {
            Serveur(
                "chrono",
                env = mapOf(
                    "ERUDITUS_DELAI_MS" to "1000",
                    "ERUDITUS_MARGE_MS" to "500",
                    "ERUDITUS_INACTIVITE_MS" to "3000",
                    "ERUDITUS_DELAI_PSEUDO_MS" to "1000",
                    "ERUDITUS_AVIS_WEBHOOK" to FauxWebhook.adresse,
                    "ERUDITUS_DELAI_AVIS_MS" to "1000",
                ),
            ).demarrer()
        }

        /**
         * Elo (D102 – D113) : les 5 questions à niveau de la banque d'exemple (niveaux 1 à 5,
         * 800 à 2400), toutes servies dans chaque partie ; inactivité de 4 s pour T47. Constantes
         * de l'Elo celles du VPS (D111).
         */
        val elo: Serveur by lazy {
            Serveur("elo", env = mapOf("ERUDITUS_INACTIVITE_MS" to "4000"), questions = false).demarrer().also {
                it.importerListe(Banque.questions.take(5))
            }
        }

        /** Avis vers le faux webhook (D91), délai des avis par défaut (600 000 ms). */
        val avis: Serveur by lazy {
            Serveur("avis", env = mapOf("ERUDITUS_AVIS_WEBHOOK" to FauxWebhook.adresse)).demarrer()
        }
    }
}

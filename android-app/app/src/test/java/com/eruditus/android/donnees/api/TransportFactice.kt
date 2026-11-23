package com.eruditus.android.donnees.api

import kotlinx.coroutines.CompletableDeferred

/**
 * Serveur factice des tests JVM : garde chaque requête envoyée et répond selon la route. Une
 * route sans réponse prévue fait échouer le test (aucun appel inattendu). Les routes de la partie
 * ont un identifiant dans leur chemin (`{id}`).
 */
class TransportFactice : Transport {

    val requetes = mutableListOf<Requete>()
    private val reponses = mutableMapOf<Route, suspend (Requete) -> ReponseHttp>()

    fun repondre(route: Route, statut: Int, corps: String = "") {
        reponses[route] = { ReponseHttp(statut, corps) }
    }

    /** Réponse calculée à chaque appel : question suivante, correction de la réponse envoyée… */
    fun repondreAvec(route: Route, reponse: suspend (Requete) -> ReponseHttp) {
        reponses[route] = reponse
    }

    /** La réponse de [route] reste en attente jusqu'à ce que le test complète le résultat. */
    fun retenir(route: Route): CompletableDeferred<ReponseHttp> {
        val attente = CompletableDeferred<ReponseHttp>()
        reponses[route] = { attente.await() }
        return attente
    }

    /** Pas de réseau ([connexionEtablie] faux) ou pas de réponse après la connexion (vrai). */
    fun echouer(route: Route, connexionEtablie: Boolean) {
        reponses[route] = { throw TransportEchoue(connexionEtablie) }
    }

    fun requetesVers(route: Route) = requetes.filter { route.correspond(it) }

    override suspend fun envoyer(requete: Requete): ReponseHttp {
        requetes += requete
        val route = Route.entries.first { it.correspond(requete) }
        val reponse = reponses[route] ?: throw AssertionError("appel inattendu : ${route.name}")
        return reponse(requete)
    }

    companion object {
        const val INFO = """{"versionMinimale":1000}"""
        const val CODE = "7K3M-Q9XA-2HDE-F8RT"
        const val COMPTE_CREE =
            """{"token":"jeton-cree","joueur":{"id":"id-hibou","pseudo":"Hibou_42"},"codeRecuperation":"$CODE"}"""
        const val CONNEXION =
            """{"token":"jeton-connexion","record":{"id":"id-hibou","pseudo":"Hibou_42","collectionName":"eruditus_joueurs"}}"""
        const val RENOUVELLEMENT =
            """{"token":"jeton-renouvele","record":{"id":"id-hibou","pseudo":"Hibou_42","collectionName":"eruditus_joueurs"}}"""
        const val RECUPERATION =
            """{"token":"jeton-recupere","joueur":{"id":"id-hibou","pseudo":"Hibou_42"},"codeRecuperation":"Q9XA-7K3M-F8RT-2HDE"}"""
        /** `POST /comptes/pseudo` (D79). */
        const val PSEUDO_CHANGE = """{"joueur":{"id":"id-hibou","pseudo":"Chouette_7"}}"""

        /** `POST /comptes/mot-de-passe` (D81) : un jeton neuf. */
        const val MOT_DE_PASSE_CHANGE = """{"token":"jeton-neuf","joueur":{"id":"id-hibou","pseudo":"Hibou_42"},"champFutur":1}"""

        /** `GET /moi` (D109) : `score`, `rang` et `classes` par points restent envoyés à l'app précédente, l'app de l'Elo ne les lit pas. */
        fun moi(elo: String = ELO_CLASSE, score: Int = 42) =
            """{"joueur":{"id":"id-hibou","pseudo":"Hibou_42"},"score":$score,"rang":9,"classes":48,"elo":$elo}"""

        const val ELO_CLASSE = """{"valeur":1247,"provisoire":false,"restantes":0,"rang":3,"classes":18,"revision":false}"""
        const val ELO_PROVISOIRE = """{"valeur":1012,"provisoire":true,"restantes":12,"rang":null,"classes":18,"revision":false,"champFutur":1}"""
        val MOI = moi()

        /** L'objet `bilan` de la dernière correction (D109). */
        fun bilan(valeur: Int = 1261, variation: Int = 14, revision: Boolean = false) =
            """{"bonnes":3,"points":2,"score":14,"elo":{"valeur":$valeur,"variation":$variation,"revision":$revision}}"""

        /** Une ligne de classement : rang, pseudo, Elo, rien d'autre (T30). */
        fun ligne(rang: Int, pseudo: String, elo: Int) = """{"rang":$rang,"pseudo":"$pseudo","elo":$elo}"""

        /** Réponse de `GET /classement/elo` ; [moi] : une [ligne] ou `null` (provisoire). */
        fun classement(top: List<String>, moi: String = "null", classes: Int = 48, restantes: Int = 0) =
            """{"top":[${top.joinToString(",")}],"moi":$moi,"classes":$classes,"restantes":$restantes}"""

        /** Un top 10 sans ex æquo, rangs 1 à 10, Elo 1990 à 1900, et ma ligne au rang 27. */
        val CLASSEMENT = classement((1..10).map { ligne(it, "Joueur_$it", 2000 - 10 * it) }, moi = ligne(27, "Hibou_42", 1180))

        /** Une partie de `GET /historique` (D131) ; [variation] et [valeur] nuls : partie sans effet sur l'Elo. */
        fun partieListee(
            id: String,
            date: String = "2026-11-22T10:00:00.123Z",
            etat: String = "terminee",
            bonnes: Int = 3,
            servies: Int = 5,
            variation: Int? = 14,
            valeur: Int? = 1261,
        ) = """{"id":"$id","date":"$date","etat":"$etat","bonnes":$bonnes,"servies":$servies,"elo":{"variation":$variation,"valeur":$valeur}}"""

        /** Réponse de `GET /historique` : [suivant], le curseur opaque, ou `null`. */
        fun historique(parties: List<String>, suivant: String? = null) =
            """{"parties":[${parties.joinToString(",")}],"suivant":${suivant?.let { "\"$it\"" }}}"""

        /** Réponse de `GET /elo/courbe` (D135) : [points] du plus ancien au plus récent, `date` à `elo`. */
        fun courbe(depart: Int?, points: List<Pair<String, Int>>, provisoire: Boolean = false) =
            """{"depart":$depart,"points":[${points.joinToString(",") { """{"date":"${it.first}","elo":${it.second}}""" }}],"provisoire":$provisoire}"""

        /** Identifiant de partie de PocketBase : 15 caractères `a-z0-9`. */
        const val ID_PARTIE = "p4rt1e0000abcde"

        fun erreur(code: String, statut: Int = 400) = """{"status":$statut,"message":"Erreur.","data":{"code":"$code"}}"""

        /** L'objet `question` de spec/serveur.md § 5.2. */
        fun objetQuestion(numero: Int, restantMs: Long = 20_000, total: Int = 5) =
            """{"numero":$numero,"total":$total,"enonce":"Énoncé $numero ?","choix":["A$numero","B$numero","C$numero","D$numero"],"delaiMs":20000,"restantMs":$restantMs}"""

        /** Réponse de `POST /parties`. */
        fun partieCreee(restantMs: Long = 20_000, id: String = ID_PARTIE) =
            """{"partie":{"id":"$id","total":5},"question":${objetQuestion(1, restantMs)}}"""

        /** Réponse de `POST /parties/{id}/question`. */
        fun questionServie(numero: Int, restantMs: Long = 20_000) = """{"question":${objetQuestion(numero, restantMs)}}"""

        /** Réponse de `POST /parties/{id}/reponse` ; [bilan] : objet JSON ou `null`. */
        fun corrigee(
            numero: Int,
            choix: Int?,
            bonne: Int,
            raison: String,
            correcte: Boolean = raison == "gagne" || raison == "deja_reussie",
            point: Int = if (raison == "gagne") 1 else 0,
            horsDelai: Boolean = raison == "hors_delai",
            score: Int = 12,
            bilan: String = "null",
            contenu: String = "",
        ) = """{"correction":{"numero":$numero,"choix":$choix,"correcte":$correcte,"bonneReponse":$bonne,""" +
            """"horsDelai":$horsDelai,"point":$point,"raison":"$raison","score":$score$contenu},"bilan":$bilan}"""
    }
}

/** La requête vise-t-elle cette route ? `{id}` vaut n'importe quel segment de chemin. */
fun Route.correspond(requete: Requete): Boolean {
    val motif = chemin.split("{id}").joinToString("[^/]+") { Regex.escape(it) }
    // Le texte de la requête (`?avant=…`) ne fait pas partie de la route.
    return methode == requete.methode && Regex("^$motif$").matches(requete.chemin.substringBefore('?'))
}

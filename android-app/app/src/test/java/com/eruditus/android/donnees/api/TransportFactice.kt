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
        const val MOI = """{"joueur":{"id":"id-hibou","pseudo":"Hibou_42"},"score":12,"rang":3,"classes":48}"""
        const val MOI_NON_CLASSE = """{"joueur":{"id":"id-hibou","pseudo":"Hibou_42"},"score":0,"rang":null,"classes":48,"champFutur":true}"""

        /** Une ligne de classement : rang, pseudo, score, rien d'autre (T30). */
        fun ligne(rang: Int, pseudo: String, score: Int) = """{"rang":$rang,"pseudo":"$pseudo","score":$score}"""

        /** Réponse de `GET /classement` ; [moi] : une [ligne] ou `null`. */
        fun classement(top: List<String>, moi: String = "null", classes: Int = 48) =
            """{"top":[${top.joinToString(",")}],"moi":$moi,"classes":$classes}"""

        /** Un top 10 sans ex æquo, rangs 1 à 10, scores 19 à 10, et ma ligne au rang 27. */
        val CLASSEMENT = classement((1..10).map { ligne(it, "Joueur_$it", 20 - it) }, moi = ligne(27, "Hibou_42", 4))

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
        ) = """{"correction":{"numero":$numero,"choix":$choix,"correcte":$correcte,"bonneReponse":$bonne,""" +
            """"horsDelai":$horsDelai,"point":$point,"raison":"$raison","score":$score},"bilan":$bilan}"""
    }
}

/** La requête vise-t-elle cette route ? `{id}` vaut n'importe quel segment de chemin. */
fun Route.correspond(requete: Requete): Boolean {
    val motif = chemin.split("{id}").joinToString("[^/]+") { Regex.escape(it) }
    return methode == requete.methode && Regex("^$motif$").matches(requete.chemin)
}

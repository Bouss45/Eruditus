package com.eruditus.android.donnees.api

/**
 * Serveur factice des tests JVM : garde chaque requête envoyée et répond selon la route. Une
 * route sans réponse prévue fait échouer le test (aucun appel inattendu).
 */
class TransportFactice : Transport {

    val requetes = mutableListOf<Requete>()
    private val reponses = mutableMapOf<Route, () -> ReponseHttp>()

    fun repondre(route: Route, statut: Int, corps: String = "") {
        reponses[route] = { ReponseHttp(statut, corps) }
    }

    /** Pas de réseau ([connexionEtablie] faux) ou pas de réponse après la connexion (vrai). */
    fun echouer(route: Route, connexionEtablie: Boolean) {
        reponses[route] = { throw TransportEchoue(connexionEtablie) }
    }

    fun requetesVers(route: Route) = requetes.filter { it.chemin == route.chemin && it.methode == route.methode }

    override suspend fun envoyer(requete: Requete): ReponseHttp {
        requetes += requete
        val route = Route.entries.first { it.chemin == requete.chemin && it.methode == requete.methode }
        val reponse = reponses[route] ?: throw AssertionError("appel inattendu : ${route.name}")
        return reponse()
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

        fun erreur(code: String) = """{"status":400,"message":"Erreur.","data":{"code":"$code"}}"""
    }
}

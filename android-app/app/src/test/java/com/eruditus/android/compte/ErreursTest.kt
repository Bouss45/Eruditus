package com.eruditus.android.compte

import com.eruditus.android.compte.Affichage.AuDessusAction
import com.eruditus.android.compte.Affichage.EnHaut
import com.eruditus.android.compte.Affichage.PasDeConnexion
import com.eruditus.android.compte.Affichage.SessionExpiree
import com.eruditus.android.compte.Affichage.SousMotDePasse
import com.eruditus.android.compte.Affichage.SousPseudo
import com.eruditus.android.donnees.api.ErreurApi
import com.eruditus.android.donnees.api.ErreurApi.NonAutorise
import com.eruditus.android.donnees.api.ErreurApi.Refus
import com.eruditus.android.donnees.api.ErreurApi.Reseau
import com.eruditus.android.donnees.api.ErreurApi.Serveur
import com.eruditus.android.donnees.api.ErreurApi.TropDeTentatives
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chaque erreur de l'API, sur chaque écran du compte, donne son message à sa place
 * (spec/ecrans.md § 15, D53). Les textes eux-mêmes sont confrontés à la spec par
 * `TextesErreursTest`.
 */
class ErreursTest {

    private fun refus(code: String?, statut: Int = 400) = Refus(statut, code)

    private fun verifier(ecran: EcranErreur, attendus: List<Pair<ErreurApi, Affichage>>) {
        for ((erreur, attendu) in attendus) assertEquals("$ecran, $erreur", attendu, affichage(erreur, ecran))
    }

    /** Ce que tous les écrans du compte partagent : réseau, serveur. */
    private val communs = listOf(
        Reseau to PasDeConnexion(serveur = false),
        Serveur to PasDeConnexion(serveur = true),
    )

    @Test
    fun `creer un compte`() = verifier(
        EcranErreur.Creation,
        communs + listOf(
            refus("pseudo_invalide") to SousPseudo(TexteErreur.PseudoInvalide),
            refus("pseudo_reserve") to SousPseudo(TexteErreur.PseudoReserve),
            refus("pseudo_pris") to SousPseudo(TexteErreur.PseudoPris),
            refus("mot_de_passe_invalide") to SousMotDePasse(TexteErreur.MotDePasseInvalide),
            TropDeTentatives to EnHaut(TexteErreur.TropDeTentatives),
            refus("requete_invalide") to EnHaut(TexteErreur.Generique),
            refus("code_que_l_app_ne_connait_pas") to EnHaut(TexteErreur.Generique),
            refus(null) to EnHaut(TexteErreur.Generique),
            // Une route publique ne reçoit jamais de jeton (D30) : un 401 n'y est pas une session expirée.
            NonAutorise to EnHaut(TexteErreur.Generique),
        ),
    )

    @Test
    fun `se connecter`() = verifier(
        EcranErreur.Connexion,
        communs + listOf(
            refus(null) to AuDessusAction(TexteErreur.ConnexionEchouee),
            refus("validation_required") to AuDessusAction(TexteErreur.ConnexionEchouee),
            TropDeTentatives to EnHaut(TexteErreur.TropDeTentatives),
            refus(null, statut = 404) to EnHaut(TexteErreur.Generique),
            NonAutorise to EnHaut(TexteErreur.Generique),
        ),
    )

    @Test
    fun `mot de passe oublie`() = verifier(
        EcranErreur.Recuperation,
        communs + listOf(
            refus("mot_de_passe_invalide") to SousMotDePasse(TexteErreur.MotDePasseInvalide),
            refus("code_incorrect") to AuDessusAction(TexteErreur.CodeIncorrect),
            TropDeTentatives to EnHaut(TexteErreur.TropDeTentatives),
            refus("requete_invalide") to EnHaut(TexteErreur.Generique),
            NonAutorise to EnHaut(TexteErreur.Generique),
        ),
    )

    @Test
    fun `accueil`() = verifier(
        EcranErreur.Accueil,
        communs + listOf(
            NonAutorise to SessionExpiree,
            TropDeTentatives to EnHaut(TexteErreur.TropDeTentatives),
            refus("requete_invalide") to EnHaut(TexteErreur.Generique),
            refus(null, statut = 404) to EnHaut(TexteErreur.Generique),
        ),
    )

    @Test
    fun `boite de suppression, erreurs sous son champ`() = verifier(
        EcranErreur.Suppression,
        communs + listOf(
            refus("mot_de_passe_incorrect") to SousMotDePasse(TexteErreur.MotDePasseIncorrect),
            TropDeTentatives to SousMotDePasse(TexteErreur.TropDeTentatives),
            refus("requete_invalide") to SousMotDePasse(TexteErreur.Generique),
            refus(null) to SousMotDePasse(TexteErreur.Generique),
            NonAutorise to SessionExpiree,
        ),
    )

    @Test
    fun `message en haut, alerte pour 429 et erreur generique, info sinon`() {
        assertTrue(TexteErreur.TropDeTentatives.alerte)
        assertTrue(TexteErreur.Generique.alerte)
        for (texte in TexteErreur.entries - TexteErreur.TropDeTentatives - TexteErreur.Generique) {
            assertFalse(texte.name, texte.alerte)
        }
    }
}

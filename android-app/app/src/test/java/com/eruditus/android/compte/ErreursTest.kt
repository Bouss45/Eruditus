package com.eruditus.android.compte

import com.eruditus.android.compte.Affichage.AuDessusAction
import com.eruditus.android.compte.Affichage.EnHaut
import com.eruditus.android.compte.Affichage.PasDeConnexion
import com.eruditus.android.compte.Affichage.SessionExpiree
import com.eruditus.android.compte.Affichage.SousAvis
import com.eruditus.android.compte.Affichage.SousMotDePasse
import com.eruditus.android.compte.Affichage.SousMotDePasseActuel
import com.eruditus.android.compte.Affichage.SousPseudo
import com.eruditus.android.compte.Affichage.SousSignalement
import com.eruditus.android.compte.Affichage.VersAccueil
import com.eruditus.android.compte.Affichage.VersHistorique
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
    fun `classement`() = verifier(
        EcranErreur.Classement,
        communs + listOf(
            NonAutorise to SessionExpiree,
            TropDeTentatives to EnHaut(TexteErreur.TropDeTentatives),
            refus("requete_invalide") to EnHaut(TexteErreur.Generique),
            refus(null, statut = 404) to EnHaut(TexteErreur.Generique),
        ),
    )

    @Test
    fun `historique, curseur illisible compris`() = verifier(
        EcranErreur.Historique,
        communs + listOf(
            NonAutorise to SessionExpiree,
            TropDeTentatives to EnHaut(TexteErreur.TropDeTentatives),
            refus("requete_invalide") to EnHaut(TexteErreur.Generique),
            refus("partie_introuvable", statut = 404) to EnHaut(TexteErreur.Generique),
            refus(null, statut = 404) to EnHaut(TexteErreur.Generique),
        ),
    )

    @Test
    fun `detail d'une partie, partie introuvable retourne a l'Historique`() = verifier(
        EcranErreur.DetailPartie,
        communs + listOf(
            refus("partie_introuvable", statut = 404) to VersHistorique(TexteErreur.PartieIndisponible),
            NonAutorise to SessionExpiree,
            TropDeTentatives to EnHaut(TexteErreur.TropDeTentatives),
            refus("requete_invalide") to EnHaut(TexteErreur.Generique),
            refus(null, statut = 404) to EnHaut(TexteErreur.Generique),
        ),
    )

    @Test
    fun `cette partie n'est plus disponible est une info, pas une alerte`() {
        assertFalse(TexteErreur.PartieIndisponible.alerte)
    }

    @Test
    fun `changer de pseudo, les erreurs du pseudo de la creation et le delai`() {
        verifier(
            EcranErreur.ChangementPseudo,
            communs + listOf(
                refus("pseudo_trop_recent", statut = 409) to SousPseudo(TexteErreur.PseudoTropRecent),
                NonAutorise to SessionExpiree,
                TropDeTentatives to EnHaut(TexteErreur.TropDeTentatives),
                refus("requete_invalide") to EnHaut(TexteErreur.Generique),
                refus("mot_de_passe_invalide") to EnHaut(TexteErreur.Generique),
                refus(null, statut = 404) to EnHaut(TexteErreur.Generique),
            ),
        )
        // Une seule règle pour les codes du pseudo : ceux de la création.
        for (code in listOf("pseudo_invalide", "pseudo_reserve", "pseudo_pris")) {
            assertEquals(code, affichage(refus(code), EcranErreur.Creation), affichage(refus(code), EcranErreur.ChangementPseudo))
        }
    }

    @Test
    fun `changer de mot de passe, l'actuel et le nouveau sous leur champ`() = verifier(
        EcranErreur.ChangementMotDePasse,
        communs + listOf(
            refus("mot_de_passe_incorrect") to SousMotDePasseActuel(TexteErreur.MotDePasseIncorrect),
            refus("mot_de_passe_invalide") to SousMotDePasse(TexteErreur.MotDePasseInvalide),
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
    fun `jouer ou rejouer`() = verifier(
        EcranErreur.Jouer,
        communs + listOf(
            refus("banque_insuffisante", statut = 503) to VersAccueil(TexteErreur.AucunePartie),
            NonAutorise to SessionExpiree,
            TropDeTentatives to EnHaut(TexteErreur.TropDeTentatives),
            refus("requete_invalide") to EnHaut(TexteErreur.Generique),
            refus("code_que_l_app_ne_connait_pas") to EnHaut(TexteErreur.Generique),
            refus(null, statut = 404) to EnHaut(TexteErreur.Generique),
        ),
    )

    @Test
    fun `pendant la partie`() = verifier(
        EcranErreur.Partie,
        communs + listOf(
            refus("partie_introuvable", statut = 404) to VersAccueil(TexteErreur.PartiePlusValable),
            refus("partie_finie", statut = 409) to VersAccueil(TexteErreur.PartiePlusValable),
            refus("numero_invalide", statut = 409) to VersAccueil(TexteErreur.PartiePlusValable),
            refus("deja_repondu", statut = 409) to VersAccueil(TexteErreur.PartiePlusValable),
            NonAutorise to SessionExpiree,
            TropDeTentatives to EnHaut(TexteErreur.TropDeTentatives),
            refus("choix_invalide") to EnHaut(TexteErreur.Generique),
            refus("requete_invalide") to EnHaut(TexteErreur.Generique),
            refus(null, statut = 404) to EnHaut(TexteErreur.Generique),
        ),
    )

    @Test
    fun `envoyer un avis, reseau et serveur en haut, le texte reste`() = verifier(
        EcranErreur.Avis,
        listOf(
            // Écart voulu au § 15 (D93) : pas d'écran « Pas de connexion », qui perdrait le texte.
            Reseau to EnHaut(TexteErreur.ReseauEnHaut),
            Serveur to EnHaut(TexteErreur.ServeurEnHaut),
            refus("avis_invalide") to SousAvis(TexteErreur.AvisInvalide),
            refus("avis_trop_recent", statut = 409) to SousAvis(TexteErreur.AvisTropRecent),
            refus("avis_indisponible", statut = 503) to EnHaut(TexteErreur.AvisIndisponible),
            NonAutorise to SessionExpiree,
            TropDeTentatives to EnHaut(TexteErreur.TropDeTentatives),
            refus("requete_invalide") to EnHaut(TexteErreur.Generique),
            refus("code_que_l_app_ne_connait_pas", statut = 503) to EnHaut(TexteErreur.Generique),
            refus(null, statut = 404) to EnHaut(TexteErreur.Generique),
        ),
    )

    @Test
    fun `signaler une question, tout sous le champ de la boite, la partie continue`() = verifier(
        EcranErreur.Signalement,
        listOf(
            // D118 : la boîte garde le motif et le commentaire, aucune erreur ne quitte la partie.
            refus("signalement_trop_frequent", statut = 409) to SousSignalement(TexteErreur.SignalementTropFrequent),
            Reseau to SousSignalement(TexteErreur.ReseauEnHaut),
            Serveur to SousSignalement(TexteErreur.ServeurEnHaut),
            NonAutorise to SessionExpiree,
            TropDeTentatives to SousSignalement(TexteErreur.Generique),
            refus("signalement_invalide") to SousSignalement(TexteErreur.Generique),
            refus("partie_introuvable", statut = 404) to SousSignalement(TexteErreur.Generique),
            refus("numero_invalide", statut = 409) to SousSignalement(TexteErreur.Generique),
            refus("requete_invalide") to SousSignalement(TexteErreur.Generique),
            refus(null, statut = 404) to SousSignalement(TexteErreur.Generique),
        ),
    )

    @Test
    fun `message en haut, alerte pour 429, erreur generique, reseau et serveur, info sinon`() {
        val alertes = setOf(TexteErreur.TropDeTentatives, TexteErreur.Generique, TexteErreur.ReseauEnHaut, TexteErreur.ServeurEnHaut)
        for (texte in alertes) assertTrue(texte.name, texte.alerte)
        for (texte in TexteErreur.entries - alertes) assertFalse(texte.name, texte.alerte)
        assertFalse(TexteErreur.AvisIndisponible.alerte)
    }
}

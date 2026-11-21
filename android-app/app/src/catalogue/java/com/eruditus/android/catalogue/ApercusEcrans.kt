package com.eruditus.android.catalogue

import androidx.compose.runtime.Composable
import com.eruditus.android.compte.TexteErreur
import com.eruditus.android.classement.EtatClassement
import com.eruditus.android.donnees.api.Bilan
import com.eruditus.android.donnees.api.Classement
import com.eruditus.android.donnees.api.Correction
import com.eruditus.android.donnees.api.Moi
import com.eruditus.android.donnees.api.Place
import com.eruditus.android.donnees.api.QuestionServie
import com.eruditus.android.donnees.api.Raison
import com.eruditus.android.donnees.api.ReponseCorrigee
import com.eruditus.android.partie.EtatPartie
import com.eruditus.android.partie.Phase
import com.eruditus.android.ui.ecrans.ArriveeBienvenue
import com.eruditus.android.ui.ecrans.EcranAccueil
import com.eruditus.android.ui.ecrans.EcranBienvenue
import com.eruditus.android.ui.ecrans.EcranClassement
import com.eruditus.android.ui.ecrans.EcranCode
import com.eruditus.android.ui.ecrans.EcranCompte
import com.eruditus.android.ui.ecrans.EcranConnexion
import com.eruditus.android.ui.ecrans.EcranCreation
import com.eruditus.android.ui.ecrans.EcranMiseAJour
import com.eruditus.android.ui.ecrans.EcranPartie
import com.eruditus.android.ui.ecrans.EcranPasDeConnexion
import com.eruditus.android.ui.ecrans.EcranRecuperation
import com.eruditus.android.ui.ecrans.EcranResultat
import com.eruditus.android.ui.ecrans.EtatAccueil
import com.eruditus.android.ui.ecrans.EtatCode
import com.eruditus.android.ui.ecrans.EtatCompte
import com.eruditus.android.ui.ecrans.EtatConnexion
import com.eruditus.android.ui.ecrans.EtatCreation
import com.eruditus.android.ui.ecrans.EtatRecuperation
import com.eruditus.android.ui.ecrans.EtatResultat

/*
 * Aperçus des écrans (spec/ecrans.md § 3 à § 14), un par état des planches « Écrans »
 * (Accès, Jeu, Classement et Compte, Système) ; sombre, clair et sombre à 200 %. Les boîtes
 * (fenêtres à part) se voient dans le catalogue et sur le téléphone.
 */

private val rien: () -> Unit = {}
private val ignore: (String) -> Unit = {}

@ApercusGabarit
@Composable
fun ApercuEcranBienvenueSessionExpiree() = ApercuGabarit {
    EcranBienvenue(ArriveeBienvenue.SessionExpiree, rien, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranCreationVide() = ApercuGabarit {
    EcranCreation(EtatCreation(), ignore, ignore, rien, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranCreationPseudoPris() = ApercuGabarit {
    EcranCreation(
        EtatCreation(pseudo = "Hibou_42", motDePasse = "nuit-blanche-42", erreurPseudoServeur = TexteErreur.PseudoPris),
        ignore, ignore, rien, rien,
    )
}

@ApercusGabarit
@Composable
fun ApercuEcranCreationSaisieRefusee() = ApercuGabarit {
    EcranCreation(EtatCreation(pseudo = "Hibou-42", motDePasse = "court"), ignore, ignore, rien, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranCreationEnvoi() = ApercuGabarit {
    EcranCreation(EtatCreation(pseudo = "Hibou_42", motDePasse = "nuit-blanche-42", envoi = true), ignore, ignore, rien, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranCode() = ApercuGabarit {
    EcranCode(EtatCode("7K3M-Q9XA-2HDE-F8RT"), rien, rien, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranCodeCopie() = ApercuGabarit {
    EcranCode(EtatCode("7K3M-Q9XA-2HDE-F8RT", copie = true), rien, rien, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranConnexionRefusee() = ApercuGabarit {
    EcranConnexion(
        EtatConnexion(pseudo = "Hibou_42", motDePasse = "nuit-blanche", erreur = TexteErreur.ConnexionEchouee),
        ignore, ignore, rien, rien, rien,
    )
}

@ApercusGabarit
@Composable
fun ApercuEcranConnexionTropDeTentatives() = ApercuGabarit {
    EcranConnexion(
        EtatConnexion(pseudo = "Hibou_42", motDePasse = "nuit-blanche-42", message = TexteErreur.TropDeTentatives),
        ignore, ignore, rien, rien, rien,
    )
}

@ApercusGabarit
@Composable
fun ApercuEcranRecuperationCodeRefuse() = ApercuGabarit {
    EcranRecuperation(
        EtatRecuperation(pseudo = "Hibou_42", code = "7k3m q9xa 2hde f8rt", motDePasse = "lune-glacee-17", erreur = TexteErreur.CodeIncorrect),
        ignore, ignore, ignore, rien, rien,
    )
}

@ApercusGabarit
@Composable
fun ApercuEcranAccueil() = ApercuGabarit {
    EcranAccueil(EtatAccueil(Moi("Hibou_42", 12, 3, 48), chargement = false), "Hibou_42", "1.0.12", rien, rien, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranAccueilMiseAJour() = ApercuGabarit {
    EcranAccueil(EtatAccueil(Moi("Hibou_42", 12, 3, 48), chargement = false, annonce = RELEASE_EXEMPLE), "Hibou_42", "1.0.12", rien, rien, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranAccueilPasEncoreClasse() = ApercuGabarit {
    EcranAccueil(EtatAccueil(Moi("Hibou_42", 0, null, 48), chargement = false), "Hibou_42", "1.0.12", rien, rien, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranAccueilChargement() = ApercuGabarit {
    EcranAccueil(EtatAccueil(), "Hibou_42", "1.0.12", rien, rien, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranAccueilTropDeTentatives() = ApercuGabarit {
    EcranAccueil(
        EtatAccueil(Moi("Hibou_42", 1, 1, 1), chargement = false, message = TexteErreur.TropDeTentatives),
        "Hibou_42",
        "1.0.12",
        rien,
        rien,
        rien,
    )
}

@ApercusGabarit
@Composable
fun ApercuEcranAccueilAucunePartie() = ApercuGabarit {
    EcranAccueil(
        EtatAccueil(Moi("Hibou_42", 12, 3, 48), chargement = false, message = TexteErreur.AucunePartie),
        "Hibou_42",
        "1.0.12",
        rien,
        rien,
        rien,
    )
}

/** Le top 10 des maquettes (planche « Classement et Compte ») ; [moi] : ma place, au rang 3 si dedans. */
private fun classementExemple(moi: Place?): Classement {
    val dedans = moi?.rang == 3
    val top = if (dedans) {
        listOf("Hypatie" to 19, "LeBaronPerche" to 15, "Hibou_42" to 12, "MarieCurieDuDimanche" to 11, "Albatros" to 10,
            "tartine42" to 9, "Vercingetorix" to 8, "Zoe_L" to 7, "Quiz_Kid" to 6, "Penelope" to 5)
    } else {
        listOf("Hypatie" to 19, "LeBaronPerche" to 15, "MarieCurieDuDimanche" to 14, "Albatros" to 12, "tartine42" to 11,
            "Vercingetorix" to 10, "Zoe_L" to 9, "Quiz_Kid" to 8, "Penelope" to 7, "LaChouette" to 6)
    }
    return Classement(top.mapIndexed { i, (pseudo, score) -> Place(i + 1, pseudo, score) }, moi, classes = 48)
}

private val CLASSEMENT_HORS_TOP = classementExemple(Place(27, "Hibou_42", 4))

@ApercusGabarit
@Composable
fun ApercuEcranClassementTop10() = ApercuGabarit {
    EcranClassement(EtatClassement(classementExemple(Place(3, "Hibou_42", 12))), rien, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranClassementHorsTop() = ApercuGabarit {
    EcranClassement(EtatClassement(CLASSEMENT_HORS_TOP), rien, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranClassementPasEncoreClasse() = ApercuGabarit {
    EcranClassement(EtatClassement(classementExemple(null)), rien, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranClassementPersonne() = ApercuGabarit {
    EcranClassement(EtatClassement(Classement(emptyList(), null, classes = 0)), rien, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranClassementChargement() = ApercuGabarit {
    EcranClassement(EtatClassement(chargement = true), rien, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranClassementTirage() = ApercuGabarit {
    EcranClassement(EtatClassement(CLASSEMENT_HORS_TOP, chargement = true), rien, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranClassementTropDeTentatives() = ApercuGabarit {
    EcranClassement(EtatClassement(CLASSEMENT_HORS_TOP, message = TexteErreur.TropDeTentatives), rien, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranCompte() = ApercuGabarit {
    EcranCompte(EtatCompte(), "Hibou_42", "1.0.12", rien, {}, rien, rien, ignore, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranPasDeConnexionReseau() = ApercuGabarit {
    EcranPasDeConnexion(serveur = false, enCours = false, onReessayer = rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranPasDeConnexionServeur() = ApercuGabarit {
    EcranPasDeConnexion(serveur = true, enCours = false, onReessayer = rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranPasDeConnexionReessai() = ApercuGabarit {
    EcranPasDeConnexion(serveur = false, enCours = true, onReessayer = rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranMiseAJour() = ApercuGabarit {
    EcranMiseAJour(onMettreAJour = rien)
}

// Partie et Résultat (planche « Écrans · Jeu ») ------------------------------------------------

private val Q2 = QuestionServie(2, 5, ENONCE_Q2, CHOIX_Q2, 20_000, 20_000)
private val Q5 = QuestionServie(5, 5, ENONCE_Q5, CHOIX_Q5, 20_000, 20_000)

/** La Partie de [question] dans [phase], le chrono à [restantMs]. */
@Composable
private fun ExempleEcranPartie(question: QuestionServie, phase: Phase, restantMs: Long) {
    EcranPartie(
        etat = EtatPartie("p4rt1e0000abcde", question, finMs = 0, phase = phase),
        restantMs = restantMs,
        abandon = false,
        onChoix = {},
        onBouton = rien,
        onFermer = rien,
        onContinuer = rien,
        onAbandonner = rien,
    )
}

/** Une correction de la question 2 (bonne réponse C), ou de la 5e avec son bilan. */
private fun corrigee(choix: Int?, bonne: Int, raison: Raison, numero: Int = 2, restantMs: Long = 9_000): Phase.Corrigee {
    val correcte = raison == Raison.Gagne || raison == Raison.DejaReussie
    val correction = Correction(numero, choix, correcte, bonne, raison == Raison.HorsDelai, if (raison == Raison.Gagne) 1 else 0, raison, 14)
    return Phase.Corrigee(ReponseCorrigee(correction, if (numero == 5) Bilan(3, 2, 14) else null), restantMs)
}

@ApercusGabarit
@Composable
fun ApercuEcranPartieQuestion() = ApercuGabarit {
    ExempleEcranPartie(Q2, Phase.Attente, restantMs = 14_000)
}

@ApercusGabarit
@Composable
fun ApercuEcranPartieEnvoi() = ApercuGabarit {
    ExempleEcranPartie(Q2, Phase.Envoi(choix = 0, restantMs = 9_000), restantMs = 9_000)
}

@ApercusGabarit
@Composable
fun ApercuEcranPartieGagne() = ApercuGabarit {
    ExempleEcranPartie(Q2, corrigee(choix = 2, bonne = 2, raison = Raison.Gagne), restantMs = 9_000)
}

@ApercusGabarit
@Composable
fun ApercuEcranPartieDejaReussie() = ApercuGabarit {
    ExempleEcranPartie(Q2, corrigee(choix = 2, bonne = 2, raison = Raison.DejaReussie), restantMs = 9_000)
}

@ApercusGabarit
@Composable
fun ApercuEcranPartieFausse() = ApercuGabarit {
    ExempleEcranPartie(Q2, corrigee(choix = 0, bonne = 2, raison = Raison.Fausse), restantMs = 9_000)
}

@ApercusGabarit
@Composable
fun ApercuEcranPartieTempsEcoule() = ApercuGabarit {
    ExempleEcranPartie(Q5, corrigee(choix = null, bonne = 3, raison = Raison.SansReponse, numero = 5, restantMs = 0), restantMs = 0)
}

@ApercusGabarit
@Composable
fun ApercuEcranResultat() = ApercuGabarit {
    EcranResultat(EtatResultat(Bilan(bonnes = 3, points = 2, score = 14), total = 5), chargement = false, onRejouer = rien, onAccueil = rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranResultatAucunPoint() = ApercuGabarit {
    EcranResultat(EtatResultat(Bilan(bonnes = 1, points = 0, score = 12), total = 5), chargement = false, onRejouer = rien, onAccueil = rien)
}

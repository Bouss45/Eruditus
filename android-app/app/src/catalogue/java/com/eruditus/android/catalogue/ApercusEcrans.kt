package com.eruditus.android.catalogue

import androidx.compose.runtime.Composable
import com.eruditus.android.compte.TexteErreur
import com.eruditus.android.classement.EtatClassement
import com.eruditus.android.donnees.api.Bilan
import com.eruditus.android.donnees.api.Classement
import com.eruditus.android.donnees.api.Correction
import com.eruditus.android.donnees.api.EloBilan
import com.eruditus.android.donnees.api.EloMoi
import com.eruditus.android.donnees.api.Moi
import com.eruditus.android.donnees.api.Place
import com.eruditus.android.donnees.api.QuestionServie
import com.eruditus.android.donnees.api.Raison
import com.eruditus.android.donnees.api.ReponseCorrigee
import com.eruditus.android.partie.EtatPartie
import com.eruditus.android.partie.Phase
import com.eruditus.android.ui.ecrans.ArriveeBienvenue
import com.eruditus.android.ui.ecrans.EcranAccueil
import com.eruditus.android.ui.ecrans.EcranAvis
import com.eruditus.android.ui.ecrans.EcranBienvenue
import com.eruditus.android.ui.ecrans.EcranClassement
import com.eruditus.android.ui.ecrans.EcranCode
import com.eruditus.android.ui.ecrans.EcranChangementMotDePasse
import com.eruditus.android.ui.ecrans.EcranChangementPseudo
import com.eruditus.android.ui.ecrans.EcranParametres
import com.eruditus.android.ui.ecrans.EcranConnexion
import com.eruditus.android.ui.ecrans.EcranCreation
import com.eruditus.android.ui.ecrans.EcranMiseAJour
import com.eruditus.android.ui.ecrans.EcranPartie
import com.eruditus.android.ui.ecrans.EcranPasDeConnexion
import com.eruditus.android.ui.ecrans.EcranRecuperation
import com.eruditus.android.ui.ecrans.EcranResultat
import com.eruditus.android.ui.ecrans.EtatAccueil
import com.eruditus.android.ui.ecrans.EtatAvis
import com.eruditus.android.ui.ecrans.EtatCode
import com.eruditus.android.ui.ecrans.EtatChangementMotDePasse
import com.eruditus.android.ui.ecrans.EtatChangementPseudo
import com.eruditus.android.ui.ecrans.EtatParametres
import com.eruditus.android.ui.ecrans.InfoParametres
import com.eruditus.android.parametres.Theme
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
    EcranAccueil(EtatAccueil(Moi("Hibou_42", 42, ELO_3E), chargement = false), "Hibou_42", "1.0.12", rien, rien, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranAccueilMiseAJour() = ApercuGabarit {
    EcranAccueil(EtatAccueil(Moi("Hibou_42", 42, ELO_3E), chargement = false, annonce = RELEASE_EXEMPLE), "Hibou_42", "1.0.12", rien, rien, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranAccueilProvisoire() = ApercuGabarit {
    EcranAccueil(EtatAccueil(Moi("Hibou_42", 18, ELO_PROVISOIRE), chargement = false), "Hibou_42", "1.0.12", rien, rien, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranAccueilPremier() = ApercuGabarit {
    EcranAccueil(EtatAccueil(Moi("Hibou_42", 42, ELO_3E.copy(rang = 1)), chargement = false), "Hibou_42", "1.0.12", rien, rien, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranAccueilBanqueEpuisee() = ApercuGabarit {
    EcranAccueil(EtatAccueil(Moi("Hibou_42", 42, ELO_3E.copy(revision = true)), chargement = false), "Hibou_42", "1.0.12", rien, rien, rien)
}

/** Le pire cas de l'Accueil à 200 % : provisoire, banque épuisée et annonce ensemble. */
@ApercusGabarit
@ApercusGabaritClair200
@Composable
fun ApercuEcranAccueilPireCas() = ApercuGabarit {
    EcranAccueil(
        EtatAccueil(Moi("Hibou_42", 18, ELO_PROVISOIRE.copy(revision = true)), chargement = false, annonce = RELEASE_EXEMPLE),
        "Hibou_42", "1.0.12", rien, rien, rien,
    )
}

private val ELO_3E = EloMoi(valeur = 1247, provisoire = false, restantes = 0, rang = 3, classes = 18, revision = false)
private val ELO_PROVISOIRE = EloMoi(valeur = 1012, provisoire = true, restantes = 12, rang = null, classes = 18, revision = false)

@ApercusGabarit
@Composable
fun ApercuEcranAccueilChargement() = ApercuGabarit {
    EcranAccueil(EtatAccueil(), "Hibou_42", "1.0.12", rien, rien, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranAccueilTropDeTentatives() = ApercuGabarit {
    EcranAccueil(
        EtatAccueil(Moi("Hibou_42", 1, ELO_3E), chargement = false, message = TexteErreur.TropDeTentatives),
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
        EtatAccueil(Moi("Hibou_42", 42, ELO_3E), chargement = false, message = TexteErreur.AucunePartie),
        "Hibou_42",
        "1.0.12",
        rien,
        rien,
        rien,
    )
}

/** Le top 10 des maquettes (`Elo-Classement`) ; [moi] : ma place, au rang 3 si dedans ; [restantes] : provisoire. */
private fun classementExemple(moi: Place?, restantes: Int = 0): Classement {
    val dedans = moi?.rang == 3
    val top = if (dedans) {
        listOf("Hypatie" to 2140, "LeBaronPerche" to 1532, "Hibou_42" to 1247, "MarieCurieDuDimanche" to 1236, "Albatros" to 1198,
            "tartine42" to 1175, "Vercingetorix" to 1150, "Zoe_L" to 1121, "Quiz_Kid" to 1096, "Penelope" to 1072)
    } else {
        listOf("Hypatie" to 2140, "LeBaronPerche" to 1980, "MarieCurieDuDimanche" to 1874, "Albatros" to 1702, "tartine42" to 1611,
            "Vercingetorix" to 1540, "Zoe_L" to 1466, "Quiz_Kid" to 1398, "Penelope" to 1355, "LaChouette" to 1310)
    }
    return Classement(top.mapIndexed { i, (pseudo, score) -> Place(i + 1, pseudo, score) }, moi, classes = 48, restantes = restantes)
}

private val CLASSEMENT_HORS_TOP = classementExemple(Place(27, "Hibou_42", 1180))

@ApercusGabarit
@ApercusGabaritClair200
@Composable
fun ApercuEcranClassementTop10() = ApercuGabarit {
    EcranClassement(EtatClassement(classementExemple(Place(3, "Hibou_42", 1247))), rien, rien)
}

@ApercusGabarit
@ApercusGabaritClair200
@Composable
fun ApercuEcranClassementHorsTop() = ApercuGabarit {
    EcranClassement(EtatClassement(CLASSEMENT_HORS_TOP), rien, rien)
}

@ApercusGabarit
@ApercusGabaritClair200
@Composable
fun ApercuEcranClassementProvisoire() = ApercuGabarit {
    EcranClassement(EtatClassement(classementExemple(null, restantes = 12)), rien, rien)
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

// Paramètres et ses formulaires (spec/ecrans.md § 12, D76 – D85) : pas de maquette, les
// composants et gabarits de DESIGN.md.

@Composable
private fun Parametres(etat: EtatParametres = EtatParametres(), theme: Theme = Theme.Systeme, pseudo: String = "Hibou_42") =
    EcranParametres(etat, pseudo, "1.0.12", theme, rien, rien, rien, {}, rien, {}, rien, rien, ignore, rien)

@ApercusGabarit
@Composable
fun ApercuEcranParametres() = ApercuGabarit { Parametres() }

@ApercusGabarit
@Composable
fun ApercuEcranParametresPseudoChange() = ApercuGabarit {
    Parametres(EtatParametres(info = InfoParametres.PseudoChange("Chouette_7")), theme = Theme.Sombre, pseudo = "Chouette_7")
}

@ApercusGabarit
@Composable
fun ApercuEcranParametresAvisEnvoye() = ApercuGabarit { Parametres(EtatParametres(info = InfoParametres.AvisEnvoye)) }

@ApercusGabarit
@Composable
fun ApercuEcranChangementPseudo() = ApercuGabarit {
    EcranChangementPseudo(EtatChangementPseudo("Hibou_42", pseudo = "Chouette_7"), ignore, rien, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranChangementPseudoTropRecent() = ApercuGabarit {
    EcranChangementPseudo(
        EtatChangementPseudo("Hibou_42", pseudo = "Chouette_7", erreurServeur = TexteErreur.PseudoTropRecent),
        ignore, rien, rien,
    )
}

@ApercusGabarit
@Composable
fun ApercuEcranChangementMotDePasse() = ApercuGabarit {
    EcranChangementMotDePasse(EtatChangementMotDePasse(actuel = "nuit-blanche-42", nouveau = "lune-glacee-17"), ignore, ignore, rien, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranChangementMotDePasseRefus() = ApercuGabarit {
    EcranChangementMotDePasse(
        EtatChangementMotDePasse(actuel = "nuit-blanche", nouveau = "é".repeat(37), erreurActuel = TexteErreur.MotDePasseIncorrect),
        ignore, ignore, rien, rien,
    )
}

@Composable
private fun Avis(etat: EtatAvis) = EcranAvis(etat, ignore, rien, rien, rien, rien)

private const val AVIS = "Une idée : des questions sur les volcans.\n\nEt la question 3 d’hier était ambiguë, deux réponses " +
    "pouvaient convenir."

@ApercusGabarit
@Composable
fun ApercuEcranAvis() = ApercuGabarit { Avis(EtatAvis()) }

@ApercusGabarit
@Composable
fun ApercuEcranAvisSaisie() = ApercuGabarit { Avis(EtatAvis(AVIS)) }

@ApercusGabarit
@Composable
fun ApercuEcranAvisTropLong() = ApercuGabarit { Avis(EtatAvis("Une idée. ".repeat(105))) }

@ApercusGabarit
@Composable
fun ApercuEcranAvisTropRecent() = ApercuGabarit { Avis(EtatAvis(AVIS, erreurServeur = TexteErreur.AvisTropRecent)) }

@ApercusGabarit
@Composable
fun ApercuEcranAvisReseau() = ApercuGabarit { Avis(EtatAvis(AVIS, message = TexteErreur.ReseauEnHaut)) }

@ApercusGabarit
@Composable
fun ApercuEcranAvisIndisponible() = ApercuGabarit { Avis(EtatAvis(AVIS, message = TexteErreur.AvisIndisponible)) }

@ApercusGabarit
@Composable
fun ApercuEcranAvisEnvoi() = ApercuGabarit { Avis(EtatAvis(AVIS, envoi = true)) }

@ApercusGabarit
@Composable
fun ApercuEcranAvisAbandon() = ApercuGabarit { Avis(EtatAvis(AVIS, abandon = true)) }

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

/**
 * Une correction de la question 2 (bonne réponse C), ou de la 5e avec son bilan ; [explication] et
 * [source] comme le serveur les envoie (D97).
 */
private fun corrigee(
    choix: Int?,
    bonne: Int,
    raison: Raison,
    numero: Int = 2,
    restantMs: Long = 9_000,
    explication: String? = null,
    source: String? = null,
): Phase.Corrigee {
    val correcte = raison == Raison.Gagne || raison == Raison.DejaReussie
    val correction = Correction(
        numero, choix, correcte, bonne, raison == Raison.HorsDelai, raison, explication, source,
    )
    return Phase.Corrigee(ReponseCorrigee(correction, if (numero == 5) Bilan(3, EloBilan(1261, 14, false)) else null), restantMs)
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

/** Contenu sourcé (D101) : une explication longue (près de 300 caractères), puis sa source. */
@ApercusGabarit
@Composable
fun ApercuEcranPartieFausseExpliquee() = ApercuGabarit {
    ExempleEcranPartie(
        Q2,
        corrigee(choix = 0, bonne = 2, raison = Raison.Fausse, explication = EXPLICATION_Q2, source = SOURCE_Q2),
        restantMs = 9_000,
    )
}

/** Explication courte, sans source. */
@ApercusGabarit
@Composable
fun ApercuEcranPartieGagneExpliquee() = ApercuGabarit {
    ExempleEcranPartie(
        Q2,
        corrigee(choix = 2, bonne = 2, raison = Raison.Gagne, explication = "Dalí l’a peint en 1931, à Port Lligat."),
        restantMs = 9_000,
    )
}

@ApercusGabarit
@Composable
fun ApercuEcranPartieTempsEcoule() = ApercuGabarit {
    ExempleEcranPartie(Q5, corrigee(choix = null, bonne = 3, raison = Raison.SansReponse, numero = 5, restantMs = 0), restantMs = 0)
}

@ApercusGabarit
@ApercusGabaritClair200
@Composable
fun ApercuEcranResultat() = ApercuGabarit {
    EcranResultat(resultat(1261, 14), chargement = false, onRejouer = rien, onAccueil = rien)
}

@ApercusGabarit
@ApercusGabaritClair200
@Composable
fun ApercuEcranResultatPerte() = ApercuGabarit {
    EcranResultat(resultat(1239, -8), chargement = false, onRejouer = rien, onAccueil = rien)
}

@ApercusGabarit
@ApercusGabaritClair200
@Composable
fun ApercuEcranResultatInchange() = ApercuGabarit {
    EcranResultat(resultat(1247, 0), chargement = false, onRejouer = rien, onAccueil = rien)
}

@ApercusGabarit
@ApercusGabaritClair200
@Composable
fun ApercuEcranResultatRevision() = ApercuGabarit {
    EcranResultat(resultat(1247, 0, revision = true), chargement = false, onRejouer = rien, onAccueil = rien)
}

private fun resultat(valeur: Int, variation: Int, revision: Boolean = false) =
    EtatResultat(Bilan(bonnes = 3, elo = EloBilan(valeur, variation, revision)), total = 5)

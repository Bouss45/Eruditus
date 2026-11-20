package com.eruditus.android.catalogue

import androidx.compose.runtime.Composable
import com.eruditus.android.compte.TexteErreur
import com.eruditus.android.donnees.api.Moi
import com.eruditus.android.ui.ecrans.ArriveeBienvenue
import com.eruditus.android.ui.ecrans.EcranAccueil
import com.eruditus.android.ui.ecrans.EcranBienvenue
import com.eruditus.android.ui.ecrans.EcranCode
import com.eruditus.android.ui.ecrans.EcranCompte
import com.eruditus.android.ui.ecrans.EcranConnexion
import com.eruditus.android.ui.ecrans.EcranCreation
import com.eruditus.android.ui.ecrans.EcranMiseAJour
import com.eruditus.android.ui.ecrans.EcranPasDeConnexion
import com.eruditus.android.ui.ecrans.EcranRecuperation
import com.eruditus.android.ui.ecrans.EtatAccueil
import com.eruditus.android.ui.ecrans.EtatCode
import com.eruditus.android.ui.ecrans.EtatCompte
import com.eruditus.android.ui.ecrans.EtatConnexion
import com.eruditus.android.ui.ecrans.EtatCreation
import com.eruditus.android.ui.ecrans.EtatRecuperation

/*
 * Aperçus des écrans du compte (spec/ecrans.md § 3 à § 14), un par état des planches « Écrans »
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
    EcranAccueil(EtatAccueil(Moi("Hibou_42", 12, 3, 48), chargement = false), "Hibou_42", rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranAccueilPasEncoreClasse() = ApercuGabarit {
    EcranAccueil(EtatAccueil(Moi("Hibou_42", 0, null, 48), chargement = false), "Hibou_42", rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranAccueilChargement() = ApercuGabarit {
    EcranAccueil(EtatAccueil(), "Hibou_42", rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranAccueilTropDeTentatives() = ApercuGabarit {
    EcranAccueil(
        EtatAccueil(Moi("Hibou_42", 1, 1, 1), chargement = false, message = TexteErreur.TropDeTentatives),
        "Hibou_42",
        rien,
    )
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

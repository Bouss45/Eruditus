package com.eruditus.android.catalogue

import androidx.compose.runtime.Composable
import com.eruditus.android.ui.designsystem.components.ArriveeBonne
import com.eruditus.android.ui.designsystem.components.EtatChoix

/*
 * Un aperçu par gabarit de DESIGN.md § 8, rempli comme sur les planches « Écrans » ; sombre, clair
 * et sombre à 200 %. Information et Partie en ont deux : ce qui change de l'un à l'autre (marque ou
 * titre ; question ou correction) est ce que le gabarit doit tenir.
 */

@ApercusGabarit
@Composable
fun ApercuGabaritFormulaire() = ApercuGabarit { ExempleFormulaire() }

@ApercusGabarit
@Composable
fun ApercuGabaritInformationBienvenue() = ApercuGabarit { ExempleBienvenue() }

@ApercusGabarit
@Composable
fun ApercuGabaritInformationCode() = ApercuGabarit { ExempleCodeRecuperation() }

@ApercusGabarit
@Composable
fun ApercuGabaritAccueil() = ApercuGabarit { ExempleAccueil() }

@ApercusGabarit
@Composable
fun ApercuGabaritPartieQuestion() = ApercuGabarit {
    ExemplePartie(questionQ2(ETATS_NEUTRES), restantMs = 14_000, enCours = true)
}

@ApercusGabarit
@Composable
fun ApercuGabaritPartieFausse() = ApercuGabarit {
    ExemplePartie(questionQ2(ETATS_FAUSSE, resultatFausse("C", "Salvador Dalí")), restantMs = 9_000, enCours = false)
}

@ApercusGabarit
@Composable
fun ApercuGabaritPartieGagne() = ApercuGabarit {
    val etats = listOf(EtatChoix.Verrouille, EtatChoix.Verrouille, EtatChoix.Bonne(ArriveeBonne.Choisie), EtatChoix.Verrouille)
    ExemplePartie(questionQ2(etats, RESULTAT_GAGNE), restantMs = 9_000, enCours = false)
}

@ApercusGabarit
@Composable
fun ApercuGabaritListe() = ApercuGabarit { ExempleClassement() }

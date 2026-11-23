package com.eruditus.android.catalogue

import androidx.compose.runtime.Composable
import com.eruditus.android.compte.TexteErreur
import com.eruditus.android.historique.EtatDetail
import com.eruditus.android.historique.EtatHistorique
import com.eruditus.android.ui.ecrans.EcranDetailPartie
import com.eruditus.android.ui.ecrans.EcranHistorique

/*
 * Aperçus de l'Historique et du détail d'une partie (spec/ecrans.md § 16, § 17), un par état des
 * planches `Historique`, `Historique-Courbe` et `Historique-Detail`. Sombre, clair, sombre à 200 %.
 */

private val rien: () -> Unit = {}
private val ignore: (String) -> Unit = {}

@ApercusTexte
@ApercusComposantClair200
@Composable
fun ApercuCourbeElo() = ApercuComposant { ExemplesCourbe() }

@ApercusTexte
@Composable
fun ApercuLigneActionAvecValeur() = ApercuComposant { ExemplesActionValeur() }

@ApercusTexte
@Composable
fun ApercuChoixReponseRelecture() = ApercuComposant { ExemplesChoixRelecture() }

// --- Historique ----------------------------------------------------------------------------

@ApercusGabarit
@ApercusGabaritClair200
@Composable
fun ApercuEcranHistorique() = ApercuGabarit {
    EcranHistorique(EtatHistorique(courbe = COURBE_12, parties = PARTIES_12), rien, rien, ignore, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranHistoriqueChargement() = ApercuGabarit {
    EcranHistorique(EtatHistorique(chargement = true), rien, rien, ignore, rien)
}

@ApercusGabarit
@ApercusGabaritClair200
@Composable
fun ApercuEcranHistoriqueAucunePartie() = ApercuGabarit {
    EcranHistorique(EtatHistorique(parties = emptyList()), rien, rien, ignore, rien)
}

@ApercusGabarit
@ApercusGabaritClair200
@Composable
fun ApercuEcranHistoriqueProvisoire() = ApercuGabarit {
    EcranHistorique(EtatHistorique(courbe = COURBE_PROVISOIRE, parties = PARTIES_4), rien, rien, ignore, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranHistoriqueUnePartie() = ApercuGabarit {
    EcranHistorique(EtatHistorique(courbe = COURBE_1, parties = PARTIES_1), rien, rien, ignore, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranHistoriqueSansPoint() = ApercuGabarit {
    EcranHistorique(
        EtatHistorique(courbe = com.eruditus.android.donnees.api.CourbeLue(null, emptyList(), false), parties = PARTIES_4.take(1).map { it.copy(elo = com.eruditus.android.donnees.api.EloPartie(null, null)) }),
        rien, rien, ignore, rien,
    )
}

@ApercusGabarit
@ApercusGabaritClair200
@Composable
fun ApercuEcranHistorique50Parties() = ApercuGabarit {
    EcranHistorique(EtatHistorique(courbe = COURBE_50, parties = PARTIES_20, suivant = "c0ffee"), rien, rien, ignore, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranHistoriqueVoirPlusChargement() = ApercuGabarit {
    EcranHistorique(EtatHistorique(courbe = COURBE_50, parties = PARTIES_20, suivant = "c0ffee", suiteEnCours = true), rien, rien, ignore, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranHistoriqueTropDeTentatives() = ApercuGabarit {
    EcranHistorique(EtatHistorique(courbe = COURBE_12, parties = PARTIES_12, message = TexteErreur.TropDeTentatives), rien, rien, ignore, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranHistoriqueErreur() = ApercuGabarit {
    EcranHistorique(EtatHistorique(courbe = COURBE_12, parties = PARTIES_12, message = TexteErreur.Generique), rien, rien, ignore, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranHistoriqueCourbeEnEchec() = ApercuGabarit {
    EcranHistorique(EtatHistorique(parties = PARTIES_12, message = TexteErreur.Generique), rien, rien, ignore, rien)
}

@ApercusGabarit
@Composable
fun ApercuEcranHistoriquePartieIntrouvable() = ApercuGabarit {
    EcranHistorique(EtatHistorique(courbe = COURBE_12, parties = PARTIES_12, message = TexteErreur.PartieIndisponible), rien, rien, ignore, rien)
}

// --- Détail d'une partie -------------------------------------------------------------------

@ApercusGabarit
@ApercusGabaritClair200
@Composable
fun ApercuEcranDetailJuste() = ApercuGabarit { EcranDetailPartie(EtatDetail(DETAIL_JUSTE), rien, rien) }

@ApercusGabarit
@Composable
fun ApercuEcranDetailChargement() = ApercuGabarit { EcranDetailPartie(EtatDetail(chargement = true), rien, rien) }

@ApercusGabarit
@ApercusGabaritClair200
@Composable
fun ApercuEcranDetailFausse() = ApercuGabarit { EcranDetailPartie(EtatDetail(DETAIL_FAUSSE), rien, rien) }

@ApercusGabarit
@Composable
fun ApercuEcranDetailSansReponse() = ApercuGabarit { EcranDetailPartie(EtatDetail(DETAIL_SANS_REPONSE), rien, rien) }

@ApercusGabarit
@Composable
fun ApercuEcranDetailHorsDelai() = ApercuGabarit { EcranDetailPartie(EtatDetail(DETAIL_HORS_DELAI), rien, rien) }

@ApercusGabarit
@ApercusGabaritClair200
@Composable
fun ApercuEcranDetailNonRepondue() = ApercuGabarit { EcranDetailPartie(EtatDetail(DETAIL_ABANDONNEE), rien, rien) }

@ApercusGabarit
@Composable
fun ApercuEcranDetailModifiee() = ApercuGabarit { EcranDetailPartie(EtatDetail(DETAIL_MODIFIEE), rien, rien) }

@ApercusGabarit
@Composable
fun ApercuEcranDetailRetiree() = ApercuGabarit { EcranDetailPartie(EtatDetail(DETAIL_RETIREE), rien, rien) }

@ApercusGabarit
@Composable
fun ApercuEcranDetailSansEffet() = ApercuGabarit { EcranDetailPartie(EtatDetail(DETAIL_SANS_EFFET), rien, rien) }

@ApercusGabarit
@Composable
fun ApercuEcranDetailSansExplication() = ApercuGabarit { EcranDetailPartie(EtatDetail(DETAIL_SANS_EXPLICATION), rien, rien) }

@ApercusGabarit
@Composable
fun ApercuEcranDetailIndisponible() = ApercuGabarit { EcranDetailPartie(EtatDetail(DETAIL_INDISPONIBLE), rien, rien) }

@ApercusGabarit
@Composable
fun ApercuEcranDetailTropDeTentatives() = ApercuGabarit { EcranDetailPartie(EtatDetail(message = TexteErreur.TropDeTentatives), rien, rien) }

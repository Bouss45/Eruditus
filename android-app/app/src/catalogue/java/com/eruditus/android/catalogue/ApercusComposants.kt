package com.eruditus.android.catalogue

import androidx.compose.runtime.Composable
import com.eruditus.android.ui.designsystem.components.Encadre
import com.eruditus.android.ui.designsystem.components.ErreurFormulaire
import com.eruditus.android.ui.designsystem.components.Lien
import com.eruditus.android.ui.designsystem.components.Marque
import com.eruditus.android.ui.designsystem.components.SeparateurPoints

/*
 * Un aperçu par composant de DESIGN.md § 7, avec tous ses états, dans l'ordre de la planche
 * « Composants et états ». Sombre et clair ; à 200 % pour ceux qui ont du texte.
 */

@ApercusTexte
@Composable
fun ApercuBarreHaut() = ApercuComposant { ExemplesBarre() }

@ApercusTexte
@Composable
fun ApercuChrono() = ApercuComposant { EtatsChrono() }

@ApercusTexte
@Composable
fun ApercuChoixReponse() = ApercuComposant { EtatsChoixAvantCorrection() }

@ApercusTexte
@Composable
fun ApercuChoixReponseCorrection() = ApercuComposant { EtatsChoixCorrection() }

@ApercusTexte
@Composable
fun ApercuLigneResultat() = ApercuComposant { EtatsResultat() }

@ApercusTexte
@Composable
fun ApercuChamp() = ApercuComposant { EtatsChampSaisie() }

@ApercusTexte
@Composable
fun ApercuChampMotDePasse() = ApercuComposant { EtatsChampMotDePasse() }

@ApercusTexte
@Composable
fun ApercuChampPlusieursLignes() = ApercuComposant { EtatsChampPlusieursLignes() }

@ApercusTexte
@Composable
fun ApercuChampPlusieursLignesErreur() = ApercuComposant { EtatsChampPlusieursLignesErreur() }

@ApercusTexte
@Composable
fun ApercuBoutonPrincipal() = ApercuComposant { EtatsBoutonPrincipal() }

@ApercusTexte
@Composable
fun ApercuBoutonSecondaire() = ApercuComposant { EtatsBoutonSecondaire() }

@ApercusTexte
@Composable
fun ApercuBoutonDestructeurEtLien() = ApercuComposant { EtatsBoutonDestructeurEtLien() }

@ApercusTexte
@Composable
fun ApercuRangeeBoutons() = ApercuComposant { ExemplesRangee() }

@ApercusTexte
@Composable
fun ApercuLien() = ApercuComposant { Lien("Mot de passe oublié$NB?", {}) }

@ApercusTexte
@Composable
fun ApercuMessageHaut() = ApercuComposant { ExemplesMessages() }

@ApercusTexte
@Composable
fun ApercuErreurFormulaire() = ApercuComposant { ErreurFormulaire("Pseudo ou mot de passe incorrect.") }

@ApercusTexte
@Composable
fun ApercuCodeRecuperation() = ApercuComposant { ExempleCode() }

@ApercusTexte
@ApercusComposantClair200
@Composable
fun ApercuStatistiques() = ApercuComposant { ExemplesStatistiques() }

@ApercusTexte
@Composable
fun ApercuMarque() = ApercuComposant { Marque("La culture générale, une question à la fois.") }

@ApercusTexte
@ApercusComposantClair200
@Composable
fun ApercuListeGroupeeEtLigneClassement() = ApercuComposant { ExemplesClassement() }

@ApercusComposant
@Composable
fun ApercuSeparateurPoints() = ApercuComposant { SeparateurPoints() }

@ApercusTexte
@Composable
fun ApercuEncadre() = ApercuComposant { Encadre("Réussis une question pour entrer au classement.") }

@ApercusTexte
@Composable
fun ApercuLigneInfo() = ApercuComposant { ExemplesInfo() }

@ApercusTexte
@Composable
fun ApercuLigneOption() = ApercuComposant { ExemplesOption() }

@ApercusTexte
@Composable
fun ApercuLigneAction() = ApercuComposant { ExemplesAction() }

@ApercusTexte
@Composable
fun ApercuBoite() = ApercuComposant { ExemplesBoitesQuestion() }

@ApercusTexte
@Composable
fun ApercuBoiteDeconnexion() = ApercuComposant { ExempleBoiteDeconnexion() }

@ApercusTexte
@Composable
fun ApercuBoiteSuppression() = ApercuComposant { ExempleBoiteSuppressionVide() }

@ApercusTexte
@Composable
fun ApercuBoiteSuppressionErreur() = ApercuComposant { ExempleBoiteSuppressionErreur() }

@ApercusTexte
@Composable
fun ApercuBoiteSuppressionEnvoi() = ApercuComposant { ExemplesBoiteSuppressionEnvoi() }

@ApercusComposant
@Composable
fun ApercuIconeEtat() = ApercuComposant { ExemplesIconesEtat() }

@ApercusComposant
@Composable
fun ApercuIndicateurEtChargement() = ApercuComposant { ExemplesIndicateurs() }

@ApercusTexte
@Composable
fun ApercuAnnonce() = ApercuComposant { ExempleAnnonce() }

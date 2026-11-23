package com.eruditus.android.catalogue

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import com.eruditus.android.donnees.api.CourbeLue
import com.eruditus.android.donnees.api.DetailPartie
import com.eruditus.android.donnees.api.DetailQuestion
import com.eruditus.android.donnees.api.EloPartie
import com.eruditus.android.donnees.api.EloQuestion
import com.eruditus.android.donnees.api.FinPartie
import com.eruditus.android.donnees.api.PartieDetail
import com.eruditus.android.donnees.api.PartieListee
import com.eruditus.android.donnees.api.PointElo
import com.eruditus.android.donnees.api.QuestionRelue
import com.eruditus.android.donnees.api.StatutQuestion
import com.eruditus.android.ui.designsystem.components.ChoixReponse
import com.eruditus.android.ui.designsystem.components.CourbeElo
import com.eruditus.android.ui.designsystem.components.EtatChoix
import com.eruditus.android.ui.designsystem.components.LigneAction
import com.eruditus.android.ui.designsystem.components.ListeGroupee
import com.eruditus.android.ui.designsystem.theme.AppuiForce
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.sin

/*
 * Données des aperçus de l'Historique et du détail d'une partie : celles des planches
 * `Historique`, `Historique-Courbe` et `Historique-Detail`, qui font foi.
 */

private val rien: () -> Unit = {}
private val LE_22 = Instant.parse("2026-11-22T10:00:00Z")
private val MOINS = "−"

/** Les valeurs de la courbe : [depart], puis l'Elo après chaque variation de [variations] (plancher de 100). */
fun courbeExemple(depart: Int, variations: List<Int>, provisoire: Boolean = false): CourbeLue {
    var elo = depart
    return CourbeLue(depart, variations.mapIndexed { i, v ->
        elo = maxOf(100, elo + v)
        PointElo(LE_22.minus(((variations.size - i).toLong()), ChronoUnit.DAYS), elo)
    }, provisoire)
}

val COURBE_12 = courbeExemple(1000, listOf(12, -9, -16, 11, 12, 0, 16, 14, 21, -8, -6, -4))
val COURBE_1 = courbeExemple(1000, listOf(14))
val COURBE_PROVISOIRE = courbeExemple(1000, listOf(38, -19, 42, -14), provisoire = true)
val COURBE_50 = courbeExemple(1000, List(50) { (22 * sin(it * 0.7)).toInt() + 3 })
val COURBE_ECART = courbeExemple(640, List(40) { (it * 53) % 130 - 25 })
val COURBE_PLANCHER = courbeExemple(180, listOf(-18, -21, -23, -30, 12, -9, -14, 21, -11))

/** Une partie de la liste, [jours] avant le 22 nov. 2026. */
private fun partie(id: String, jours: Int, bonnes: Int, variation: Int?, servies: Int = 5, fin: FinPartie = FinPartie.Terminee) =
    PartieListee(id, LE_22.minus(jours.toLong(), ChronoUnit.DAYS), fin, bonnes, servies, EloPartie(variation, variation?.let { 1000 + it }))

val PARTIES_12 = listOf(
    partie("p1", 0, 3, -4), partie("p2", 0, 2, -6, servies = 3, fin = FinPartie.Abandonnee), partie("p3", 1, 4, null),
    partie("p4", 1, 2, -8), partie("p5", 2, 5, 21), partie("p6", 2, 4, 14), partie("p7", 3, 4, 16), partie("p8", 4, 3, 0),
    partie("p9", 4, 4, 12), partie("p10", 5, 4, 11), partie("p11", 6, 1, -16), partie("p12", 6, 2, -9), partie("p13", 7, 3, 12),
)
val PARTIES_4 = listOf(partie("q1", 0, 3, -14), partie("q2", 1, 5, 42), partie("q3", 1, 2, -19), partie("q4", 2, 4, 38))
val PARTIES_1 = listOf(partie("r1", 0, 3, 14))
val PARTIES_20 = List(20) { i -> partie("s$i", i / 2, (i * 3) % 6, (i * 7) % 45 - 19) }

// --- Détail --------------------------------------------------------------------------------

private fun complet(choixJoueur: Int?, bonne: Int?, explication: String? = EXPLICATION_Q2, source: String? = SOURCE_Q2) =
    DetailQuestion.Complet(ENONCE_Q2, CHOIX_Q2, choixJoueur, bonne, explication, source)

private fun question(
    numero: Int,
    statut: StatutQuestion,
    niveau: Int?,
    variation: Int?,
    detail: DetailQuestion,
) = QuestionRelue(numero, statut, EloQuestion(niveau, variation), detail)

private fun detail(
    bonnes: Int,
    servies: Int,
    variation: Int?,
    valeur: Int?,
    vararg questions: QuestionRelue,
    fin: FinPartie = FinPartie.Terminee,
) = DetailPartie(PartieDetail("p1", LE_22, fin, bonnes, servies, 5), EloPartie(variation, valeur), questions.toList())

/** Juste, puis fausse (planche : cadre « Juste »). */
val DETAIL_JUSTE = detail(
    3, 5, 14, 1261,
    question(1, StatutQuestion.Juste, 4, 12, complet(choixJoueur = 2, bonne = 2)),
    question(2, StatutQuestion.Fausse, 2, -19, complet(choixJoueur = 0, bonne = 2)),
)
val DETAIL_FAUSSE = detail(2, 5, -36, 1225, question(2, StatutQuestion.Fausse, 4, -28, complet(choixJoueur = 0, bonne = 2)))
val DETAIL_SANS_REPONSE = detail(2, 5, -36, 1225, question(3, StatutQuestion.SansReponse, 2, -19, complet(choixJoueur = null, bonne = 2)))
val DETAIL_HORS_DELAI = detail(2, 5, -36, 1225, question(4, StatutQuestion.HorsDelai, 5, -9, complet(choixJoueur = 1, bonne = 2)))
val DETAIL_SANS_EXPLICATION = detail(2, 5, -36, 1225, question(5, StatutQuestion.Juste, 3, 8, complet(choixJoueur = 2, bonne = 2, explication = null, source = null)))
val DETAIL_ABANDONNEE = detail(
    2, 3, -6, 1047,
    question(2, StatutQuestion.Juste, 3, 8, complet(choixJoueur = 2, bonne = 2, explication = null, source = null)),
    question(3, StatutQuestion.NonRepondue, 3, -19, complet(choixJoueur = null, bonne = null, explication = null, source = null)),
    fin = FinPartie.Abandonnee,
)
val DETAIL_MODIFIEE = detail(3, 5, 3, 1250, question(2, StatutQuestion.Fausse, 3, -14, DetailQuestion.Modifiee))
val DETAIL_RETIREE = detail(3, 5, 3, 1250, question(3, StatutQuestion.Juste, 2, 5, DetailQuestion.Retiree))
val DETAIL_SANS_EFFET = detail(4, 5, null, null, question(1, StatutQuestion.Juste, 4, null, complet(choixJoueur = 2, bonne = 2)))
val DETAIL_INDISPONIBLE = detail(3, 5, 3, 1250, QuestionRelue(2, null, EloQuestion(3, -14), DetailQuestion.Indisponible))

// --- Composants ----------------------------------------------------------------------------

/** `CourbeElo` (D135) : normale, un point, provisoire, 50 points, valeurs extrêmes. */
@Composable
fun ColumnScope.ExemplesCourbe() {
    Legende("Normale : 12 parties", premiere = true)
    CourbeElo(COURBE_12.valeurs, provisoire = false)
    Legende("Un point : le départ et la partie")
    CourbeElo(COURBE_1.valeurs, provisoire = false)
    Legende("Provisoire : tracé en tirets")
    CourbeElo(COURBE_PROVISOIRE.valeurs, provisoire = true)
    Legende("50 points : les points se taisent, le dernier reste")
    CourbeElo(COURBE_50.valeurs, provisoire = false)
    Legende("Valeurs extrêmes : grand écart")
    CourbeElo(COURBE_ECART.valeurs, provisoire = false)
    Legende("Valeurs extrêmes : près du plancher de 100")
    CourbeElo(COURBE_PLANCHER.valeurs, provisoire = false)
}

/** `LigneAction` avec valeur (D132 – D136) : normale, abandonnée, 0, valeur trop longue dessous, pressée. */
@Composable
fun ColumnScope.ExemplesActionValeur() {
    Legende("Avec valeur", premiere = true)
    ListeGroupee(enTete = "Mes parties") {
        LigneAction("22 nov. 2026", rien, complement = "3 bonnes réponses sur 5", valeur = "+14")
        LigneAction("21 nov. 2026", rien, separateur = true, complement = "2 bonnes réponses sur 5", valeur = "${MOINS}8", interactionSource = AppuiForce)
        LigneAction("18 nov. 2026", rien, separateur = true, complement = "3 bonnes réponses sur 5", valeur = "0")
        LigneAction("22 nov. 2026", rien, separateur = true, complement = "2 bonnes réponses sur 3 questions · Abandonnée", valeur = "${MOINS}6")
    }
    Legende("Valeur trop longue : dessous")
    ListeGroupee {
        LigneAction("21 nov. 2026", rien, complement = "4 bonnes réponses sur 5", valeur = "Sans effet sur l’Elo")
    }
}

/** `ChoixReponse` en Relecture (D132) : neutre, bonne, mauvaise, sans appui ni pression. */
@Composable
fun ColumnScope.ExemplesChoixRelecture() {
    Legende("Relecture : neutre (question non répondue)", premiere = true)
    ChoixReponse("A", "René Magritte", EtatChoix.Neutre, rien, lecture = true, interactionSource = AppuiForce)
    Legende("Relecture : bonne réponse, et ta réponse")
    ChoixReponse("C", "Salvador Dalí", EtatChoix.Bonne(com.eruditus.android.ui.designsystem.components.ArriveeBonne.Choisie), rien, lecture = true)
    ChoixReponse("A", "René Magritte", EtatChoix.Mauvaise, rien, lecture = true)
}

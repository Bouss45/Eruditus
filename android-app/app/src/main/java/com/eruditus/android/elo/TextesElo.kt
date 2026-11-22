package com.eruditus.android.elo

import com.eruditus.android.R
import com.eruditus.android.classement.TextesClassement
import com.eruditus.android.ui.ecrans.Textes
import kotlin.math.abs

/**
 * Textes de l'Elo (spec/ecrans.md § 8, § 10, § 11, D112, D115) : la variation par son seul signe
 * (« +14 », « −8 » avec le vrai signe moins), « Elo inchangé », « Révision : sans effet sur l'Elo » ;
 * le rang de l'Accueil, accordé comme celui du Classement ; la phrase provisoire, la même à
 * l'Accueil et au Classement ; la banque épuisée. Vérifiés sur `strings.xml` par `TextesEloTest`.
 */
object TextesElo {

    /** La variation de la partie ; une partie de [revision] n'en a aucune, jamais « +0 » ni « Elo inchangé ». */
    fun variation(t: Textes, variation: Int, revision: Boolean): String = when {
        revision -> t.texte(R.string.elo_revision)
        variation > 0 -> t.texte(R.string.elo_variation_hausse, variation)
        variation < 0 -> t.texte(R.string.elo_variation_baisse, abs(variation))
        else -> t.texte(R.string.elo_inchange)
    }

    /** Ce que TalkBack lit pour la variation : « Elo, plus 14 », « Elo, moins 8 ». */
    fun variationLue(t: Textes, variation: Int, revision: Boolean): String = when {
        revision || variation == 0 -> variation(t, variation, revision)
        variation > 0 -> t.texte(R.string.elo_variation_hausse_lue, variation)
        else -> t.texte(R.string.elo_variation_baisse_lue, abs(variation))
    }

    /** « Elo : 1261 », sous la variation. */
    fun apres(t: Textes, valeur: Int): String = t.texte(R.string.elo_apres, valeur)

    /** « 3e sur 18 », « 1er sur 18 » : l'ordinal du Classement. */
    fun rang(t: Textes, rang: Int, classes: Int): String = t.texte(R.string.elo_rang, TextesClassement.rang(t, rang), classes)

    /** « Elo provisoire : encore 12 réponses avant d'entrer au classement. » (« encore 1 réponse »). */
    fun provisoire(t: Textes, restantes: Int): String =
        t.texte(R.string.elo_provisoire, t.pluriel(R.plurals.elo_restantes, restantes, restantes))

    fun banqueEpuisee(t: Textes): String = t.texte(R.string.elo_banque_epuisee)
}

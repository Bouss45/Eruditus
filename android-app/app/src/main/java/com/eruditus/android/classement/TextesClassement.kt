package com.eruditus.android.classement

import com.eruditus.android.R
import com.eruditus.android.ui.ecrans.Textes

/**
 * Textes du Classement (spec/ecrans.md § 11, D72) : « 1er », « 2e » ; « 19 » puis « points » ;
 * « 48 joueurs classés » ; chaque ligne lue d'un seul bloc par TalkBack, « rang 27, Hibou_42,
 * 4 points, toi ». Vérifiés sur `strings.xml` par `TextesClassementTest`.
 */
object TextesClassement {

    fun rang(t: Textes, rang: Int): String =
        if (rang == 1) t.texte(R.string.classement_rang_premier) else t.texte(R.string.classement_rang_suivant, rang)

    /** Le nombre de points, en `choix` 600 dans la ligne. */
    fun nombre(score: Int): String = score.toString()

    /** L'unité, collée au nombre par une espace insécable : « point », « points ». */
    fun unite(t: Textes, score: Int): String = t.pluriel(R.plurals.classement_unite, score)

    fun pied(t: Textes, classes: Int): String = t.pluriel(R.plurals.classement_classes, classes, classes)

    /** Ce que TalkBack lit pour la ligne, d'un seul bloc (D74). */
    fun description(t: Textes, ligne: LigneVue): String = t.texte(
        if (ligne.moi) R.string.classement_ligne_toi else R.string.classement_ligne,
        ligne.rang,
        ligne.pseudo,
        t.pluriel(R.plurals.accueil_points, ligne.score, ligne.score),
    )
}

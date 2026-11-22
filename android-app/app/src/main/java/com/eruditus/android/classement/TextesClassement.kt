package com.eruditus.android.classement

import com.eruditus.android.R
import com.eruditus.android.ui.ecrans.Textes

/**
 * Textes du Classement (spec/ecrans.md § 11, D72, D112) : « 1er », « 2e » ; l'Elo seul, sans
 * unité ; « 48 joueurs classés » ; chaque ligne lue d'un seul bloc par TalkBack, « rang 27,
 * Hibou_42, Elo 1180, toi ». Vérifiés sur `strings.xml` par `TextesClassementTest`.
 */
object TextesClassement {

    fun rang(t: Textes, rang: Int): String =
        if (rang == 1) t.texte(R.string.classement_rang_premier) else t.texte(R.string.classement_rang_suivant, rang)

    /** L'Elo, en `choix` 600 dans la ligne : entier, sans espace des milliers (D112). */
    fun nombre(elo: Int): String = elo.toString()

    fun pied(t: Textes, classes: Int): String = t.pluriel(R.plurals.classement_classes, classes, classes)

    /** Ce que TalkBack lit pour la ligne, d'un seul bloc (D74). */
    fun description(t: Textes, ligne: LigneVue): String = t.texte(
        if (ligne.moi) R.string.classement_ligne_toi else R.string.classement_ligne,
        ligne.rang,
        ligne.pseudo,
        ligne.elo,
    )
}

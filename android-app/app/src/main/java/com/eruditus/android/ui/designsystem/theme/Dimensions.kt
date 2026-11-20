package com.eruditus.android.ui.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Espacements, rayons et tailles (DESIGN.md § 5), en dp. Les cotes propres à un seul composant
 * (marges internes d'un choix, d'un code…) sont des constantes privées de ce composant.
 */

/** Espacements, nommés par leur usage. */
@Immutable
class Espacements {
    /** 4 — décalage d'une erreur, d'un lien, du pied. */
    val decalage: Dp = 4.dp

    /** 8 — icône et texte ; haut d'un champ. */
    val icone: Dp = 8.dp

    /** 12 — entre blocs, choix, boutons ; contenu sous la barre. */
    val blocs: Dp = 12.dp

    /** 16 — marge interne des groupes ; action du bas au-dessus de la barre système. */
    val groupe: Dp = 16.dp

    /** 20 — marge latérale des écrans. */
    val ecran: Dp = 20.dp

    /** 24 — marge des boîtes ; haut du contenu sans barre. */
    val boite: Dp = 24.dp

    /** 32 — entre sections. */
    val section: Dp = 32.dp
}

/** Rayons, en formes prêtes à l'emploi. */
@Immutable
class Rayons {
    /** 10 — pastille de lettre. */
    val pastille = RoundedCornerShape(10.dp)

    /** 12 — champ. */
    val champ = RoundedCornerShape(12.dp)

    /** 16 — choix, message. */
    val choix = RoundedCornerShape(16.dp)

    /** 20 — liste groupée, code, encadré. */
    val groupe = RoundedCornerShape(20.dp)

    /** 28 — boîte. */
    val boite = RoundedCornerShape(28.dp)

    /** Pilule — boutons, « Toi ». */
    val pilule = RoundedCornerShape(percent = 50)
}

/** Tailles. */
@Immutable
class Tailles {
    /** 48 — toute cible tactile, au minimum. */
    val cible: Dp = 48.dp

    /** 56 — bouton, champ, barre du haut. */
    val bouton: Dp = 56.dp

    /** 64 — « Jouer », choix (minimum). */
    val grand: Dp = 64.dp

    /** 52 — ligne de classement (minimum). */
    val ligneClassement: Dp = 52.dp

    /** 4 — barre du chrono. */
    val chrono: Dp = 4.dp

    /** 1 — contour. */
    val contour: Dp = 1.dp

    /** 2 — contour au focus, en erreur, en envoi. */
    val contourFort: Dp = 2.dp
}

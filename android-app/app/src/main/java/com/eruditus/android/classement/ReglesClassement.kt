package com.eruditus.android.classement

import com.eruditus.android.donnees.api.Classement
import com.eruditus.android.donnees.api.Place

/** Une ligne affichée : rang, pseudo et Elo du serveur ; [moi] : ma ligne, marquée « Toi ». */
data class LigneVue(val rang: Int, val pseudo: String, val elo: Int, val moi: Boolean)

/** Ce que montre l'écran Classement (spec/ecrans.md § 11, D72). */
sealed interface VueClassement {
    /** Personne n'est classé : « Personne n'est encore classé. Lance une partie ! » seul, sans pied. */
    data object Personne : VueClassement

    /**
     * [top] : les lignes du serveur, la mienne marquée si j'y suis ; [maLigne] : classé hors du top
     * 10, sous le séparateur ; [restantes] : provisoire, réponses qui manquent (l'`Encadre`), nul
     * sinon ; [classes] : le pied.
     */
    data class Liste(val top: List<LigneVue>, val maLigne: LigneVue?, val restantes: Int?, val classes: Int) : VueClassement
}

/**
 * Lecture du classement reçu (spec/regles.md § 4, D72) : rangs, ordre et Elo du serveur, jamais
 * recalculés. Ma ligne est dans le top 10 si l'une de ses lignes porte le pseudo de `moi`, casse
 * ignorée (pseudo unique, D9 ; aucune ligne n'a d'identifiant, T30) ; sinon elle va sous le top 10,
 * même à rang égal (ex æquo coupé par la 10e ligne). Vérifiée par `ReglesClassementTest`.
 */
object ReglesClassement {

    fun vue(c: Classement): VueClassement {
        if (c.top.isEmpty() && c.moi == null) return VueClassement.Personne
        val moi = c.moi
        val top = c.top.map { it.vue(moi = moi != null && it.pseudo.equals(moi.pseudo, ignoreCase = true)) }
        return VueClassement.Liste(
            top = top,
            maLigne = moi?.takeUnless { top.any(LigneVue::moi) }?.vue(moi = true),
            restantes = c.restantes.takeIf { moi == null },
            classes = c.classes,
        )
    }

    private fun Place.vue(moi: Boolean) = LigneVue(rang, pseudo, elo, moi)
}

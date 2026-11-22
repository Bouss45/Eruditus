package com.eruditus.android.partie

import com.eruditus.android.donnees.api.Correction
import com.eruditus.android.donnees.api.Raison
import com.eruditus.android.ui.designsystem.components.ArriveeBonne
import com.eruditus.android.ui.designsystem.components.EtatChoix

/** Le message de la correction (spec/ecrans.md § 9). */
enum class Verdict {
    /** « Bonne réponse ! », sans point (D116) : `gagne`, `deja_reussie`, ou une raison inconnue et `correcte`. */
    Bonne,

    /** « Mauvaise réponse. » et « La bonne réponse était C. ». */
    Fausse,

    /** « Temps écoulé. » et « La bonne réponse était C. ». */
    TempsEcoule,
}

/**
 * Ce que montre une correction : [verdict] ; [lettreDetail] : la lettre de la bonne réponse quand
 * le verdict a un détail ; [etats] : les 4 choix, dont le changement d'état joue le mouvement et la
 * vibration (DESIGN.md § 6).
 */
data class LectureCorrection(val verdict: Verdict, val lettreDetail: Char?, val etats: List<EtatChoix>)

/** Traduction d'une correction du serveur en verdict et en états des choix (D67). Vérifiée par `ReglesPartieTest`. */
object ReglesPartie {
    const val LETTRES = "ABCD"

    fun lire(correction: Correction): LectureCorrection {
        val verdict = verdict(correction)
        val detail = LETTRES[correction.bonneReponse].takeIf { verdict == Verdict.Fausse || verdict == Verdict.TempsEcoule }
        return LectureCorrection(verdict, detail, etats(correction))
    }

    /**
     * Selon `raison` ; une raison inconnue (serveur plus récent, D22) se lit sur `correcte`,
     * toujours présent. `point` n'est plus lu (D116).
     */
    private fun verdict(c: Correction): Verdict = when (c.raison) {
        Raison.Gagne, Raison.DejaReussie -> Verdict.Bonne
        Raison.Fausse -> Verdict.Fausse
        Raison.SansReponse, Raison.HorsDelai -> Verdict.TempsEcoule
        Raison.Inconnue -> if (c.correcte) Verdict.Bonne else Verdict.Fausse
    }

    /**
     * Vert sur la bonne réponse, rouge sur le choix du joueur s'il en diffère, les autres
     * verrouillés. La bonne réponse s'allume choisie (remplissage, rebond, Confirm) si elle est
     * correcte et choisie ; après le « non » d'un autre choix ; sinon en fondu (temps écoulé,
     * Reject). Seuls `correcte`, `choix` et `bonneReponse` comptent.
     */
    private fun etats(c: Correction): List<EtatChoix> {
        val autreChoix = c.choix != null && c.choix != c.bonneReponse
        val arrivee = when {
            c.correcte && c.choix == c.bonneReponse -> ArriveeBonne.Choisie
            autreChoix -> ArriveeBonne.ApresErreur
            else -> ArriveeBonne.TempsEcoule
        }
        return List(4) { i ->
            when {
                i == c.bonneReponse -> EtatChoix.Bonne(arrivee)
                autreChoix && i == c.choix -> EtatChoix.Mauvaise
                else -> EtatChoix.Verrouille
            }
        }
    }
}

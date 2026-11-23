package com.eruditus.android.historique

import com.eruditus.android.donnees.api.DetailQuestion
import com.eruditus.android.donnees.api.EloQuestion
import com.eruditus.android.donnees.api.QuestionRelue
import com.eruditus.android.donnees.api.StatutQuestion
import com.eruditus.android.ui.designsystem.components.ArriveeBonne
import com.eruditus.android.ui.designsystem.components.EtatChoix

/** Le verdict d'une question relue (spec/ecrans.md § 17). */
enum class VerdictRelu { Bonne, Fausse, TempsEcoule, NonRepondue }

/** Un choix en lecture seule : son [etat] (neutre, ✓ vert, ✗ rouge). */
data class ChoixRelu(val texte: String, val etat: EtatChoix)

/** Ce qu'une question montre sous son en-tête (§ 17, tableau des `statut` et `detail`). */
sealed interface CorpsQuestion {
    /** `detail` `ok` : l'énoncé, les quatre choix, le verdict, puis l'explication et la source s'il y en a. */
    data class Complet(
        val enonce: String,
        val choix: List<ChoixRelu>,
        val verdict: VerdictRelu,
        val explication: String?,
        val source: String?,
    ) : CorpsQuestion

    /** `modifiee` ou `retiree` : le verdict et la note, rien de la question. */
    data class VerdictSeul(val verdict: VerdictRelu, val note: DetailQuestion) : CorpsQuestion

    /** `statut` ou `detail` inconnu : « Détail indisponible. ». */
    data object Indisponible : CorpsQuestion
}

/** Une question du détail : [enjeu] pour la ligne d'enjeu du § 9, nul quand le détail est indisponible. */
data class VueQuestion(val numero: Int, val enjeu: EloQuestion?, val corps: CorpsQuestion)

/** Ce que le détail d'une partie montre de chaque question (spec/ecrans.md § 17). Vérifié par `ReglesHistoriqueTest`. */
object ReglesHistorique {
    /** Points de la courbe : les 50 dernières parties (D135). */
    const val POINTS_MAX = 50

    fun vues(questions: List<QuestionRelue>): List<VueQuestion> = questions.map(::vue)

    fun vue(q: QuestionRelue): VueQuestion {
        val statut = q.statut
        if (statut == null || q.detail == DetailQuestion.Indisponible) return VueQuestion(q.numero, null, CorpsQuestion.Indisponible)
        val verdict = when (statut) {
            StatutQuestion.Juste -> VerdictRelu.Bonne
            StatutQuestion.Fausse -> VerdictRelu.Fausse
            StatutQuestion.SansReponse, StatutQuestion.HorsDelai -> VerdictRelu.TempsEcoule
            StatutQuestion.NonRepondue -> VerdictRelu.NonRepondue
        }
        val corps = when (val d = q.detail) {
            is DetailQuestion.Complet -> complet(d, verdict)
            else -> CorpsQuestion.VerdictSeul(verdict, d)
        }
        return VueQuestion(q.numero, q.elo, corps)
    }

    /**
     * ✓ vert sur la bonne réponse, ✗ rouge sur le choix du joueur s'il en diffère. Une question
     * non répondue n'a ni ✓, ni explication, ni source, même si un serveur les envoyait (D134).
     */
    private fun complet(d: DetailQuestion.Complet, verdict: VerdictRelu): CorpsQuestion.Complet {
        val repondue = verdict != VerdictRelu.NonRepondue
        val choix = d.choix.mapIndexed { i, texte ->
            ChoixRelu(
                texte,
                when {
                    !repondue -> EtatChoix.Neutre
                    i == d.bonneReponse -> EtatChoix.Bonne(ArriveeBonne.Choisie)
                    i == d.choixJoueur -> EtatChoix.Mauvaise
                    else -> EtatChoix.Neutre
                },
            )
        }
        return CorpsQuestion.Complet(
            d.enonce, choix, verdict,
            explication = d.explication?.takeIf { repondue && it.isNotBlank() },
            source = d.source?.takeIf { repondue && it.isNotBlank() },
        )
    }
}

package com.eruditus.android.historique

import com.eruditus.android.R
import com.eruditus.android.donnees.api.CourbeLue
import com.eruditus.android.donnees.api.DetailQuestion
import com.eruditus.android.donnees.api.EloPartie
import com.eruditus.android.donnees.api.FinPartie
import com.eruditus.android.donnees.api.PartieDetail
import com.eruditus.android.donnees.api.PartieListee
import com.eruditus.android.elo.TextesElo
import com.eruditus.android.elo.TextesEnjeu
import com.eruditus.android.ui.ecrans.Textes
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Le résumé de la courbe, lu par TalkBack à la place du dessin, et la mention « Elo provisoire. » (D135). */
data class TexteCourbe(val resume: String, val provisoire: String?)

/** Une ligne de la liste : [libelle] (la date), [complement], [valeur] à droite, et [lue] : la phrase de TalkBack. */
data class LigneHistorique(val libelle: String, val complement: String, val valeur: String, val lue: String)

/**
 * Les textes de l'Historique et du détail d'une partie (spec/ecrans.md § 16, § 17, D135) : le
 * résumé de la courbe, « 3 bonnes réponses sur 5 », la variation (« +14 », « −8 » avec le vrai
 * signe moins, « 0 », « Sans effet sur l'Elo »), les dates, les verdicts. Vérifiés sur
 * `strings.xml` par `TextesHistoriqueTest`.
 */
object TextesHistorique {

    /** « Elo 1043 après 12 parties. Plus haut 1061, plus bas 987. » ; nul sans point : ni dessin ni résumé. */
    fun courbe(t: Textes, c: CourbeLue): TexteCourbe? {
        if (c.points.isEmpty()) return null
        val valeurs = c.valeurs
        val n = c.points.size
        val dernier = c.points.last().elo
        val resume = if (n >= ReglesHistorique.POINTS_MAX) {
            t.texte(R.string.historique_courbe_derniers, dernier, n, valeurs.max(), valeurs.min())
        } else {
            t.pluriel(R.plurals.historique_courbe, n, dernier, n, valeurs.max(), valeurs.min())
        }
        return TexteCourbe(resume, t.texte(R.string.historique_provisoire).takeIf { c.provisoire })
    }

    fun sansPoint(t: Textes): String = t.texte(R.string.historique_sans_point)

    fun aucunePartie(t: Textes): String = t.texte(R.string.historique_aucune)

    fun aucunePartieDetail(t: Textes): String = t.texte(R.string.historique_aucune_detail)

    /** « 3 bonnes réponses sur 5 » ; abandonnée : « 2 bonnes réponses sur 3 questions · Abandonnée ». */
    fun bonnes(t: Textes, bonnes: Int, servies: Int, abandonnee: Boolean): String =
        if (abandonnee) t.texte(R.string.historique_suite_abandonnee, sur(t, bonnes, servies, avecQuestions = true)) else sur(t, bonnes, servies, avecQuestions = false)

    /** « +14 », « −8 », « 0 », ou « Sans effet sur l'Elo » (aucune question comptée). */
    fun variation(t: Textes, elo: EloPartie): String =
        elo.variation?.let { TextesEnjeu.signe(t, it) } ?: t.texte(R.string.enjeu_sans_effet)

    /** Ce que TalkBack lit pour la variation : « Elo, plus 14 », « Elo, 0 ». */
    fun variationLue(t: Textes, variation: Int): String = t.texte(R.string.enjeu_elo_lu, TextesEnjeu.lu(t, variation))

    fun ligne(t: Textes, p: PartieListee, zone: ZoneId, langue: Locale): LigneHistorique {
        val abandonnee = p.fin == FinPartie.Abandonnee
        val lue = listOfNotNull(
            FormatDates.longue(p.date, zone, langue),
            sur(t, p.bonnes, p.servies, avecQuestions = abandonnee),
            t.texte(R.string.historique_abandonnee_lue).takeIf { abandonnee },
            p.elo.variation?.let { variationLue(t, it) } ?: t.texte(R.string.historique_sans_effet_lu),
        ).joinToString(", ")
        return LigneHistorique(FormatDates.moyenne(p.date, zone, langue), bonnes(t, p.bonnes, p.servies, abandonnee), variation(t, p.elo), lue)
    }

    /** « Partie du 22 nov. » : le jour et le mois ne se coupent pas. */
    fun titreDetail(t: Textes, date: Instant, zone: ZoneId, langue: Locale): String =
        t.texte(R.string.historique_titre_detail, FormatDates.courte(date, zone, langue).replace(' ', ' '))

    /** « 3 bonnes réponses sur 5 », ou « Abandonnée · 2 bonnes réponses sur 3 questions ». */
    fun entete(t: Textes, p: PartieDetail): String =
        if (p.fin == FinPartie.Abandonnee) t.texte(R.string.historique_entete_abandonnee, sur(t, p.bonnes, p.servies, avecQuestions = true)) else sur(t, p.bonnes, p.servies, avecQuestions = false)

    /** « Elo : 1261 », sous la variation de la partie. */
    fun eloApres(t: Textes, valeur: Int): String = TextesElo.apres(t, valeur)

    fun numero(t: Textes, numero: Int): String = t.texte(R.string.historique_question, numero)

    /** Le verdict, avec sa ponctuation sous le détail ([complet]) et sans elle quand il est seul. */
    fun verdict(t: Textes, v: VerdictRelu, complet: Boolean): String = t.texte(
        when (v) {
            VerdictRelu.Bonne -> if (complet) R.string.partie_bonne_reponse else R.string.historique_verdict_bonne
            VerdictRelu.Fausse -> if (complet) R.string.partie_fausse else R.string.historique_verdict_fausse
            VerdictRelu.TempsEcoule -> if (complet) R.string.partie_temps_ecoule else R.string.historique_verdict_temps
            VerdictRelu.NonRepondue -> R.string.historique_non_repondue
        },
    )

    /** La note d'une question dont le détail n'est plus affiché. */
    fun note(t: Textes, detail: DetailQuestion): String = t.texte(
        when (detail) {
            DetailQuestion.Modifiee -> R.string.historique_note_modifiee
            DetailQuestion.Retiree -> R.string.historique_note_retiree
            else -> R.string.historique_note_indisponible
        },
    )

    /** « 3 bonnes réponses sur 5 » (le pluriel du Résultat), et, [avecQuestions], « … sur 3 questions ». */
    private fun sur(t: Textes, bonnes: Int, servies: Int, avecQuestions: Boolean): String {
        val base = t.pluriel(R.plurals.resultat_bonnes, bonnes, bonnes, servies)
        return if (avecQuestions) base + " " + t.pluriel(R.plurals.historique_questions, servies) else base
    }
}

package com.eruditus.android.historique

import com.eruditus.android.TextesRessources
import com.eruditus.android.donnees.api.CourbeLue
import com.eruditus.android.donnees.api.DetailQuestion
import com.eruditus.android.donnees.api.EloPartie
import com.eruditus.android.donnees.api.FinPartie
import com.eruditus.android.donnees.api.PartieDetail
import com.eruditus.android.donnees.api.PartieListee
import com.eruditus.android.donnees.api.PointElo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Les textes de l'Historique tels que le téléphone les affiche et les lit (spec/ecrans.md § 16 et
 * § 17, D135) : lus dans `strings.xml`, comme `TextesEnjeuTest`. Les dates sont mises en forme dans la
 * langue et le fuseau du téléphone, ici donnés en paramètres.
 */
class TextesHistoriqueTest {

    private val t = TextesRessources()
    private val m = "−"
    private val nb = " "
    private val paris = ZoneId.of("Europe/Paris")
    private val le22 = Instant.parse("2026-11-22T10:00:00Z")

    private fun courbe(depart: Int?, vararg elo: Int, provisoire: Boolean = false) =
        CourbeLue(depart, elo.map { PointElo(le22, it) }, provisoire)

    private fun partie(bonnes: Int, servies: Int, fin: FinPartie = FinPartie.Terminee, variation: Int? = 14, instant: Instant = le22) =
        PartieListee("p", instant, fin, bonnes, servies, EloPartie(variation, variation?.let { 1261 }))

    // Courbe -----------------------------------------------------------------------------------

    @Test
    fun `resume de la courbe, l'Elo apres N parties, le plus haut et le plus bas`() {
        val c = courbe(1000, 1012, 1003, 987, 998, 1010, 1010, 1026, 1040, 1061, 1053, 1047, 1043)
        assertEquals("Elo 1043 après 12 parties. Plus haut 1061, plus bas 987.", TextesHistorique.courbe(t, c)!!.resume)
    }

    @Test
    fun `le plus haut et le plus bas comptent le depart`() {
        assertEquals("Elo 1043 après 2 parties. Plus haut 1100, plus bas 1043.", TextesHistorique.courbe(t, courbe(1100, 1060, 1043))!!.resume)
        assertEquals("Elo 1100 après 2 parties. Plus haut 1100, plus bas 900.", TextesHistorique.courbe(t, courbe(900, 1000, 1100))!!.resume)
    }

    @Test
    fun `une seule partie, le singulier, le depart compte`() {
        assertEquals("Elo 1014 après 1 partie. Plus haut 1014, plus bas 1000.", TextesHistorique.courbe(t, courbe(1000, 1014))!!.resume)
        assertEquals("Elo 987 après 1 partie. Plus haut 1000, plus bas 987.", TextesHistorique.courbe(t, courbe(1000, 987))!!.resume)
    }

    @Test
    fun `cinquante points, sur les 50 dernieres parties`() {
        val c = courbe(1000, *IntArray(50) { 1000 + it })
        assertEquals("Elo 1049 sur les 50 dernières parties. Plus haut 1049, plus bas 1000.", TextesHistorique.courbe(t, c)!!.resume)
        // 49 : le compte exact.
        assertEquals("Elo 1048 après 49 parties. Plus haut 1048, plus bas 1000.", TextesHistorique.courbe(t, courbe(1000, *IntArray(49) { 1000 + it }))!!.resume)
    }

    @Test
    fun `aucun point, ni resume ni dessin`() {
        assertNull(TextesHistorique.courbe(t, courbe(null)))
        assertNull(TextesHistorique.courbe(t, courbe(null, provisoire = true)))
    }

    @Test
    fun `provisoire, la mention sous le resume, sinon aucune`() {
        assertEquals("Elo provisoire.", TextesHistorique.courbe(t, courbe(1000, 1014, provisoire = true))!!.provisoire)
        assertNull(TextesHistorique.courbe(t, courbe(1000, 1014))!!.provisoire)
    }

    @Test
    fun `phrase sans point, la liste sans courbe`() {
        assertEquals("Pas encore d’Elo à tracer.", TextesHistorique.sansPoint(t))
    }

    @Test
    fun `aucune partie, la phrase et son texte`() {
        assertEquals("Tu n’as pas encore terminé de partie.", TextesHistorique.aucunePartie(t))
        assertEquals("Elles apparaîtront ici.", TextesHistorique.aucunePartieDetail(t))
    }

    // Liste ------------------------------------------------------------------------------------

    @Test
    fun `bonnes reponses, le pluriel et le singulier`() {
        assertEquals("3 bonnes réponses sur 5", TextesHistorique.bonnes(t, 3, 5, abandonnee = false))
        assertEquals("5 bonnes réponses sur 5", TextesHistorique.bonnes(t, 5, 5, abandonnee = false))
        assertEquals("2 bonnes réponses sur 5", TextesHistorique.bonnes(t, 2, 5, abandonnee = false))
        assertEquals("1 bonne réponse sur 5", TextesHistorique.bonnes(t, 1, 5, abandonnee = false))
    }

    @Test
    fun `partie abandonnee, le nombre de questions servies et la mention`() {
        assertEquals("2 bonnes réponses sur 3 questions · Abandonnée", TextesHistorique.bonnes(t, 2, 3, abandonnee = true))
        assertEquals("1 bonne réponse sur 1 question · Abandonnée", TextesHistorique.bonnes(t, 1, 1, abandonnee = true))
        assertEquals("0 bonne réponse sur 2 questions · Abandonnée", TextesHistorique.bonnes(t, 0, 2, abandonnee = true))
    }

    @Test
    fun `variation, le vrai signe moins, 0 sans signe, sans effet sur l'Elo`() {
        assertEquals("+14", TextesHistorique.variation(t, EloPartie(14, 1261)))
        assertEquals("${m}8", TextesHistorique.variation(t, EloPartie(-8, 1253)))
        assertEquals("0", TextesHistorique.variation(t, EloPartie(0, 1261)))
        assertEquals("Sans effet sur l’Elo", TextesHistorique.variation(t, EloPartie(null, null)))
        assertEquals('−', TextesHistorique.variation(t, EloPartie(-8, 1253))[0])
    }

    @Test
    fun `la ligne de la liste, la date, les bonnes reponses, la variation`() {
        val l = TextesHistorique.ligne(t, partie(3, 5), paris, Locale.FRENCH)
        assertEquals("22 nov. 2026", l.libelle)
        assertEquals("3 bonnes réponses sur 5", l.complement)
        assertEquals("+14", l.valeur)
        val a = TextesHistorique.ligne(t, partie(2, 3, FinPartie.Abandonnee, variation = -6), paris, Locale.FRENCH)
        assertEquals("2 bonnes réponses sur 3 questions · Abandonnée", a.complement)
        assertEquals("${m}6", a.valeur)
        assertEquals("Sans effet sur l’Elo", TextesHistorique.ligne(t, partie(4, 5, variation = null), paris, Locale.FRENCH).valeur)
    }

    @Test
    fun `TalkBack lit la ligne en une phrase`() {
        fun lue(p: PartieListee) = TextesHistorique.ligne(t, p, paris, Locale.FRENCH).lue
        assertEquals("22 novembre 2026, 3 bonnes réponses sur 5, Elo, plus 14", lue(partie(3, 5)))
        assertEquals("22 novembre 2026, 2 bonnes réponses sur 5, Elo, moins 8", lue(partie(2, 5, variation = -8)))
        assertEquals("22 novembre 2026, 3 bonnes réponses sur 5, Elo, 0", lue(partie(3, 5, variation = 0)))
        assertEquals("22 novembre 2026, 4 bonnes réponses sur 5, sans effet sur l’Elo", lue(partie(4, 5, variation = null)))
        assertEquals("22 novembre 2026, 2 bonnes réponses sur 3 questions, abandonnée, Elo, moins 6", lue(partie(2, 3, FinPartie.Abandonnee, -6)))
    }

    // Dates ------------------------------------------------------------------------------------

    @Test
    fun `la date dans le fuseau du telephone`() {
        // 22 h 30 UTC : déjà le 23 à Paris, encore le 22 à New York.
        val tard = Instant.parse("2026-11-22T22:30:00Z")
        assertEquals("23 nov. 2026", FormatDates.moyenne(tard, paris, Locale.FRENCH))
        assertEquals("22 nov. 2026", FormatDates.moyenne(tard, ZoneId.of("America/New_York"), Locale.FRENCH))
        assertEquals("23 novembre 2026", FormatDates.longue(tard, paris, Locale.FRENCH))
        assertEquals("23 nov.", FormatDates.courte(tard, paris, Locale.FRENCH))
    }

    @Test
    fun `la date dans la langue du telephone`() {
        assertEquals("Nov 22, 2026", FormatDates.moyenne(le22, paris, Locale.US))
        assertEquals("22 nov. 2026", FormatDates.moyenne(le22, paris, Locale.FRENCH))
        assertEquals("November 22, 2026", FormatDates.longue(le22, paris, Locale.US))
    }

    // Détail -----------------------------------------------------------------------------------

    private fun detail(bonnes: Int, servies: Int, fin: FinPartie = FinPartie.Terminee) =
        PartieDetail("p", le22, fin, bonnes, servies, 5)

    @Test
    fun `le titre du detail, Partie du 22 nov`() {
        assertEquals("Partie du 22${nb}nov.", TextesHistorique.titreDetail(t, le22, paris, Locale.FRENCH))
    }

    @Test
    fun `l'en-tete du detail, la partie abandonnee passe la mention devant`() {
        assertEquals("3 bonnes réponses sur 5", TextesHistorique.entete(t, detail(3, 5)))
        assertEquals("Abandonnée · 2 bonnes réponses sur 3 questions", TextesHistorique.entete(t, detail(2, 3, FinPartie.Abandonnee)))
        assertEquals("Abandonnée · 1 bonne réponse sur 1 question", TextesHistorique.entete(t, detail(1, 1, FinPartie.Abandonnee)))
    }

    @Test
    fun `la variation de la partie en grand, puis l'Elo apres`() {
        assertEquals("+14", TextesHistorique.variation(t, EloPartie(14, 1261)))
        assertEquals("Elo${nb}: 1261", TextesHistorique.eloApres(t, 1261))
    }

    @Test
    fun `titre d'une question`() {
        assertEquals("Question 2", TextesHistorique.numero(t, 2))
    }

    @Test
    fun `les verdicts, avec leur ponctuation sous le detail, sans elle quand seul`() {
        val fs = " "
        assertEquals("Bonne réponse${fs}!", TextesHistorique.verdict(t, VerdictRelu.Bonne, complet = true))
        assertEquals("Mauvaise réponse.", TextesHistorique.verdict(t, VerdictRelu.Fausse, complet = true))
        assertEquals("Temps écoulé.", TextesHistorique.verdict(t, VerdictRelu.TempsEcoule, complet = true))
        assertEquals("Question non répondue", TextesHistorique.verdict(t, VerdictRelu.NonRepondue, complet = true))
        assertEquals("Bonne réponse", TextesHistorique.verdict(t, VerdictRelu.Bonne, complet = false))
        assertEquals("Mauvaise réponse", TextesHistorique.verdict(t, VerdictRelu.Fausse, complet = false))
        assertEquals("Temps écoulé", TextesHistorique.verdict(t, VerdictRelu.TempsEcoule, complet = false))
        assertEquals("Question non répondue", TextesHistorique.verdict(t, VerdictRelu.NonRepondue, complet = false))
    }

    @Test
    fun `les notes d'une question modifiee, retiree ou indisponible`() {
        assertEquals("Cette question a été modifiée depuis${nb}: son détail n’est plus affiché.", TextesHistorique.note(t, DetailQuestion.Modifiee))
        assertEquals("Cette question a été retirée du jeu${nb}: son détail n’est plus affiché.", TextesHistorique.note(t, DetailQuestion.Retiree))
        assertEquals("Détail indisponible.", TextesHistorique.note(t, DetailQuestion.Indisponible))
    }
}

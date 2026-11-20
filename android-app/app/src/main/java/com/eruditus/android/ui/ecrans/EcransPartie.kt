package com.eruditus.android.ui.ecrans

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.eruditus.android.R
import com.eruditus.android.donnees.api.QuestionServie
import com.eruditus.android.partie.EtatPartie
import com.eruditus.android.partie.LectureCorrection
import com.eruditus.android.partie.Phase
import com.eruditus.android.partie.ReglesPartie
import com.eruditus.android.partie.Verdict
import com.eruditus.android.ui.designsystem.components.Boite
import com.eruditus.android.ui.designsystem.components.BoutonDestructeur
import com.eruditus.android.ui.designsystem.components.BoutonPrincipal
import com.eruditus.android.ui.designsystem.components.BoutonSecondaire
import com.eruditus.android.ui.designsystem.components.EtatChoix
import com.eruditus.android.ui.designsystem.components.GenreResultat
import com.eruditus.android.ui.designsystem.components.Resultat
import com.eruditus.android.ui.designsystem.components.Statistique
import com.eruditus.android.ui.designsystem.components.Statistiques
import com.eruditus.android.ui.designsystem.templates.ChoixPartie
import com.eruditus.android.ui.designsystem.templates.GabaritInformation
import com.eruditus.android.ui.designsystem.templates.GabaritPartie
import com.eruditus.android.ui.designsystem.templates.QuestionPartie
import com.eruditus.android.ui.designsystem.theme.Eruditus

/*
 * Écrans de la partie (spec/ecrans.md § 9, § 10) : Partie et Résultat. Le serveur décide de tout ;
 * ces écrans n'affichent que l'état tenu par `DeroulementPartie`.
 */

/**
 * § 9 Partie (D65 – D68, D70) : la question de [etat], le chrono à [restantMs] (calculé à chaque
 * image par l'appelant, depuis le temps restant du serveur), les choix, la correction, puis
 * « Question suivante » ou « Voir le résultat ». [abandon] : la boîte « Abandonner la partie ? ».
 */
@Composable
fun EcranPartie(
    etat: EtatPartie,
    restantMs: Long,
    abandon: Boolean,
    onChoix: (Int) -> Unit,
    onBouton: () -> Unit,
    onFermer: () -> Unit,
    onContinuer: () -> Unit,
    onAbandonner: () -> Unit,
) {
    val q = etat.question
    val phase = etat.phase
    val corrigee = phase as? Phase.Corrigee
    val lecture = corrigee?.let { ReglesPartie.lire(it.reponse.correction) }
    val etats: List<EtatChoix> = when (phase) {
        // À 0, en attendant l'envoi automatique (ou après un envoi refusé), plus rien ne se touche.
        Phase.Attente -> List(4) { if (restantMs > 0) EtatChoix.Neutre else EtatChoix.Verrouille }
        is Phase.Envoi -> List(4) { if (it == phase.choix) EtatChoix.Envoi else EtatChoix.Verrouille }
        is Phase.Corrigee -> lecture!!.etats
    }
    GabaritPartie(
        compteur = stringResource(R.string.partie_compteur, q.numero, q.total),
        restantMs = restantMs,
        totalMs = q.delaiMs,
        chronoEnCours = phase == Phase.Attente && restantMs > 0,
        question = QuestionPartie(
            numero = q.numero,
            enonce = q.enonce,
            choix = q.choix.mapIndexed { i, texte -> ChoixPartie(ReglesPartie.LETTRES[i].toString(), texte, etats[i]) },
            resultat = lecture?.let { resultatAffiche(it, q) },
            resultatsPossibles = resultatsPossibles(q),
        ),
        onChoix = onChoix,
        bouton = stringResource(if (corrigee?.reponse?.bilan != null) R.string.partie_voir_resultat else R.string.partie_question_suivante),
        boutonVisible = corrigee != null,
        onBouton = onBouton,
        onFermer = onFermer,
        message = etat.message.enMessage(),
        boutonChargement = corrigee?.suite == true,
    )
    // Le chrono continue pendant la boîte, et son envoi automatique aussi (§ 9).
    Boite(
        visible = abandon,
        onFermer = onContinuer,
        titre = stringResource(R.string.partie_abandon_titre),
        texte = stringResource(R.string.partie_abandon_texte),
        actions = {
            BoutonSecondaire(stringResource(R.string.partie_continuer), onContinuer)
            BoutonDestructeur(stringResource(R.string.partie_abandonner), onAbandonner)
        },
    )
}

/**
 * La ligne de résultat d'une correction : verdict, détail « La bonne réponse était C. », et pour
 * TalkBack le détail avec le texte du choix (« … La bonne réponse était C, Salvador Dalí. »).
 */
@Composable
private fun resultatAffiche(lecture: LectureCorrection, q: QuestionServie): Resultat {
    val verdict = stringResource(
        when (lecture.verdict) {
            Verdict.Gagne -> R.string.partie_gagne
            Verdict.BonneReponse -> R.string.partie_bonne_reponse
            Verdict.DejaReussie -> R.string.partie_deja_reussie
            Verdict.Fausse -> R.string.partie_fausse
            Verdict.TempsEcoule -> R.string.partie_temps_ecoule
        },
    )
    val genre = when (lecture.verdict) {
        Verdict.Gagne, Verdict.BonneReponse, Verdict.DejaReussie -> GenreResultat.Bonne
        Verdict.Fausse -> GenreResultat.Fausse
        Verdict.TempsEcoule -> GenreResultat.TempsEcoule
    }
    val lettre = lecture.lettreDetail ?: return Resultat(genre, verdict)
    val texteBonne = q.choix[ReglesPartie.LETTRES.indexOf(lettre)]
    return Resultat(
        genre,
        verdict,
        detail = stringResource(R.string.partie_detail, lettre.toString()),
        annonce = stringResource(R.string.partie_annonce, verdict, lettre.toString(), texteBonne),
    )
}

/**
 * Toutes les lignes que peut donner cette question, pour réserver la place de la plus longue
 * (DESIGN.md § 8) : la bonne réponse n'est pas connue avant la correction, d'où chaque lettre.
 */
@Composable
private fun resultatsPossibles(q: QuestionServie): List<Resultat> {
    val sansDetail = listOf(Verdict.Gagne, Verdict.BonneReponse, Verdict.DejaReussie).map { verdict ->
        resultatAffiche(LectureCorrection(verdict, null, emptyList()), q)
    }
    val avecDetail = ReglesPartie.LETTRES.flatMap { lettre ->
        listOf(Verdict.Fausse, Verdict.TempsEcoule).map { verdict -> resultatAffiche(LectureCorrection(verdict, lettre, emptyList()), q) }
    }
    return sansDetail + avecDetail
}

/**
 * § 10 Résultat (D69) : « 3 bonnes réponses sur 5 », les points gagnés en grand, le score total ;
 * « Accueil » et « Rejouer » ([chargement] : la nouvelle partie est demandée).
 */
@Composable
fun EcranResultat(etat: EtatResultat, chargement: Boolean, onRejouer: () -> Unit, onAccueil: () -> Unit) {
    val bilan = etat.bilan
    GabaritInformation(
        titre = pluralStringResource(R.plurals.resultat_bonnes, bilan.bonnes, bilan.bonnes, etat.total),
        message = etat.message.enMessage(),
        secondaire = { BoutonSecondaire(stringResource(R.string.resultat_accueil), onAccueil, actif = !chargement) },
        principal = { BoutonPrincipal(stringResource(R.string.resultat_rejouer), onRejouer, chargement = chargement) },
    ) {
        val points = if (bilan.points == 0) {
            stringResource(R.string.resultat_aucun_point)
        } else {
            pluralStringResource(R.plurals.resultat_points, bilan.points, bilan.points)
        }
        Statistiques(listOf(Statistique(points)), Modifier.padding(top = Eruditus.espacements.decalage))
        Text(
            stringResource(R.string.resultat_score, textePoints(bilan.score)),
            style = Eruditus.typo.corps,
            color = Eruditus.couleurs.texteSecondaire,
        )
    }
}

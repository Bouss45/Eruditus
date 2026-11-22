package com.eruditus.android.ui.ecrans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.eruditus.android.R
import com.eruditus.android.donnees.api.QuestionServie
import com.eruditus.android.partie.EtatPartie
import com.eruditus.android.partie.EtatSignalement
import com.eruditus.android.partie.MotifSignalement
import com.eruditus.android.partie.ReglesSignalement
import com.eruditus.android.partie.LectureCorrection
import com.eruditus.android.partie.Phase
import com.eruditus.android.partie.ReglesPartie
import com.eruditus.android.elo.LigneEnjeu
import com.eruditus.android.elo.TextesElo
import com.eruditus.android.elo.TextesEnjeu
import com.eruditus.android.partie.Verdict
import com.eruditus.android.ui.designsystem.components.Boite
import com.eruditus.android.ui.designsystem.components.BoutonDestructeur
import com.eruditus.android.ui.designsystem.components.BoutonPrincipal
import com.eruditus.android.ui.designsystem.components.BoutonSecondaire
import com.eruditus.android.ui.designsystem.components.Champ
import com.eruditus.android.ui.designsystem.components.Compteur
import com.eruditus.android.ui.designsystem.components.EtatChoix
import com.eruditus.android.ui.designsystem.components.GenreResultat
import com.eruditus.android.ui.designsystem.components.LigneOption
import com.eruditus.android.ui.designsystem.components.ListeGroupee
import com.eruditus.android.ui.designsystem.components.Resultat
import com.eruditus.android.ui.designsystem.components.Statistique
import com.eruditus.android.ui.designsystem.components.Statistiques
import com.eruditus.android.ui.designsystem.templates.ChoixPartie
import com.eruditus.android.ui.designsystem.templates.GabaritInformation
import com.eruditus.android.ui.designsystem.templates.GabaritPartie
import com.eruditus.android.ui.designsystem.templates.QuestionPartie
import com.eruditus.android.ui.designsystem.templates.SignalementAffiche
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
    onSignaler: () -> Unit,
    onFermerSignalement: () -> Unit,
    onMotif: (MotifSignalement) -> Unit,
    onCommentaire: (String) -> Unit,
    onEnvoyerSignalement: () -> Unit,
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
    val t = textes()
    // Pendant la question, l'enjeu annoncé ; à la correction, la variation obtenue (D129).
    val ligneQuestion = TextesEnjeu.question(t, q.enjeu)
    val ligneCorrection = corrigee?.let { TextesEnjeu.correction(t, it.reponse.correction.elo) }
    GabaritPartie(
        compteur = stringResource(R.string.partie_compteur, q.numero, q.total),
        restantMs = restantMs,
        totalMs = q.delaiMs,
        chronoEnCours = phase == Phase.Attente && restantMs > 0,
        question = QuestionPartie(
            numero = q.numero,
            enonce = q.enonce,
            choix = q.choix.mapIndexed { i, texte -> ChoixPartie(ReglesPartie.LETTRES[i].toString(), texte, etats[i]) },
            resultat = lecture?.let { resultatAffiche(it, q, ligneCorrection) },
            resultatsPossibles = resultatsPossibles(q),
            explication = corrigee?.reponse?.correction?.explication,
            source = corrigee?.reponse?.correction?.source?.let { stringResource(R.string.partie_source, it) },
            signalement = corrigee?.let {
                SignalementAffiche(
                    stringResource(if (etat.signalement.envoye) R.string.partie_signale else R.string.partie_signaler),
                    etat.signalement.envoye,
                )
            },
        ),
        onChoix = onChoix,
        bouton = stringResource(if (corrigee?.reponse?.bilan != null) R.string.partie_voir_resultat else R.string.partie_question_suivante),
        boutonVisible = corrigee != null,
        onBouton = onBouton,
        onFermer = onFermer,
        message = etat.message.enMessage(),
        boutonChargement = corrigee?.suite == true,
        onSignaler = onSignaler,
        enjeu = if (corrigee != null) ligneCorrection else ligneQuestion,
        enjeuReserve = listOfNotNull(ligneQuestion?.texte, ligneCorrection?.texte),
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

    // La partie reste affichée derrière (D118) ; rien ne se ferme pendant l'envoi.
    val signalement = etat.signalement
    val fermer = { if (!signalement.envoi) onFermerSignalement() }
    Boite(
        visible = signalement.ouvert,
        onFermer = fermer,
        titre = stringResource(R.string.signalement_titre),
        texte = stringResource(R.string.signalement_texte),
        champ = { ChampsSignalement(signalement, onMotif, onCommentaire) },
        actions = { ActionsSignalement(signalement, fermer, onEnvoyerSignalement) },
    )
}

/** Les quatre motifs en choix unique, puis le commentaire facultatif (D118, `LigneOption` et `Champ`). */
@Composable
fun ChampsSignalement(etat: EtatSignalement, onMotif: (MotifSignalement) -> Unit, onCommentaire: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Eruditus.espacements.groupe)) {
        ListeGroupee(Modifier.selectableGroup()) {
            MotifSignalement.entries.forEachIndexed { i, motif ->
                LigneOption(
                    stringResource(motif.libelle()),
                    choisie = etat.motif == motif,
                    onChoisir = { onMotif(motif) },
                    separateur = i > 0,
                )
            }
        }
        Champ(
            etat.commentaire,
            onCommentaire,
            stringResource(R.string.champ_signalement),
            erreur = etat.erreur?.texte(),
            actif = !etat.envoi,
            plusieursLignes = true,
            compteur = Compteur(etat.longueur, ReglesSignalement.COMMENTAIRE_MAX),
        )
    }
}

/** « Annuler » à gauche (action sûre), « Envoyer » actif dès qu'un motif est choisi. */
@Composable
fun ActionsSignalement(etat: EtatSignalement, onAnnuler: () -> Unit, onEnvoyer: () -> Unit) {
    BoutonSecondaire(stringResource(R.string.annuler), onAnnuler, actif = !etat.envoi)
    BoutonPrincipal(stringResource(R.string.signalement_action), onEnvoyer, chargement = etat.envoi, actif = etat.actif || etat.envoi)
}

private fun MotifSignalement.libelle(): Int = when (this) {
    MotifSignalement.ReponseFausse -> R.string.signalement_motif_reponse_fausse
    MotifSignalement.EnonceAmbigu -> R.string.signalement_motif_enonce_ambigu
    MotifSignalement.ExplicationFausse -> R.string.signalement_motif_explication_fausse
    MotifSignalement.Autre -> R.string.signalement_motif_autre
}

/**
 * La ligne de résultat d'une correction : verdict, détail « La bonne réponse était C. », et pour
 * TalkBack le détail avec le texte du choix (« … La bonne réponse était C, Salvador Dalí. »), puis
 * la ligne d'enjeu [enjeu] (« Difficile. Elo, plus 12. ») : après le détail, avant l'explication.
 */
@Composable
private fun resultatAffiche(lecture: LectureCorrection, q: QuestionServie, enjeu: LigneEnjeu? = null): Resultat {
    val verdict = stringResource(
        when (lecture.verdict) {
            Verdict.Bonne -> R.string.partie_bonne_reponse
            Verdict.Fausse -> R.string.partie_fausse
            Verdict.TempsEcoule -> R.string.partie_temps_ecoule
        },
    )
    val genre = when (lecture.verdict) {
        Verdict.Bonne -> GenreResultat.Bonne
        Verdict.Fausse -> GenreResultat.Fausse
        Verdict.TempsEcoule -> GenreResultat.TempsEcoule
    }
    val lettre = lecture.lettreDetail
        ?: return Resultat(genre, verdict, annonce = enjeu?.let { "$verdict ${it.lue}" })
    val texteBonne = q.choix[ReglesPartie.LETTRES.indexOf(lettre)]
    val annonce = stringResource(R.string.partie_annonce, verdict, lettre.toString(), texteBonne)
    return Resultat(
        genre,
        verdict,
        detail = stringResource(R.string.partie_detail, lettre.toString()),
        annonce = enjeu?.let { "$annonce ${it.lue}" } ?: annonce,
    )
}

/**
 * Toutes les lignes que peut donner cette question, pour réserver la place de la plus longue
 * (DESIGN.md § 8) : la bonne réponse n'est pas connue avant la correction, d'où chaque lettre.
 */
@Composable
private fun resultatsPossibles(q: QuestionServie): List<Resultat> {
    val sansDetail = listOf(resultatAffiche(LectureCorrection(Verdict.Bonne, null, emptyList()), q))
    val avecDetail = ReglesPartie.LETTRES.flatMap { lettre ->
        listOf(Verdict.Fausse, Verdict.TempsEcoule).map { verdict -> resultatAffiche(LectureCorrection(verdict, lettre, emptyList()), q) }
    }
    return sansDetail + avecDetail
}

/**
 * § 10 Résultat (D69, D115) : « 3 bonnes réponses sur 5 », la variation de l'Elo en grand, sans
 * couleur, puis l'Elo après la partie ;
 * « Accueil » et « Rejouer » ([chargement] : la nouvelle partie est demandée).
 */
@Composable
fun EcranResultat(etat: EtatResultat, chargement: Boolean, onRejouer: () -> Unit, onAccueil: () -> Unit) {
    val bilan = etat.bilan
    val t = textes()
    GabaritInformation(
        titre = pluralStringResource(R.plurals.resultat_bonnes, bilan.bonnes, bilan.bonnes, etat.total),
        message = etat.message.enMessage(),
        secondaire = { BoutonSecondaire(stringResource(R.string.resultat_accueil), onAccueil, actif = !chargement) },
        principal = { BoutonPrincipal(stringResource(R.string.resultat_rejouer), onRejouer, chargement = chargement) },
    ) {
        val elo = bilan.elo
        Statistiques(
            listOf(Statistique(TextesElo.variation(t, elo.variation, elo.revision), lecture = TextesElo.variationLue(t, elo.variation, elo.revision))),
            Modifier.padding(top = Eruditus.espacements.decalage),
        )
        Text(
            TextesElo.apres(t, elo.valeur),
            style = Eruditus.typo.corps,
            color = Eruditus.couleurs.texteSecondaire,
        )
    }
}

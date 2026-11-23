package com.eruditus.android.ui.ecrans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import com.eruditus.android.R
import com.eruditus.android.donnees.api.DetailPartie
import com.eruditus.android.donnees.api.DetailQuestion
import com.eruditus.android.elo.TextesEnjeu
import com.eruditus.android.historique.CorpsQuestion
import com.eruditus.android.historique.EtatDetail
import com.eruditus.android.historique.EtatHistorique
import com.eruditus.android.historique.ReglesHistorique
import com.eruditus.android.historique.TextesHistorique
import com.eruditus.android.historique.VerdictRelu
import com.eruditus.android.historique.VueQuestion
import com.eruditus.android.ui.designsystem.components.ChoixReponse
import com.eruditus.android.ui.designsystem.components.CourbeElo
import com.eruditus.android.ui.designsystem.components.GenreResultat
import com.eruditus.android.ui.designsystem.components.LigneAction
import com.eruditus.android.ui.designsystem.components.LigneResultat
import com.eruditus.android.ui.designsystem.components.Lien
import com.eruditus.android.ui.designsystem.components.ListeGroupee
import com.eruditus.android.ui.designsystem.components.Resultat
import com.eruditus.android.ui.designsystem.components.Statistique
import com.eruditus.android.ui.designsystem.components.Statistiques
import com.eruditus.android.ui.designsystem.templates.GabaritListe
import com.eruditus.android.ui.designsystem.templates.Tirage
import com.eruditus.android.ui.designsystem.theme.Eruditus
import java.time.ZoneId
import java.util.Locale

/*
 * § 16 Historique et § 17 Détail d'une partie (D131 – D136). Gabarit Liste ; les dates suivent la
 * langue et le fuseau du téléphone.
 */

/**
 * § 16 Historique : la courbe de l'Elo (ou « Pas encore d'Elo à tracer. »), son résumé, puis la
 * liste « Mes parties » et « Voir plus » ; aucune partie : la phrase et son texte. Premier
 * chargement : indicateur centré ; tirer vers le bas recharge, le contenu reste. Une courbe absente
 * (son échec seul) laisse la liste seule.
 */
@Composable
fun EcranHistorique(
    etat: EtatHistorique,
    onRetour: () -> Unit,
    onTirage: () -> Unit,
    onPartie: (String) -> Unit,
    onVoirPlus: () -> Unit,
) {
    val t = textes()
    val zone = remember { ZoneId.systemDefault() }
    val langue = remember { Locale.getDefault() }
    val parties = etat.parties
    val aucune = parties?.isEmpty() == true
    GabaritListe(
        titre = stringResource(R.string.historique_titre),
        onRetour = onRetour,
        message = etat.message.enMessage(),
        chargement = etat.indicateurCentre,
        tirage = Tirage(etat.tirageEnCours, onTirage),
        vide = TextesHistorique.aucunePartie(t).takeIf { aucune },
        videDetail = TextesHistorique.aucunePartieDetail(t).takeIf { aucune },
    ) {
        if (parties.isNullOrEmpty()) return@GabaritListe
        etat.courbe?.let { courbe ->
            val texte = TextesHistorique.courbe(t, courbe)
            if (texte == null) {
                Text(
                    TextesHistorique.sansPoint(t),
                    Modifier.padding(horizontal = Eruditus.espacements.groupe),
                    style = Eruditus.typo.corps,
                    color = Eruditus.couleurs.texteSecondaire,
                )
            } else {
                CourbeElo(courbe.valeurs, courbe.provisoire)
                Column(
                    Modifier.padding(horizontal = Eruditus.espacements.groupe),
                    verticalArrangement = Arrangement.spacedBy(Eruditus.espacements.decalage),
                ) {
                    Text(texte.resume, style = Eruditus.typo.corps, color = Eruditus.couleurs.texteSecondaire)
                    texte.provisoire?.let { Text(it, style = Eruditus.typo.corps, color = Eruditus.couleurs.texteSecondaire) }
                }
            }
        }
        Column(
            Modifier.padding(top = Eruditus.espacements.section - Eruditus.espacements.blocs),
            verticalArrangement = Arrangement.spacedBy(Eruditus.espacements.decalage),
        ) {
            ListeGroupee(enTete = stringResource(R.string.historique_mes_parties)) {
                parties.forEachIndexed { i, partie ->
                    val ligne = TextesHistorique.ligne(t, partie, zone, langue)
                    LigneAction(
                        libelle = ligne.libelle,
                        onClick = { onPartie(partie.id) },
                        separateur = i > 0,
                        complement = ligne.complement,
                        valeur = ligne.valeur,
                        description = ligne.lue,
                    )
                }
            }
            if (etat.suivant != null) Lien(stringResource(R.string.historique_voir_plus), onVoirPlus, chargement = etat.suiteEnCours)
        }
    }
}

/**
 * § 17 Détail d'une partie : l'en-tête (« 3 bonnes réponses sur 5 », la variation en grand, « Elo :
 * 1261 »), puis une `ListeGroupee` par question, dans l'ordre, toutes dépliées. Premier chargement :
 * indicateur centré ; refusé (429, autre) : le message en haut, la barre reste, tirer réessaie.
 */
@Composable
fun EcranDetailPartie(etat: EtatDetail, onRetour: () -> Unit, onTirage: () -> Unit) {
    val t = textes()
    val zone = remember { ZoneId.systemDefault() }
    val langue = remember { Locale.getDefault() }
    val detail = etat.detail
    GabaritListe(
        titre = detail?.let { TextesHistorique.titreDetail(t, it.partie.date, zone, langue) }.orEmpty(),
        onRetour = onRetour,
        message = etat.message.enMessage(),
        chargement = etat.indicateurCentre,
        tirage = Tirage(etat.tirageEnCours, onTirage),
    ) {
        if (detail == null) return@GabaritListe
        EnteteDetail(detail)
        ReglesHistorique.vues(detail.questions).forEach { QuestionDetail(it) }
    }
}

/** « 3 bonnes réponses sur 5 », la variation de la partie en grand (sans couleur, D115), puis « Elo : 1261 ». */
@Composable
private fun EnteteDetail(detail: DetailPartie) {
    val t = textes()
    val elo = detail.elo
    Column(Modifier.padding(horizontal = Eruditus.espacements.groupe), verticalArrangement = Arrangement.spacedBy(Eruditus.espacements.decalage)) {
        Text(
            TextesHistorique.entete(t, detail.partie),
            style = Eruditus.typo.corps.copy(fontWeight = FontWeight.SemiBold),
            color = Eruditus.couleurs.texte,
        )
        Statistiques(listOf(Statistique(TextesHistorique.variation(t, elo), lecture = elo.variation?.let { TextesHistorique.variationLue(t, it) })))
        elo.valeur?.takeIf { elo.variation != null }?.let {
            Text(TextesHistorique.eloApres(t, it), style = Eruditus.typo.corps, color = Eruditus.couleurs.texteSecondaire)
        }
    }
}

/**
 * Une question : « Question 2 » en en-tête, la ligne d'enjeu du § 9 (version correction), puis
 * l'énoncé, les quatre choix en lecture seule, le verdict, l'explication et la source ; ou le
 * verdict et la note ; ou « Détail indisponible. ».
 */
@Composable
private fun QuestionDetail(q: VueQuestion) {
    val t = textes()
    val c = Eruditus.couleurs
    ListeGroupee(
        Modifier.padding(top = Eruditus.espacements.section - Eruditus.espacements.blocs),
        enTete = TextesHistorique.numero(t, q.numero),
    ) {
        Column(
            Modifier.padding(Eruditus.espacements.groupe),
            verticalArrangement = Arrangement.spacedBy(Eruditus.espacements.blocs),
        ) {
            TextesEnjeu.correction(t, q.enjeu)?.let { ligne ->
                Text(
                    ligne.texte,
                    Modifier.clearAndSetSemantics { contentDescription = ligne.lue },
                    style = Eruditus.typo.libelle,
                    color = c.texte,
                )
            }
            when (val corps = q.corps) {
                is CorpsQuestion.Complet -> {
                    Text(corps.enonce, style = Eruditus.typo.enonce, color = c.texte)
                    corps.choix.forEachIndexed { i, choix ->
                        ChoixReponse("ABCD"[i].toString(), choix.texte, choix.etat, onClick = {}, lecture = true)
                    }
                    Verdict(corps.verdict, complet = true)
                    corps.explication?.let { Text(it, style = Eruditus.typo.corps, color = c.texte) }
                    corps.source?.let {
                        Text(stringResource(R.string.partie_source, it), Modifier.padding(top = Eruditus.espacements.decalage - Eruditus.espacements.blocs), style = Eruditus.typo.petit, color = c.texteSecondaire)
                    }
                }
                is CorpsQuestion.VerdictSeul -> {
                    Verdict(corps.verdict, complet = false)
                    Text(TextesHistorique.note(t, corps.note), style = Eruditus.typo.corps, color = c.texteSecondaire)
                }
                CorpsQuestion.Indisponible -> Text(
                    TextesHistorique.note(t, DetailQuestion.Indisponible),
                    style = Eruditus.typo.corps,
                    color = c.texteSecondaire,
                )
            }
        }
    }
}

/** Le verdict d'une question relue : jamais annoncé à l'ouverture de l'écran (il est déjà à l'écran). */
@Composable
private fun Verdict(verdict: VerdictRelu, complet: Boolean) {
    LigneResultat(
        Resultat(
            when (verdict) {
                VerdictRelu.Bonne -> GenreResultat.Bonne
                VerdictRelu.Fausse -> GenreResultat.Fausse
                VerdictRelu.TempsEcoule -> GenreResultat.TempsEcoule
                VerdictRelu.NonRepondue -> GenreResultat.NonRepondue
            },
            TextesHistorique.verdict(textes(), verdict, complet),
        ),
        annoncee = false,
    )
}

package com.eruditus.android.catalogue

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import com.eruditus.android.ui.designsystem.components.ArriveeBonne
import com.eruditus.android.ui.designsystem.components.BoutonDestructeur
import com.eruditus.android.ui.designsystem.components.BoutonPrincipal
import com.eruditus.android.ui.designsystem.components.BoutonSecondaire
import com.eruditus.android.ui.designsystem.components.Champ
import com.eruditus.android.ui.designsystem.components.CodeRecuperation
import com.eruditus.android.ui.designsystem.components.ErreurFormulaire
import com.eruditus.android.ui.designsystem.components.EtatChoix
import com.eruditus.android.ui.designsystem.components.EtatSysteme
import com.eruditus.android.ui.designsystem.components.LigneClassement
import com.eruditus.android.ui.designsystem.components.LigneInfo
import com.eruditus.android.ui.designsystem.components.Lien
import com.eruditus.android.ui.designsystem.components.ListeGroupee
import com.eruditus.android.ui.designsystem.components.Message
import com.eruditus.android.ui.designsystem.components.Resultat
import com.eruditus.android.ui.designsystem.components.SeparateurPoints
import com.eruditus.android.ui.designsystem.components.Statistique
import com.eruditus.android.ui.designsystem.templates.ChoixPartie
import com.eruditus.android.ui.designsystem.templates.GabaritAccueil
import com.eruditus.android.ui.designsystem.templates.GabaritFormulaire
import com.eruditus.android.ui.designsystem.templates.GabaritInformation
import com.eruditus.android.ui.designsystem.templates.GabaritListe
import com.eruditus.android.ui.designsystem.templates.GabaritPartie
import com.eruditus.android.ui.designsystem.templates.QuestionPartie
import com.eruditus.android.ui.designsystem.theme.Eruditus
import com.eruditus.android.ui.designsystem.theme.Icones
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.eruditus.android.parametres.Theme
import com.eruditus.android.ui.ecrans.EcranParametres
import com.eruditus.android.ui.ecrans.EtatParametres

/*
 * Les gabarits remplis comme les maquettes des planches « Écrans » : aperçus et démonstrations du
 * catalogue. Ce ne sont pas les écrans de l'app (étape Écrans, SPEC § 4.2) : rien n'est branché.
 */

private val rien: () -> Unit = {}

@Composable
fun ExempleFormulaire(onRetour: () -> Unit = rien) {
    GabaritFormulaire(
        titre = "Créer un compte",
        onRetour = onRetour,
        action = { BoutonPrincipal("Créer mon compte", rien) },
    ) {
        Champ("Hibou_42", {}, "Pseudo", erreur = "Ce pseudo est déjà pris.", typeContenu = ContentType.Username)
        Champ(
            "nuit-blanche-42",
            {},
            "Mot de passe",
            aide = "8 caractères minimum",
            motDePasse = true,
            motDePasseAfficheAuDepart = true,
            typeContenu = ContentType.NewPassword,
        )
    }
}

@Composable
fun ExempleConnexion(onRetour: () -> Unit = rien) {
    GabaritFormulaire(
        titre = "Se connecter",
        onRetour = onRetour,
        action = { BoutonPrincipal("Se connecter", rien) },
    ) {
        Champ("Hibou_42", {}, "Pseudo", typeContenu = ContentType.Username)
        Champ("nuit-blanche", {}, "Mot de passe", motDePasse = true, typeContenu = ContentType.Password)
        ErreurFormulaire("Pseudo ou mot de passe incorrect.")
        Lien("Mot de passe oublié$NB?", rien)
    }
}

@Composable
fun ExempleBienvenue(message: Message? = null) {
    GabaritInformation(
        message = message,
        marque = "La culture générale, une question à la fois.",
        secondaire = { BoutonSecondaire("Se connecter", rien) },
        principal = { BoutonPrincipal("Créer un compte", rien) },
    )
}

@Composable
fun ExempleCodeRecuperation() {
    GabaritInformation(
        titre = "Note ton code de récupération",
        principal = { BoutonPrincipal("J’ai noté mon code", rien) },
    ) {
        CodeRecuperation("7K3M-Q9XA-2HDE-F8RT", Modifier.padding(top = Eruditus.espacements.icone))
        BoutonSecondaire("Copier", rien, petit = true, icone = Icones.Copier)
        Text(
            "Sans e-mail, c’est le seul moyen de retrouver ton compte si tu oublies ton mot de passe. Il ne sera plus jamais affiché.",
            style = Eruditus.typo.corps,
            color = Eruditus.couleurs.texteSecondaire,
        )
    }
}

@Composable
fun ExemplePasDeConnexion() {
    GabaritInformation(
        icone = EtatSysteme.Reseau,
        titre = "Pas de connexion",
        principal = { BoutonPrincipal("Réessayer", rien) },
    ) {
        Text(
            "Vérifie ta connexion internet, puis réessaie.",
            style = Eruditus.typo.corps,
            color = Eruditus.couleurs.texteSecondaire,
        )
    }
}

@Composable
fun ExempleAccueil(message: Message? = null) {
    GabaritAccueil(
        titre = "Hibou_42",
        statistiques = listOf(Statistique("12${points(12)}", "Score"), Statistique("3e sur 48", "Rang")),
        message = message,
        petitsBoutons = {
            BoutonSecondaire("Classement", rien, petit = true)
            BoutonSecondaire("Paramètres", rien, petit = true)
        },
        jouer = { BoutonPrincipal("Jouer", rien, grand = true) },
    )
}

/** Question 2 de la maquette : choix [etats], [resultat] facultatif. */
fun questionQ2(etats: List<EtatChoix>, resultat: Resultat? = null) = QuestionPartie(
    numero = 2,
    enonce = ENONCE_Q2,
    choix = CHOIX_Q2.mapIndexed { i, texte -> ChoixPartie("ABCD"[i].toString(), texte, etats[i]) },
    resultat = resultat,
    resultatsPossibles = resultatsPossibles("C", "Salvador Dalí"),
)

val ETATS_NEUTRES = List(4) { EtatChoix.Neutre }
val ETATS_FAUSSE = listOf(EtatChoix.Mauvaise, EtatChoix.Verrouille, EtatChoix.Bonne(ArriveeBonne.ApresErreur), EtatChoix.Verrouille)

@Composable
fun ExemplePartie(question: QuestionPartie, restantMs: Long, enCours: Boolean, onFermer: () -> Unit = rien) {
    GabaritPartie(
        compteur = "Question ${question.numero}/5",
        restantMs = restantMs,
        totalMs = 20_000,
        chronoEnCours = enCours,
        question = question,
        onChoix = {},
        bouton = "Question suivante",
        boutonVisible = question.resultat != null,
        onBouton = rien,
        onFermer = onFermer,
    )
}

@Composable
fun ExempleClassement(onRetour: () -> Unit = rien) {
    val top = listOf(
        "Hypatie" to 19, "LeBaronPerche" to 15, "MarieCurieDuDimanche" to 14, "Albatros" to 12, "tartine42" to 11,
        "Vercingetorix" to 10, "Zoe_L" to 9, "Quiz_Kid" to 8, "Penelope" to 7, "LaChouette" to 6,
    )
    GabaritListe(titre = "Classement", onRetour = onRetour, pied = "48 joueurs classés") {
        ListeGroupee(enTete = "Top 10", enTeteDroite = "Points") {
            top.forEachIndexed { i, (pseudo, score) ->
                LigneClassement(
                    if (i == 0) "1er" else "${i + 1}e",
                    pseudo,
                    "$score",
                    points(score),
                    lecture(i + 1, pseudo, score),
                    separateur = i > 0,
                )
            }
        }
        SeparateurPoints()
        ListeGroupee(teintee = true) {
            LigneClassement("27e", "Hibou_42", "4", points(4), lecture(27, "Hibou_42", 4, moi = true), moi = true)
        }
    }
}

/** L'écran Paramètres (D76) ; le thème choisi ici ne change que le rond, pas l'app. */
@Composable
fun ExempleParametres(onRetour: () -> Unit = rien) {
    var theme by remember { mutableStateOf(Theme.Systeme) }
    EcranParametres(
        EtatParametres(), "Hibou_42", "1.0.12", theme,
        onRetour = onRetour, onChangerPseudo = rien, onChangerMotDePasse = rien, onTheme = { theme = it }, onAvis = rien,
        onOuvrir = {}, onFermer = rien, onDeconnecter = rien, onMotDePasse = {}, onSupprimer = rien,
    )
}

@Composable
fun ExempleClassementVide(onRetour: () -> Unit = rien) {
    GabaritListe(titre = "Classement", onRetour = onRetour, vide = "Personne n’est encore classé. Lance une partie\u202F!")
}

package com.eruditus.android.catalogue

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.unit.dp
import com.eruditus.android.misesajour.Release
import com.eruditus.android.ui.designsystem.components.ArriveeBonne
import com.eruditus.android.ui.designsystem.components.BarreHaut
import com.eruditus.android.ui.designsystem.components.BarreHautPartie
import com.eruditus.android.ui.designsystem.components.BoutonDestructeur
import com.eruditus.android.ui.designsystem.components.BoutonPrincipal
import com.eruditus.android.ui.designsystem.components.BoutonSecondaire
import com.eruditus.android.ui.designsystem.components.Champ
import com.eruditus.android.ui.designsystem.components.Compteur
import com.eruditus.android.ui.designsystem.components.ChargementEcran
import com.eruditus.android.ui.designsystem.components.ChoixReponse
import com.eruditus.android.ui.designsystem.components.Chrono
import com.eruditus.android.ui.designsystem.components.CodeRecuperation
import com.eruditus.android.ui.designsystem.components.ContenuBoite
import com.eruditus.android.ui.designsystem.components.Encadre
import com.eruditus.android.ui.designsystem.components.ErreurFormulaire
import com.eruditus.android.ui.designsystem.components.EtatChoix
import com.eruditus.android.ui.designsystem.components.EtatSysteme
import com.eruditus.android.ui.designsystem.components.GenreMessage
import com.eruditus.android.ui.designsystem.components.GenreResultat
import com.eruditus.android.ui.designsystem.components.IconeEtat
import com.eruditus.android.ui.designsystem.components.Indicateur
import com.eruditus.android.ui.designsystem.components.Lien
import com.eruditus.android.ui.designsystem.components.LigneClassement
import com.eruditus.android.ui.designsystem.components.LigneInfo
import com.eruditus.android.ui.designsystem.components.LigneResultat
import com.eruditus.android.ui.designsystem.components.ListeGroupee
import com.eruditus.android.ui.designsystem.components.Marque
import com.eruditus.android.ui.designsystem.components.Message
import com.eruditus.android.ui.designsystem.components.MessageHaut
import com.eruditus.android.ui.designsystem.components.RangeeBoutons
import com.eruditus.android.ui.designsystem.components.Resultat
import com.eruditus.android.ui.designsystem.components.SaisieForcee
import com.eruditus.android.ui.designsystem.components.SeparateurPoints
import com.eruditus.android.ui.designsystem.components.Statistique
import com.eruditus.android.ui.designsystem.components.Statistiques
import com.eruditus.android.ui.designsystem.theme.AppuiForce
import com.eruditus.android.ui.designsystem.theme.Eruditus
import com.eruditus.android.ui.designsystem.theme.Icones
import com.eruditus.android.ui.ecrans.AnnonceMiseAJour
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.mutableIntStateOf
import com.eruditus.android.ui.designsystem.components.LigneAction
import com.eruditus.android.ui.designsystem.components.LigneOption

/*
 * Exemples du catalogue et des aperçus : les états de chaque composant, avec les données des
 * maquettes (planches « Composants et états » et « Écrans »). Code de debug et de preview
 * seulement : les textes d'exemple sont écrits ici, pas dans les ressources.
 */

const val NB = " "
private const val MOT_DE_PASSE = "nuit-blanche-42"
private val AIDE_PSEUDO = "3 à 20 caractères$NB: lettres, chiffres et _"
private const val AIDE_MOT_DE_PASSE = "8 caractères minimum"
private val rien: () -> Unit = {}

fun points(n: Int) = "$NB${if (n > 1) "points" else "point"}"

/** Ce que TalkBack lit pour une ligne du classement, d'un seul bloc (SPEC D74). */
fun lecture(rang: Int, pseudo: String, score: Int, moi: Boolean = false) =
    "rang $rang, $pseudo, $score${points(score)}${if (moi) ", toi" else ""}"

// --- Partie --------------------------------------------------------------------------------

val ENONCE_Q2 = "Qui a peint «${NB}La Persistance de la mémoire$NB», le tableau aux montres molles$NB?"
val CHOIX_Q2 = listOf("René Magritte", "Max Ernst", "Salvador Dalí", "Joan Miró")
val EXPLICATION_Q2 = "Salvador Dalí a peint ce petit tableau en 1931. Les montres qui fondent au soleil de " +
    "Port Lligat, près de chez lui en Catalogne, sont devenues l’image la plus connue du surréalisme. " +
    "Il est exposé au MoMA de New York depuis 1934. Dalí disait s’être inspiré d’un camembert fondu."
val SOURCE_Q2 = "MoMA, «${NB}The Persistence of Memory$NB»"
val ENONCE_Q5 = "Qui a écrit «${NB}Les Misérables$NB»$NB?"
val CHOIX_Q5 = listOf("Émile Zola", "Gustave Flaubert", "Honoré de Balzac", "Victor Hugo")

val RESULTAT_GAGNE = Resultat(GenreResultat.Bonne, "Bonne réponse$NB! +1${NB}point")
val RESULTAT_DEJA = Resultat(GenreResultat.Bonne, "Bonne réponse, déjà réussie$NB: pas de point cette fois.")
fun resultatFausse(lettre: String, texte: String) = Resultat(
    GenreResultat.Fausse,
    "Mauvaise réponse.",
    "La bonne réponse était $lettre.",
    annonce = "Mauvaise réponse. La bonne réponse était $lettre, $texte.",
)
fun resultatTemps(lettre: String, texte: String) = Resultat(
    GenreResultat.TempsEcoule,
    "Temps écoulé.",
    "La bonne réponse était $lettre.",
    annonce = "Temps écoulé. La bonne réponse était $lettre, $texte.",
)

/** Les lignes que peut donner une question dont la bonne réponse est [lettre]. */
fun resultatsPossibles(lettre: String, texte: String) =
    listOf(RESULTAT_GAGNE, RESULTAT_DEJA, resultatFausse(lettre, texte), resultatTemps(lettre, texte))

@Composable
fun ColumnScope.EtatsChoix() {
    EtatsChoixAvantCorrection()
    EtatsChoixCorrection(premiere = false)
}

@Composable
fun ColumnScope.EtatsChoixAvantCorrection() {
    Legende("Neutre", premiere = true)
    ChoixReponse("A", "René Magritte", EtatChoix.Neutre, rien)
    Legende("Pressé")
    ChoixReponse("A", "René Magritte", EtatChoix.Neutre, rien, interactionSource = AppuiForce)
    Legende("Envoi : indicateur dans le choix touché")
    ChoixReponse("A", "René Magritte", EtatChoix.Envoi, rien)
    Legende("Verrouillé : les autres choix, pendant l’envoi et la correction")
    ChoixReponse("B", "Max Ernst", EtatChoix.Verrouille, rien)
}

@Composable
fun ColumnScope.EtatsChoixCorrection(premiere: Boolean = true) {
    Legende("Bonne réponse (choisie)", premiere)
    ChoixReponse("C", "Salvador Dalí", EtatChoix.Bonne(ArriveeBonne.Choisie), rien)
    Legende("Mauvaise réponse (choisie)")
    ChoixReponse("A", "René Magritte", EtatChoix.Mauvaise, rien)
    Legende("Bonne réponse révélée")
    ChoixReponse("C", "Salvador Dalí", EtatChoix.Bonne(ArriveeBonne.ApresErreur), rien)
}

@Composable
fun ColumnScope.EtatsChrono() {
    Legende("En cours", premiere = true)
    BarreEtChrono(14_000, enCours = true)
    Legende("Fin proche : même couleur")
    BarreEtChrono(5_000, enCours = true)
    Legende("Arrêté après la réponse")
    BarreEtChrono(9_000, enCours = false)
    Legende("Temps écoulé")
    BarreEtChrono(0, enCours = false)
}

/** La barre de la partie et son chrono, sur la largeur d'un écran (les marges de 20 comprises). */
@Composable
fun BarreEtChrono(restantMs: Long, enCours: Boolean) {
    Column {
        BarreHautPartie("Question 2/5", restantMs, enCours, rien)
        Chrono(restantMs, 20_000, enCours, question = 2)
    }
}

@Composable
fun ColumnScope.EtatsResultat() {
    Legende("gagne", premiere = true)
    LigneResultat(RESULTAT_GAGNE)
    Legende("deja_reussie")
    LigneResultat(RESULTAT_DEJA)
    Legende("fausse")
    LigneResultat(resultatFausse("C", "Salvador Dalí"))
    Legende("sans_reponse, hors_delai")
    LigneResultat(resultatTemps("C", "Salvador Dalí"))
    Legende("Puis")
    BoutonSecondaire("Question suivante", rien)
}

// --- Saisie --------------------------------------------------------------------------------

@Composable
fun ColumnScope.EtatsChamp() {
    EtatsChampSaisie()
    EtatsChampMotDePasse(premiere = false)
    EtatsChampPlusieursLignes(premiere = false)
    EtatsChampPlusieursLignesErreur(premiere = false)
}

@Composable
fun ColumnScope.EtatsChampSaisie() {
    Legende("Vide, avec aide", premiere = true)
    Champ("", {}, "Pseudo", aide = AIDE_PSEUDO, typeContenu = ContentType.Username)
    Legende("En saisie")
    Champ("Hibou_4", {}, "Pseudo", aide = AIDE_PSEUDO, interactionSource = SaisieForcee)
    Legende("Erreur")
    Champ("Hibou_42", {}, "Pseudo", erreur = "Ce pseudo est déjà pris.")
}

@Composable
fun ColumnScope.EtatsChampMotDePasse(premiere: Boolean = true) {
    Legende("Mot de passe masqué", premiere)
    Champ(MOT_DE_PASSE, {}, "Mot de passe", aide = AIDE_MOT_DE_PASSE, motDePasse = true)
    Legende("Mot de passe affiché")
    Champ(MOT_DE_PASSE, {}, "Mot de passe", aide = AIDE_MOT_DE_PASSE, motDePasse = true, motDePasseAfficheAuDepart = true)
    Legende("Désactivé pendant un envoi")
    Champ("Hibou_42", {}, "Pseudo", actif = false)
}

/** Plusieurs lignes, avec compteur (D94) : le champ « Ton avis ». */
@Composable
fun ColumnScope.EtatsChampPlusieursLignes(premiere: Boolean = true) {
    Legende("Plusieurs lignes, vide, compteur", premiere)
    Champ("", {}, "Ton avis", plusieursLignes = true, compteur = Compteur(0, 1000))
    Legende("En saisie")
    Champ(AVIS, {}, "Ton avis", plusieursLignes = true, compteur = Compteur(AVIS.length, 1000), interactionSource = SaisieForcee)
    Legende("Au-delà du maximum : compteur rouge, contour inchangé")
    Champ(AVIS, {}, "Ton avis", plusieursLignes = true, compteur = Compteur(1042, 1000))
}

@Composable
fun ColumnScope.EtatsChampPlusieursLignesErreur(premiere: Boolean = true) {
    Legende("Plusieurs lignes, erreur du serveur", premiere)
    Champ(
        AVIS, {}, "Ton avis", plusieursLignes = true, compteur = Compteur(AVIS.length, 1000),
        erreur = "Tu as déjà envoyé un avis il y a peu. Réessaie dans 10 minutes.",
    )
    Legende("Désactivé pendant un envoi")
    Champ(AVIS, {}, "Ton avis", plusieursLignes = true, compteur = Compteur(AVIS.length, 1000), actif = false)
}

private const val AVIS = "Une idée : des questions sur les volcans.\n\nEt la question 3 d’hier était ambiguë."

@Composable
fun ColumnScope.ExempleCode() {
    Legende("Code", premiere = true)
    CodeRecuperation("7K3M-Q9XA-2HDE-F8RT")
    Legende("Copier")
    BoutonSecondaire("Copier", rien, petit = true, icone = Icones.Copier)
}

@Composable
fun ColumnScope.ExemplesMessages() {
    MessageHaut(Message("Ta session a expiré, reconnecte-toi."))
    MessageHaut(Message("Ton compte a été supprimé."))
    MessageHaut(Message("Une erreur est survenue. Réessaie.", GenreMessage.Alerte))
}

@Composable
fun ColumnScope.ExempleErreurFormulaire() {
    ErreurFormulaire("Pseudo ou mot de passe incorrect.")
    Lien("Mot de passe oublié$NB?", rien)
}

// --- Boutons -------------------------------------------------------------------------------

@Composable
fun ColumnScope.EtatsBoutonPrincipal() {
    Legende("Normal", premiere = true)
    BoutonPrincipal("Se connecter", rien)
    Legende("Pressé")
    BoutonPrincipal("Se connecter", rien, interactionSource = AppuiForce)
    Legende("Chargement")
    BoutonPrincipal("Se connecter", rien, chargement = true)
    Legende("Désactivé")
    BoutonPrincipal("Créer mon compte", rien, actif = false)
    Legende("Grand : Jouer")
    BoutonPrincipal("Jouer", rien, grand = true)
}

@Composable
fun ColumnScope.EtatsBoutonSecondaire() {
    Legende("Normal", premiere = true)
    BoutonSecondaire("Question suivante", rien)
    Legende("Pressé")
    BoutonSecondaire("Question suivante", rien, interactionSource = AppuiForce)
    Legende("Chargement")
    BoutonSecondaire("Question suivante", rien, chargement = true)
    Legende("Désactivé")
    BoutonSecondaire("Accueil", rien, actif = false)
    Legende("Petit, 48 dp : en retrait sur l’Accueil")
    RangeeBoutons {
        BoutonSecondaire("Classement", rien, petit = true)
        BoutonSecondaire("Paramètres", rien, petit = true)
    }
}

@Composable
fun ColumnScope.EtatsBoutonDestructeurEtLien() {
    Legende("Normal", premiere = true)
    BoutonDestructeur("Supprimer mon compte", rien)
    Legende("Pressé")
    BoutonDestructeur("Supprimer mon compte", rien, interactionSource = AppuiForce)
    Legende("Chargement")
    BoutonDestructeur("Supprimer définitivement", rien, chargement = true)
    Legende("Désactivé (mot de passe pas encore saisi)")
    BoutonDestructeur("Supprimer définitivement", rien, actif = false)
    Legende("Lien")
    Lien("Mot de passe oublié$NB?", rien)
    Legende("Lien pressé")
    Lien("Mot de passe oublié$NB?", rien, interactionSource = AppuiForce)
}

@Composable
fun ColumnScope.ExemplesRangee() {
    Legende("Les libellés tiennent : côte à côte, parts égales", premiere = true)
    RangeeBoutons {
        BoutonSecondaire("Classement", rien, petit = true)
        BoutonSecondaire("Paramètres", rien, petit = true)
    }
    Legende("Un mot ne tient plus : l’un sous l’autre")
    RangeeBoutons {
        BoutonSecondaire("Annuler", rien)
        BoutonDestructeur("Supprimer définitivement", rien)
    }
}

// --- Listes et chiffres --------------------------------------------------------------------

@Composable
fun ColumnScope.ExemplesClassement() {
    Legende("Lignes du top 10", premiere = true)
    ListeGroupee(enTete = "Top 10", enTeteDroite = "Points") {
        LigneClassement("1er", "Hypatie", "19", points(19), lecture(1, "Hypatie", 19))
        LigneClassement("2e", "LeBaronPerche", "15", points(15), lecture(2, "LeBaronPerche", 15), separateur = true)
        LigneClassement("4e", "MarieCurieDuDimanche", "11", points(11), lecture(4, "MarieCurieDuDimanche", 11), separateur = true)
    }
    Legende("Ma ligne dans le top 10")
    ListeGroupee { LigneClassement("3e", "Hibou_42", "12", points(12), lecture(3, "Hibou_42", 12, moi = true), moi = true) }
    Legende("Ma ligne sous le top 10")
    SeparateurPoints()
    ListeGroupee(teintee = true) {
        LigneClassement("27e", "Hibou_42", "4", points(4), lecture(27, "Hibou_42", 4, moi = true), moi = true)
    }
    Legende("Pas encore classé")
    Encadre("Réussis une question pour entrer au classement.")
}

@Composable
fun ColumnScope.ExemplesInfo() {
    ListeGroupee {
        LigneInfo("Pseudo", "Hibou_42")
        LigneInfo("Version", "Eruditus 1.0.12", separateur = true)
    }
}

/** `LigneOption` (D84) : les trois états, puis un choix à essayer. */
@Composable
fun ColumnScope.ExemplesOption() {
    Legende("Normal, pressé, choisie", premiere = true)
    ListeGroupee(Modifier.selectableGroup(), enTete = "Apparence") {
        LigneOption("Système", choisie = false, onChoisir = rien)
        LigneOption("Sombre", choisie = false, onChoisir = rien, separateur = true, interactionSource = AppuiForce)
        LigneOption("Clair", choisie = true, onChoisir = rien, separateur = true)
    }
    Legende("Libellé long : à la ligne, le rond reste centré")
    ListeGroupee(Modifier.selectableGroup()) {
        LigneOption("Une option au libellé assez long pour passer à la ligne", choisie = true, onChoisir = rien)
    }
}

/** `LigneAction` (D95) : normale, pressée, destructrice, puis un libellé long. */
@Composable
fun ColumnScope.ExemplesAction() {
    Legende("Normale, pressée", premiere = true)
    ListeGroupee(enTete = "Compte") {
        LigneInfo("Pseudo", "Hibou_42")
        LigneAction("Changer de pseudo", rien, separateur = true)
        LigneAction("Changer de mot de passe", rien, separateur = true, interactionSource = AppuiForce)
        LigneAction("Se déconnecter", rien, separateur = true)
    }
    Legende("Destructrice : libellé rouge, sans fond ; pressée")
    ListeGroupee {
        LigneAction("Supprimer mon compte", rien, destructrice = true)
    }
    ListeGroupee {
        LigneAction("Supprimer mon compte", rien, destructrice = true, interactionSource = AppuiForce)
    }
    Legende("Libellé long : à la ligne plutôt que coupé")
    ListeGroupee {
        LigneAction("Une action au libellé assez long pour passer à la ligne", rien)
    }
}

/** Un choix unique qu'on peut essayer : le rond suit la ligne touchée, sans mouvement. */
@Composable
fun ColumnScope.ChoixOptionJouable() {
    var choisie by remember { mutableIntStateOf(0) }
    ListeGroupee(Modifier.selectableGroup(), enTete = "À essayer") {
        listOf("Système", "Sombre", "Clair").forEachIndexed { i, libelle ->
            LigneOption(libelle, choisie = i == choisie, onChoisir = { choisie = i }, separateur = i > 0)
        }
    }
}

@Composable
fun ColumnScope.ExemplesStatistiques() {
    Legende("Accueil", premiere = true)
    Statistiques(listOf(Statistique("12${points(12)}", "Score"), Statistique("3e sur 48", "Rang")))
    Legende("Résultat : une valeur sans libellé")
    Statistiques(listOf(Statistique("+2${points(2)}")))
    Legende("Marque")
    Marque("La culture générale, une question à la fois.")
}

// --- Boîtes --------------------------------------------------------------------------------

private const val TEXTE_SUPPRESSION =
    "Ton compte, tes parties et ton score seront effacés définitivement. Ton pseudo deviendra libre."

/** Une boîte sur le voile, sans fenêtre : ce que montre l'écran. */
@Composable
fun SurVoile(contenu: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Eruditus.couleurs.fond)
            .background(Eruditus.couleurs.voile, Eruditus.rayons.groupe)
            .padding(vertical = Eruditus.espacements.boite, horizontal = Eruditus.espacements.blocs),
        contentAlignment = Alignment.Center,
    ) { contenu() }
}

@Composable
fun ColumnScope.ExemplesBoites() {
    ExemplesBoitesQuestion()
    ExempleBoiteDeconnexion(premiere = false)
}

@Composable
fun ColumnScope.ExemplesBoitesQuestion() {
    Legende("Code de récupération, retour système", premiere = true)
    SurVoile {
        ContenuBoite(
            titre = "As-tu bien noté ton code$NB?",
            texte = "Il ne sera plus jamais affiché.",
            actions = {
                BoutonSecondaire("Pas encore", rien)
                BoutonPrincipal("Oui", rien)
            },
        )
    }
    Legende("Partie, fermer ou retour système")
    SurVoile {
        ContenuBoite(
            titre = "Abandonner la partie$NB?",
            texte = "Les réponses déjà données comptent.",
            actions = {
                BoutonSecondaire("Continuer", rien)
                BoutonDestructeur("Abandonner", rien)
            },
        )
    }
}

@Composable
fun ColumnScope.ExempleBoiteDeconnexion(premiere: Boolean = true) {
    Legende("Compte, se déconnecter", premiere)
    SurVoile {
        ContenuBoite(
            titre = "Se déconnecter de tous tes appareils$NB?",
            actions = {
                BoutonSecondaire("Annuler", rien)
                BoutonPrincipal("Se déconnecter", rien)
            },
        )
    }
}

@Composable
fun ColumnScope.ExemplesBoiteSuppression() {
    ExempleBoiteSuppressionVide()
    ExempleBoiteSuppressionErreur(premiere = false)
    ExemplesBoiteSuppressionEnvoi(premiere = false)
}

@Composable
fun ColumnScope.ExempleBoiteSuppressionVide() {
    Legende("Vide", premiere = true)
    SurVoile { BoiteSuppression("", erreur = null, envoi = false) }
}

@Composable
fun ColumnScope.ExempleBoiteSuppressionErreur(premiere: Boolean = true) {
    Legende("Erreur", premiere)
    SurVoile { BoiteSuppression("nuit-blanche", erreur = "Mot de passe incorrect.", envoi = false) }
}

@Composable
fun ColumnScope.ExemplesBoiteSuppressionEnvoi(premiere: Boolean = true) {
    Legende("Envoi", premiere)
    SurVoile { BoiteSuppression(MOT_DE_PASSE, erreur = null, envoi = true) }
}

@Composable
fun BoiteSuppression(motDePasse: String, erreur: String?, envoi: Boolean, onMotDePasse: (String) -> Unit = {}, onAnnuler: () -> Unit = rien, onSupprimer: () -> Unit = rien) {
    ContenuBoite(
        texte = TEXTE_SUPPRESSION,
        champ = {
            Champ(
                motDePasse,
                onMotDePasse,
                "Mot de passe",
                erreur = erreur,
                motDePasse = true,
                actif = !envoi,
                typeContenu = ContentType.Password,
            )
        },
        actions = {
            BoutonSecondaire("Annuler", onAnnuler, actif = !envoi)
            BoutonDestructeur("Supprimer définitivement", onSupprimer, chargement = envoi, actif = motDePasse.isNotEmpty())
        },
    )
}

// --- États et chargement -------------------------------------------------------------------

@Composable
fun ColumnScope.ExemplesIconesEtat() {
    Row(horizontalArrangement = Arrangement.spacedBy(Eruditus.espacements.boite)) {
        IconeEtat(EtatSysteme.Reseau)
        IconeEtat(EtatSysteme.Serveur)
        IconeEtat(EtatSysteme.MiseAJour)
    }
}

@Composable
fun ColumnScope.ExemplesIndicateurs() {
    val c = Eruditus.couleurs
    Legende("Choix (24), bouton (20), écran (40)", premiere = true)
    Row(
        horizontalArrangement = Arrangement.spacedBy(Eruditus.espacements.boite),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Indicateur(c.texte)
        Indicateur(c.texte, taille = 20.dp, opacitePiste = 0.3f)
        Indicateur(c.texteSecondaire, taille = 40.dp, trait = 4.dp)
    }
    Legende("Chargement d’un écran")
    Box(Modifier.fillMaxWidth().height(160.dp)) { ChargementEcran() }
}

/** La barre du haut, avec retour et titre. */
@Composable
fun ColumnScope.ExemplesBarre() {
    Legende("Retour et titre", premiere = true)
    BarreHaut("Créer un compte", rien)
    Legende("Partie : fermer, compteur, secondes")
    BarreHautPartie("Question 2/5", 14_000, chronoEnCours = true, onFermer = rien)
    Legende("Partie, chrono arrêté")
    BarreHautPartie("Question 2/5", 9_000, chronoEnCours = false, onFermer = rien)
}

// --- Annonce ------------------------------------------------------------------------------------

/** La Release annoncée dans les exemples : Eruditus 1.0.13, l'app en 1.0.12. */
val RELEASE_EXEMPLE = Release(
    "1.0.13",
    "https://github.com/Bouss45/Eruditus/releases/tag/v1.0.13",
    "https://github.com/Bouss45/Eruditus/releases/download/v1.0.13/Eruditus-v1.0.13.apk",
)

@Composable
fun ExempleAnnonce() {
    AnnonceMiseAJour(RELEASE_EXEMPLE, "1.0.12", onPlusTard = {}, onTelecharger = {})
}

/** L'annonce dans sa place de l'Accueil : elle sort et entre comme le message en haut. */
@Composable
fun AnnonceInteractive() {
    var visible by remember { mutableStateOf(true) }
    GabaritAccueilMiniature(
        annonce = if (visible) RELEASE_EXEMPLE else null,
        onPlusTard = { visible = false },
        onTelecharger = { visible = true },
    )
    if (!visible) BoutonSecondaire("Réafficher l’annonce", { visible = true }, petit = true)
}

/** Statistiques et annonce de l'Accueil, sans le reste de l'écran. */
@Composable
private fun GabaritAccueilMiniature(annonce: Release?, onPlusTard: () -> Unit, onTelecharger: () -> Unit) {
    Column {
        Statistiques(listOf(Statistique("12${points(12)}", "Score"), Statistique("3e sur 48", "Rang")))
        AnimatedVisibility(
            visible = annonce != null,
            enter = expandVertically(Eruditus.mouvement.message()) + fadeIn(Eruditus.mouvement.message()),
            exit = shrinkVertically(Eruditus.mouvement.message()) + fadeOut(Eruditus.mouvement.message()),
        ) {
            Box(Modifier.padding(top = Eruditus.espacements.section)) {
                AnnonceMiseAJour(RELEASE_EXEMPLE, "1.0.12", onPlusTard, onTelecharger)
            }
        }
    }
}

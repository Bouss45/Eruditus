package com.eruditus.android.catalogue

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.eruditus.android.ui.designsystem.components.ArriveeBonne
import com.eruditus.android.ui.designsystem.components.BarreHautPartie
import com.eruditus.android.ui.designsystem.components.Boite
import com.eruditus.android.ui.designsystem.components.BoutonDestructeur
import com.eruditus.android.ui.designsystem.components.BoutonPrincipal
import com.eruditus.android.ui.designsystem.components.BoutonSecondaire
import com.eruditus.android.ui.designsystem.components.Champ
import com.eruditus.android.ui.designsystem.components.ChoixReponse
import com.eruditus.android.ui.designsystem.components.Chrono
import com.eruditus.android.ui.designsystem.components.EtatChoix
import com.eruditus.android.ui.designsystem.components.GenreMessage
import com.eruditus.android.ui.designsystem.components.LigneResultat
import com.eruditus.android.ui.designsystem.components.Message
import com.eruditus.android.ui.designsystem.components.RangeeBoutons
import com.eruditus.android.ui.designsystem.components.Resultat
import com.eruditus.android.ui.designsystem.components.ZoneMessage
import com.eruditus.android.ui.designsystem.templates.ChoixPartie
import com.eruditus.android.ui.designsystem.templates.GabaritPartie
import com.eruditus.android.ui.designsystem.templates.QuestionPartie
import com.eruditus.android.ui.designsystem.theme.Eruditus
import com.eruditus.android.ui.designsystem.theme.EruditusTheme
import com.eruditus.android.ui.designsystem.theme.MouvementEruditus
import com.eruditus.android.ui.designsystem.theme.animationsActivesDansAndroid
import com.eruditus.android.ui.designsystem.theme.rememberVibrations
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Catalogue du design system (D42) : l'écran d'ouverture des builds debug et preview, pour essayer
 * chaque composant sur un téléphone. Tous les états, sombre et clair, vibrations, ressorts et
 * gabarits. Jamais dans l'APK release.
 */

private enum class ThemeCatalogue(val libelle: String) { Systeme("Système"), Sombre("Sombre"), Clair("Clair") }

private enum class Demonstration(val libelle: String) {
    Formulaire("Formulaire · Créer un compte"),
    Connexion("Formulaire · Se connecter"),
    Bienvenue("Information · Bienvenue"),
    BienvenueMessage("Information · Bienvenue, session expirée"),
    Code("Information · Code de récupération"),
    PasDeConnexion("Information · Pas de connexion"),
    Accueil("Accueil"),
    Partie("Partie (jouable)"),
    Classement("Liste · Classement"),
    ClassementVide("Liste · Personne n’est classé"),
    Parametres("Liste · Paramètres"),
}

@Composable
fun Catalogue() {
    var theme by rememberSaveable { mutableStateOf(ThemeCatalogue.Systeme) }
    var animationsCoupees by rememberSaveable { mutableStateOf(false) }
    var demonstration by rememberSaveable { mutableStateOf<Demonstration?>(null) }
    val sombre = when (theme) {
        ThemeCatalogue.Systeme -> isSystemInDarkTheme()
        ThemeCatalogue.Sombre -> true
        ThemeCatalogue.Clair -> false
    }
    BarresSysteme(sombre)
    val animationsAndroid = animationsActivesDansAndroid()
    EruditusTheme(sombre = sombre, animationsActives = animationsAndroid && !animationsCoupees) {
        val ouverte = demonstration
        if (ouverte == null) {
            ListeCatalogue(
                theme = theme,
                onTheme = { theme = it },
                animationsAndroid = animationsAndroid,
                animationsCoupees = animationsCoupees,
                onAnimationsCoupees = { animationsCoupees = it },
                onOuvrir = { demonstration = it },
            )
        } else {
            BackHandler { demonstration = null }
            AfficherDemonstration(ouverte) { demonstration = null }
        }
    }
}

/** Icônes des barres système claires ou sombres, selon le thème choisi dans le catalogue. */
@Composable
private fun BarresSysteme(sombre: Boolean) {
    val activite = LocalActivity.current as? ComponentActivity
    DisposableEffect(activite, sombre) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (sombre) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        activite?.enableEdgeToEdge(style, style)
        onDispose { }
    }
}

@Composable
private fun ListeCatalogue(
    theme: ThemeCatalogue,
    onTheme: (ThemeCatalogue) -> Unit,
    animationsAndroid: Boolean,
    animationsCoupees: Boolean,
    onAnimationsCoupees: (Boolean) -> Unit,
    onOuvrir: (Demonstration) -> Unit,
) {
    val e = Eruditus.espacements
    val marges = WindowInsets.safeDrawing.asPaddingValues()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Eruditus.couleurs.fond),
        contentPadding = PaddingValues(
            start = e.ecran,
            end = e.ecran,
            top = marges.calculateTopPadding() + e.boite,
            bottom = marges.calculateBottomPadding() + e.section,
        ),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(e.blocs)) {
                Text("Catalogue", style = Eruditus.typo.titre1, color = Eruditus.couleurs.texte)
                Text(
                    "Le design system d’Eruditus (DESIGN.md) : chaque composant avec tous ses états, les ressorts, " +
                        "les vibrations et les gabarits. Builds debug et preview seulement.",
                    style = Eruditus.typo.corps,
                    color = Eruditus.couleurs.texteSecondaire,
                )
                Legende("Thème")
                RangeeBoutons {
                    ThemeCatalogue.entries.forEach { choix ->
                        BoutonSecondaire(coche(choix.libelle, choix == theme), { onTheme(choix) }, petit = true)
                    }
                }
                Legende("Animations (Android : ${if (animationsAndroid) "actives" else "désactivées"})")
                RangeeBoutons {
                    BoutonSecondaire(coche("Comme Android", !animationsCoupees), { onAnimationsCoupees(false) }, petit = true)
                    BoutonSecondaire(coche("Coupées", animationsCoupees), { onAnimationsCoupees(true) }, petit = true)
                }
            }
        }
        section("Gabarits", "Plein écran, remplis comme les maquettes ; retour pour revenir ici.") {
            Demonstration.entries.forEach { demo -> BoutonSecondaire(demo.libelle, { onOuvrir(demo) }) }
        }
        section("Mouvement et vibrations", "Vibrations : réglage « Vibreur au toucher » d’Android. Avant Android 11, Confirm devient un tic court et Reject l’appui long (D41).") {
            MouvementEtVibrations()
        }
        section("Choix de réponse", "États, puis à essayer : touche un choix (C est la bonne réponse).") {
            EtatsChoix()
            Legende("À essayer")
            ChoixInteractifs()
        }
        section("Chrono", "Barre et secondes, calés sur le temps restant ; TalkBack l’annonce à 10 s et à 5 s.") {
            EtatsChrono()
            Legende("À essayer")
            ChronoInteractif()
        }
        section("Ligne de résultat") { EtatsResultat() }
        section("Champ de saisie") {
            EtatsChamp()
            Legende("À essayer")
            ChampsInteractifs()
        }
        section("Code de récupération") { ExempleCode() }
        section("Message en haut d’écran", "Il pousse le contenu, ne recouvre jamais rien.") {
            ExemplesMessages()
            Legende("À essayer")
            MessageInteractif()
        }
        section("Annonce", "Mise à jour disponible sur l’Accueil (D62), en release seulement : à voir ici.") {
            ExempleAnnonce()
            Legende("À essayer : « Plus tard » la fait sortir")
            AnnonceInteractive()
        }
        section("Bouton principal") {
            EtatsBoutonPrincipal()
            Legende("À essayer : chargement de 2 s")
            BoutonInteractif()
        }
        section("Bouton secondaire") { EtatsBoutonSecondaire() }
        section("Bouton destructeur et lien") { EtatsBoutonDestructeurEtLien() }
        section("Rangée de boutons") { ExemplesRangee() }
        section("Erreur de formulaire") { ExempleErreurFormulaire() }
        section("Listes et classement") {
            ExemplesClassement()
            Legende("Ligne d’information")
            ExemplesInfo()
        }
        section("Ligne d’option", "Choix unique dans une liste (thème de Paramètres, D84) : toute la ligne se touche.") {
            ExemplesOption()
            ChoixOptionJouable()
        }
        section("Ligne d’action", "Une action dans une liste (Paramètres, D95) : toute la ligne se touche, sans chevron.") {
            ExemplesAction()
        }
        section("Chiffres et marque") { ExemplesStatistiques() }
        section("Boîte de confirmation") {
            ExemplesBoites()
            ExemplesBoiteSuppression()
            Legende("À essayer : vraies boîtes")
            BoitesInteractives()
        }
        section("Barre du haut") { ExemplesBarre() }
        section("Icônes d’état, indicateurs, chargement") {
            ExemplesIconesEtat()
            ExemplesIndicateurs()
        }
        section("Couleurs", "Tokens du thème affiché (DESIGN.md § 3).") { Couleurs() }
        section("Typographie", "Styles de DESIGN.md § 4, avec opsz réglé sur leur taille.") { Typographie() }
    }
}

private fun coche(libelle: String, actif: Boolean) = if (actif) "✓ $libelle" else libelle

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    titre: String,
    note: String? = null,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    item(key = titre) {
        Column(
            Modifier.padding(top = Eruditus.espacements.section),
            verticalArrangement = Arrangement.spacedBy(Eruditus.espacements.blocs),
        ) {
            Text(titre, style = Eruditus.typo.titreBoite, color = Eruditus.couleurs.texte)
            if (note != null) Text(note, style = Eruditus.typo.petit, color = Eruditus.couleurs.texteSecondaire)
            contenu()
        }
    }
}

// --- Démonstrations interactives -----------------------------------------------------------

private const val BONNE = 2

@Composable
private fun ColumnScope.ChoixInteractifs() {
    val neutres = List<EtatChoix>(4) { EtatChoix.Neutre }
    var etats by remember { mutableStateOf(neutres) }
    var resultat by remember { mutableStateOf<Resultat?>(null) }
    val portee = rememberCoroutineScope()
    CHOIX_Q2.forEachIndexed { i, texte ->
        ChoixReponse("ABCD"[i].toString(), texte, etats[i], onClick = {
            etats = List(4) { if (it == i) EtatChoix.Envoi else EtatChoix.Verrouille }
            portee.launch {
                delay(700)
                etats = List(4) { j ->
                    when {
                        j == BONNE && i == BONNE -> EtatChoix.Bonne(ArriveeBonne.Choisie)
                        j == i -> EtatChoix.Mauvaise
                        j == BONNE -> EtatChoix.Bonne(ArriveeBonne.ApresErreur)
                        else -> EtatChoix.Verrouille
                    }
                }
                resultat = if (i == BONNE) RESULTAT_GAGNE else resultatFausse("C", CHOIX_Q2[BONNE])
            }
        })
    }
    resultat?.let { LigneResultat(it) }
    RangeeBoutons {
        BoutonSecondaire("Recommencer", {
            etats = neutres
            resultat = null
        }, petit = true)
        BoutonSecondaire("Temps écoulé", {
            etats = List(4) { if (it == BONNE) EtatChoix.Bonne(ArriveeBonne.TempsEcoule) else EtatChoix.Verrouille }
            resultat = resultatTemps("C", CHOIX_Q2[BONNE])
        }, petit = true)
    }
}

/** Compte à rebours de [totalMs] piloté par l'horloge des images, comme dans une partie. */
@Composable
private fun compteARebours(enCours: Boolean, cle: Any, restant: Long, onRestant: (Long) -> Unit) {
    LaunchedEffect(enCours, cle) {
        if (!enCours) return@LaunchedEffect
        val depart = restant
        val debut = withFrameMillis { it }
        while (true) {
            val maintenant = withFrameMillis { it }
            val r = (depart - (maintenant - debut)).coerceAtLeast(0)
            onRestant(r)
            if (r == 0L) break
        }
    }
}

@Composable
private fun ColumnScope.ChronoInteractif() {
    var restant by remember { mutableLongStateOf(20_000) }
    var enCours by remember { mutableStateOf(false) }
    var question by remember { mutableIntStateOf(1) }
    compteARebours(enCours, question, restant) {
        restant = it
        if (it == 0L) enCours = false
    }
    Column {
        BarreHautPartie("Question $question/5", restant, enCours, onFermer = {})
        Chrono(restant, 20_000, enCours, question)
    }
    RangeeBoutons {
        BoutonSecondaire(if (enCours) "Arrêter" else "Lancer", {
            if (restant == 0L) restant = 20_000
            enCours = !enCours
        }, petit = true)
        BoutonSecondaire("Question suivante", {
            restant = 20_000
            question = question % 5 + 1
            enCours = true
        }, petit = true)
    }
}

@Composable
private fun ColumnScope.ChampsInteractifs() {
    var pseudo by remember { mutableStateOf("") }
    var motDePasse by remember { mutableStateOf("") }
    val pseudoRefuse = pseudo.isNotEmpty() && !Regex("[A-Za-z0-9_]{0,20}").matches(pseudo)
    Champ(
        pseudo,
        { pseudo = it },
        "Pseudo",
        aide = "3 à 20 caractères$NB: lettres, chiffres et _",
        erreur = if (pseudoRefuse) "Lettres, chiffres et _ seulement." else null,
    )
    Champ(motDePasse, { motDePasse = it }, "Mot de passe", aide = "8 caractères minimum", motDePasse = true)
}

@Composable
private fun ColumnScope.MessageInteractif() {
    var message by remember { mutableStateOf<Message?>(null) }
    ZoneMessage(message, PaddingValues())
    RangeeBoutons {
        BoutonSecondaire("Info", { message = Message("Ta session a expiré, reconnecte-toi.") }, petit = true)
        BoutonSecondaire("Alerte", { message = Message("Trop de tentatives. Réessaie dans quelques minutes.", GenreMessage.Alerte) }, petit = true)
        BoutonSecondaire("Fermer", { message = null }, petit = true)
    }
}

@Composable
private fun ColumnScope.BoutonInteractif() {
    var chargement by remember { mutableStateOf(false) }
    val portee = rememberCoroutineScope()
    BoutonPrincipal("Se connecter", {
        chargement = true
        portee.launch {
            delay(2000)
            chargement = false
        }
    }, chargement = chargement)
}

@Composable
private fun ColumnScope.BoitesInteractives() {
    var abandon by remember { mutableStateOf(false) }
    var suppression by remember { mutableStateOf(false) }
    var motDePasse by remember { mutableStateOf("") }
    var envoi by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }
    val portee = rememberCoroutineScope()
    BoutonSecondaire("Abandonner la partie$NB?", { abandon = true })
    BoutonDestructeur("Supprimer mon compte", {
        motDePasse = ""
        erreur = null
        suppression = true
    })
    Boite(
        visible = abandon,
        onFermer = { abandon = false },
        titre = "Abandonner la partie$NB?",
        texte = "Les réponses déjà données comptent.",
        actions = {
            BoutonSecondaire("Continuer", { abandon = false })
            BoutonDestructeur("Abandonner", { abandon = false })
        },
    )
    Boite(
        visible = suppression,
        onFermer = { if (!envoi) suppression = false },
        texte = "Ton compte, tes parties et ton score seront effacés définitivement. Ton pseudo deviendra libre.",
        champ = {
            Champ(motDePasse, {
                motDePasse = it
                erreur = null
            }, "Mot de passe", erreur = erreur, motDePasse = true, actif = !envoi)
        },
        actions = {
            BoutonSecondaire("Annuler", { suppression = false }, actif = !envoi)
            BoutonDestructeur("Supprimer définitivement", {
                envoi = true
                portee.launch {
                    delay(1500)
                    envoi = false
                    erreur = "Mot de passe incorrect."
                }
            }, chargement = envoi, actif = motDePasse.isNotEmpty())
        },
    )
}

@Composable
private fun ColumnScope.MouvementEtVibrations() {
    val vibrations = rememberVibrations()
    val mouvement = Eruditus.mouvement
    Legende("Vibrations (Android ${Build.VERSION.SDK_INT})")
    RangeeBoutons {
        BoutonSecondaire("Confirm", { vibrations.confirmation() }, petit = true)
        BoutonSecondaire("Reject", { vibrations.rejet() }, petit = true)
    }
    Legende("Ressorts")
    val echelle = remember { Animatable(1f) }
    val decalage = remember { Animatable(0f) }
    val portee = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val largeur = maxWidth.value
        Box(
            Modifier.graphicsLayer {
                scaleX = echelle.value
                scaleY = echelle.value
                translationX = decalage.value * density
            },
        ) {
            ChoixReponse("C", "Salvador Dalí", EtatChoix.Neutre, onClick = {})
        }
        Column(Modifier.padding(top = 80.dp)) {
            RangeeBoutons {
                BoutonSecondaire("Rebond", {
                    portee.launch {
                        echelle.animateTo(MouvementEruditus.REBOND_ECHELLE, mouvement.elanRebond())
                        echelle.animateTo(1f, mouvement.rebond())
                    }
                }, petit = true)
                BoutonSecondaire("Non", {
                    portee.launch {
                        if (mouvement.animationsActives) {
                            decalage.animateTo(0f, mouvement.non(), initialVelocity = MouvementEruditus.NON_VITESSE_DP_PAR_S)
                        }
                    }
                }, petit = true)
                BoutonSecondaire("Arrivée", {
                    portee.launch {
                        decalage.snapTo(largeur)
                        decalage.animateTo(0f, mouvement.arrivee())
                    }
                }, petit = true)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.Couleurs() {
    val c = Eruditus.couleurs
    val tokens = listOf(
        "fond" to c.fond, "surface" to c.surface, "surface-haute" to c.surfaceHaute, "contour" to c.contour,
        "contour-champ" to c.contourChamp, "separateur" to c.separateur, "texte" to c.texte,
        "texte-secondaire" to c.texteSecondaire, "neutre" to c.neutre, "piste" to c.piste, "accent" to c.accent,
        "sur-accent" to c.surAccent, "accent-teinte" to c.accentTeinte, "vert" to c.vert, "sur-vert" to c.surVert,
        "rouge" to c.rouge, "sur-rouge" to c.surRouge, "rouge-conteneur" to c.rougeConteneur,
        "sur-rouge-conteneur" to c.surRougeConteneur, "desactive-fond" to c.desactiveFond,
        "desactive-texte" to c.desactiveTexte,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Eruditus.espacements.blocs), verticalArrangement = Arrangement.spacedBy(Eruditus.espacements.blocs)) {
        tokens.forEach { (nom, couleur) -> Echantillon(nom, couleur) }
    }
}

@Composable
private fun Echantillon(nom: String, couleur: Color) {
    Column(Modifier.width(100.dp), verticalArrangement = Arrangement.spacedBy(Eruditus.espacements.decalage)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(Eruditus.tailles.cible)
                .background(couleur, Eruditus.rayons.champ)
                .border(Eruditus.tailles.contour, Eruditus.couleurs.separateur, Eruditus.rayons.champ),
        )
        Text(nom, style = Eruditus.typo.petit, color = Eruditus.couleurs.texteSecondaire)
    }
}

@Composable
private fun ColumnScope.Typographie() {
    val t = Eruditus.typo
    val styles: List<Triple<String, TextStyle, String>> = listOf(
        Triple("enonce", t.enonce, "Qui a peint «${NB}La Persistance de la mémoire$NB»$NB?"),
        Triple("marque", t.marque, "Eruditus"),
        Triple("titre-1", t.titre1, "3 bonnes réponses sur 5"),
        Triple("titre-barre", t.titreBarre, "Créer un compte"),
        Triple("chiffre", t.chiffre, "12${NB}points"),
        Triple("code", t.code, "7K3M Q9XA 0I1L"),
        Triple("titre-boite", t.titreBoite, "Abandonner la partie$NB?"),
        Triple("choix", t.choix, "Salvador Dalí · MarieCurieDuDimanche"),
        Triple("corps", t.corps, "Vérifie ta connexion internet, puis réessaie."),
        Triple("verdict", t.verdict, "Bonne réponse$NB! +1${NB}point"),
        Triple("bouton", t.bouton, "Se connecter"),
        Triple("bouton-grand", t.boutonGrand, "Jouer"),
        Triple("libelle", t.libelle, "Question 2/5 · 14${NB}s · Mot de passe"),
        Triple("lettre", t.lettre, "A B C D"),
        Triple("petit", t.petit, "3 à 20 caractères$NB: lettres, chiffres et _"),
        Triple("etiquette", t.etiquette, "Toi"),
    )
    styles.forEach { (nom, style, exemple) ->
        Legende(nom)
        Text(exemple, style = style, color = Eruditus.couleurs.texte)
    }
}

// --- Gabarits plein écran ------------------------------------------------------------------

@Composable
private fun AfficherDemonstration(demonstration: Demonstration, fermer: () -> Unit) {
    when (demonstration) {
        Demonstration.Formulaire -> ExempleFormulaire(onRetour = fermer)
        Demonstration.Connexion -> ExempleConnexion(onRetour = fermer)
        Demonstration.Bienvenue -> ExempleBienvenue()
        Demonstration.BienvenueMessage -> ExempleBienvenue(Message("Ta session a expiré, reconnecte-toi."))
        Demonstration.Code -> ExempleCodeRecuperation()
        Demonstration.PasDeConnexion -> ExemplePasDeConnexion()
        Demonstration.Accueil -> ExempleAccueil()
        Demonstration.Partie -> PartieJouable(fermer)
        Demonstration.Classement -> ExempleClassement(onRetour = fermer)
        Demonstration.ClassementVide -> ExempleClassementVide(onRetour = fermer)
        Demonstration.Parametres -> ExempleParametres(onRetour = fermer)
    }
}

/** Une partie qui alterne les deux questions des maquettes : envoi, correction, question suivante. */
@Composable
private fun PartieJouable(fermer: () -> Unit) {
    var numero by remember { mutableIntStateOf(1) }
    var etats by remember { mutableStateOf(List<EtatChoix>(4) { EtatChoix.Neutre }) }
    var resultat by remember { mutableStateOf<Resultat?>(null) }
    var restant by remember { mutableLongStateOf(20_000) }
    var enCours by remember { mutableStateOf(true) }
    var abandon by remember { mutableStateOf(false) }
    val portee = rememberCoroutineScope()
    val paire = numero % 2 == 0
    val enonce = if (paire) ENONCE_Q2 else ENONCE_Q5
    val textes = if (paire) CHOIX_Q2 else CHOIX_Q5
    val bonne = if (paire) 2 else 3
    val lettreBonne = "ABCD"[bonne].toString()

    compteARebours(enCours, numero, restant) {
        restant = it
        if (it == 0L && enCours) {
            enCours = false
            etats = List(4) { j -> if (j == bonne) EtatChoix.Bonne(ArriveeBonne.TempsEcoule) else EtatChoix.Verrouille }
            resultat = resultatTemps(lettreBonne, textes[bonne])
        }
    }
    BackHandler { abandon = true }
    GabaritPartie(
        compteur = "Question $numero/5",
        restantMs = restant,
        totalMs = 20_000,
        chronoEnCours = enCours,
        question = QuestionPartie(
            numero = numero,
            enonce = enonce,
            choix = textes.mapIndexed { i, t -> ChoixPartie("ABCD"[i].toString(), t, etats[i]) },
            resultat = resultat,
            resultatsPossibles = resultatsPossibles(lettreBonne, textes[bonne]),
        ),
        onChoix = { i ->
            enCours = false
            etats = List(4) { if (it == i) EtatChoix.Envoi else EtatChoix.Verrouille }
            portee.launch {
                delay(600)
                etats = List(4) { j ->
                    when {
                        j == bonne && i == bonne -> EtatChoix.Bonne(ArriveeBonne.Choisie)
                        j == i -> EtatChoix.Mauvaise
                        j == bonne -> EtatChoix.Bonne(ArriveeBonne.ApresErreur)
                        else -> EtatChoix.Verrouille
                    }
                }
                resultat = if (i == bonne) RESULTAT_GAGNE else resultatFausse(lettreBonne, textes[bonne])
            }
        },
        bouton = if (numero == 5) "Voir le résultat" else "Question suivante",
        boutonVisible = resultat != null,
        onBouton = {
            if (numero == 5) {
                fermer()
            } else {
                numero += 1
                etats = List(4) { EtatChoix.Neutre }
                resultat = null
                restant = 20_000
                enCours = true
            }
        },
        onFermer = { abandon = true },
    )
    Boite(
        visible = abandon,
        onFermer = { abandon = false },
        titre = "Abandonner la partie$NB?",
        texte = "Les réponses déjà données comptent.",
        actions = {
            BoutonSecondaire("Continuer", { abandon = false })
            BoutonDestructeur("Abandonner", {
                abandon = false
                fermer()
            })
        },
    )
}

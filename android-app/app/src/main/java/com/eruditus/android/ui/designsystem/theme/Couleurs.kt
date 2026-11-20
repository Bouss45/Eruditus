package com.eruditus.android.ui.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Couleurs du design system (DESIGN.md § 3), une instance par thème. Monochrome + accent bleu ;
 * vert et rouge seulement pour la correction et les saisies refusées, jamais seuls.
 * Vérifiées par `CouleursDesignTest` (valeurs du tableau) et `ContrastesTest` (contrastes).
 */
@Immutable
class CouleursEruditus(
    val sombre: Boolean,
    /** Fond des écrans. */
    val fond: Color,
    /** Choix, champs, listes, code. */
    val surface: Color,
    /** Boîte, message en haut. */
    val surfaceHaute: Color,
    /** Choix, bouton secondaire, message. */
    val contour: Color,
    /** Champs. */
    val contourChamp: Color,
    /** Lignes de liste, choix verrouillé, code. */
    val separateur: Color,
    /** Texte, énoncé, contour du choix en envoi. */
    val texte: Color,
    /** Aides, libellés, rangs, secondes arrêtées. */
    val texteSecondaire: Color,
    /** Chrono arrêté, points de séparation : jamais du texte. */
    val neutre: Color,
    /** Piste du chrono, pastille de lettre. */
    val piste: Color,
    /** Bouton principal, chrono qui tourne, liens, « Toi ». */
    val accent: Color,
    val surAccent: Color,
    /** Ma ligne au classement, encadré. */
    val accentTeinte: Color,
    /** Case, ✓ et verdict de la bonne réponse. */
    val vert: Color,
    val surVert: Color,
    /** Calque translucide de la pastille de lettre, posé sur [vert]. */
    val pastilleVert: Color,
    /** Contour et ✗ de la mauvaise réponse, erreur de saisie. */
    val rouge: Color,
    /** Lettre sur la pastille rouge. */
    val surRouge: Color,
    /** Fond de la mauvaise réponse, bouton destructeur. */
    val rougeConteneur: Color,
    val surRougeConteneur: Color,
    val desactiveFond: Color,
    val desactiveTexte: Color,
    /** Derrière une boîte. */
    val voile: Color,
    /** Appui. */
    val pression: Color,
    /** Appui sur le bouton principal. */
    val pressionAccent: Color,
    /** Ombre de la boîte (flou 32, 12 vers le bas). */
    val ombreBoite: Color,
)

private val Noir = Color(0xFF000000)
private val Blanc = Color(0xFFFFFFFF)

/** Thème sombre : celui des maquettes, par défaut (DESIGN.md § 2). */
val CouleursSombres = CouleursEruditus(
    sombre = true,
    fond = Color(0xFF080A0C),
    surface = Color(0xFF121518),
    surfaceHaute = Color(0xFF1B1F22),
    contour = Color(0xFF44484C),
    contourChamp = Color(0xFF646A6E),
    separateur = Color(0xFF23272A),
    texte = Color(0xFFE7ECF0),
    texteSecondaire = Color(0xFFA5ACB1),
    neutre = Color(0xFF7B8186),
    piste = Color(0xFF25292C),
    accent = Color(0xFF7DD0FA),
    surAccent = Color(0xFF080A0C),
    accentTeinte = Color(0xFF18262D),
    vert = Color(0xFF62D286),
    surVert = Color(0xFF061B0D),
    pastilleVert = Noir.copy(alpha = 0.16f),
    rouge = Color(0xFFF2716A),
    surRouge = Color(0xFF361717),
    rougeConteneur = Color(0xFF361717),
    surRougeConteneur = Color(0xFFFBDCDA),
    desactiveFond = Color(0xFF171A1C),
    desactiveTexte = Color(0xFF6E7276),
    voile = Noir.copy(alpha = 0.64f),
    pression = Color(0xFFE7ECF0).copy(alpha = 0.10f),
    pressionAccent = Color(0xFF080A0C).copy(alpha = 0.12f),
    ombreBoite = Noir.copy(alpha = 0.50f),
)

/** Thème clair : quand le téléphone est en clair. */
val CouleursClaires = CouleursEruditus(
    sombre = false,
    fond = Color(0xFFF3F6F8),
    surface = Color(0xFFFFFFFF),
    surfaceHaute = Color(0xFFFFFFFF),
    contour = Color(0xFFC0C5C9),
    contourChamp = Color(0xFF8D9398),
    separateur = Color(0xFFDEE2E5),
    texte = Color(0xFF14191C),
    texteSecondaire = Color(0xFF585F64),
    neutre = Color(0xFF81878C),
    piste = Color(0xFFDBDFE2),
    accent = Color(0xFF1364B0),
    surAccent = Color(0xFFFFFFFF),
    accentTeinte = Color(0xFFE7F0F7),
    vert = Color(0xFF21763C),
    surVert = Color(0xFFFFFFFF),
    // L'export donnait blanc 18 % (3,9:1) : noir 12 % (6,8:1), DESIGN.md § 3 note ¹.
    pastilleVert = Noir.copy(alpha = 0.12f),
    rouge = Color(0xFFBA2B28),
    surRouge = Color(0xFFFFFFFF),
    rougeConteneur = Color(0xFFFFEAE9),
    surRougeConteneur = Color(0xFF75201C),
    desactiveFond = Color(0xFFE3E7E9),
    desactiveTexte = Color(0xFF7E8489),
    voile = Color(0xFF14191C).copy(alpha = 0.45f),
    pression = Color(0xFF14191C).copy(alpha = 0.08f),
    pressionAccent = Blanc.copy(alpha = 0.14f),
    ombreBoite = Color(0xFF14191C).copy(alpha = 0.16f),
)

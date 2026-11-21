package com.eruditus.android.avis

/**
 * Le texte d'un avis (spec/regles.md § 7.1, D86), exactement la règle des hooks (`avis.js`, qui
 * fait foi) : retours à la ligne ramenés à `\n`, contrôles retirés, blancs retirés autour, puis 1 à
 * [MAX] caractères comptés en points de code. `ReglesAvisTest` la confronte aux hooks.
 */
object ReglesAvis {
    const val MAX = 1000

    /** Retirés partout : U+0000 – U+001F sauf `\n` (la tabulation comprise), U+007F – U+009F. */
    val CONTROLES: Set<Char> = (('\u0000'..'\u001F') - '\n' + ('\u007F'..'\u009F')).toSet()

    /** Retirés autour : les blancs de `trim()` en JavaScript, hors contrôles (déjà retirés). */
    val BLANCS: Set<Char> =
        setOf(' ', '\n', ' ', ' ', ' ', ' ', ' ', ' ', '　', '﻿') + (' '..' ')

    /** Le texte tel qu'il partira. */
    fun normaliser(saisie: String): String =
        saisie.replace("\r\n", "\n").replace('\r', '\n').filterNot { it in CONTROLES }.trim { it in BLANCS }

    /** En points de code : un émoji simple compte 1, une famille 5 (spec/regles.md § 7.1). */
    fun longueur(texte: String): Int = texte.codePointCount(0, texte.length)

    fun valide(saisie: String): Boolean = longueur(normaliser(saisie)) in 1..MAX
}

package com.eruditus.android.compte

/*
 * Règles du compte appliquées avant l'envoi (spec/regles.md § 1, D9, D10, D55) : exactement celles
 * des hooks (`server/pb_hooks/eruditus/comptes.js`), que `ReglesServeurTest` relit. Le serveur les
 * vérifie quand même : en cas de désaccord, il a raison.
 */

/** Où en est une saisie : valide, juste trop courte (l'aide suffit), ou impossible à rendre valide. */
enum class EtatSaisie { Valide, TropCourt, Invalide }

/** Pseudo (D9) : 3 à 20 caractères parmi `A–Z a–z 0–9 _`. Les noms réservés sont jugés par le serveur. */
object ReglesPseudo {
    val MOTIF = Regex("^[A-Za-z0-9_]{3,20}\$")
    private val PERMIS = Regex("^[A-Za-z0-9_]*\$")
    private const val MIN = 3

    /** Les espaces au début et à la fin sont retirés (spec/regles.md § 1.1). */
    fun nettoyer(saisie: String): String = saisie.trim()

    fun valide(pseudo: String): Boolean = MOTIF.matches(pseudo)

    fun etat(saisie: String): EtatSaisie {
        val pseudo = nettoyer(saisie)
        return when {
            valide(pseudo) -> EtatSaisie.Valide
            pseudo.length < MIN && PERMIS.matches(pseudo) -> EtatSaisie.TropCourt
            else -> EtatSaisie.Invalide
        }
    }
}

/**
 * Mot de passe (D10) : 8 à 71 caractères (points de code, comme PocketBase) et 72 octets au plus
 * en UTF-8 (limite de bcrypt). Jamais nettoyé : chaque espace compte.
 */
object ReglesMotDePasse {
    const val MIN = 8
    const val MAX = 71
    const val OCTETS_MAX = 72

    fun caracteres(motDePasse: String): Int = motDePasse.codePointCount(0, motDePasse.length)

    /** Octets en UTF-8 ; une moitié d'emoji isolée devient U+FFFD (3 octets), comme sur le serveur. */
    fun octets(motDePasse: String): Int {
        var total = 0
        var i = 0
        while (i < motDePasse.length) {
            val point = motDePasse.codePointAt(i)
            total += when {
                point < 0x80 -> 1
                point < 0x800 -> 2
                point < 0x10000 -> 3
                else -> 4
            }
            i += Character.charCount(point)
        }
        return total
    }

    fun valide(motDePasse: String): Boolean = etat(motDePasse) == EtatSaisie.Valide

    fun etat(motDePasse: String): EtatSaisie {
        val n = caracteres(motDePasse)
        return when {
            n > MAX || octets(motDePasse) > OCTETS_MAX -> EtatSaisie.Invalide
            n < MIN -> EtatSaisie.TropCourt
            else -> EtatSaisie.Valide
        }
    }
}

/**
 * Code de récupération (spec/regles.md § 1.6) : saisie tolérante puis règle de format du serveur
 * (`normaliserCode` de `comptes.js`, mêmes remplacements dans le même ordre).
 */
object SaisieCode {
    /** Alphabet de Crockford : 0–9 et A–Z sans I, L, O, U. */
    const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    const val LONGUEUR = 16
    const val SAISIE_MAX = 64

    /** Les 16 symboles du code, ou null s'il est mal formé. */
    fun normaliser(saisie: String): String? {
        if (saisie.length > SAISIE_MAX) return null
        val code = saisie.uppercase()
            .filterNot { blancJavaScript(it) || it == '-' }
            .replace('O', '0')
            .replace('I', '1')
            .replace('L', '1')
        return code.takeIf { it.length == LONGUEUR && it.all { c -> c in ALPHABET } }
    }

    /** `\s` des expressions régulières JavaScript (blancs et fins de ligne d'ECMAScript). */
    private fun blancJavaScript(c: Char): Boolean =
        c in "\t\n\u000B\u000C\r       　﻿" || c in ' '..' '

    /** `7K3M-Q9XA-2HDE-F8RT` : le format affiché, copié et renvoyé par le serveur (D35). */
    fun formater(code: String): String = code.chunked(4).joinToString("-")
}

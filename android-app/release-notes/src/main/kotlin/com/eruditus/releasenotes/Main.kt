package com.eruditus.releasenotes

import java.io.File
import kotlin.system.exitProcess

/**
 * Écrit le texte d'une Release (spec/publication.md § 3), appelé par `release.yml` :
 *
 * `--version 1.0.12 --code 1012 --commit <sha> --sortie notes.md [--log commits.log --comparaison <url>]`
 *
 * `--log` = sortie de `git log` au format [NotesDeVersion.FORMAT_GIT_LOG] depuis le tag de la
 * Release précédente ; absent = pas de Release précédente dans l'historique.
 */
fun main(args: Array<String>) {
    val options = lireOptions(args)
    fun requise(nom: String): String = options[nom] ?: usage("--$nom manquant")

    val entete = NotesDeVersion.entete(requise("version"), requise("code"), requise("commit"))
    val commits = options["log"]?.let { NotesDeVersion.lireGitLog(File(it).readText(Charsets.UTF_8)) }
    val texte = NotesDeVersion.texte(entete, commits, options["comparaison"]?.takeIf { commits != null })
    File(requise("sortie")).writeText(texte, Charsets.UTF_8)
}

private val OPTIONS = setOf("version", "code", "commit", "sortie", "log", "comparaison")

private fun lireOptions(args: Array<String>): Map<String, String> {
    if (args.size % 2 != 0) usage("arguments par paires attendus")
    return args.toList().chunked(2).associate { (nom, valeur) ->
        val cle = nom.removePrefix("--")
        if (!nom.startsWith("--") || cle !in OPTIONS) usage("option inconnue : $nom")
        cle to valeur
    }
}

private fun usage(message: String): Nothing {
    System.err.println("release-notes : $message")
    System.err.println("usage : --version V --code C --commit SHA --sortie FICHIER [--log FICHIER --comparaison URL]")
    exitProcess(2)
}

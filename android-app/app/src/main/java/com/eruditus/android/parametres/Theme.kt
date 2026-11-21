package com.eruditus.android.parametres

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Le thème de l'app (D77) : [Systeme] suit le téléphone ; [code] : sa valeur dans le fichier. */
enum class Theme(val code: String) {
    Systeme("systeme"),
    Sombre("sombre"),
    Clair("clair"),
}

/** Ce que l'app garde de ses réglages (D78) : le thème, rien d'autre. */
data class Preferences(val theme: Theme = Theme.Systeme)

/**
 * `preferences.json` (D78, spec/donnees.md § 1) : `{ version: 1, theme }`, en clair, écrit de
 * façon atomique. Thème inconnu, absent ou mal typé : Système, fichier gardé (un réglage plus
 * récent n'efface rien) ; fichier illisible ou d'une autre version : effacé, Système. Il n'est
 * lié à aucun compte : déconnexion et suppression n'y touchent pas.
 */
class FichierPreferences(private val fichier: File) {

    fun lire(): Preferences {
        if (!fichier.exists()) return Preferences()
        val lues = try {
            decoder(fichier.readText())
        } catch (e: IOException) {
            null
        }
        if (lues == null) fichier.delete()
        return lues ?: Preferences()
    }

    fun ecrire(preferences: Preferences) {
        val temporaire = File(fichier.parentFile, "${fichier.name}.tmp")
        try {
            temporaire.writeText(buildJsonObject {
                put("version", VERSION)
                put("theme", preferences.theme.code)
            }.toString())
            Files.move(temporaire.toPath(), fichier.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            temporaire.delete()
        }
    }

    private fun decoder(texte: String): Preferences? {
        val objet = try {
            Json.parseToJsonElement(texte) as? JsonObject
        } catch (e: IllegalArgumentException) {
            null
        } ?: return null
        val version = (objet["version"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
        if (version != VERSION.toLong()) return null
        val code = (objet["theme"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        return Preferences(Theme.entries.firstOrNull { it.code == code } ?: Theme.Systeme)
    }

    companion object {
        /** Le fichier, dans `noBackupFilesDir` (D78). */
        const val NOM = "preferences.json"
        private const val VERSION = 1
    }
}

/**
 * Le thème comme mode nuit de l'app (D77), en entiers d'Android pour rester testable sur la JVM :
 * Android 12 et plus le reçoivent par `UiModeManager.setApplicationNightMode` ([modeNuit]) ;
 * avant, l'activité impose le `uiMode` ([uiModeImpose]) dans `attachBaseContext`.
 */
object ReglesTheme {
    // android.app.UiModeManager
    private const val MODE_NIGHT_AUTO = 0
    private const val MODE_NIGHT_NO = 1
    private const val MODE_NIGHT_YES = 2

    // android.content.res.Configuration
    private const val UI_MODE_NIGHT_MASK = 0x30
    private const val UI_MODE_NIGHT_NO = 0x10
    private const val UI_MODE_NIGHT_YES = 0x20

    /** Pour `setApplicationNightMode` : auto (le téléphone décide), non ou oui. */
    fun modeNuit(theme: Theme): Int = when (theme) {
        Theme.Systeme -> MODE_NIGHT_AUTO
        Theme.Sombre -> MODE_NIGHT_YES
        Theme.Clair -> MODE_NIGHT_NO
    }

    /** Le `uiMode` à imposer à partir de celui du téléphone ([base]) ; null pour Système. */
    fun uiModeImpose(base: Int, theme: Theme): Int? = when (theme) {
        Theme.Systeme -> null
        Theme.Sombre -> (base and UI_MODE_NIGHT_MASK.inv()) or UI_MODE_NIGHT_YES
        Theme.Clair -> (base and UI_MODE_NIGHT_MASK.inv()) or UI_MODE_NIGHT_NO
    }

    /** Le `uiMode` qu'aura l'app avec [theme], sur un téléphone en [base]. */
    fun uiMode(base: Int, theme: Theme): Int = uiModeImpose(base, theme) ?: base

    fun sombre(uiMode: Int): Boolean = uiMode and UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES
}

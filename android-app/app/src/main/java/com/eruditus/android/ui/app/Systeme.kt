package com.eruditus.android.ui.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import com.eruditus.android.R

/*
 * Ce que l'app demande au système : presse-papiers (spec/ecrans.md § 5) et Obtainium (§ 14, D60).
 * Aucune permission (D26).
 */

/**
 * Copie le code au format `7K3M-Q9XA-2HDE-F8RT`, marqué comme sensible : Android 13 et plus n'en
 * montrent pas d'aperçu. Rend vrai si Android affiche lui-même une confirmation (13 et plus) ;
 * sinon l'écran montre « Code copié. ».
 */
fun copierCode(contexte: Context, code: String): Boolean {
    val presse = contexte.getSystemService(ClipboardManager::class.java) ?: return true // rien de copié : pas de « Code copié. »
    val clip = ClipData.newPlainText(contexte.getString(R.string.champ_code), code)
    clip.description.extras = PersistableBundle().apply {
        val cle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ClipDescription.EXTRA_IS_SENSITIVE else CLE_SENSIBLE
        putBoolean(cle, true)
    }
    presse.setPrimaryClip(clip)
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

/** Avant Android 13, la même clé, que certains claviers et presse-papiers lisent déjà. */
private const val CLE_SENSIBLE = "android.content.extra.IS_SENSITIVE"

/** Obtainium, version GitHub puis F-Droid (D60). */
private val OBTAINIUM = listOf("dev.imranr.obtainium", "dev.imranr.obtainium.fdroid")

/**
 * « Mettre à jour » (D60) : lance Obtainium par son intent de lancement, sans `<queries>` ni
 * permission ; s'il n'est pas installé, la page des Releases dans le navigateur.
 */
fun ouvrirMiseAJour(contexte: Context) {
    for (paquet in OBTAINIUM) {
        val lancement = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(paquet)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            contexte.startActivity(lancement)
            return
        } catch (e: ActivityNotFoundException) {
            // Pas installé sous ce nom : on essaie le suivant.
        }
    }
    try {
        contexte.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(contexte.getString(R.string.mise_a_jour_releases)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: ActivityNotFoundException) {
        // Aucun navigateur : rien d'autre à proposer.
    }
}

package com.eruditus.android.ui.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import com.eruditus.android.R
import com.eruditus.android.misesajour.Release
import com.eruditus.android.misesajour.ReglesMisesAJour
import com.eruditus.android.misesajour.ReglesMisesAJour.Cible

/*
 * Ce que l'app demande au système : presse-papiers (spec/ecrans.md § 5), Obtainium ou le
 * navigateur pour mettre à jour (§ 8, § 14, D63).
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

/**
 * « Télécharger » de l'Accueil et « Mettre à jour » (D63), une seule fonction : Obtainium s'il a
 * installé l'app (sa variante d'abord), lancé par son intent de lancement sans `<queries>` ni
 * permission ; sinon, ou s'il ne s'ouvre pas, l'APK de [release] dans le navigateur, à défaut sa
 * page ou la dernière Release du dépôt. L'ordre vient de `ReglesMisesAJour.cibles`.
 */
fun ouvrirMiseAJour(contexte: Context, release: Release?) {
    for (cible in ReglesMisesAJour.cibles(installateurs(contexte), release)) {
        val intent = when (cible) {
            is Cible.Obtainium -> Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(cible.paquet)
            is Cible.Navigateur -> Intent(Intent.ACTION_VIEW, Uri.parse(cible.adresse))
        }
        try {
            contexte.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            // Pas installé sous ce nom, ou aucun navigateur : on essaie la cible suivante.
        }
    }
}

/**
 * L'app qui a installé Eruditus et celle à l'origine de l'installation (Android 11 et plus), si
 * Android les connaît. Aucune permission : c'est l'app elle-même qu'on interroge.
 */
private fun installateurs(contexte: Context): List<String> {
    val pm = contexte.packageManager
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val info = pm.getInstallSourceInfo(contexte.packageName)
            listOfNotNull(info.installingPackageName, info.initiatingPackageName)
        } else {
            @Suppress("DEPRECATION")
            listOfNotNull(pm.getInstallerPackageName(contexte.packageName))
        }
    } catch (e: PackageManager.NameNotFoundException) {
        emptyList()
    } catch (e: IllegalArgumentException) {
        emptyList()
    }
}

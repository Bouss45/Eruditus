package com.eruditus.android.donnees.session

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Chiffrement du fichier de session par l'Android Keystore (spec/donnees.md § 2, D54) : clé
 * AES-256 `eruditus_session`, non exportable, sans authentification de l'utilisateur ; GCM, IV de
 * 12 octets tiré par le Keystore et écrit en tête des données.
 */
class ChiffreurKeystore : Chiffreur {

    private val magasin: KeyStore by lazy { KeyStore.getInstance(MAGASIN).apply { load(null) } }

    private fun cleExistante(): SecretKey? = magasin.getKey(ALIAS, null) as? SecretKey

    private fun cle(): SecretKey = cleExistante() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, MAGASIN).run {
        init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        generateKey()
    }

    override fun chiffrer(clair: ByteArray): ByteArray {
        val chiffre = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, cle()) }
        val iv = chiffre.iv
        if (iv.size != TAILLE_IV) throw GeneralSecurityException("IV inattendu")
        return iv + chiffre.doFinal(clair)
    }

    override fun dechiffrer(donnees: ByteArray): ByteArray {
        val cle = cleExistante() ?: throw GeneralSecurityException("clé absente")
        if (donnees.size <= TAILLE_IV) throw GeneralSecurityException("données trop courtes")
        val chiffre = Cipher.getInstance(TRANSFORMATION)
        chiffre.init(Cipher.DECRYPT_MODE, cle, GCMParameterSpec(TAILLE_TAG, donnees, 0, TAILLE_IV))
        return chiffre.doFinal(donnees, TAILLE_IV, donnees.size - TAILLE_IV)
    }

    override fun oublierCle() {
        try {
            magasin.deleteEntry(ALIAS)
        } catch (e: Exception) {
            // Keystore illisible : la prochaine écriture essaiera d'en recréer une.
        }
    }

    private companion object {
        const val MAGASIN = "AndroidKeyStore"
        const val ALIAS = "eruditus_session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAILLE_IV = 12
        const val TAILLE_TAG = 128
    }
}

package com.eruditus.android.donnees.session

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Le chiffrement du Keystore (D54) rejoué en JVM : même algorithme (AES-256-GCM, IV de 12 octets
 * en tête), clé en mémoire. [perdreCle] imite une clé effacée ou invalidée par Android.
 */
class ChiffreurLogiciel : Chiffreur {

    private var cle: SecretKey? = null
    var clesOubliees = 0
        private set

    private fun cle(): SecretKey = cle ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { cle = it }

    fun perdreCle() {
        cle = null
    }

    override fun chiffrer(clair: ByteArray): ByteArray {
        val chiffre = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, cle()) }
        return chiffre.iv + chiffre.doFinal(clair)
    }

    override fun dechiffrer(donnees: ByteArray): ByteArray {
        val actuelle = cle ?: throw GeneralSecurityException("clé absente")
        if (donnees.size < 12) throw GeneralSecurityException("trop court")
        val chiffre = Cipher.getInstance("AES/GCM/NoPadding")
        chiffre.init(Cipher.DECRYPT_MODE, actuelle, GCMParameterSpec(128, donnees, 0, 12))
        return chiffre.doFinal(donnees, 12, donnees.size - 12)
    }

    override fun oublierCle() {
        clesOubliees++
        cle = null
    }
}

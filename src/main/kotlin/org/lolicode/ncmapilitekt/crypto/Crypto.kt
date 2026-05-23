package org.lolicode.ncmapilitekt.crypto

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * EApi crypto: AES-128-ECB
 *
 * Encryption:
 * 1. Build message: nobody{apiPath}use{json}md5forencrypt
 * 2. Compute MD5 of message → hex lower = digest
 * 3. requestData = {apiPath}-36cd479b6b5-{json}-36cd479b6b5-{digest}
 * 4. AES-ECB encrypt requestData → hex upper = params
 *
 * Decryption: AES-ECB decrypt response if not plain JSON
 */
internal object EApiCrypto {
    val KEY: ByteArray = "e82ckenh8dichen8".toByteArray(Charsets.UTF_8)

    fun encrypt(apiPath: String, json: String): String {
        val message = "nobody${apiPath}use${json}md5forencrypt"
        val digest = md5(message.toByteArray(Charsets.UTF_8))
        val requestData = "${apiPath}-36cd479b6b5-${json}-36cd479b6b5-${digest}"
        return aesEcbEncrypt(requestData.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }
    }

    fun decrypt(bytes: ByteArray): ByteArray {
        return aesEcbDecrypt(bytes)
    }

    private fun md5(data: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    private fun aesEcbEncrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(KEY, "AES"))
        return cipher.doFinal(data)
    }

    private fun aesEcbDecrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(KEY, "AES"))
        return cipher.doFinal(data)
    }
}

package org.lolicode.ncmapilitekt

import org.lolicode.ncmapilitekt.crypto.EApiCrypto
import kotlin.test.Test
import kotlin.test.assertTrue

internal class CryptoTest {

    @Test
    fun `EApi encrypt produces non-empty hex string`() {
        val result = EApiCrypto.encrypt("/api/v3/song/detail", """{"c":"[{\"id\": '123'}]"}""")
        assertTrue(result.isNotEmpty())
        assertTrue(result.matches(Regex("[0-9A-F]+")))
    }

    @Test
    fun `EApi encrypt and decrypt round-trip`() {
        val apiPath = "/api/v3/song/detail"
        val json = """{"c":"[{\"id\": '123'}]","e_r":true}"""
        val encrypted = EApiCrypto.encrypt(apiPath, json)
        // The encrypted form is hex; convert back to bytes then decrypt
        val encryptedBytes = encrypted.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val decrypted = EApiCrypto.decrypt(encryptedBytes).toString(Charsets.UTF_8)
        // Decrypted text contains the original requestData string
        assertTrue(decrypted.contains(apiPath))
        assertTrue(decrypted.contains("36cd479b6b5"))
    }
}

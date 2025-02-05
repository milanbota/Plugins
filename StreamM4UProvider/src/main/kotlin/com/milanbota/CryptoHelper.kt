package com.milanbota

import android.os.Build
import androidx.annotation.RequiresApi
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

object Crypto {
    private val KEY_SIZE = 256
    private val IV_SIZE = 128
    private val HASH_CIPHER = "AES/CBC/PKCS5Padding"
    private val AES = "AES"
    private val APPEND = "Salted__"

    fun generateChecksum(encryptedPlayerData: String): String {
        val secretKey = "KRWN3AdgmxEMcd2vLN1ju9qKe8Feco5h"
        val combinedData = encryptedPlayerData + secretKey

        val md = MessageDigest.getInstance("MD5")
        val hashBytes = md.digest(combinedData.toByteArray())

        return hashBytes.joinToString("") { "%02x".format(it) }
    }


    fun generateSalt(length: Int): ByteArray {
        return ByteArray(length).apply {
            SecureRandom().nextBytes(this)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun encrypt(password: String, plainText: String): String {
        val saltBytes = generateSalt(8)
        val key = ByteArray(KEY_SIZE / 8)
        val iv = ByteArray(IV_SIZE / 8)
        EvpKDF(password.toByteArray(), KEY_SIZE, IV_SIZE, saltBytes, key, iv)

        val keyS = SecretKeySpec(key, AES)
        val cipher = Cipher.getInstance(HASH_CIPHER)
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, keyS, ivSpec)

        val cipherText = cipher.doFinal(plainText.toByteArray())
        // Thanks kientux for this: https://gist.github.com/kientux/bb48259c6f2133e628ad
        // Create CryptoJS-like encrypted!
        val sBytes = APPEND.toByteArray()
        val b = ByteArray(sBytes.size + saltBytes.size + cipherText.size)
        System.arraycopy(sBytes, 0, b, 0, sBytes.size)
        System.arraycopy(saltBytes, 0, b, sBytes.size, saltBytes.size)
        System.arraycopy(cipherText, 0, b, sBytes.size + saltBytes.size, cipherText.size)

        val bEncode = Base64.getEncoder().encode(b)
        return base64ToHex(String(bEncode))
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun base64ToHex(base64Encoded: String): String {
        val decodedBytes = Base64.getDecoder().decode(base64Encoded)
        return decodedBytes.joinToString("") { String.format("%02x", it) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun hexToBase64(hexString: String): ByteArray {
        val byteArray = hexString.chunked(2) // Split the hex string into pairs of characters
            .map { it.toInt(16).toByte() }      // Convert each pair to a byte
            .toByteArray()

        return Base64.getEncoder().encode(byteArray)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun decrypt(ciphertext: String, password: String): String {
        val keySize = 256
        val ivSize = 128
        val base64Encoded = hexToBase64(ciphertext)

        // var wordArray = WordArray.create([0x53616c74, 0x65645f5f]).concat(salt).concat(ciphertext);
        val ctBytes = Base64.getDecoder().decode(base64Encoded.decodeToString())
        val saltBytes = Arrays.copyOfRange(ctBytes, 8, 16)
        val ciphertextBytes = Arrays.copyOfRange(ctBytes, 16, ctBytes.size)

        val key = ByteArray(keySize / 8)
        val iv = ByteArray(ivSize / 8)
        EvpKDF(password.toByteArray(charset("UTF-8")), keySize, ivSize, saltBytes, key, iv)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val recoveredPlaintextBytes = cipher.doFinal(ciphertextBytes)
        val recoveredPlaintext = String(recoveredPlaintextBytes)

        return recoveredPlaintext
    }

    fun EvpKDF(
        password: ByteArray?,
        keySize: Int,
        ivSize: Int,
        salt: ByteArray?,
        resultKey: ByteArray?,
        resultIv: ByteArray?
    ): ByteArray {
        return EvpKDF(password, keySize, ivSize, salt, 1, "MD5", resultKey, resultIv)
    }

    fun EvpKDF(
        password: ByteArray?,
        keySize: Int,
        ivSize: Int,
        salt: ByteArray?,
        iterations: Int,
        hashAlgorithm: String?,
        resultKey: ByteArray?,
        resultIv: ByteArray?
    ): ByteArray {
        var keySize = keySize
        var ivSize = ivSize
        keySize = keySize / 32
        ivSize = ivSize / 32
        val targetKeySize = keySize + ivSize
        val derivedBytes = ByteArray(targetKeySize * 4)
        var numberOfDerivedWords = 0
        var block: ByteArray? = null
        val hasher = MessageDigest.getInstance(hashAlgorithm)
        while (numberOfDerivedWords < targetKeySize) {
            if (block != null) {
                hasher.update(block)
            }
            hasher.update(password)
            block = hasher.digest(salt)
            hasher.reset()

            // Iterations
            for (i in 1 until iterations) {
                block = hasher.digest(block)
                hasher.reset()
            }

            System.arraycopy(
                block, 0, derivedBytes, numberOfDerivedWords * 4,
                min(block!!.size.toDouble(), ((targetKeySize - numberOfDerivedWords) * 4).toDouble())
                    .toInt()
            )

            numberOfDerivedWords += block.size / 4
        }

        System.arraycopy(derivedBytes, 0, resultKey, 0, keySize * 4)
        System.arraycopy(derivedBytes, keySize * 4, resultIv, 0, ivSize * 4)

        return derivedBytes // key + iv
    }

    /**
     * Copied from https://stackoverflow.com/a/140861
     */
    fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((s[i].digitToIntOrNull(16) ?: -1 shl 4)
            + s[i + 1].digitToIntOrNull(16)!! ?: -1).toByte()
            i += 2
        }
        return data
    }

}
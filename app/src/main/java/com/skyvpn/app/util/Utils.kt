package com.skyvpn.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

object CryptoUtils {

    fun encryptAES(data: String, key: String): ByteArray {
        val salt = ByteArray(SALT_SIZE)
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(salt)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(key, salt), GCMParameterSpec(TAG_SIZE_BITS, iv))
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        return salt + iv + encrypted
    }

    fun decryptAES(encrypted: ByteArray, key: String): String {
        require(encrypted.size > SALT_SIZE + IV_SIZE) { "Invalid encrypted payload" }
        val salt = encrypted.copyOfRange(0, SALT_SIZE)
        val iv = encrypted.copyOfRange(SALT_SIZE, SALT_SIZE + IV_SIZE)
        val payload = encrypted.copyOfRange(SALT_SIZE + IV_SIZE, encrypted.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(key, salt), GCMParameterSpec(TAG_SIZE_BITS, iv))
        return cipher.doFinal(payload).toString(Charsets.UTF_8)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_SIZE_BITS = 128
}

object ClipboardUtils {
    fun getText(context: Context): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.getItemAt(0)?.text?.toString()
    }

    fun setText(context: Context, label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        cm.setPrimaryClip(clip)
    }
}

object ShareUtils {
    fun shareText(context: Context, text: String, title: String = "Share Config") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

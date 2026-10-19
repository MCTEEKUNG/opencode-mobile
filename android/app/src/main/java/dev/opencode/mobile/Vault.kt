package dev.opencode.mobile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Computer(val id: String, val name: String, val origin: String, val token: String)

/** Credentials never enter saved-instance state, logs, or Android backups. */
class Vault(context: Context) {
    private val prefs = context.getSharedPreferences("vault", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("opencode.mobile", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("opencode.mobile", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun load(): List<Computer> {
        val value = prefs.getString("computers", null) ?: return emptyList()
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val array = JSONArray(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        return (0 until array.length()).map { i -> array.getJSONObject(i).let {
            Computer(it.getString("id"), it.getString("name"), it.getString("origin"), it.getString("token"))
        } }
    }
    fun save(computers: List<Computer>) {
        val array = JSONArray()
        computers.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("origin", it.origin).put("token", it.token)) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(array.toString().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("computers", Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()) { "Cannot save credentials" }
    }
    fun add(name: String, origin: String, token: String): Computer =
        Computer(UUID.randomUUID().toString(), name.ifBlank { "My computer" }, origin, token)
}

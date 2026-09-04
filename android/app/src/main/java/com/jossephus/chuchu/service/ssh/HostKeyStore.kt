package com.jossephus.chuchu.service.ssh

import android.content.SharedPreferences
import java.util.Base64
import java.security.MessageDigest

class HostKeyStore(
    private val prefs: SharedPreferences,
) {
    companion object {
        const val PREFS_NAME = "host_keys"
    }

    fun loadKey(host: String, port: Int, algorithm: String): ByteArray? {
        val encoded = prefs.getString(key(host, port, algorithm), null) ?: return null
        return Base64.getDecoder().decode(encoded)
    }

    fun saveKey(host: String, port: Int, algorithm: String, keyBytes: ByteArray) {
        val encoded = Base64.getEncoder().encodeToString(keyBytes)
        prefs.edit().putString(key(host, port, algorithm), encoded).apply()
    }

    /**
     * Key đã lưu cho host:port ở MỌI thuật toán khác [algorithm]. Khoá lưu theo thuật
     * toán, nên server (hay kẻ đứng giữa) chìa ra key loại khác — RSA thay vì ed25519 —
     * thì tra đúng thuật toán sẽ trống và trông y hệt "host lần đầu" (audit 4/9 #3).
     * OpenSSH coi đó là KEY ĐÃ ĐỔI; ở đây cũng phải thế.
     */
    private fun otherAlgorithmKeys(host: String, port: Int, algorithm: String): List<ByteArray> {
        val prefix = "$host:$port:"
        return prefs.all.keys
            .filter { it.startsWith(prefix) && it != key(host, port, algorithm) }
            .mapNotNull { k -> (prefs.all[k] as? String)?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() } }
    }

    fun check(
        host: String,
        port: Int,
        algorithm: String,
        keyBytes: ByteArray,
    ): HostKeyCheck {
        val existing = loadKey(host, port, algorithm)
        if (existing == null) {
            val other = otherAlgorithmKeys(host, port, algorithm).firstOrNull()
            return if (other == null) HostKeyCheck.Unknown(fingerprint = fingerprintSha256(keyBytes))
            else HostKeyCheck.Changed(
                previousFingerprint = fingerprintSha256(other),
                fingerprint = fingerprintSha256(keyBytes),
            )
        }
        return when {
            existing.contentEquals(keyBytes) -> HostKeyCheck.Match
            else ->
                HostKeyCheck.Changed(
                    previousFingerprint = fingerprintSha256(existing),
                    fingerprint = fingerprintSha256(keyBytes),
                )
        }
    }

    private fun key(host: String, port: Int, algorithm: String): String =
        "$host:$port:$algorithm"

    private fun fingerprintSha256(keyBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(keyBytes)
        val encoded = Base64.getEncoder().withoutPadding().encodeToString(digest)
        return "SHA256:$encoded"
    }
}

sealed interface HostKeyCheck {
    data object Match : HostKeyCheck
    data class Unknown(val fingerprint: String) : HostKeyCheck
    data class Changed(
        val previousFingerprint: String,
        val fingerprint: String,
    ) : HostKeyCheck
}

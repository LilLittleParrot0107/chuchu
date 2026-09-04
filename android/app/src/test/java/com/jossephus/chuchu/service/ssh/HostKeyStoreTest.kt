package com.jossephus.chuchu.service.ssh

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Khoá lưu theo host:port:thuật toán. Kịch bản đáng sợ nhất: đã lưu ed25519, kẻ đứng
 * giữa chìa RSA — tra đúng thuật toán thì trống, trước đây trông y hệt "host lần đầu".
 */
class HostKeyStoreTest {
    private val ed = byteArrayOf(1, 2, 3, 4)
    private val rsa = byteArrayOf(9, 9, 9, 9, 9)

    @Test
    fun `key da luu, khop la Match`() {
        val s = HostKeyStore(MemPrefs())
        s.saveKey("h", 22, "ssh-ed25519", ed)
        assertEquals(HostKeyCheck.Match, s.check("h", 22, "ssh-ed25519", ed))
    }

    @Test
    fun `chua luu gi la Unknown`() {
        val r = HostKeyStore(MemPrefs()).check("h", 22, "ssh-ed25519", ed)
        assertTrue(r is HostKeyCheck.Unknown)
    }

    @Test
    fun `da luu ed25519, server chia RSA la Changed chu khong phai Unknown`() {
        val s = HostKeyStore(MemPrefs())
        s.saveKey("h", 22, "ssh-ed25519", ed)
        val r = s.check("h", 22, "ssh-rsa", rsa)
        assertTrue("phải là Changed: $r", r is HostKeyCheck.Changed)
        r as HostKeyCheck.Changed
        assertTrue(r.previousFingerprint.startsWith("SHA256:"))
        assertTrue(r.previousFingerprint != r.fingerprint)
    }

    @Test
    fun `cung thuat toan, key khac la Changed`() {
        val s = HostKeyStore(MemPrefs())
        s.saveKey("h", 22, "ssh-ed25519", ed)
        assertTrue(s.check("h", 22, "ssh-ed25519", rsa) is HostKeyCheck.Changed)
    }

    @Test
    fun `host khac cong khac khong dinh nhau`() {
        val s = HostKeyStore(MemPrefs())
        s.saveKey("h", 22, "ssh-ed25519", ed)
        assertTrue(s.check("h", 2222, "ssh-rsa", rsa) is HostKeyCheck.Unknown)
        assertTrue(s.check("h2", 22, "ssh-rsa", rsa) is HostKeyCheck.Unknown)
    }

    /** SharedPreferences trong RAM — chỉ phần HostKeyStore dùng. */
    private class MemPrefs : SharedPreferences {
        private val map = mutableMapOf<String, Any?>()
        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int) = defValue
        override fun getLong(key: String?, defValue: Long) = defValue
        override fun getFloat(key: String?, defValue: Float) = defValue
        override fun getBoolean(key: String?, defValue: Boolean) = defValue
        override fun contains(key: String?) = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            override fun putString(key: String?, value: String?) = apply { pending[key!!] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = this
            override fun putInt(key: String?, value: Int) = this
            override fun putLong(key: String?, value: Long) = this
            override fun putFloat(key: String?, value: Float) = this
            override fun putBoolean(key: String?, value: Boolean) = this
            override fun remove(key: String?) = apply { pending[key!!] = null }
            override fun clear() = apply { map.clear() }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() { pending.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }; pending.clear() }
        }
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }
}

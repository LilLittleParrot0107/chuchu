package com.jossephus.chuchu.service

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Bật/tắt Tailscale qua intent mà app Tailscale Android công bố cho Tasker:
 * broadcast tường minh tới `com.tailscale.ipn.IPNReceiver` với action CONNECT_VPN /
 * DISCONNECT_VPN. Không có Tailscale thì broadcast rơi vào khoảng không — vô hại.
 * Manifest cần <queries><package android:name="com.tailscale.ipn"/></queries> (API 30+).
 *
 * [lastEvent] = dòng chẩn đoán hiện ở Settings: user không đọc được logcat, mà "VPN
 * không tắt" có thể do kohi chưa gửi lệnh (session vẫn còn sống) hoặc gửi rồi mà
 * Tailscale không nghe (Always-on VPN của Android bật lại ngay). Phải phân biệt được.
 */
object TailscaleControl {
    private const val PKG = "com.tailscale.ipn"
    private const val RECEIVER = "com.tailscale.ipn.IPNReceiver"

    private val _lastEvent = MutableStateFlow("")
    val lastEvent: StateFlow<String> = _lastEvent

    fun connect(context: Context) = send(context, "$PKG.CONNECT_VPN", "connect")
    fun disconnect(context: Context) = send(context, "$PKG.DISCONNECT_VPN", "disconnect")

    private fun send(context: Context, action: String, label: String) {
        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        runCatching {
            context.applicationContext.sendBroadcast(Intent(action).setClassName(PKG, RECEIVER))
            _lastEvent.value = "$label sent $stamp"
            Log.i("TailscaleControl", "$action sent")
        }.onFailure {
            _lastEvent.value = "$label FAILED $stamp: ${it.message}"
            Log.w("TailscaleControl", "$action failed: ${it.message}")
        }
    }
}

package com.jossephus.chuchu.service

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Bật/tắt Tailscale qua intent mà app Tailscale Android công bố cho Tasker: broadcast
 * tường minh tới `com.tailscale.ipn.IPNReceiver` với action CONNECT_VPN / DISCONNECT_VPN.
 * Không có Tailscale thì broadcast rơi vào khoảng không — vô hại. Bản thử 8/9 đã chứng
 * minh CONNECT ăn trên máy user mà không cần miễn trừ pin; .50 hỏng vì kohi tự tắt lại
 * ngay sau đó (xem TerminalSessionRepository).
 * Manifest cần <queries><package android:name="com.tailscale.ipn"/></queries> (API 30+).
 *
 * [events] = các dòng chẩn đoán hiện ở Settings: user không đọc được logcat, mà "VPN
 * không tắt" có thể do kohi chưa gửi lệnh (session vẫn còn sống) hoặc gửi rồi mà
 * Tailscale không nghe (Always-on VPN của Android bật lại ngay). Phải phân biệt được.
 */
object TailscaleControl {
    private const val PKG = "com.tailscale.ipn"
    private const val RECEIVER = "com.tailscale.ipn.IPNReceiver"

    /** 8 lệnh gần nhất, mới nhất trước — Settings hiện để thấy có bật/tắt liên tục không. */
    private val _events = MutableStateFlow<List<String>>(emptyList())
    val events: StateFlow<List<String>> = _events

    /** [why] = luật nào bắn lệnh, hiện ở Settings để đối chiếu khi test. */
    fun connect(context: Context, why: String) = send(context, "$PKG.CONNECT_VPN", "connect", why)
    fun disconnect(context: Context, why: String) = send(context, "$PKG.DISCONNECT_VPN", "disconnect", why)

    private fun send(context: Context, action: String, label: String, why: String) {
        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        runCatching {
            // FLAG_INCLUDE_STOPPED_PACKAGES: vivo dọn app nền kiểu force-stop → Tailscale ở
            // trạng thái "stopped" và broadcast mặc định bị hệ thống bỏ im lặng. Có cờ thì
            // hệ thống khởi động lại tiến trình Tailscale để giao lệnh (user nhận ra 8/9).
            context.applicationContext.sendBroadcast(
                Intent(action)
                    .setClassName(PKG, RECEIVER)
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES),
            )
            record("$label ($why) sent $stamp")
            Log.i("TailscaleControl", "$action sent")
        }.onFailure {
            record("$label FAILED $stamp: ${it.message}")
            Log.w("TailscaleControl", "$action failed: ${it.message}")
        }
    }

    private fun record(line: String) {
        _events.value = (listOf(line) + _events.value).take(8)
    }
}

package com.jossephus.chuchu.service

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tắt Tailscale qua intent mà app Tailscale Android công bố cho Tasker: broadcast
 * tường minh tới `com.tailscale.ipn.IPNReceiver` với action DISCONNECT_VPN. Không có
 * Tailscale thì broadcast rơi vào khoảng không — vô hại.
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

    /**
     * Chỉ có tắt. CONNECT_VPN cố tình không có: Tailscale thi hành nó bằng job nền rồi
     * startForegroundService — Android 12+ cấm app ở nền làm vậy trừ khi user tắt tối ưu
     * pin cho Tailscale, nên lệnh bật rơi vào im lặng (user bỏ 7/9). DISCONNECT thì ăn vì
     * lúc đó Tailscale đang là foreground service. [why] hiện ở Settings để đối chiếu.
     */
    fun disconnect(context: Context, why: String) = send(context, "$PKG.DISCONNECT_VPN", "disconnect", why)

    /**
     * BẢN THỬ 8/9 — chỉ nút "test: send connect" ở Settings gọi. Gửi CONNECT_VPN vô điều
     * kiện, ghi kết quả kiểm tra tailnet trước và 3s sau để phân biệt: (a) kohi đọc sai
     * "up" nên xưa nay không gửi, (b) Android chặn Tailscale khởi động ở nền, (c) ăn.
     * Kết luận xong thì xoá hàm này cùng nút.
     */
    suspend fun testConnect(context: Context, checker: com.jossephus.chuchu.service.ssh.TailscaleStatusChecker): String {
        val before = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { checker.probe() }
        send(context, "$PKG.CONNECT_VPN", "connect", "test")
        kotlinx.coroutines.delay(3_000)
        val after = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { checker.probe() }
        return "before: $before · after 3s: $after"
    }

    private fun send(context: Context, action: String, label: String, why: String) {
        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        runCatching {
            context.applicationContext.sendBroadcast(Intent(action).setClassName(PKG, RECEIVER))
            _lastEvent.value = "$label ($why) sent $stamp"
            Log.i("TailscaleControl", "$action sent")
        }.onFailure {
            _lastEvent.value = "$label FAILED $stamp: ${it.message}"
            Log.w("TailscaleControl", "$action failed: ${it.message}")
        }
    }
}

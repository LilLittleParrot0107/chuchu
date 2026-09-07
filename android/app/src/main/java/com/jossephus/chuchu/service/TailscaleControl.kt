package com.jossephus.chuchu.service

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Bật/tắt Tailscale qua intent mà app Tailscale Android công bố cho Tasker:
 * broadcast tường minh tới `com.tailscale.ipn.IPNReceiver` với action CONNECT_VPN /
 * DISCONNECT_VPN. Không có Tailscale thì broadcast rơi vào khoảng không — vô hại.
 * Manifest cần <queries><package android:name="com.tailscale.ipn"/></queries> (API 30+).
 */
object TailscaleControl {
    private const val PKG = "com.tailscale.ipn"
    private const val RECEIVER = "com.tailscale.ipn.IPNReceiver"

    fun connect(context: Context) = send(context, "$PKG.CONNECT_VPN")
    fun disconnect(context: Context) = send(context, "$PKG.DISCONNECT_VPN")

    private fun send(context: Context, action: String) {
        runCatching {
            context.sendBroadcast(Intent(action).setClassName(PKG, RECEIVER))
        }.onFailure { Log.w("TailscaleControl", "$action failed: ${it.message}") }
    }
}

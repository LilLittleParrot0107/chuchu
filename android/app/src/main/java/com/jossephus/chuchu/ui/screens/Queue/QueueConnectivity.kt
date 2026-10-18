package com.jossephus.chuchu.ui.screens.Queue

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

private const val NET_TAG = "QueueConnectivity"

/**
 * Nghe mạng ở tầng UI rồi đẩy vào QueueViewModel. VM không giữ NetworkCallback
 * (không rò sau khi VM chết, unit-test được). Dùng chung cho màn Queue đầy đủ
 * và bubble — trước đây bubble hard-code setOnline(true) nên offline vẫn poll.
 */
@Composable
internal fun ObserveQueueConnectivity(vm: QueueViewModel) {
    val context = LocalContext.current.applicationContext
    DisposableEffect(context, vm) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        if (cm == null) {
            vm.setOnline(true)
            onDispose {}
        } else {
            fun currentOnline(): Boolean {
                val net = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(net) ?: return false
                return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
            vm.setOnline(currentOnline())
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = vm.setOnline(true)

                override fun onLost(network: Network) {
                    // Mất một mạng chưa chắc mất hết (wifi rớt còn mobile).
                    vm.setOnline(currentOnline())
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    vm.setOnline(currentOnline())
                }
            }
            runCatching { cm.registerDefaultNetworkCallback(cb) }
                .onFailure { Log.w(NET_TAG, "registerDefaultNetworkCallback failed", it) }
            onDispose {
                runCatching { cm.unregisterNetworkCallback(cb) }
                    .onFailure { Log.w(NET_TAG, "unregisterNetworkCallback failed", it) }
            }
        }
    }
}

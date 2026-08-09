package com.deniscerri.ytdl.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Thin wrapper over [ConnectivityManager] for the in-app update flow: a one-shot [isOnline] check
 * and a live [online] flow that emits the current state up front and again on every connectivity
 * change. Validated capability (NET_CAPABILITY_VALIDATED) is required, so a captive-portal Wi-Fi
 * with no real internet still reads as offline.
 */
object NetworkMonitor {

    /** True when a network with verified internet access is currently available. */
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService<ConnectivityManager>() ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasInternet()
    }

    /**
     * Emits connectivity as it changes, starting with the current state. [distinctUntilChanged]
     * keeps it to real transitions, so a collector re-runs its update check only when the device
     * actually comes back online.
     */
    fun online(context: Context): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService<ConnectivityManager>()
        if (cm == null) { trySend(false); close(); return@callbackFlow }

        trySend(isOnline(context))
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(isOnline(context)) }
            override fun onLost(network: Network) { trySend(isOnline(context)) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(caps.hasInternet())
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun NetworkCapabilities.hasInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

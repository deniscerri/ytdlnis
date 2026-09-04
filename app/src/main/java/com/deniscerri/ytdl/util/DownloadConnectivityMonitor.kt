package com.deniscerri.ytdl.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

/** Tracks whether Android currently has a validated default internet connection. */
class DownloadConnectivityMonitor private constructor(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _isOnline = MutableStateFlow(readCurrentState())
    val isOnline = _isOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onUnavailable() = refresh()

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            _isOnline.value = isUsable(networkCapabilities)
        }
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(callback)
    }

    suspend fun awaitOnline() {
        isOnline.filter { it }.first()
    }

    private fun refresh() {
        _isOnline.value = readCurrentState()
    }

    private fun readCurrentState(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        return isUsable(connectivityManager.getNetworkCapabilities(network))
    }

    private fun isUsable(capabilities: NetworkCapabilities?): Boolean {
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    companion object {
        @Volatile
        private var instance: DownloadConnectivityMonitor? = null

        fun getInstance(context: Context): DownloadConnectivityMonitor {
            return instance ?: synchronized(this) {
                instance ?: DownloadConnectivityMonitor(context).also { instance = it }
            }
        }
    }
}

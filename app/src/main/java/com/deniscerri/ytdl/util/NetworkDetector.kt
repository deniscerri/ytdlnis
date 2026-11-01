package com.deniscerri.ytdl.util

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager
import java.util.concurrent.CopyOnWriteArrayList

object NetworkDetector {
    private const val TAG = "NetworkDetector"

    enum class NetworkCategory {
        WIFI,
        CELLULAR_5G,
        CELLULAR_4G_OR_LOWER,
        OTHER,
        DISCONNECTED
    }

    @Volatile
    private var is5GDisplayOverride: Boolean = false

    private val activeObservers = CopyOnWriteArrayList<NetworkStateObserver>()

    interface NetworkStateObserver {
        fun onNetworkStateChanged(category: NetworkCategory, isAllowed: Boolean)
    }

    /**
     * Checks if active network is Wi-Fi, Ethernet, or other unmetered local transport.
     */
    fun isWifiOrEthernet(context: Context): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)
        }.getOrDefault(false)
    }

    /**
     * Determines whether the current cellular connection is 5G (SA or NSA).
     */
    @SuppressLint("MissingPermission")
    fun is5GConnected(context: Context): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false

            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return false
            }

            // 1. Check live TelephonyCallback/PhoneStateListener override
            if (is5GDisplayOverride) {
                return true
            }

            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return false

            // 2. Direct data network type check (NETWORK_TYPE_NR = 20)
            val dataNetworkType = tm.dataNetworkType
            if (dataNetworkType == TelephonyManager.NETWORK_TYPE_NR) {
                return true
            }

            // 3. Check ServiceState for NSA (Non-Standalone) 5G via reflection
            checkServiceStateFor5G(tm)
        }.getOrDefault(false)
    }

    private fun checkServiceStateFor5G(tm: TelephonyManager): Boolean {
        return runCatching {
            val method = tm.javaClass.getMethod("getServiceState")
            val serviceState = method.invoke(tm) ?: return false
            val str = serviceState.toString()
            str.contains("nrState=CONNECTED", ignoreCase = true) ||
                    str.contains("nrState=NOT_RESTRICTED", ignoreCase = true) ||
                    str.contains("nsaState=5", ignoreCase = true) ||
                    (str.contains("EnDc=true", ignoreCase = true) && str.contains("5G Allocated=true", ignoreCase = true)) ||
                    str.contains("displayInfo=5G", ignoreCase = true)
        }.getOrDefault(false)
    }

    /**
     * Returns the high-level network category for the active connection.
     */
    fun getCurrentNetworkCategory(context: Context): NetworkCategory {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return NetworkCategory.DISCONNECTED
            val activeNetwork = cm.activeNetwork ?: return NetworkCategory.DISCONNECTED
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return NetworkCategory.DISCONNECTED

            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> NetworkCategory.WIFI

                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    if (is5GConnected(context)) {
                        NetworkCategory.CELLULAR_5G
                    } else {
                        NetworkCategory.CELLULAR_4G_OR_LOWER
                    }
                }

                else -> NetworkCategory.OTHER
            }
        }.getOrDefault(NetworkCategory.DISCONNECTED)
    }

    /**
     * Checks if downloads are permitted on the current network based on user settings:
     * - metered_networks (default true)
     * - allow_5g_networks (default true)
     * - stop_download_on_non_5g (default false)
     */
    fun isNetworkAllowedForDownload(context: Context): Boolean {
        return runCatching {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val allowMetered = prefs.getBoolean("metered_networks", true)
            val allow5G = prefs.getBoolean("allow_5g_networks", true)
            val stopOnNon5G = prefs.getBoolean("stop_download_on_non_5g", false)

            val category = getCurrentNetworkCategory(context)
            if (category == NetworkCategory.DISCONNECTED) return false

            // Wi-Fi or Ethernet is always allowed
            if (category == NetworkCategory.WIFI) return true

            // When stopOnNon5G is enabled, downloads only proceed on 5G or Wi-Fi
            if (stopOnNon5G) {
                return category == NetworkCategory.CELLULAR_5G
            }

            // When metered networks are disabled, allow 5G if user opted to treat 5G as unmetered
            if (!allowMetered) {
                return allow5G && category == NetworkCategory.CELLULAR_5G
            }

            // Normal metered cellular allowed
            true
        }.getOrDefault(true)
    }

    /**
     * Registers a network and telephony observer to actively watch for connectivity changes.
     * Returns an unregister cleanup function.
     */
    @SuppressLint("MissingPermission")
    fun registerDownloadNetworkObserver(
        context: Context,
        onNetworkDisallowed: (reason: String) -> Unit
    ): () -> Unit {
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        var telephonyCallback: Any? = null
        var phoneStateListener: Any? = null

        fun checkAndNotify() {
            if (!isNetworkAllowedForDownload(appContext)) {
                val category = getCurrentNetworkCategory(appContext)
                val reason = when (category) {
                    NetworkCategory.CELLULAR_4G_OR_LOWER -> "Switched to 4G or lower network"
                    NetworkCategory.DISCONNECTED -> "No network connection"
                    else -> "Metered network restricted"
                }
                onNetworkDisallowed(reason)
            }
        }

        // Register ConnectivityManager network callback
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                checkAndNotify()
            }

            override fun onLost(network: Network) {
                checkAndNotify()
            }
        }

        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm?.registerNetworkCallback(request, networkCallback)
        }

        // Register Telephony listener for real-time 5G display changes
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && tm != null) {
                val callback = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                    override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                        val overrideType = telephonyDisplayInfo.overrideNetworkType
                        is5GDisplayOverride = overrideType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
                                overrideType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED ||
                                (Build.VERSION.SDK_INT >= 34 && overrideType == 5 /* OVERRIDE_NETWORK_TYPE_NR_SA */) ||
                                telephonyDisplayInfo.networkType == TelephonyManager.NETWORK_TYPE_NR
                        checkAndNotify()
                    }
                }
                tm.registerTelephonyCallback(appContext.mainExecutor, callback)
                telephonyCallback = callback
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && tm != null) {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                        val overrideType = telephonyDisplayInfo.overrideNetworkType
                        is5GDisplayOverride = overrideType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
                                overrideType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED
                        checkAndNotify()
                    }
                }
                @Suppress("DEPRECATION")
                tm.listen(listener, PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED)
                phoneStateListener = listener
            }
        }

        return {
            runCatching { cm?.unregisterNetworkCallback(networkCallback) }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && tm != null && telephonyCallback is TelephonyCallback) {
                    tm.unregisterTelephonyCallback(telephonyCallback as TelephonyCallback)
                } else if (tm != null && phoneStateListener is PhoneStateListener) {
                    @Suppress("DEPRECATION")
                    tm.listen(phoneStateListener as PhoneStateListener, PhoneStateListener.LISTEN_NONE)
                }
            }
        }
    }
}

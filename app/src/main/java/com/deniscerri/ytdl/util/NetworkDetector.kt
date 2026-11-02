package com.deniscerri.ytdl.util

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
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

    data class NetworkDiagnosticInfo(
        val category: NetworkCategory,
        val categoryName: String,
        val transportName: String,
        val networkTypeName: String,
        val is5GNR: Boolean,
        val fiveGDetails: String,
        val isMetered: Boolean,
        val downlinkSpeedMbps: String,
        val carrierName: String,
        val allowedSetting: String,
        val isAllowedForDownload: Boolean
    )

    @Volatile
    private var isInitialized: Boolean = false

    @Volatile
    private var is5GDisplayOverride: Boolean = false

    @Volatile
    private var lastOverrideNetworkType: Int = 0

    @Volatile
    private var lastDisplayInfoNetworkType: Int = 0

    private val activeObservers = CopyOnWriteArrayList<NetworkStateObserver>()

    interface NetworkStateObserver {
        fun onNetworkStateChanged(category: NetworkCategory, isAllowed: Boolean)
    }

    /**
     * Globally initialize network and telephony listeners from Application.onCreate().
     */
    @SuppressLint("MissingPermission")
    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val tm = getActiveTelephonyManager(appContext)

        // 1. Connectivity callback
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            cm?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    notifyObservers(appContext)
                }

                override fun onLost(network: Network) {
                    notifyObservers(appContext)
                }

                override fun onAvailable(network: Network) {
                    notifyObservers(appContext)
                }
            })
        }

        // 2. Real-time Telephony / 5G NR listener
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && tm != null) {
                val callback = object : TelephonyCallback(),
                    TelephonyCallback.DisplayInfoListener,
                    TelephonyCallback.DataConnectionStateListener {

                    override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                        val overrideType = telephonyDisplayInfo.overrideNetworkType
                        lastOverrideNetworkType = overrideType
                        lastDisplayInfoNetworkType = telephonyDisplayInfo.networkType

                        is5GDisplayOverride = overrideType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
                                overrideType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED ||
                                overrideType >= 3 || // 3: NR_NSA, 4: NR_NSA_MMWAVE, 5: NR_ADVANCED
                                telephonyDisplayInfo.networkType == TelephonyManager.NETWORK_TYPE_NR

                        notifyObservers(appContext)
                    }

                    override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
                        if (networkType == TelephonyManager.NETWORK_TYPE_NR) {
                            is5GDisplayOverride = true
                            notifyObservers(appContext)
                        }
                    }
                }
                val executor = ContextCompat.getMainExecutor(appContext)
                tm.registerTelephonyCallback(executor, callback)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && tm != null) {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                        val overrideType = telephonyDisplayInfo.overrideNetworkType
                        lastOverrideNetworkType = overrideType
                        is5GDisplayOverride = overrideType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
                                overrideType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED ||
                                overrideType >= 3
                        notifyObservers(appContext)
                    }
                }
                @Suppress("DEPRECATION")
                tm.listen(listener, PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED)
            }
        }
    }

    private fun getActiveTelephonyManager(context: Context): TelephonyManager? {
        val baseTm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val defaultSubId = SubscriptionManager.getDefaultDataSubscriptionId()
                if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    baseTm.createForSubscriptionId(defaultSubId)
                } else {
                    baseTm
                }
            } else {
                baseTm
            }
        }.getOrDefault(baseTm)
    }

    private fun notifyObservers(context: Context) {
        val category = getCurrentNetworkCategory(context)
        val isAllowed = isNetworkAllowedForDownload(context)
        for (observer in activeObservers) {
            runCatching { observer.onNetworkStateChanged(category, isAllowed) }
        }
    }

    fun addObserver(observer: NetworkStateObserver) {
        if (!activeObservers.contains(observer)) {
            activeObservers.add(observer)
        }
    }

    fun removeObserver(observer: NetworkStateObserver) {
        activeObservers.remove(observer)
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
     * Determines whether the current cellular connection is 5G NR (SA or NSA).
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

            // 1. Live TelephonyCallback/PhoneStateListener override check (NSA/SA)
            if (is5GDisplayOverride) {
                return true
            }

            val tm = getActiveTelephonyManager(context) ?: return false

            // 2. Direct data network type check (NETWORK_TYPE_NR = 20)
            val dataNetworkType = tm.dataNetworkType
            if (dataNetworkType == TelephonyManager.NETWORK_TYPE_NR) {
                return true
            }

            // 3. ServiceState reflection for 5G NSA/SA indicators
            if (checkServiceStateFor5G(tm)) {
                return true
            }

            // 4. Temporarily not metered capability on cellular (often set for unlimited 5G)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && caps.hasCapability(25 /* NET_CAPABILITY_TEMPORARILY_NOT_METERED */)) {
                return true
            }

            false
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
                    str.contains("displayInfo=5G", ignoreCase = true) ||
                    str.contains("networkType=NR", ignoreCase = true)
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
     * - allowed_network_type ("all", "wifi_and_5g", "5g_only", "wifi_only")
     * - metered_networks
     * - allow_5g_networks
     * - stop_download_on_non_5g
     */
    fun isNetworkAllowedForDownload(context: Context): Boolean {
        return runCatching {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val category = getCurrentNetworkCategory(context)
            if (category == NetworkCategory.DISCONNECTED) return false

            val allowedType = prefs.getString("allowed_network_type", "all") ?: "all"
            when (allowedType) {
                "wifi_only" -> return category == NetworkCategory.WIFI
                "5g_only" -> return category == NetworkCategory.CELLULAR_5G
                "wifi_and_5g" -> return category == NetworkCategory.WIFI || category == NetworkCategory.CELLULAR_5G
                "all" -> {
                    // Check if user set legacy stop_download_on_non_5g
                    val stopOnNon5G = prefs.getBoolean("stop_download_on_non_5g", false)
                    if (stopOnNon5G) {
                        return category == NetworkCategory.WIFI || category == NetworkCategory.CELLULAR_5G
                    }

                    val allowMetered = prefs.getBoolean("metered_networks", true)
                    val allow5G = prefs.getBoolean("allow_5g_networks", true)
                    if (!allowMetered) {
                        return (category == NetworkCategory.WIFI) || (allow5G && category == NetworkCategory.CELLULAR_5G)
                    }
                    return true
                }
            }

            true
        }.getOrDefault(true)
    }

    /**
     * Returns full diagnostic telemetry for real-time testing and display in Settings.
     */
    @SuppressLint("MissingPermission")
    fun getDiagnosticInfo(context: Context): NetworkDiagnosticInfo {
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val tm = getActiveTelephonyManager(appContext)

        val activeNetwork = cm?.activeNetwork
        val caps = if (activeNetwork != null) cm.getNetworkCapabilities(activeNetwork) else null
        val category = getCurrentNetworkCategory(appContext)
        val is5G = is5GConnected(appContext)
        val isAllowed = isNetworkAllowedForDownload(appContext)

        val transportName = when {
            caps == null -> "Disconnected"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular (Mobile Data)"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            else -> "Other"
        }

        val networkTypeName = when (category) {
            NetworkCategory.WIFI -> "Wi-Fi"
            NetworkCategory.CELLULAR_5G -> "5G NR (New Radio)"
            NetworkCategory.CELLULAR_4G_OR_LOWER -> "4G LTE or lower"
            NetworkCategory.OTHER -> "Other"
            NetworkCategory.DISCONNECTED -> "No Internet"
        }

        var fiveGDetails = ""
        if (is5G) {
            val overrideStr = when (lastOverrideNetworkType) {
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> "NSA (Non-Standalone 5G)"
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "NR Advanced / mmWave"
                4 -> "NR NSA mmWave"
                5 -> "NR Advanced"
                else -> if (lastDisplayInfoNetworkType == TelephonyManager.NETWORK_TYPE_NR) "SA (Standalone 5G)" else "Detected (Active)"
            }
            fiveGDetails = overrideStr
        }

        val carrierName = runCatching { tm?.networkOperatorName ?: "" }.getOrDefault("")
        val isMetered = caps != null && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val downlinkKbps = caps?.linkDownstreamBandwidthKbps ?: 0
        val downlinkSpeedMbps = if (downlinkKbps > 0) String.format("%.1f", downlinkKbps / 1000f) else ""

        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        val allowedType = prefs.getString("allowed_network_type", "all") ?: "all"
        val activeSetting = when (allowedType) {
            "wifi_and_5g" -> "Wi-Fi & 5G NR only"
            "5g_only" -> "5G NR only"
            "wifi_only" -> "Wi-Fi only"
            else -> if (prefs.getBoolean("stop_download_on_non_5g", false)) "Wi-Fi & 5G NR only (Auto-pause 4G)" else "All networks allowed"
        }

        return NetworkDiagnosticInfo(
            category = category,
            categoryName = category.name,
            transportName = transportName,
            networkTypeName = networkTypeName,
            is5GNR = is5G,
            fiveGDetails = fiveGDetails,
            isMetered = isMetered,
            downlinkSpeedMbps = downlinkSpeedMbps,
            carrierName = carrierName,
            allowedSetting = activeSetting,
            isAllowedForDownload = isAllowed
        )
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

        val observer = object : NetworkStateObserver {
            override fun onNetworkStateChanged(category: NetworkCategory, isAllowed: Boolean) {
                if (!isAllowed) {
                    val reason = when (category) {
                        NetworkCategory.CELLULAR_4G_OR_LOWER -> "Switched to 4G or lower network"
                        NetworkCategory.DISCONNECTED -> "No network connection"
                        else -> "Network restricted by download policy"
                    }
                    onNetworkDisallowed(reason)
                }
            }
        }

        addObserver(observer)
        init(appContext)

        return {
            removeObserver(observer)
        }
    }
}

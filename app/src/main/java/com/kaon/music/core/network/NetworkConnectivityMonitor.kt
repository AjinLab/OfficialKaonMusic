package com.kaon.music.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

interface NetworkMonitor {
    val isOnline: Flow<Boolean>
}

/**
 * Monitors device network availability for offline/online UX switching.
 */
open class NetworkConnectivityMonitor(private val context: Context) : NetworkMonitor {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override val isOnline: Flow<Boolean> = callbackFlow {
        val cm = connectivityManager
        if (cm == null) {
            trySend(true)
            awaitClose {}
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                val hasInternet = checkCurrentConnectivity(cm)
                trySend(hasInternet)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                trySend(hasInternet)
            }
        }

        // Emit initial state
        trySend(checkCurrentConnectivity(cm))

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)

        awaitClose {
            cm.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    fun isCurrentlyOnline(): Boolean {
        val cm = connectivityManager ?: return true
        return checkCurrentConnectivity(cm)
    }

    private fun checkCurrentConnectivity(cm: ConnectivityManager): Boolean {
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

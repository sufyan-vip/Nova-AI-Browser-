package com.nova.browser.core.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Connectivity state helpers (no deprecated APIs). */
object NetworkUtils {

    data class Status(val online: Boolean, val unmetered: Boolean)

    fun currentStatus(context: Context): Status {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return Status(online = false, unmetered = false)
        val network = cm.activeNetwork ?: return Status(false, false)
        val caps = cm.getNetworkCapabilities(network) ?: return Status(false, false)
        val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        return Status(online, unmetered)
    }

    fun isOnline(context: Context): Boolean = currentStatus(context).online

    fun isUnmetered(context: Context): Boolean = currentStatus(context).unmetered

    fun observe(context: Context): Flow<Status> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(Status(false, false))
            awaitClose { }
            return@callbackFlow
        }
        trySend(currentStatus(context))
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(currentStatus(context)) }
            override fun onLost(network: Network) { trySend(currentStatus(context)) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(currentStatus(context))
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            trySend(Status(false, false))
        }
        awaitClose {
            try { cm.unregisterNetworkCallback(callback) } catch (e: Exception) { /* already gone */ }
        }
    }.distinctUntilChanged()
}

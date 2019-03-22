package com.aegamesi.steamtrade

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.preference.PreferenceManager
import android.util.Log

import com.aegamesi.steamtrade.steam.SteamService

import uk.co.thomasc.steamkit.base.generated.steamlanguage.EUniverse

class IceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ConnectivityManager.CONNECTIVITY_ACTION) {
            Log.d("SteamBroadcastReceiver", "connectivity change")
            if (SteamService.singleton == null || SteamService.singleton!!.steamClient == null || SteamService.singleton!!.steamClient!!.connectedUniverse == null || SteamService.singleton!!.steamClient!!.connectedUniverse == EUniverse.Invalid) {
                // we are not connected. Reconnect.
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val activeNetwork: NetworkInfo?
                activeNetwork = cm.activeNetworkInfo
                val connected = activeNetwork != null && activeNetwork.isConnected
                Log.d("SteamBroadcastReceiver", "attempting reconnect:" + SteamService.attemptReconnect + " | connected to internet: " + connected)
                if (SteamService.attemptReconnect && connected) {
                    val prefReconnect = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_reconnect", true)
                    if (prefReconnect) {
                        // first make sure that we are connected to the internet
                        // and the last failure was not "connectfailed"

                        // do the reconnection process
                        if (SteamService.extras != null) {
                            // steam guard key will no longer be valid-- and, provided we had a successful login, we shouldn't need it anyway
                            if (SteamService.extras!!.containsKey("steamguard"))
                                SteamService.extras!!.remove("steamguard")

                            SteamService.attemptLogon(context, null, SteamService.extras)
                        }
                    }
                }
            }
        }
    }
}

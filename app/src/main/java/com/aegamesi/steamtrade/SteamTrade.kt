package com.aegamesi.steamtrade

import android.app.Application
import android.preference.PreferenceManager
import com.aegamesi.steamtrade.steam.SteamLogcatDebugListener
import uk.co.thomasc.steamkit.steam3.CMClient
import uk.co.thomasc.steamkit.util.logging.DebugLog
import java.io.File

class SteamTrade : Application() {

    override fun onCreate() {
        super.onCreate()
        filesDirectory = filesDir

        DebugLog.addListener(SteamLogcatDebugListener())

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.contains("cm_server_list")) {
            val serverString = prefs.getString("cm_server_list", "")
            val servers = serverString!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (servers.isNotEmpty())
                CMClient.updateCMServers(servers)
        }
    }

    companion object {
        lateinit var filesDirectory: File
    }
}
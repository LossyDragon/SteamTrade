package com.aegamesi.steamtrade.steam

import uk.co.thomasc.steamkit.base.generated.steamlanguage.EResult

interface SteamConnectionListener {

    // warning: this will be called from a non-main thread
    fun onConnectionResult(result: EResult)


    // warning: this will be called from a non-main thread
    fun onConnectionStatusUpdate(status: Int)

    companion object {
        const val STATUS_UNKNOWN = 0
        const val STATUS_INITIALIZING = 1
        const val STATUS_CONNECTING = 2
        const val STATUS_LOGON = 3
        const val STATUS_AUTH = 4
        const val STATUS_APIKEY = 5
        const val STATUS_CONNECTED = 6
        const val STATUS_FAILURE = 7
    }
}

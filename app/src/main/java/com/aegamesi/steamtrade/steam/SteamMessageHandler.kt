package com.aegamesi.steamtrade.steam

import uk.co.thomasc.steamkit.steam3.steamclient.callbackmgr.CallbackMsg

interface SteamMessageHandler {
    fun handleSteamMessage(msg: CallbackMsg)
}

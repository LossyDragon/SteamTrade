package com.aegamesi.steamtrade.steam

import android.util.Log

import uk.co.thomasc.steamkit.util.logging.IDebugListener

class SteamLogcatDebugListener : IDebugListener {
    override fun writeLine(tag: String, msg: String) {
        val ignoreTags = arrayOf("EMsg GET", "Connect")
        for (ignore_tag in ignoreTags)
            if (ignore_tag == tag)
                return

        val lines = msg.split(System.getProperty("line.separator")!!.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (line in lines)
            Log.d("SteamKit", "[$tag] $line")

        /* debug mode */
        if (tag == "NEW_EX") {
            for (e in Thread.currentThread().stackTrace)
                Log.d("StackTrace", e.toString())
        }
    }
}

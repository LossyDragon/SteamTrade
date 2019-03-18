package com.aegamesi.steamtrade.steam

import android.util.Log

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.HashMap

object SteamUtil {
    private val hexArray = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')
    private val bbCodeMap = HashMap<String, String>()
    var webApiKey: String? = null // kept in secret.xml

    init {
        bbCodeMap["(?i)\\[b\\](.+?)\\[/b\\]"] = "<b>$1</b>"
        bbCodeMap["(?i)\\[i\\](.+?)\\[/i\\]"] = "<i>$1</i>"
        bbCodeMap["(?i)\\[u\\](.+?)\\[/u\\]"] = "<u>$1</u>"
        bbCodeMap["(?i)\\[h1\\](.+?)\\[/h1\\]"] = "<h5>$1</h5>"
        bbCodeMap["(?i)\\[spoiler\\](.+?)\\[/spoiler\\]"] = "[SPOILER: $1]"
        bbCodeMap["(?i)\\[strike\\](.+?)\\[/strike\\]"] = "<strike>$1</strike>"
        bbCodeMap["(?i)\\[url\\](.+?)\\[/url\\]"] = "<a href=\"$1\">$1</a>"
        //noparse still parses inner tags.
        bbCodeMap["(?i)\\[noparse\\](.+?)\\[/noparse\\]"] = "$1"
        //Purposely expose URL(Security), but have URL-Summary in brackets.
        bbCodeMap["(?i)\\[url=(.+?)\\](.+?)\\[/url\\]"] = "[$2] $1"
    }

    internal fun calculateSHA1(data: ByteArray): ByteArray? {
        try {
            val md = MessageDigest.getInstance("SHA1")
            return md.digest(data)
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }

        return null
    }

    fun bytesToHex(bytes: ByteArray?): String {
        if (bytes == null)
            return "0000000000000000000000000000000000000000"
        val hexChars = CharArray(bytes.size * 2)
        var v: Int
        for (j in bytes.indices) {
            v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v.ushr(4)]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    fun parseBBCode(src: String): String {
        var source = src
        source = parseEmoticons(source)

        for ((key, value) in bbCodeMap)
            source = source.replace(key.toRegex(), value)

        Log.d("BB", source)
        return source
    }

    fun parseEmoticons(source: String): String {
        return source.replace("\u02D0([a-zA-Z0-9_]+)\u02D0".toRegex(), "<img src=\"http://steamcommunity-a.akamaihd.net/economy/emoticon/$1\">").replace("(\r\n|\r|\n|\n\r)".toRegex(), "<br/>")
    }

}

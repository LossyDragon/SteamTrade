package com.aegamesi.steamtrade.steam

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*

object SteamUtil {
    private val hexArray =
            charArrayOf(
                    '0', '1', '2', '3', '4',
                    '5', '6', '7', '8', '9',
                    'A', 'B', 'C', 'D', 'E', 'F')
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
        var source = parseEmoticons(src)

        for ((key, value) in bbCodeMap)
            source = source.replace(key.toRegex(), value)

        return source
    }

    fun parseEmoticons(source: String): String {
        return source.replace(
                "\u02D0([a-zA-Z0-9_]+)\u02D0".toRegex(),
                "<img src=\"http://steamcommunity-a.akamaihd.net/economy/emoticon/$1\">")
                .replace("(\r\n|\r|\n|\n\r)"
                .toRegex(), "<br/>")
    }

    fun getAvatar(source: ByteArray?): String {
        val baseURL = "http://media.steampowered.com/steamcommunity/public/images/avatars/"
        val nullImage = "fef49e7fa7e1997310d705b2a6158ff8dc1cdfeb"
        val allZERO = "0000000000000000000000000000000000000000"

        val imgHash = bytesToHex(source).toLowerCase(Locale.US)

        return if (imgHash != allZERO && imgHash.length == 40) {
            baseURL + imgHash.substring(0, 2) + "/" + imgHash + "_full.jpg"
        }
        else{
            baseURL + imgHash.substring(0, 2) + "/" + nullImage + "_full.jpg"
        }
    }

}

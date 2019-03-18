package com.aegamesi.steamtrade.steam

import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.*
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

// created by aegamesi -- decouple SteamWeb from TradeSession for maximum code reuse
object SteamWeb {
    /**
     * The user-agent string to use when making requests.
     */
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/64.0.3282.167 Safari/537.36"
    //final static String USER_AGENT_MOBILE = "Mozilla/5.0 (Linux; U; Android 4.1.1; en-us; Google Nexus 4 - 4.1.1 - API 16 - 768x1280 Build/JRO03S) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30";

    /**
     * Fetches an api key from /dev/registerkey.
     *
     * @return The api key, or null if there was an error.
     */
    internal fun requestWebAPIKey(): String? {
        var page = fetch("https://steamcommunity.com/dev/apikey", "GET", null, "http://steamcommunity.com/dev/")
        val key = parseWebAPIKey(page)
        if (key != null)
            return key
        // otherwise, we need to register for a key

        val data = HashMap<String, String>()
        data["domain"] = "localhost"
        data["agreeToTerms"] = "agreed"
        data["Submit"] = "Register"
        data["sessionid"] = SteamService.singleton!!.sessionID!! // new as of 5/12/2015
        page = fetch("https://steamcommunity.com/dev/registerkey", "POST", data, "https://steamcommunity.com/dev/apikey")
        return parseWebAPIKey(page)
    }

    /**
     * Requests a String representation of an online file (for Steam).
     *
     * @param url    Location to fetch.
     * @param method "GET" or "POST"
     * @param data   The data to be added to the data stream or request
     * params.
     * @return The server's String response to the request.
     */
    fun fetch(url: String, method: String, data: Map<String, String>?, referrer: String?): String {

        val cookies = SteamService.generateSteamWebCookies()
        return SteamWeb.request(url, method, data, cookies, referrer
                ?: "http://steamcommunity.com/trade/1")
    }

    private fun parseWebAPIKey(page: String): String? {
        val matcher = Pattern.compile("<p>Key: ([0-9A-F]+)</p>").matcher(page)

        var apikey: String? = null
        if (matcher.find())
            apikey = matcher.group(1)

        return if (apikey != null && apikey.length == 32) apikey else null
    }

    /**
     * Requests a String representation of an online file (for Steam).
     *
     * @param requestURL     Location to fetch.
     * @param method  "GET" or "POST"
     * @param data    The data to be added to the data stream or request
     * params.
     * @param cookies A string of cookie data to be added to the request
     * headers.
     * @return The server's String response to the request.
     */
    private fun request(requestURL: String, method: String, data: Map<String, String>?, cookies: String, referrer: String): String {
        var url = requestURL
        val out = StringBuilder()
        try {
            val dataString: String
            val dataStringBuffer = StringBuilder()
            if (data != null) {
                for ((key, value) in data) {
                    dataStringBuffer.append(
                            URLEncoder.encode(key, "UTF-8"))
                            .append("=").append(
                                    URLEncoder.encode(value, "UTF-8"))
                            .append("&")
                }
            }
            dataString = dataStringBuffer.toString()
            if (method != "POST" && dataString.isNotEmpty()) {
                url += "?$dataString"
            }
            val url2 = URL(url)
            val conn = url2.openConnection() as HttpURLConnection
            conn.setRequestProperty("Cookie", cookies)
            conn.requestMethod = method
            System.setProperty("http.agent", "")

            conn.setRequestProperty("User-Agent", USER_AGENT)

            //conn.setRequestProperty("Host", "steamcommunity.com");
            conn.setRequestProperty("Content-type",
                    "application/x-www-form-urlencoded; charset=UTF-8")
            conn.setRequestProperty("Accept",
                    "text/javascript, text/hml, " + "application/xml, text/xml, */*")

            /*
			  Turns out we need a referer, otherwise we get an error from
			  the server. Just use the trade URL as one since we have it on
			  hand, and it's been known to work.

			  http://steamcommunity.com/trade/1 was used for other
			  libraries, but having a hardcoded thing like that is gross.
			 */
            conn.setRequestProperty("Referer", referrer)

            // Accept compressed responses.  (We can decompress it.)
            conn.setRequestProperty("Accept-Encoding", "gzip,deflate")

            conn.setRequestProperty("X-Requested-With",
                    "XMLHttpRequest")
            conn.setRequestProperty("X-Prototype-Version", "1.7")

            if (method == "POST") {
                conn.doOutput = true
                try {
                    val os = OutputStreamWriter(conn.outputStream)
                    os.write(dataString.substring(0,
                            dataString.length - 1))
                    os.flush()
                    os.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }

            var netStream: InputStream
            val responseCode = conn.responseCode
            if (responseCode >= 400) {
                // we had an error. Read the error stream
                netStream = conn.errorStream
                System.err.println("Error reading from $url2: $responseCode")
            } else {
                netStream = conn.inputStream
            }

            // If GZIPped response, then use the gzip decoder.
            if (conn.contentEncoding != null) {
                if (conn.contentEncoding.contains("gzip")) {
                    netStream = GZIPInputStream(netStream)
                } else if (conn.contentEncoding.contains("deflate")) {
                    netStream = InflaterInputStream(netStream, Inflater(true))
                }
            }

            try {
                val reader = BufferedReader(InputStreamReader(netStream))
                var line: String?// Stores the currently read line.

                do {
                    line = reader.readLine()
                    if (out.isNotEmpty()) {
                        out.append('\n')
                    }
                    out.append(line)

                } while (line != null)

                reader.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

        } catch (e: IOException) {
            e.printStackTrace()
        }

        return out.toString()
    }
}

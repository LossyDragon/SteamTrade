package com.aegamesi.steamtrade.steam

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.util.Base64

import com.aegamesi.steamtrade.R
import com.aegamesi.steamtrade.libs.AndroidUtil

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.HashMap

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import uk.co.thomasc.steamkit.types.steamid.SteamID

object SteamTwoFactor {
    private const val DEVICE_ID_SALT = "a8cd56564282db67"
    private const val TOTP_CODE_CHARS = "23456789BCDFGHJKMNPQRTVWXY"

    /**
     * Return the current local time.
     *
     * @return Current local time, in seconds.
     */
    val currentTime: Long
        get() = getCurrentTime(0)

    val codeValidityTime: Double
        get() = 30.0 - System.currentTimeMillis() / 1000.0 % 30.0

    /**
     * Generates a consistent device ID for Steam two factor authentication.
     *
     * @param steamID The steamID to base the deviceID off of.
     * @return A String representing an android-specific device ID.
     */
    fun generateDeviceID(steamID: SteamID): String {
        var hash = steamID.render() + DEVICE_ID_SALT
        hash = SteamUtil.bytesToHex(SteamUtil.calculateSHA1(hash.toByteArray()))
        return "android:$hash"
    }

    /**
     * Return the current local time, offset by a number of seconds.
     *
     * @param time_offset The number of seconds to offset the current time by.
     * @return Local time with offset, in seconds.
     */
    @Suppress("SameParameterValue")
    private fun getCurrentTime(time_offset: Int): Long {
        return System.currentTimeMillis() / 1000L + time_offset
    }

    /**
     * Generate a Steam-style TOTP authentication code.
     *
     * @param shared_secret - the TOTP shared_secret
     * @param time          - The time to generate the code for.
     * @return authentication code
     */
    fun generateAuthCodeForTime(shared_secret: ByteArray, time: Long): String {
        var t = time
        t /= 30L
        val byteBuffer = ByteBuffer.allocate(8)
        byteBuffer.order(ByteOrder.BIG_ENDIAN)
        byteBuffer.putLong(t)

        val hash: ByteArray
        try {
            val secretkeyspec = SecretKeySpec(shared_secret, "HmacSHA1")
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(secretkeyspec)
            hash = mac.doFinal(byteBuffer.array())
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }

        val start = hash[19].toInt() and 0x0F
        var fullcode = hash[start].toInt() and 0x7f shl 24 or
                (hash[start + 1].toInt() and 0xff shl 16) or
                (hash[start + 2].toInt() and 0xff shl 8) or
                (hash[start + 3].toInt() and 0xff)

        val code = StringBuilder()
        for (i in 0..4) {
            code.append(TOTP_CODE_CHARS[fullcode % TOTP_CODE_CHARS.length])
            fullcode /= TOTP_CODE_CHARS.length
        }
        return code.toString()
    }

    /**
     * Generate a base64 confirmation key for use with mobile trade confirmations. The key can only be used once.
     *
     * @param identity_secret - The identity_secret that you received when enabling two-factor authentication
     * @param time            - The Unix time for which you are generating this secret. Generally should be the current time.
     * @param tag             - The tag which identifies what this request (and therefore key) will be for.
     * "conf" to load the confirmations page,
     * "details" to load details about a trade,
     * "allow" to confirm a trade,
     * "cancel" to cancel it.
     * @return String key
     */
    private fun generateConfirmationKey(identity_secret: ByteArray, time: Long, tag: String?): String {
        var tagg = tag
        if (tagg == null)
            tagg = ""
        val byteBuffer = ByteBuffer.allocate(8 + Math.min(tagg.length, 32))
        byteBuffer.order(ByteOrder.BIG_ENDIAN)
        byteBuffer.putLong(time)
        byteBuffer.put(tagg.toByteArray())

        return try {
            val secretkeyspec = SecretKeySpec(identity_secret, "HmacSHA1")
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(secretkeyspec)
            val hash = mac.doFinal(byteBuffer.array())
            Base64.encodeToString(hash, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }

    }

    /**
     * Generate confirmation tag parameters
     *
     * @return ""
     */
    fun generateConfirmationParameters(context: Context, tag: String): String {
        val username = SteamService.singleton!!.username
        val info = AccountLoginInfo.readAccount(context, username!!)
        if (info != null && info.hasAuthenticator) {
            val time = SteamTwoFactor.currentTime
            val steamID = SteamService.singleton!!.steamClient!!.steamId

            val params = HashMap<String, Any>()
            params["p"] = SteamTwoFactor.generateDeviceID(steamID)
            params["a"] = steamID.convertToLong()
            params["k"] = SteamTwoFactor.generateConfirmationKey(info.tfaIdentitySecret, time, tag)
            params["t"] = time
            params["m"] = "android"
            params["tag"] = tag
            return AndroidUtil.createURIDataString(params)
        }
        return ""
    }

    fun promptForMafile(activity: Activity, requestCode: Int) {
        AndroidUtil.showBasicAlert(activity,
                activity.getString(R.string.steamguard_mobile_authenticator),
                activity.getString(R.string.steamguard_select_mafile),
                DialogInterface.OnClickListener { _, _ ->
                    val intent = Intent(Intent.ACTION_GET_CONTENT)
                    intent.type = "*/*"
                    activity.startActivityForResult(intent, requestCode)
                })
    }
}

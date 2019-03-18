package com.aegamesi.steamtrade.steam

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Base64

import com.aegamesi.steamtrade.libs.AndroidUtil
import com.google.gson.Gson
import com.google.gson.GsonBuilder

import org.json.JSONException
import org.json.JSONObject

import java.util.ArrayList

import uk.co.thomasc.steamkit.types.steamid.SteamID

class AccountLoginInfo {

    // saved variables
    var username: String? = null
    var password: String? = null
    var loginkey: String? = null
    var avatar: String? = null
    internal var uniqueId = -1

    var hasAuthenticator = false
    lateinit var tfaSharedSecret: ByteArray
    lateinit var tfaSerialNumber: String
    lateinit var tfaRevocationCode: String
    lateinit var tfaUri: String
    var tfaServerTime: Long = 0
    lateinit var tfaAccountName: String
    lateinit var tfaTokenGid: String
    lateinit var tfaIdentitySecret: ByteArray
    lateinit var tfaSecret1: ByteArray

    fun exportToJson(steamID: SteamID): String {
        try {
            val obj = JSONObject()
            obj.put("steamguard_scheme", "2") // mobile
            obj.put("steamID", java.lang.Long.toString(steamID.convertToLong()))
            obj.put("account_name", tfaAccountName)
            obj.put("shared_secret", Base64.encodeToString(tfaSharedSecret, Base64.NO_WRAP))
            obj.put("serial_number", tfaSerialNumber)
            obj.put("revocation_code", tfaRevocationCode)
            obj.put("uri", tfaUri)
            obj.put("server_time", java.lang.Long.toString(tfaServerTime))
            obj.put("token_gid", tfaTokenGid)
            obj.put("identity_secret", Base64.encodeToString(tfaIdentitySecret, Base64.NO_WRAP))
            obj.put("secret_1", Base64.encodeToString(tfaSecret1, Base64.NO_WRAP))
            obj.put("status", 1)
            obj.put("device_id", SteamTwoFactor.generateDeviceID(steamID))

            // for compatibility with SteamAuth (desktop)
            obj.put("fully_enrolled", true)
            val session = JSONObject()
            session.put("SessionID", "")
            session.put("SteamLogin", "")
            session.put("SteamLoginSecure", "")
            session.put("WebCookie", "")
            session.put("OAuthToken", "")
            session.put("SteamID", steamID.convertToLong())
            obj.put("Session", session)
            // device_id
            // fully_enrolled
            return obj.toString()
        } catch (e: JSONException) {
            e.printStackTrace()
            return ""
        }

    }

    @Throws(JSONException::class, NumberFormatException::class)
    fun importFromJson(json: String) {
        val obj = JSONObject(json)
        tfaAccountName = obj.getString("account_name")
        tfaSharedSecret = Base64.decode(obj.getString("shared_secret"), Base64.DEFAULT)
        tfaSerialNumber = obj.getString("serial_number")
        tfaRevocationCode = obj.getString("revocation_code")
        tfaUri = obj.getString("uri")
        tfaServerTime = java.lang.Long.valueOf(obj.getString("server_time"))
        tfaTokenGid = obj.getString("token_gid")
        tfaIdentitySecret = Base64.decode(obj.getString("identity_secret"), Base64.DEFAULT)
        tfaSecret1 = Base64.decode(obj.getString("secret_1"), Base64.DEFAULT)
    }

    companion object {
        private const val pref_key = "accountinfo_"
        private var gson: Gson? = null

        init {
            val gsonBuilder = GsonBuilder()
            gsonBuilder.registerTypeHierarchyAdapter(ByteArray::class.java, AndroidUtil.ByteArrayToBase64TypeAdapter())
            gson = gsonBuilder.create()
        }

        private fun getSharedPreferences(context: Context): SharedPreferences {
            return PreferenceManager.getDefaultSharedPreferences(context)
        }

        fun readAccount(context: Context, name: String): AccountLoginInfo? {
            val json = getSharedPreferences(context).getString(pref_key + name, null) ?: return null

            return gson!!.fromJson<AccountLoginInfo>(json, AccountLoginInfo::class.java)
        }

        fun writeAccount(context: Context, obj: AccountLoginInfo) {
            val json = gson!!.toJson(obj, AccountLoginInfo::class.java)
            val editor = getSharedPreferences(context).edit()
            editor.putString(pref_key + obj.username!!, json)
            //Log.d("AccountLoginInfo_WRITE", json);
            editor.apply()
        }

        fun removeAccount(context: Context, name: String) {
            val editor = getSharedPreferences(context).edit()
            editor.remove(pref_key + name)
            editor.apply()
        }

        fun getAccountList(context: Context): MutableList<AccountLoginInfo> {
            val accountList = ArrayList<AccountLoginInfo>()
            for (key in getSharedPreferences(context).all.keys) {
                if (key.startsWith(pref_key)) {
                    val account = key.substring(pref_key.length)
                    accountList.add(readAccount(context, account)!!)
                }
            }
            return accountList
        }
    }
}

package com.aegamesi.steamtrade.fragments

import android.os.Bundle
import android.preference.EditTextPreference
import android.preference.PreferenceManager
import android.util.Log
import com.aegamesi.steamtrade.MainActivity
import com.aegamesi.steamtrade.R
import com.aegamesi.steamtrade.steam.SteamService
import com.aegamesi.steamtrade.steam.SteamUtil
import com.github.machinarius.preferencefragment.PreferenceFragment

class FragmentSettings : PreferenceFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences)

        // allow webapi key to be per-user
        val prefWebApiKey = findPreference("pref_webapikey") as EditTextPreference
        prefWebApiKey.text = SteamUtil.webApiKey
        prefWebApiKey.setOnPreferenceChangeListener { _, newValue ->
            var key = newValue.toString().trim { it <= ' ' }
            if (key.length > 32)
                key = key.substring(0, 32)

            SteamUtil.webApiKey = key
            prefWebApiKey.text = SteamUtil.webApiKey
            Log.d("Preferences", "Accepted new webapi key: " + SteamUtil.webApiKey!!)

            if (activity != null && SteamService.singleton != null && SteamService.singleton!!.steamClient != null) {
                val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
                prefs.edit().putString("webapikey_" + SteamService.singleton!!.steamClient!!.steamId.convertToLong(), SteamUtil.webApiKey).apply()
            }
            false
        }
    }

    override fun onResume() {
        super.onResume()
        if (activity != null && activity is MainActivity) {
            (activity as MainActivity).toolbar.title = getString(R.string.nav_settings)
        }
    }

}

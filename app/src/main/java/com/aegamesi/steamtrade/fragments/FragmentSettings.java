package com.aegamesi.steamtrade.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.util.Log;

import com.aegamesi.steamtrade.MainActivity;
import com.aegamesi.steamtrade.R;
import com.aegamesi.steamtrade.steam.SteamService;
import com.aegamesi.steamtrade.steam.SteamUtil;
import com.github.machinarius.preferencefragment.PreferenceFragment;

public class FragmentSettings extends PreferenceFragment {
    private RingtonePreference pref_notification_sound = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        // allow webapi key to be per-user
        final EditTextPreference pref_webapikey = (EditTextPreference) findPreference("pref_webapikey");
        pref_webapikey.setText(SteamUtil.webApiKey);
        pref_webapikey.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                String key = newValue.toString().trim();
                if (key.length() > 32)
                    key = key.substring(0, 32);

                SteamUtil.webApiKey = key;
                pref_webapikey.setText(SteamUtil.webApiKey);
                Log.d("Preferences", "Accepted new webapi key: " + SteamUtil.webApiKey);

                if (getActivity() != null && SteamService.singleton != null && SteamService.singleton.steamClient != null) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    prefs.edit().putString("webapikey_" + SteamService.singleton.steamClient.getSteamId().convertToLong(), SteamUtil.webApiKey).apply();
                }
                return false;
            }
        });

        pref_notification_sound = (RingtonePreference) findPreference("pref_notification_sound");
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null && getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).toolbar.setTitle(getString(R.string.nav_settings));
        }
    }


    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        // workaround for Ringtone Fragment not saving data
        if (pref_notification_sound != null) {
            if (pref_notification_sound.onActivityResult(requestCode, resultCode, data))
                return true;
        }

        return false;
    }
}
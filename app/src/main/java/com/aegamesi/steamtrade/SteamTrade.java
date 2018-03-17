package com.aegamesi.steamtrade;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.aegamesi.steamtrade.steam.SteamLogcatDebugListener;

import java.io.File;

import uk.co.thomasc.steamkit.steam3.CMClient;
import uk.co.thomasc.steamkit.util.logging.DebugLog;

public class SteamTrade extends Application {
	public static File filesDir;

	@Override
	public void onCreate() {
		super.onCreate();
		filesDir = getFilesDir();

		DebugLog.addListener(new SteamLogcatDebugListener());

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		if (prefs.contains("cm_server_list")) {
			String serverString = prefs.getString("cm_server_list", "");
			String[] servers = serverString.split(",");
			if (servers.length > 0)
				CMClient.updateCMServers(servers);
		}
	}
}
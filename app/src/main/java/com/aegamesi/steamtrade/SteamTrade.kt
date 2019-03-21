package com.aegamesi.steamtrade

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.net.Uri
import android.os.Build
import android.preference.PreferenceManager
import android.widget.Toast
import com.aegamesi.steamtrade.steam.SteamLogcatDebugListener
import org.acra.ACRA
import org.acra.annotation.AcraCore
import org.acra.annotation.AcraHttpSender
import org.acra.annotation.AcraToast
import org.acra.data.StringFormat
import org.acra.sender.HttpSender
import uk.co.thomasc.steamkit.steam3.CMClient
import uk.co.thomasc.steamkit.util.logging.DebugLog
import java.io.File

@AcraCore(reportFormat = StringFormat.JSON,
        excludeMatchingSharedPreferencesKeys =
        ["accountinfo_\\w+\$", "webapikey_\\w+\$", "avatar_\\w+\$"])
@AcraHttpSender(uri = "http://96.19.12.56:8080/Acra/report",
        basicAuthLogin = BuildConfig.acraAuthLogin,
        basicAuthPassword = BuildConfig.acraAuthPassword,
        httpMethod = HttpSender.Method.POST)
@AcraToast(resText = R.string.acra_crash, length = Toast.LENGTH_LONG)
class SteamTrade : Application() {

    override fun onCreate() {
        super.onCreate()
        filesDirectory = filesDir

        DebugLog.addListener(SteamLogcatDebugListener())

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.contains("cm_server_list")) {
            val serverString = prefs.getString("cm_server_list", "")
            val servers = serverString!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (servers.isNotEmpty())
                CMClient.updateCMServers(servers)
        }

        createNotificationChannels()

        ACRA.init(this)
    }

    private fun createNotificationChannels() {

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            //Set up the notification channel for the Foreground Service
            val serviceChannel = NotificationChannel (
                    SERVICE_ID,
                    STEAM_SERVICE,
                    NotificationManager.IMPORTANCE_LOW
            )
            serviceChannel.description = STEAM_SERVICE_DESC

            //Set up the notification channel for the Friend Requests
            val requestChannel = NotificationChannel (
                    REQUEST_ID,
                    STEAM_FRIEND_REQUEST,
                    NotificationManager.IMPORTANCE_HIGH
            )
            requestChannel.description = STEAM_FRIEND_REQUEST_DESC
            requestChannel.setSound(Uri.parse(prefs.getString("pref_notification_sound", "DEFAULT_SOUND")), null)
            requestChannel.vibrationPattern = if (prefs.getBoolean("pref_vibrate", true)) longArrayOf(0, 500, 200, 500, 1000) else longArrayOf(0)

            //Set up the notification channel for the Friend Messages
            val messageChannel = NotificationChannel (
                    MESSAGE_ID,
                    STEAM_FRIEND_MESSAGE,
                    NotificationManager.IMPORTANCE_HIGH
            )
            messageChannel.description = STEAM_FRIEND_MESSAGE_DESC

            //Apply the channels to be registered
            val manager: NotificationManager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(requestChannel)
            manager.createNotificationChannel(messageChannel)
        }

    }

    companion object {
        lateinit var filesDirectory: File

        const val SERVICE_CHANNEL = 49715
        const val REQUEST_CHANNEL = 49716
        const val MESSAGE_CHANNEL = 49717

        const val SERVICE_ID = "steamServiceID"
        const val MESSAGE_ID = "steamMessageID"
        const val REQUEST_ID = "steamRequestID"

        private const val STEAM_SERVICE = "Ice Service"
        private const val STEAM_FRIEND_REQUEST = "Friend Requests"
        private const val STEAM_FRIEND_MESSAGE = "Chat Notifications"

        private const val STEAM_SERVICE_DESC = "Allows Steam Service to run in the foreground. " +
                "Creating a permanent notification in the status bar, while the application is running."
        private const val STEAM_FRIEND_REQUEST_DESC = "Allows friend requests to appear when one is pending."
        private const val STEAM_FRIEND_MESSAGE_DESC = "Allows friend chats to appear when one is sent."
    }
}
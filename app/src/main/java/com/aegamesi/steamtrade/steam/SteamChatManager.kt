package com.aegamesi.steamtrade.steam

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.LongSparseArray
import com.aegamesi.steamtrade.MainActivity
import com.aegamesi.steamtrade.R
import com.aegamesi.steamtrade.steam.DBHelper.ChatEntry
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EChatEntryType
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.SteamFriends
import uk.co.thomasc.steamkit.types.steamid.SteamID
import java.util.*
import java.util.concurrent.TimeUnit

class SteamChatManager internal constructor(private val context: Context) {

    var unreadMessages: MutableSet<SteamID> = HashSet()
    var receivers: MutableList<ChatReceiver> = ArrayList()

    //New notification methods, replaces Html.fromHtml();
    private val mBoldSpan = StyleSpan(Typeface.BOLD)

    internal fun receiveMessage(from: SteamID, message: String, time: Long) {
        broadcastMessage(
                time,
                SteamService.singleton!!.steamClient!!.steamId,
                from,
                false,
                CHAT_TYPE_CHAT,
                message
        )
    }

    internal fun broadcastMessage(time: Long, id_us: SteamID, id_them: SteamID, sent_by_us: Boolean, type: Int, message: String) {
        // first, log this.
        DBHelper.ChatEntry.insert(
                SteamService.singleton!!.db(),
                time,
                id_us.convertToLong(),
                id_them.convertToLong(),
                sent_by_us,
                type,
                message
        )

        // then notify FragmentFriends and FragmentChat
        if (type == CHAT_TYPE_CHAT) {
            var delivered = false
            for (receiver in receivers)
                delivered = delivered or receiver.receiveChatLine(time, id_us, id_them, sent_by_us, type, message)
            if (!delivered && !sent_by_us) {
                // use a notification
                unreadMessages.add(id_them)
                updateNotification()
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    fun updateNotification() {

        var bitmap: Bitmap? = null

        val prefs = PreferenceManager.getDefaultSharedPreferences(SteamService.singleton)
        val enableNotification = prefs.getBoolean("pref_notification_chat", true)
        if (!enableNotification)
            return

        //We need this to create a Notification Channel ID (on 1st installation), Android O.
        val notificationManager = SteamService.singleton!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationChannel = NotificationChannel(Integer.toString(NOTIFICATION_ID), "Chat Notifications", NotificationManager.IMPORTANCE_HIGH)
        notificationChannel.enableVibration(true)
        notificationChannel.enableLights(true)

        notificationManager.createNotificationChannel(notificationChannel)

        if (unreadMessages.size == 0) {
            notificationManager.cancel(NOTIFICATION_ID)
            return
        }
        val steamFriends = SteamService.singleton!!.steamClient!!.getHandler<SteamFriends>(SteamFriends::class.java)
                ?: return

        //basics for the notification
        val builder = NotificationCompat.Builder(SteamService.singleton!!, Integer.toString(NOTIFICATION_ID))
                .setSmallIcon(R.drawable.ic_notify_msg)

        builder.priority = NotificationCompat.PRIORITY_HIGH
        builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        builder.setLights(-0x512124, 1250, 1000)
        builder.setVibrate(if (prefs.getBoolean("pref_vibrate", true)) longArrayOf(0, 500, 150, 500) else longArrayOf(0))
        builder.setSound(Uri.parse(prefs.getString("pref_notification_sound", "DEFAULT_SOUND")))
        builder.setOnlyAlertOnce(false)
        builder.setLocalOnly(true)


        // let's grab the last lines of our messages
        val whereStatement = StringBuilder()
        for (id in unreadMessages) {
            if (whereStatement.isNotEmpty())
                whereStatement.append(" OR ")
            whereStatement
                    .append(ChatEntry.COLUMN_OTHER_ID)
                    .append(" = ")
                    .append(id.convertToLong())
                    .append("")
        }
        val cursor = SteamService.singleton!!.db().query(
                ChatEntry.TABLE, // The table to query
                arrayOf(ChatEntry.COLUMN_OTHER_ID, ChatEntry.COLUMN_MESSAGE),
                ChatEntry.COLUMN_OUR_ID + " = ? AND " + ChatEntry.COLUMN_SENDER + " = 1 AND " +
                        ChatEntry.COLUMN_TYPE + " = ? AND (" + whereStatement + ")",
                arrayOf("" + SteamService.singleton!!.steamClient!!.steamId.convertToLong(), "" + SteamChatManager.CHAT_TYPE_CHAT),
                ChatEntry.COLUMN_OTHER_ID, null, // don't filter by row groups
                ChatEntry.COLUMN_TIME + " DESC", // ORDER BY
                "5" // LIMIT
        )// GROUP BY

        val recentMessages = LongSparseArray<String>()
        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
            recentMessages.put(cursor.getLong(0), cursor.getString(1))
            cursor.moveToNext()
        }
        cursor.close()

        /* Contents of Chat Notifications */

        // One Person.
        if (unreadMessages.size == 1) {
            val entry = unreadMessages.iterator().next()
            val friendName = steamFriends.getFriendPersonaName(entry)
            val lastLine = recentMessages.get(entry.convertToLong())
            builder.setContentTitle(friendName)
            builder.setContentText(lastLine)

            // start of interactive Notifications
            val imgHash = SteamUtil.bytesToHex(steamFriends.getFriendAvatar(entry)).toLowerCase(Locale.US)
            var avatarURL = ""
            if (imgHash != "0000000000000000000000000000000000000000" && imgHash.length == 40) {
                avatarURL = "http://media.steampowered.com/steamcommunity/public/images/avatars/" + imgHash.substring(0, 2) + "/" + imgHash + "_full.jpg"
            }

            // Add friend's avatar to notification shade.


            try {
                bitmap = Glide.with(context)
                        .asBitmap()
                        .load(avatarURL)
                        .apply(RequestOptions().circleCrop())
                        .submit()
                        .get(5, TimeUnit.SECONDS)

            } catch (ignored: Exception) {
                /* Nothing */
            }

            builder.setLargeIcon(bitmap)

        } else {
            // 2 or more people.
            val i = unreadMessages.iterator()
            val style = NotificationCompat.InboxStyle()
            val friendNames = StringBuilder()
            while (i.hasNext()) {
                val entry = i.next()
                val friendName = steamFriends.getFriendPersonaName(entry)
                val lastLine = recentMessages.get(entry.convertToLong())

                style.addLine(makeNotificationLine(friendName, lastLine))//New method for BOLD notifications.

                if (friendNames.isNotEmpty())
                    friendNames.append(", ")
                friendNames.append(friendName)
            }

            builder.setContentTitle(String.format(SteamService.singleton!!.getString(R.string.x_new_messages), unreadMessages.size))
            builder.setContentText(friendNames)
            builder.setStyle(style)
        }

        // and the intent of the notifications.
        val notificationSteamID = if (unreadMessages.size == 1) unreadMessages.iterator().next().convertToLong() else 0
        val fragmentArguments = Bundle()

        fragmentArguments.putLong("steamId", notificationSteamID)

        val intent = Intent(SteamService.singleton, MainActivity::class.java)
        intent.putExtra("fragment", "com.aegamesi.steamtrade.fragments." + if (notificationSteamID == 0L) "FragmentFriends" else "FragmentChat")
        intent.putExtra("arguments", fragmentArguments)
        val stackBuilder = TaskStackBuilder.create(SteamService.singleton!!)
        stackBuilder.addParentStack(MainActivity::class.java)
        stackBuilder.addNextIntent(intent)
        val resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
        builder.setContentIntent(resultPendingIntent)

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun makeNotificationLine(title: String?, text: String): SpannableString {
        val spannableString: SpannableString
        if (title != null && title.isNotEmpty()) {
            spannableString = SpannableString(String.format("%s  %s", title, text))
            spannableString.setSpan(mBoldSpan, 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            spannableString = SpannableString(text)
        }
        return spannableString
    }

    fun sendMessage(recipient: SteamID, message: String) {
        val steamFriends = SteamService.singleton!!.steamClient!!.getHandler<SteamFriends>(SteamFriends::class.java)
        if (steamFriends != null) {
            steamFriends.sendChatMessage(recipient, EChatEntryType.ChatMsg, message)

            broadcastMessage(
                    System.currentTimeMillis(),
                    SteamService.singleton!!.steamClient!!.steamId,
                    recipient,
                    true,
                    CHAT_TYPE_CHAT,
                    message)
        }
    }

    fun getRecentChats(threshold: Long): ArrayList<SteamID> {
        val timediff = System.currentTimeMillis() - threshold
        val cursor = SteamService.singleton!!.db().query(
                ChatEntry.TABLE, // The table to query
                arrayOf(ChatEntry.COLUMN_OTHER_ID),
                ChatEntry.COLUMN_OUR_ID + " = ? AND " + ChatEntry.COLUMN_TYPE + " = ? AND " + ChatEntry.COLUMN_TIME + " > ?",
                arrayOf("" + SteamService.singleton!!.steamClient!!.steamId.convertToLong(), "" + SteamChatManager.CHAT_TYPE_CHAT, "" + timediff),
                ChatEntry.COLUMN_OTHER_ID, null, // don't filter by row groups
                ChatEntry.COLUMN_TIME + " DESC", // ORDER BY
                "5" // LIMIT
        )

        val recentChats = ArrayList<SteamID>()
        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
            recentChats.add(SteamID(cursor.getLong(0)))
            cursor.moveToNext()
        }
        cursor.close()

        return recentChats
    }

    interface ChatReceiver {
        fun receiveChatLine(time: Long, id_us: SteamID, id_them: SteamID, sent_by_us: Boolean, type: Int, message: String): Boolean
    }

    companion object {
        var CHAT_TYPE_CHAT = 0
        internal var CHAT_TYPE_TRADE = 1
        private const val NOTIFICATION_ID = 49717
    }
}

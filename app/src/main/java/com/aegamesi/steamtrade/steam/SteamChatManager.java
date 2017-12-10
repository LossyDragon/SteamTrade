package com.aegamesi.steamtrade.steam;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.util.LongSparseArray;

import com.aegamesi.steamtrade.MainActivity;
import com.aegamesi.steamtrade.R;
import com.aegamesi.steamtrade.steam.DBHelper.ChatEntry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import uk.co.thomasc.steamkit.base.generated.steamlanguage.EChatEntryType;
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.SteamFriends;
import uk.co.thomasc.steamkit.types.steamid.SteamID;

public class SteamChatManager {
    public static int CHAT_TYPE_CHAT = 0;
    @SuppressWarnings("WeakerAccess")
    public static int CHAT_TYPE_TRADE = 1;
    private static final int NOTIFICATION_ID = 49717;

    public Set<SteamID> unreadMessages;
    public List<ChatReceiver> receivers;

    @SuppressWarnings("WeakerAccess")
    public SteamChatManager() {
        unreadMessages = new HashSet<>();
        receivers = new ArrayList<>();
    }

     void receiveMessage(SteamID from, String message, long time) {
        broadcastMessage(
                time,
                SteamService.singleton.steamClient.getSteamId(),
                from,
                false,
                CHAT_TYPE_CHAT,
                message
        );
    }

     void broadcastMessage(long time, SteamID id_us, SteamID id_them, boolean sent_by_us, int type, String message) {
        // first, log this.
        DBHelper.ChatEntry.insert(
                SteamService.singleton.db(),
                time,
                id_us.convertToLong(),
                id_them.convertToLong(),
                sent_by_us,
                type,
                message
        );

        // then notify FragmentFriends and FragmentChat
        if (type == CHAT_TYPE_CHAT) {
            boolean delivered = false;
            for (ChatReceiver receiver : receivers)
                if (receiver != null)
                    delivered |= receiver.receiveChatLine(time, id_us, id_them, sent_by_us, type, message);
            if (!delivered && !sent_by_us) {
                // use a notification
                unreadMessages.add(id_them);
                updateNotification();
            }
        }
    }

    public void updateNotification() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SteamService.singleton);
        boolean enableNotification = prefs.getBoolean("pref_notification_chat", true);
        if (!enableNotification)
            return;

        //We need this to create a Notification Channel ID (on 1st installation), Android O.
        NotificationManager notificationManager = (NotificationManager) SteamService.singleton.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel notificationChannel = new NotificationChannel(Integer.toString(NOTIFICATION_ID), "Chat Notifications", NotificationManager.IMPORTANCE_HIGH);
        notificationChannel.enableVibration(true);
        notificationChannel.enableLights(true);

        if (notificationManager != null)
            notificationManager.createNotificationChannel(notificationChannel);

        if (unreadMessages.size() == 0) {
            if (notificationManager != null) {
                notificationManager.cancel(NOTIFICATION_ID);
            }
            return;
        }
        SteamFriends steamFriends = SteamService.singleton.steamClient.getHandler(SteamFriends.class);
        if (steamFriends == null)
            return;

        // basics for the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(SteamService.singleton, Integer.toString(NOTIFICATION_ID)).setSmallIcon(R.drawable.ic_notify_msg);
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE);
        //builder.setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_SOUND);
        builder.setLights(0xFFAEDEDC, 1250, 1000);
        builder.setVibrate(prefs.getBoolean("pref_vibrate", true) ? new long[]{0, 500, 150, 500} : new long[]{0});
        builder.setSound(Uri.parse(prefs.getString("pref_notification_sound", "DEFAULT_SOUND")));
        builder.setOnlyAlertOnce(true);
        builder.setLocalOnly(true);

        // let's grab the last lines of our messages
        StringBuilder whereStatement = new StringBuilder();
        for (SteamID id : unreadMessages) {
            if (whereStatement.length() > 0)
                whereStatement.append(" OR ");
            whereStatement
                    .append(ChatEntry.COLUMN_OTHER_ID)
                    .append(" = ")
                    .append(id.convertToLong())
                    .append("");
        }
        Cursor cursor = SteamService.singleton.db().query(
                ChatEntry.TABLE,                    // The table to query
                new String[]{ChatEntry.COLUMN_OTHER_ID, ChatEntry.COLUMN_MESSAGE},
                ChatEntry.COLUMN_OUR_ID + " = ? AND " + ChatEntry.COLUMN_SENDER + " = 1 AND " +
                        ChatEntry.COLUMN_TYPE + " = ? AND (" + whereStatement + ")",
                new String[]{"" + SteamService.singleton.steamClient.getSteamId().convertToLong(), "" +
                        SteamChatManager.CHAT_TYPE_CHAT},
                ChatEntry.COLUMN_OTHER_ID, // GROUP BY
                null, // don't filter by row groups
                ChatEntry.COLUMN_TIME + " DESC", // ORDER BY
                "5" // LIMIT
        );

        LongSparseArray<String> recentMessages = new LongSparseArray<>();
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            recentMessages.put(cursor.getLong(0), cursor.getString(1));
            cursor.moveToNext();
        }
        cursor.close();

        // content of the notifications
        if (unreadMessages.size() == 1) {
            // just one person
            SteamID entry = unreadMessages.iterator().next();
            String friendName = steamFriends.getFriendPersonaName(entry);
            String lastLine = recentMessages.get(entry.convertToLong());
            builder.setContentTitle(friendName);
            builder.setContentText(lastLine);


        } else {
            // more than one person
            Iterator<SteamID> i = unreadMessages.iterator();
            NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
            StringBuilder friendNames = new StringBuilder();
            while (i.hasNext()) {
                SteamID entry = i.next();
                String friendName = steamFriends.getFriendPersonaName(entry);
                String lastLine = recentMessages.get(entry.convertToLong());

                style.addLine(makeNotificationLine(friendName, lastLine));//New method for BOLD notifications.

                if (friendNames.length() > 0)
                    friendNames.append(", ");
                friendNames.append(friendName);
            }

            builder.setContentTitle(String.format(SteamService.singleton.getString(R.string.x_new_messages), unreadMessages.size()));
            builder.setContentText(friendNames);
            builder.setStyle(style);
        }

        // and the intent
        long notificationSteamID = unreadMessages.size() == 1 ? unreadMessages.iterator().next().convertToLong() : 0;
        Bundle fragmentArguments = new Bundle();
        fragmentArguments.putLong("steamId", notificationSteamID);

        Intent intent = new Intent(SteamService.singleton, MainActivity.class);
        intent.putExtra("fragment", "com.aegamesi.steamtrade.fragments." + (notificationSteamID == 0 ? "FragmentFriends" : "FragmentChat"));
        intent.putExtra("arguments", fragmentArguments);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(SteamService.singleton);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(intent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(resultPendingIntent);

        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    //New notification methods, replaces Html.fromHtml();
    private final StyleSpan mBoldSpan = new StyleSpan(Typeface.BOLD);
    private SpannableString makeNotificationLine(String title, String text) {
        final SpannableString spannableString;
        if (title != null && title.length() > 0) {
            spannableString = new SpannableString(String.format("%s  %s", title, text));
            spannableString.setSpan(mBoldSpan, 0, title.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            spannableString = new SpannableString(text);
        }
        return spannableString;
    }

    public void sendMessage(SteamID recipient, String message) {
        SteamFriends steamFriends = SteamService.singleton.steamClient.getHandler(SteamFriends.class);
        if (steamFriends != null) {
            steamFriends.sendChatMessage(recipient, EChatEntryType.ChatMsg, message);

            broadcastMessage(
                    System.currentTimeMillis(),
                    SteamService.singleton.steamClient.getSteamId(),
                    recipient,
                    true,
                    CHAT_TYPE_CHAT,
                    message);
        }
    }

    public ArrayList<SteamID> getRecentChats(long threshold) {
        long timediff = System.currentTimeMillis() - threshold;
        Cursor cursor = SteamService.singleton.db().query(
                ChatEntry.TABLE,                    // The table to query
                new String[]{ChatEntry.COLUMN_OTHER_ID},
                ChatEntry.COLUMN_OUR_ID + " = ? AND " + ChatEntry.COLUMN_TYPE + " = ? AND " + ChatEntry.COLUMN_TIME + " > ?",
                new String[]{"" + SteamService.singleton.steamClient.getSteamId().convertToLong(), "" + SteamChatManager.CHAT_TYPE_CHAT, "" + timediff},
                ChatEntry.COLUMN_OTHER_ID, // GROUP BY
                null, // don't filter by row groups
                ChatEntry.COLUMN_TIME + " DESC", // ORDER BY
                "5" // LIMIT
        );

        ArrayList<SteamID> recentChats = new ArrayList<>();
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            recentChats.add(new SteamID(cursor.getLong(0)));
            cursor.moveToNext();
        }
        cursor.close();

        return recentChats;
    }

    public interface ChatReceiver {
        boolean receiveChatLine(long time, SteamID id_us, SteamID id_them, boolean sent_by_us, int type, String message);
    }
}
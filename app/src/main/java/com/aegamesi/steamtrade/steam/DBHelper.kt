package com.aegamesi.steamtrade.steam


import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns

import com.aegamesi.steamtrade.SteamTrade

import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

class DBHelper internal constructor(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    @Suppress("RegExpRedundantEscape")
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_ENTRIES)

        // if we have data from the previous version of the app, load it here
        // load chat data.
        // Ignore redundant character escape in chatPattern. [Android Lint]
        val chatPattern = Pattern.compile("([t|c])\\[([0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2})\\] (You|Them): (.*)")
        val chatMatcher = chatPattern.matcher("")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss", Locale.US)
        val logfolder = File(SteamTrade.filesDirectory, "logs")
        if (logfolder.exists() && logfolder.isDirectory) {
            val idFolders = logfolder.listFiles()
            for (id_folder in idFolders) {
                val userID = java.lang.Long.parseLong(id_folder.name)
                val chats = id_folder.listFiles()
                for (chat in chats) {
                    if (chat.isFile && chat.name.endsWith(".log")) {
                        val otherID = java.lang.Long.parseLong(chat.name.substring(0, chat.name.length - 4))
                        // now we can start reading and parsing this file
                        try {
                            val reader = BufferedReader(FileReader(chat))
                            val line: String? = reader.readLine()
                            while (line != null) {
                                chatMatcher.reset(line)
                                if (!chatMatcher.matches())
                                    continue
                                val type = if (chatMatcher.group(1) == "c") SteamChatManager.CHAT_TYPE_CHAT else SteamChatManager.CHAT_TYPE_TRADE
                                val time: Long
                                try {
                                    time = dateFormat.parse(chatMatcher.group(2)).time
                                } catch (e: ParseException) {
                                    continue
                                }

                                val sentByUs = chatMatcher.group(3) == "You"
                                val message = chatMatcher.group(4)

                                ChatEntry.insert(db, time, userID, otherID, sentByUs, type, message)
                            }
                            reader.close()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }

                    }
                }
            }
        }
    }

    //Update Sqlite database version (Not used yet)
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // adapted from http://blog.adamsbros.org/2012/02/28/upgrade-android-sqlite-database/
        var upgradeTo = oldVersion + 1
        while (upgradeTo <= newVersion) {
            if (upgradeTo == 1) {
                break
            }
            upgradeTo++
        }
    }

    abstract class ChatEntry : BaseColumns {
        companion object {
            const val TABLE = "chat"
            const val ID = "_id"
            const val COLUMN_TIME = "time"
            const val COLUMN_OUR_ID = "id_us"
            const val COLUMN_OTHER_ID = "id_other"
            const val COLUMN_SENDER = "sender" // 0 - us, 1 - them
            const val COLUMN_TYPE = "type" // 0 - chat, 1 - trade
            const val COLUMN_MESSAGE = "message"

            // inserts a new row, returns the new row id
            internal fun insert(db: SQLiteDatabase, time: Long, id_us: Long, id_them: Long, sent_by_us: Boolean, type: Int, message: String) {
                val values = ContentValues()
                values.put(COLUMN_TIME, time)
                values.put(COLUMN_OUR_ID, id_us)
                values.put(COLUMN_OTHER_ID, id_them)
                values.put(COLUMN_SENDER, if (sent_by_us) 0 else 1)
                values.put(COLUMN_TYPE, type)
                values.put(COLUMN_MESSAGE, message.trim { it <= ' ' })

                db.insert(TABLE, null, values)
            }
        }
    }

    companion object {
        // If you change the database schema, you must increment the database version.
        private const val DATABASE_VERSION = 2
        private const val DATABASE_NAME = "SteamTrade.db"

        // predefined sql query
        private const val SQL_CREATE_ENTRIES = "CREATE TABLE " + ChatEntry.TABLE + " (" +
        ChatEntry.ID + " INTEGER PRIMARY KEY," +
        ChatEntry.COLUMN_TIME + " INTEGER," +
        ChatEntry.COLUMN_OUR_ID + " INTEGER," +
        ChatEntry.COLUMN_OTHER_ID + " INTEGER," +
        ChatEntry.COLUMN_SENDER + " INTEGER," +
        ChatEntry.COLUMN_TYPE + " INTEGER," +
        ChatEntry.COLUMN_MESSAGE + " TEXT" +
        " )"
    }
}
package com.aegamesi.steamtrade.steam

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.preference.PreferenceManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import com.aegamesi.steamtrade.*
import com.aegamesi.steamtrade.LogoutReceiver
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import uk.co.thomasc.steamkit.base.generated.steamlanguage.*
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.SteamFriends
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.callbacks.FriendMsgCallback
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.callbacks.FriendMsgEchoCallback
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.callbacks.FriendMsgHistoryCallback
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.callbacks.PersonaStateCallback
import uk.co.thomasc.steamkit.steam3.handlers.steamnotifications.SteamNotifications
import uk.co.thomasc.steamkit.steam3.handlers.steamnotifications.callbacks.NotificationOfflineMsgCallback
import uk.co.thomasc.steamkit.steam3.handlers.steamnotifications.callbacks.NotificationUpdateCallback
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.SteamUser
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.callbacks.LoggedOffCallback
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.callbacks.LoggedOnCallback
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.callbacks.LoginKeyCallback
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.callbacks.UpdateMachineAuthCallback
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.types.LogOnDetails
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.types.MachineAuthDetails
import uk.co.thomasc.steamkit.steam3.steamclient.SteamClient
import uk.co.thomasc.steamkit.steam3.steamclient.callbackmgr.CallbackMsg
import uk.co.thomasc.steamkit.steam3.steamclient.callbackmgr.JobCallback
import uk.co.thomasc.steamkit.steam3.steamclient.callbacks.CMListCallback
import uk.co.thomasc.steamkit.steam3.steamclient.callbacks.ConnectedCallback
import uk.co.thomasc.steamkit.steam3.steamclient.callbacks.DisconnectedCallback
import uk.co.thomasc.steamkit.steam3.webapi.WebAPI
import uk.co.thomasc.steamkit.types.gameid.GameID
import uk.co.thomasc.steamkit.types.steamid.SteamID
import uk.co.thomasc.steamkit.util.KeyDictionary
import uk.co.thomasc.steamkit.util.WebHelpers
import uk.co.thomasc.steamkit.util.cSharp.events.ActionT
import uk.co.thomasc.steamkit.util.crypto.CryptoHelper
import uk.co.thomasc.steamkit.util.crypto.RSACrypto
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.lang.NumberFormatException
import java.util.*

// This is the backbone of the app. Stores SteamClient connection, message, chat, and trade handlers, schemas...
class SteamService : Service() {
    var steamClient: SteamClient? = null
    var chatManager: SteamChatManager? = null
    var messageHandler: SteamMessageHandler? = null
    var sessionID: String? = null
    var token: String? = null
    var tokenSecure: String? = null
    var webapiUserNonce: String? = null
    var sentryHash: String? = null
    var username: String? = null
    var timeLogin = 0L
    private var myTimer: Timer? = null
    private var dbHelper: DBHelper? = null
    private var _db: SQLiteDatabase? = null
    private var handler: Handler? = null
    private var timerRunning = false
    //private var broadcast: IceBroadcastReceiver? = null

    private var api: StoreFront? = null
    val gameData: MutableMap<SteamID, String> = mutableMapOf()

    private var notificationManager: NotificationManagerCompat? = null

    private fun resetAuthentication() {
        sessionID = null
        token = null
        tokenSecure = null
    }

    private fun buildNotification(code: Int, update: Boolean) {

        //Set up the Service notification intent.
        val notificationIntent = Intent(this, LoginActivity::class.java)
        notificationIntent.action = Intent.ACTION_MAIN
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        val contentIntent = PendingIntent.getActivity(this@SteamService, 0, notificationIntent, 0)

        val broadcastIntent = Intent(this, LogoutReceiver::class.java)
        val actionIntent = PendingIntent.getBroadcast(this, 0,
                broadcastIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        val action: NotificationCompat.Action = NotificationCompat.Action.Builder(
                R.drawable.ic_notify_disconnect,
                "Log Off",
                actionIntent).build()

        val builder: Notification = if (code != SteamConnectionListener.STATUS_FAILURE) {
            NotificationCompat.Builder(this@SteamService, SteamTrade.SERVICE_ID)
                    .setSmallIcon(R.drawable.ic_notify_online)
                    .setContentText(resources.getStringArray(R.array.connection_status)[code])
                    .setAutoCancel(false)
                    .addAction(action)
                    .setContentIntent(contentIntent)
                    .build()

        } else {
            NotificationCompat.Builder(this@SteamService, SteamTrade.SERVICE_ID)
                    .setSmallIcon(R.drawable.ic_notify_disconnect)
                    .setContentText(resources.getStringArray(R.array.connection_status)[code])
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .build()
        }

        if (update) {
            notificationManager!!.notify(SteamTrade.SERVICE_CHANNEL, builder)
        } else {
            startForeground(SteamTrade.SERVICE_CHANNEL, builder)
        }
    }

    // this needs to take place in a non-main thread
    private fun processLogon(listener: SteamConnectionListener?) {
        // now we wait.
        listener?.onConnectionStatusUpdate(SteamConnectionListener.STATUS_CONNECTING)
        buildNotification(SteamConnectionListener.STATUS_CONNECTING, false)
        attemptReconnect = false

        //val filter = IntentFilter()
        //filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        //registerReceiver(broadcast, filter)

        if (listener != null)
            connectionListener = listener
        db()
        SteamUtil.webApiKey = null // reset webApiKey
        steamClient!!.connect(true)
    }

    fun db(): SQLiteDatabase {
        if (_db == null)
            _db = dbHelper!!.writableDatabase
        return _db!!
    }


    override fun onCreate() {
        super.onCreate()

        notificationManager = NotificationManagerCompat.from(this)
        //broadcast = IceBroadcastReceiver()

        handler = Handler()
        dbHelper = DBHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        steamClient = SteamClient()
        chatManager = SteamChatManager(applicationContext)

        if (!timerRunning) {
            myTimer = Timer()
            myTimer!!.scheduleAtFixedRate(CheckCallbacksTask(), 0, 1000)
            timerRunning = true
        }

        running = true
        singleton = this

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()

        //unregisterReceiver(broadcast)
        stopForeground(true)
        running = false
        singleton = null

        _db?.close()
        _db = null

        myTimer?.cancel()
        timerRunning = false
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    fun handleSteamMessage(msg: CallbackMsg) {
        msg.handle(DisconnectedCallback::class.java, object : ActionT<DisconnectedCallback>() {
            override fun call(obj: DisconnectedCallback) {
                if (connectionListener != null) {
                    connectionListener!!.onConnectionResult(EResult.ConnectFailed)
                    connectionListener!!.onConnectionStatusUpdate(SteamConnectionListener.STATUS_FAILURE)
                }
                buildNotification(SteamConnectionListener.STATUS_FAILURE, true)

                // now, attempt reconnect?
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val activeNetwork: NetworkInfo?
                activeNetwork = cm.activeNetworkInfo
                val connected = activeNetwork != null && activeNetwork.isConnected
                if (attemptReconnect && connected) {
                    val prefReconnect = PreferenceManager.getDefaultSharedPreferences(this@SteamService).getBoolean("pref_reconnect", true)
                    if (prefReconnect) {
                        // first make sure that we are connected to the internet
                        // and the last failure was not "connectfailed"

                        // do the reconnection process
                        if (extras != null) {
                            // steam guard key will no longer be valid-- and, provided we had a successful login, we shouldn't need it anyway
                            if (extras!!.containsKey("steamguard"))
                                extras!!.remove("steamguard")

                            // this might not work using the own service that will be stopped as the context
                            SteamService.attemptLogon(this@SteamService, null, extras)
                        }
                    }
                }

                Log.i("Steam", "Disconnected from Steam Network, new connection: " + obj.isNewconnection)
                disconnect()
            }
        })
        msg.handle(LoggedOffCallback::class.java, object : ActionT<LoggedOffCallback>() {
            override fun call(obj: LoggedOffCallback) {
                Log.i("Steam", "Logged off from Steam, " + obj.result)
                disconnect()
            }
        })
        msg.handle(ConnectedCallback::class.java, object : ActionT<ConnectedCallback>() {
            override fun call(callback: ConnectedCallback) {
                Log.i("Steam", "Connection Status " + callback.result)
                if (callback.result == EResult.OK) {
                    //  notify listener
                    if (connectionListener != null) {
                        connectionListener!!.onConnectionStatusUpdate(SteamConnectionListener.STATUS_LOGON)
                    }
                    buildNotification(SteamConnectionListener.STATUS_LOGON, true)

                    // log in
                    this@SteamService.username = extras!!.getString("username")
                    val details = LogOnDetails()
                    details.username(extras!!.getString("username"))
                    if (extras!!.containsKey("loginkey")) {
                        // login key
                        val loginkey = extras!!.getString("loginkey")
                        if (loginkey != null && loginkey.isNotEmpty())
                            details.loginkey = loginkey
                    } else {
                        details.password(extras!!.getString("password"))
                    }
                    details.shouldRememberPassword = extras!!.getBoolean("remember", false)
                    if (extras!!.getString("steamguard") != null) {
                        //details.authCode(extras.getString("steamguard"));
                        if (extras!!.getBoolean("twofactor", false))
                            details.twoFactorCode(extras!!.getString("steamguard"))
                        else
                            details.authCode(extras!!.getString("steamguard"))
                    }


                    // sentry files
                    val prefSentry = PreferenceManager.getDefaultSharedPreferences(this@SteamService).getString("pref_machineauth", "")
                    if (prefSentry!!.trim { it <= ' ' }.isNotEmpty()) {
                        sentryHash = prefSentry.trim { it <= ' ' }
                    } else {
                        var file = File(filesDir, "sentry")
                        if (file.exists() || file.mkdir()) {
                            file = File(file, extras!!.getString("username")!! + ".sentry")
                            if (file.exists()) {
                                try {
                                    val raf = RandomAccessFile(file, "r")
                                    val data = ByteArray(raf.length().toInt())
                                    raf.readFully(data)
                                    raf.close()
                                    details.sentryFileHash = SteamUtil.calculateSHA1(data)
                                    //details.authCode = "";

                                    sentryHash = SteamUtil.bytesToHex(details.sentryFileHash)
                                } catch (e: IOException) {
                                    e.printStackTrace()
                                }

                            }
                        }
                    }
                    val steamUser = steamClient!!.getHandler<SteamUser>(SteamUser::class.java)
                    steamUser?.logOn(details, com.aegamesi.steamtrade.Installation.id())
                } else {
                    if (connectionListener != null) {
                        connectionListener!!.onConnectionResult(EResult.ConnectFailed)
                        connectionListener!!.onConnectionStatusUpdate(SteamConnectionListener.STATUS_FAILURE)
                    }
                    buildNotification(SteamConnectionListener.STATUS_FAILURE, true)
                    attemptReconnect = false

                    disconnect()
                }
            }
        })
        msg.handle(LoggedOnCallback::class.java, object : ActionT<LoggedOnCallback>() {
            override fun call(callback: LoggedOnCallback) {
                if (callback.result != EResult.OK) {
                    // if there's a loginkey saved and it's an InvalidPassword, scrap it
                    if (callback.result == EResult.InvalidPassword) {
                        if (extras != null && extras!!.containsKey("username")) {
                            val account = AccountLoginInfo.readAccount(this@SteamService, extras!!.getString("username")!!)
                            if (account != null) {
                                account.loginkey = null
                                AccountLoginInfo.writeAccount(this@SteamService, account)
                            }
                        }
                    }

                    if (connectionListener != null) {
                        connectionListener!!.onConnectionResult(callback.result)
                        connectionListener!!.onConnectionStatusUpdate(SteamConnectionListener.STATUS_FAILURE)
                    }
                    buildNotification(SteamConnectionListener.STATUS_FAILURE, true)
                    attemptReconnect = false
                    Log.i("Steam", "Login Failure: " + callback.result)
                    disconnect()
                } else {
                    // okay! :)
                    webapiUserNonce = callback.webAPIUserNonce

                    // save password (it's valid!)
                    if (extras != null && extras!!.getBoolean("remember", false) && extras!!.containsKey("password")) {
                        Log.d("SteamService", "Saving password.")
                        val account = AccountLoginInfo.readAccount(this@SteamService, extras!!.getString("username")!!)
                        if (account != null) {
                            account.password = extras!!.getString("password")
                            AccountLoginInfo.writeAccount(this@SteamService, account)
                        }
                    }

                    if (connectionListener != null)
                        connectionListener!!.onConnectionStatusUpdate(SteamConnectionListener.STATUS_AUTH)
                    buildNotification(SteamConnectionListener.STATUS_AUTH, true)

                    doSteamWebAuthentication()
                }
            }
        })
        msg.handle(JobCallback::class.java, object : ActionT<JobCallback<*>>() {
            override fun call(callback: JobCallback<*>) {
                if (callback.callbackType == UpdateMachineAuthCallback::class.java) {
                    val authCallback = callback.callback as UpdateMachineAuthCallback
                    try {
                        Log.i("Steam", "Received updated sentry file: " + authCallback.fileName)
                        var file = File(filesDir, "sentry")
                        if (file.exists() || file.mkdir()) {
                            file = File(file, extras!!.getString("username")!! + ".sentry")
                            val fos = FileOutputStream(file)
                            fos.write(authCallback.data)
                            fos.close()
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                    val auth = MachineAuthDetails()
                    auth.jobId = callback.jobId.value!!
                    auth.fileName = authCallback.fileName
                    auth.bytesWritten = authCallback.bytesToWrite
                    auth.fileSize = authCallback.data.size
                    auth.offset = authCallback.offset
                    auth.result = EResult.OK
                    auth.lastError = 0
                    auth.oneTimePassword = authCallback.oneTimePassword
                    auth.sentryFileHash = SteamUtil.calculateSHA1(authCallback.data)

                    sentryHash = SteamUtil.bytesToHex(auth.sentryFileHash)

                    val steamUser = steamClient!!.getHandler<SteamUser>(SteamUser::class.java)
                    steamUser?.sendMachineAuthResponse(auth)

                    if (extras != null)
                        extras!!.putBoolean("alertSteamGuard", true)
                }
            }
        })
        msg.handle(FriendMsgCallback::class.java, object : ActionT<FriendMsgCallback>() {
            override fun call(callback: FriendMsgCallback) {
                val type = callback.entryType

                if (callback.sender != steamClient!!.steamId) {
                    if (type == EChatEntryType.ChatMsg) {
                        chatManager!!.receiveMessage(callback.sender, callback.message, System.currentTimeMillis())
                    }
                }
            }
        })
        // echoed message from another instance
        msg.handle(FriendMsgEchoCallback::class.java, object : ActionT<FriendMsgEchoCallback>() {
            override fun call(obj: FriendMsgEchoCallback) {
                // we log it:
                if (obj.entryType == EChatEntryType.ChatMsg) {
                    chatManager!!.broadcastMessage(
                            System.currentTimeMillis(),
                            steamClient!!.steamId,
                            obj.recipient,
                            true,
                            SteamChatManager.CHAT_TYPE_CHAT,
                            obj.message
                    )
                }
            }
        })
        msg.handle(PersonaStateCallback::class.java, object : ActionT<PersonaStateCallback>() {
            // handle notifications for friend requests
            //Support legacy notification modes. [setPriority, setVibrate, setSound]
            @Suppress("DEPRECATION")
            override fun call(obj: PersonaStateCallback) {
                val enableRequestNotification = PreferenceManager.getDefaultSharedPreferences(this@SteamService).getBoolean("pref_notification_friendrequest", true)
                val steamFriends = steamClient!!.getHandler<SteamFriends>(SteamFriends::class.java)

                if (steamFriends != null && enableRequestNotification) {
                    if (steamFriends.getFriendRelationship(obj.friendID) == EFriendRelationship.RequestRecipient) {
                        // get number of friend requests pending
                        var friendRequests = 0
                        for (id in steamFriends.friendList)
                            if (steamFriends.getFriendRelationship(id) == EFriendRelationship.RequestRecipient)
                                friendRequests++

                        val prefs = PreferenceManager.getDefaultSharedPreferences(this@SteamService)

                        val builder: Notification = if (friendRequests == 1) {
                            //Just one friend request
                            val partnerName = obj.name

                            val intent = Intent(this@SteamService, MainActivity::class.java)
                            val bundle = Bundle()

                            val stackBuilder = TaskStackBuilder.create(this@SteamService)
                            stackBuilder.addParentStack(MainActivity::class.java)
                            stackBuilder.addNextIntent(intent)
                            val resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)

                            bundle.putLong("steamId", obj.friendID.convertToLong())
                            intent.putExtra("fragment", "com.aegamesi.steamtrade.fragments.FragmentProfile")
                            intent.putExtra("arguments", bundle)

                            Notification.Builder(this@SteamService, SteamTrade.REQUEST_ID)
                                    .setSmallIcon(R.drawable.ic_notify_friend)
                                    .setContentTitle(getString(R.string.friend_request))
                                    .setContentText(String.format(getString(R.string.friend_request_text), partnerName))
                                    .setPriority(Notification.PRIORITY_HIGH)
                                    .setVibrate(if (prefs.getBoolean("pref_vibrate", true)) longArrayOf(0, 500, 200, 500, 1000) else longArrayOf(0))
                                    .setSound(Uri.parse(prefs.getString("pref_notification_sound", "DEFAULT_SOUND")))
                                    .setOnlyAlertOnce(true)
                                    .setAutoCancel(true)
                                    .setContentIntent(resultPendingIntent)
                                    .build()

                        } else {
                            //More than one friend requests
                            val intent = Intent(this@SteamService, MainActivity::class.java)
                            intent.putExtra("fragment", "com.aegamesi.steamtrade.fragments.FragmentFriends")

                            val stackBuilder = TaskStackBuilder.create(this@SteamService)
                            stackBuilder.addParentStack(MainActivity::class.java)
                            stackBuilder.addNextIntent(intent)
                            val resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)

                            Notification.Builder(this@SteamService, SteamTrade.REQUEST_ID)
                                    .setSmallIcon(R.drawable.ic_notify_friend)
                                    .setContentTitle(getString(R.string.friend_request))
                                    .setContentText(String.format(getString(R.string.friend_request_multi), friendRequests))
                                    .setPriority(Notification.PRIORITY_HIGH)
                                    .setVibrate(if (prefs.getBoolean("pref_vibrate", true)) longArrayOf(0, 500, 200, 500, 1000) else longArrayOf(0))
                                    .setSound(Uri.parse(prefs.getString("pref_notification_sound", "DEFAULT_SOUND")))
                                    .setOnlyAlertOnce(true)
                                    .setAutoCancel(true)
                                    .setContentIntent(resultPendingIntent)
                                    .build()
                        }

                        notificationManager!!.notify(SteamTrade.REQUEST_CHANNEL, builder)
                    }
                }
            }
        })

        msg.handle(CMListCallback::class.java, object : ActionT<CMListCallback>() {
            override fun call(obj: CMListCallback) {
                if (obj.serverList.isNotEmpty()) {
                    val serverString = StringBuilder()
                    for (entry in obj.serverList) {
                        if (serverString.isNotEmpty())
                            serverString.append(",")
                        serverString.append(entry)
                    }

                    val prefs = PreferenceManager.getDefaultSharedPreferences(this@SteamService).edit()
                    prefs.putString("cm_server_list", serverString.toString())
                    prefs.apply()
                }
            }
        })
        msg.handle(LoginKeyCallback::class.java, object : ActionT<LoginKeyCallback>() {
            override fun call(callback: LoginKeyCallback) {
                Log.d("SteamService", "Got loginkey " + callback.loginKey + "| uniqueid: " + callback.uniqueId)
                if (extras != null && extras!!.getBoolean("remember", false)) {
                    Log.d("SteamService", "Saving loginkey.")

                    var account = AccountLoginInfo.readAccount(this@SteamService, extras!!.getString("username")!!)
                    if (account == null) {
                        account = AccountLoginInfo()
                        account.username = extras!!.getString("username")
                        account.password = extras!!.getString("password")
                        Log.d("SteamService", "New AccountLoginInfo entry")
                    }
                    account.loginkey = callback.loginKey
                    account.uniqueId = callback.uniqueId
                    AccountLoginInfo.writeAccount(this@SteamService, account)
                }
            }
        })
        msg.handle(FriendMsgHistoryCallback::class.java, object : ActionT<FriendMsgHistoryCallback>() {
            override fun call(obj: FriendMsgHistoryCallback) {
                // add all messages that are "unread" to our internal database
                // problem though... Steam send us *all messages* as unread since we last
                // requested history... perhaps we should request history
                // when we get a message. that way we confirm we read the message
                // SOLUTION: record the time that we log in. If this time is after that, ignore it
                if (obj.success > 0) {
                    val otherId = obj.steamId
                    val ourId = steamClient!!.steamId
                    for (message in obj.messages) {
                        if (!message.isUnread)
                            continue
                        if (message.timestamp * 1000L > timeLogin)
                            continue
                        val sentByUs = message.sender != otherId
                        // potentially check for if it's been read already
                        chatManager!!.broadcastMessage(
                                message.timestamp * 1000, // seconds --> millis
                                ourId,
                                otherId,
                                sentByUs,
                                SteamChatManager.CHAT_TYPE_CHAT,
                                message.message
                        )
                    }
                }
            }
        })
        msg.handle(NotificationOfflineMsgCallback::class.java, object : ActionT<NotificationOfflineMsgCallback>() {
            override fun call(callback: NotificationOfflineMsgCallback) {
                Log.d("SteamService", "Notification offline msg: " + callback.offlineMessages)

                chatManager!!.unreadMessages.addAll(callback.friendsWithOfflineMessages)
            }
        })
        msg.handle(NotificationUpdateCallback::class.java, object : ActionT<NotificationUpdateCallback>() {
            override fun call(obj: NotificationUpdateCallback) {
                buildNotification(SteamConnectionListener.STATUS_CONNECTED, true)
            }
        })
    }

    /**
     * Fetches game names from the SteamWeb API
     */
    //TODO this could use some refinement, but it does the job well enough.
    //TODO the FriendsListAdapter still does not update on-the-fly. The fragment has to be reloaded to show current data.
    //fun getGameData() {
    //    val steamFriends = steamClient!!.getHandler<SteamFriends>(SteamFriends::class.java)
    //    val friendsList: List<SteamID> = steamFriends.friendList
    //
    //    for (x in friendsList) {
    //        var id = 0
    //
    //        try {
    //            id = steamFriends.getFriendGamePlayed(x).toString().toInt()
    //        } catch (e: NumberFormatException) {
    //            Log.w("SteamService:getGameData()", x.toString() + " is playing a Non-Steam Game" + e.message )
    //            id -1
    //        }
    //
    //        if(id != 0 && id > 0) {
    //            fetchGameData(x, id)
    //        }
    //        else if (id == 0 || id < -1) {
    //        }
    //    }
    //}

    //private fun fetchGameData(steamID: SteamID, id: Int) {
    //
    //    if (api == null) {
    //        api = SteamAPI.apiInstance.create(StoreFront::class.java)
    //    }
    //
    //    api!!.getAppDetails(id).enqueue(object : Callback<Map<Int, StoreFront.AppDetailsResponse>> {
    //        override fun onResponse(call: Call<Map<Int, StoreFront.AppDetailsResponse>>, response: Response<Map<Int, StoreFront.AppDetailsResponse>>) {
    //
    //            if (response.isSuccessful) {
    //                val data = response.body()
    //                val detailsResponse = data!![id]
    //
    //                gameData[steamID] = detailsResponse!!.data!!.name!!
    //                Log.d("SteamServiceGamesResponse", "detailsResponse got: " + gameData[steamID])
    //            }
    //        }
    //
    //        override fun onFailure(call: Call<Map<Int, StoreFront.AppDetailsResponse>>, t: Throwable) {
    //            Log.e("SteamServiceGamesResponse", call.toString())
    //        }
    //    })
    //}

    fun kill() {
        running = false
        connectionListener = null
        steamClient?.disconnect()
        stopSelf()

    }

    fun disconnect() {
        steamClient?.disconnect()
        resetAuthentication()
        stopSelf()
    }

    private fun doSteamWebAuthentication() {
        sessionID = SteamUtil.bytesToHex(CryptoHelper.GenerateRandomBlock(4))
        val userAuth = WebAPI("ISteamUserAuth", null)//SteamUtil.webApiKey); // this shouldn't require an api key
        // generate an AES session key
        val sessionKey = CryptoHelper.GenerateRandomBlock(32)
        // rsa encrypt it with the public key for the universe we're on
        val cryptedSessionKey: ByteArray
        val universe = if (steamClient == null) EUniverse.Public else steamClient!!.connectedUniverse
        val rsa = RSACrypto(KeyDictionary.getPublicKey(if (universe == null || universe == EUniverse.Invalid) EUniverse.Public else universe))
        cryptedSessionKey = rsa.encrypt(sessionKey)
        val loginKey = ByteArray(20)
        System.arraycopy(webapiUserNonce!!.toByteArray(), 0, loginKey, 0, webapiUserNonce!!.length)
        // aes encrypt the loginkey with our session key
        val cryptedLoginKey = CryptoHelper.SymmetricEncrypt(loginKey, sessionKey)

        Thread {
            var tries = 3
            while (true) {
                try {
                    Log.i("Steam", "Sending auth request...")
                    val authResult = userAuth.authenticateUser(steamClient!!.steamId.convertToLong().toString(), WebHelpers.UrlEncode(cryptedSessionKey), WebHelpers.UrlEncode(cryptedLoginKey), "POST", "true")
                    token = authResult.get("token").asString()
                    tokenSecure = authResult.get("tokensecure").asString()

                    Log.d("Steam", "Successfully authenticated: $token secure: $tokenSecure")

                    // tell our listener and start fetching the webapi key
                    if (connectionListener != null)
                        connectionListener!!.onConnectionStatusUpdate(SteamConnectionListener.STATUS_APIKEY)

                    buildNotification(SteamConnectionListener.STATUS_APIKEY, true)
                    fetchAPIKey()

                    // now we're done! tell our listener
                    if (connectionListener != null) {
                        connectionListener!!.onConnectionStatusUpdate(SteamConnectionListener.STATUS_CONNECTED)
                        connectionListener!!.onConnectionResult(EResult.OK)
                    }

                    buildNotification(SteamConnectionListener.STATUS_CONNECTED, true)
                    finalizeConnection()
                    break

                } catch (e: Exception) {
                    if (--tries == 0) {
                        Log.e("Steam", "FATAL(ish): Unable to authenticate with SteamWeb. Tried several times")
                        if (connectionListener != null) {
                            connectionListener!!.onConnectionResult(EResult.ServiceUnavailable)
                            connectionListener!!.onConnectionStatusUpdate(SteamConnectionListener.STATUS_FAILURE)
                        }
                        buildNotification(SteamConnectionListener.STATUS_FAILURE, true)
                        attemptReconnect = false
                        break
                    }
                    Log.e("Steam", "Error authenticating! Retrying...")
                }

            }
        }.start()
    }

    //TODO, if you change your password, your saved apikey does not work. Especially when imported through .maFile.
    private fun fetchAPIKey() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this@SteamService)
        var apikey = prefs.getString("webapikey_" + steamClient!!.steamId.convertToLong(), "")
        if (apikey!!.trim { it <= ' ' }.isNotEmpty()) {
            SteamUtil.webApiKey = apikey
            Log.d("Steam", "Using saved api key: " + SteamUtil.webApiKey!!)
        } else {
            // fetch api key
            apikey = SteamWeb.requestWebAPIKey() // hopefully this keeps working
            SteamUtil.webApiKey = apikey ?: ""
            Log.d("Steam", "Fetched api key: " + SteamUtil.webApiKey!!)

            if (SteamUtil.webApiKey!!.trim { it <= ' ' }.isNotEmpty()) {
                prefs.edit().putString("webapikey_" + steamClient!!.steamId.convertToLong(), SteamUtil.webApiKey).apply()
            }
        }
    }

    private fun finalizeConnection() {
        timeLogin = System.currentTimeMillis()
        attemptReconnect = true

        val steamFriends = steamClient!!.getHandler<SteamFriends>(SteamFriends::class.java)
        if (steamFriends != null) {
            steamFriends.personaState = EPersonaState.Online
        }

        val steamNotifications = steamClient!!.getHandler<SteamNotifications>(SteamNotifications::class.java)
        if (steamNotifications != null) {
            steamNotifications.requestNotificationItem()
            steamNotifications.requestNotificationComments()
            steamNotifications.requestNotificationOfflineMessages()
            steamNotifications.requestNotificationGeneric()
        }
    }

    private inner class CheckCallbacksTask : TimerTask() {

        override fun run() {

            if (steamClient == null)
                return

            while (true) {
                val msg = steamClient!!.getCallback(true) ?: break
                handleSteamMessage(msg)
                if (messageHandler != null) {
                    // gotta run this on the ui thread
                    handler!!.post { messageHandler!!.handleSteamMessage(msg) }
                }
            }
        }
    }

    companion object {
        var attemptReconnect = false
        var running = false
        var singleton: SteamService? = null
        var extras: Bundle? = null
        var connectionListener: SteamConnectionListener? = null

        fun generateSteamWebCookies(): String {
            var cookies = ""
            if (singleton != null) {
                cookies += "sessionid=" + singleton!!.sessionID + ";"
                cookies += "steamLogin=" + singleton!!.token + ";"
                cookies += "steamLoginSecure=" + singleton!!.tokenSecure + ";"
                cookies += "webTradeEligibility=%7B%22allowed%22%3A1%2C%22allowed_at_time%22%3A0%2C%22steamguard_required_days%22%3A15%2C%22sales_this_year%22%3A0%2C%22max_sales_per_year%22%3A-1%2C%22forms_requested%22%3A0%2C%22new_device_cooldown_days%22%3A7%7D;"
                cookies += "steamMachineAuth" + singleton!!.steamClient!!.steamId.convertToLong() + "=" + singleton!!.sentryHash + ""
            }
            return cookies
        }

        fun attemptLogon(context: Context, listener: SteamConnectionListener?, bundle: Bundle?) {
            extras = bundle

            // start the steam service (stop if it's already started)
            val intent = Intent(context, SteamService::class.java)
            context.stopService(intent)
            SteamService.singleton = null
            context.startService(intent)

            Thread {
                listener?.onConnectionStatusUpdate(SteamConnectionListener.STATUS_INITIALIZING)

                // busy-wait for the service to start...
                while (SteamService.singleton == null) {
                    try {
                        Thread.sleep(500)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                        if (listener != null) {
                            listener.onConnectionResult(EResult.Fail)
                            listener.onConnectionStatusUpdate(SteamConnectionListener.STATUS_FAILURE)
                        }
                    }
                }

                SteamService.singleton!!.processLogon(listener)
            }.start()
        }
    }
}
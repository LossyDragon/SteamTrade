package com.aegamesi.steamtrade.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView

import com.aegamesi.steamtrade.R
import com.aegamesi.steamtrade.fragments.adapters.ChatAdapter
import com.aegamesi.steamtrade.steam.DBHelper.ChatEntry
import com.aegamesi.steamtrade.steam.SteamChatManager
import com.aegamesi.steamtrade.steam.SteamChatManager.ChatReceiver
import com.aegamesi.steamtrade.steam.SteamService
import com.aegamesi.steamtrade.steam.SteamUtil

import java.util.Locale

import uk.co.thomasc.steamkit.base.generated.steamlanguage.EChatEntryType
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EPersonaState
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.SteamFriends
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.callbacks.FriendMsgCallback
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.callbacks.FriendMsgHistoryCallback
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.callbacks.PersonaStateCallback
import uk.co.thomasc.steamkit.steam3.steamclient.callbackmgr.CallbackMsg
import uk.co.thomasc.steamkit.types.steamid.SteamID
import uk.co.thomasc.steamkit.util.cSharp.events.ActionT

class FragmentChat : FragmentBase(), ChatReceiver {
    private var ourID: SteamID? = null
    var chatID: SteamID? = null
    lateinit var adapter: ChatAdapter
    lateinit var chatList: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager
    var cursor: Cursor? = null

    private lateinit var chatInput: EditText
    private lateinit var chatButton: ImageButton
    var chatTyping: TextView? = null
    var typingHandler: Handler? = null
    var typingRunnable: Runnable? = null
    private var timeLastRead: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (abort)
            return

        setHasOptionsMenu(true)

        ourID = SteamService.singleton!!.steamClient!!.steamId

        try {
            chatID = SteamID(arguments!!.getLong("steamId"))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        timeLastRead = activity()!!.getPreferences(Context.MODE_PRIVATE).getLong("chat_read_" + ourID!!.convertToLong() + "_" + chatID!!.convertToLong(), 0)

        if (SteamService.singleton!!.chatManager!!.unreadMessages.contains(chatID!!)) {
            SteamService.singleton!!.chatManager!!.unreadMessages.remove(chatID!!)
            SteamService.singleton!!.chatManager!!.updateNotification()

            if (activity()!!.getFragmentByClass(FragmentFriends::class.java) != null)
                activity()!!.getFragmentByClass(FragmentFriends::class.java)!!.adapter!!.notifyDataSetChanged()
        }

        // typing timer
        typingHandler = Handler()
        typingRunnable.run{
            if (chatTyping != null)
                chatTyping!!.visibility = View.GONE
        }

        //Get message history from Steam.
        activity()!!.steamFriends.requestFriendMessageHistory(chatID!!)

        /* Set custom toolbar with icon and game status color */
        //TODO could this be passed better without more declarations.
        val steamFriends = SteamService.singleton!!.steamClient!!.getHandler<SteamFriends>(SteamFriends::class.java)
        var avatarUrl: String? = null
        val avatarGame = steamFriends.getFriendGamePlayed(chatID).toString()
        val state = steamFriends.getFriendPersonaState(chatID)
        var color = ContextCompat.getColor(activity()!!.applicationContext, R.color.steam_online)

        if (chatID != null && steamFriends.getFriendAvatar(chatID) != null) {
            val imgHash = SteamUtil.bytesToHex(steamFriends.getFriendAvatar(chatID)).toLowerCase(Locale.US)
            if (imgHash != "0000000000000000000000000000000000000000" && imgHash.length == 40) {
                avatarUrl = "http://media.steampowered.com/steamcommunity/public/images/avatars/" + imgHash.substring(0, 2) + "/" + imgHash + "_medium.jpg"
            }

            if (avatarGame != "0") {
                color = ContextCompat.getColor(activity()!!.applicationContext, R.color.steam_game)
                Log.i("Toolbar", chatID!!.toString() + " playing a game...")
            }

            if (state == EPersonaState.Offline) {
                color = ContextCompat.getColor(activity()!!.applicationContext, R.color.steam_offline)
            }

            Log.d("ICON", avatarUrl)
            // Apply the information we have to the Toolbar.
            setToolBarIconColor(color)
            setToolBarPicture(avatarUrl!!)
        }
    }

    override fun onResume() {
        super.onResume()

        val friendName: String = activity()!!.steamFriends.getFriendPersonaName(chatID)
        setTitle(String.format(getString(R.string.chat_with), friendName))
        setToolBarAvatar(View.VISIBLE)

        // set up the cursor
        cursor = fetchCursor()
        adapter.changeCursor(cursor)

        //Should stop if ourID is false when GC'd.
        if (ourID == null) {
            val fragment = FragmentFriends()
            activity()!!.browseToFragment(fragment, false)
        }

        activity()!!.getPreferences(Context.MODE_PRIVATE).edit()
                .putLong("chat_read_" + ourID!!.convertToLong() + "_" + chatID!!.convertToLong(), System.currentTimeMillis()).apply()

        if (SteamService.singleton != null && SteamService.singleton!!.chatManager != null)
            SteamService.singleton!!.chatManager!!.receivers.add(0, this)

        //On resume, scroll to bottom.
        chatList.scrollToPosition(cursor!!.count - 1)
    }

    override fun handleSteamMessage(msg: CallbackMsg) {
        msg.handle(FriendMsgCallback::class.java, object : ActionT<FriendMsgCallback>() {
            override fun call(callback: FriendMsgCallback) {
                val type = callback.entryType

                if (type == EChatEntryType.Typing && callback.sender == chatID) {
                    // set a timer for the thing
                    if (chatTyping != null) {

                        Log.d(TAG, activity()!!.steamFriends.getFriendPersonaName(chatID) + " is typing a message...")

                        chatTyping!!.visibility = View.VISIBLE
                    }

                    if (typingHandler != null) {
                        typingHandler!!.removeCallbacks(typingRunnable)
                        typingHandler!!.postDelayed(typingRunnable, 15 * 1000L)
                    }
                }
            }
        })
        msg.handle(PersonaStateCallback::class.java, object : ActionT<PersonaStateCallback>() {
            override fun call(obj: PersonaStateCallback) {
                if (chatID != null && chatID == obj.friendID)
                    updateView()
            }
        })
        msg.handle(FriendMsgHistoryCallback::class.java, object : ActionT<FriendMsgHistoryCallback>() {
            override fun call(obj: FriendMsgHistoryCallback) {
                if (obj.steamId == chatID) {
                    // updated list (already received...)
                    // scroll to bottom
                    if (cursor != null)
                        chatList.scrollToPosition(cursor!!.count - 1)
                }
            }
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        val view = inflater.inflate(R.layout.fragment_chat, container, false)

        chatList = view.findViewById(R.id.chat)
        chatInput = view.findViewById(R.id.chat_input)
        chatButton = view.findViewById(R.id.chat_button)
        chatTyping = view.findViewById(R.id.chat_typing)
        chatTyping!!.visibility = View.GONE

        chatInput.setOnEditorActionListener { _, _, event ->
            if (event == null)
                return@setOnEditorActionListener false
            if (event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                val message: String = chatInput.text.toString().trim { it <= ' ' }
                if (message.isEmpty())
                    return@setOnEditorActionListener false
                chatInput.setText("")
                SteamService.singleton!!.chatManager!!.sendMessage(chatID!!, message)
                return@setOnEditorActionListener true
            }
            false
        }
        chatButton.setOnClickListener {
            val message: String = chatInput.text.toString().trim { it <= ' ' }
            if (message.isEmpty())
                return@setOnClickListener
            else {
                chatInput.setText("")
                SteamService.singleton!!.chatManager!!.sendMessage(chatID!!, message)
            }
        }

        val isCompact = PreferenceManager.getDefaultSharedPreferences(activity()!!).getBoolean("pref_chat_compact", false)
        adapter = ChatAdapter(cursor, isCompact)
        adapter.timeLastRead = timeLastRead
        layoutManager = androidx.recyclerview.widget.LinearLayoutManager(activity())
        layoutManager.stackFromEnd = true
        chatList.setHasFixedSize(true)
        chatList.layoutManager = layoutManager
        chatList.adapter = adapter

        updateView()
        return view
    }

    override//Menu OnCreate, shows menu to View Friends profile.
    fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        val friendName = activity()!!.steamFriends.getFriendPersonaName(chatID)
        inflater!!.inflate(R.menu.fragment_chat, menu)
        menu!!.findItem(R.id.friend_profile_page).title = String.format(getString(R.string.menu_friend_profile_page), friendName)
    }

    override//Menu onOptions selection, navigate to chosen profile fragment.
    fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item!!.itemId == R.id.friend_profile_page) {
            val id = chatID
            val fragment = FragmentProfile()
            val bundle = Bundle()
            bundle.putLong("steamId", id!!.convertToLong())
            fragment.arguments = bundle
            activity()!!.browseToFragment(fragment, true)
        }
        return true
    }


    //PENDING: commenting out function to test YouTube PiP from making the chat disappear.
    override fun onPause() {
        super.onPause()

        if (SteamService.singleton != null && SteamService.singleton!!.chatManager != null)
            SteamService.singleton!!.chatManager!!.receivers.remove(this)
    }

    override fun onStop() {
        super.onStop()
        adapter.changeCursor(null)
        setToolBarAvatar(View.GONE) //Hide toolbar icon.
    }

    @SuppressLint("Recycle")
    private fun fetchCursor(): Cursor? {
        return if (SteamService.singleton != null) {
            SteamService.singleton!!.db().query(
                    ChatEntry.TABLE, // The table to query
                    arrayOf(ChatEntry.ID + " AS _id", ChatEntry.COLUMN_TIME, ChatEntry.COLUMN_MESSAGE, ChatEntry.COLUMN_SENDER),
                    ChatEntry.COLUMN_OUR_ID + " = ? AND " + ChatEntry.COLUMN_OTHER_ID + " = ? AND " + ChatEntry.COLUMN_TYPE + " = ?",
                    arrayOf("" + ourID!!.convertToLong(), "" + chatID!!.convertToLong(), "" + SteamChatManager.CHAT_TYPE_CHAT), null, null, // don't filter by row groups
                    ChatEntry.COLUMN_TIME + " ASC"
            )// don't group the rows
        } else null
    }

    fun updateView() {
        if (activity() == null)
            return

        val friendPersonaName = activity()!!.steamFriends.getFriendPersonaName(chatID)
        adapter.setPersonaNames(activity()!!.steamFriends.personaName, friendPersonaName)

        // do colors for profile view
        adapter.defaultColor = ContextCompat.getColor(activity()!!.applicationContext, R.color.steam_online)
    }

    override fun receiveChatLine(time: Long, id_us: SteamID, id_them: SteamID, sent_by_us: Boolean, type: Int, message: String): Boolean {
        if (id_them == chatID) {
            activity()!!.runOnUiThread {
                cursor = fetchCursor()
                adapter.changeCursor(cursor)
                // now scroll to bottom (if already near the bottom)
                if (layoutManager.findLastVisibleItemPosition() > cursor!!.count - 3)
                    chatList.scrollToPosition(cursor!!.count - 1)

                if (!sent_by_us && chatTyping != null)
                    chatTyping!!.visibility = View.GONE
            }
            return true
        }
        return false
    }

    companion object {
        private const val TAG = "SteamTrade:FragmentChat"
    }
}
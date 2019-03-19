package com.aegamesi.steamtrade.fragments

import android.os.Bundle
import android.preference.PreferenceManager
import android.text.InputType
import android.view.*
import android.view.View.OnClickListener
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import com.aegamesi.steamtrade.R
import com.aegamesi.steamtrade.fragments.adapters.FriendsListAdapter
import com.aegamesi.steamtrade.libs.AndroidUtil
import com.aegamesi.steamtrade.steam.SteamChatManager
import com.aegamesi.steamtrade.steam.SteamChatManager.ChatReceiver
import com.aegamesi.steamtrade.steam.SteamService
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EFriendRelationship
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.callbacks.FriendAddedCallback
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.callbacks.PersonaStateCallback
import uk.co.thomasc.steamkit.steam3.steamclient.callbackmgr.CallbackMsg
import uk.co.thomasc.steamkit.types.steamid.SteamID
import uk.co.thomasc.steamkit.util.cSharp.events.ActionT

class FragmentFriends : FragmentBase(), OnClickListener, ChatReceiver, SearchView.OnQueryTextListener {
    private var recentChatThreshold = (2 * 24 * 60 * 60 * 1000).toLong() // 2 days
    var adapter: FriendsListAdapter? = null
    private lateinit var recyclerView: androidx.recyclerview.widget.RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (abort) {
            return
        }

        setHasOptionsMenu(true)
    }

    override fun onResume() {
        super.onResume()
        setTitle(getString(R.string.nav_friends))

        // update list of recent chats
        recentChatThreshold = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(activity()).getString("pref_recent_chats", "48")!!).toLong()
        recentChatThreshold *= (60 * 60 * 1000).toLong() // hours -> millis
        if (SteamService.singleton != null && SteamService.singleton!!.chatManager != null) {
            val recentChats = SteamService.singleton!!.chatManager!!.getRecentChats(recentChatThreshold)
            adapter!!.updateRecentChats(recentChats)
            adapter!!.notifyDataSetChanged() // just to make sure
            SteamService.singleton!!.chatManager!!.receivers.add(this)
        }
    }

    override fun handleSteamMessage(msg: CallbackMsg) {
        msg.handle(FriendAddedCallback::class.java, object : ActionT<FriendAddedCallback>() {
            override fun call(obj: FriendAddedCallback) {
                if (adapter!!.hasUserID(obj.steamID)) {
                    adapter!!.update(obj.steamID)
                } else {
                    adapter!!.add(obj.steamID)
                }
            }
        })
        msg.handle(PersonaStateCallback::class.java, object : ActionT<PersonaStateCallback>() {
            override fun call(obj: PersonaStateCallback) {
                val id = obj.friendID
                if (adapter!!.hasUserID(id))
                    adapter!!.update(id)
                else
                    adapter!!.add(id)

                adapter!!.notifyDataSetChanged()
            }
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        val view = inflater.inflate(R.layout.fragment_friends, container, false)
        // set up the recycler view
        recyclerView = view.findViewById(R.id.friends_list)
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(activity())
        recyclerView.addItemDecoration(AndroidUtil.RecyclerViewSpacer(12))

        val hideBlockedUsers = PreferenceManager.getDefaultSharedPreferences(activity()).getBoolean("pref_hide_blocked_users", true)
        adapter = FriendsListAdapter(activity()!!.applicationContext, this, null, true, hideBlockedUsers)
        recyclerView.adapter = adapter

        return view
    }

    override fun onPause() {
        super.onPause()

        if (SteamService.singleton != null && SteamService.singleton!!.chatManager != null)
            SteamService.singleton!!.chatManager!!.receivers.remove(this)
    }

    override//FriendsList menu onCreate. Search and Add Friend.
    fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        inflater!!.inflate(R.menu.fragment_friends, menu)
        val searchView = menu!!.findItem(R.id.action_search).actionView as SearchView
        searchView.setOnQueryTextListener(this)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item!!.itemId == R.id.menu_friends_add_friend) {
            val alert = AlertDialog.Builder(activity()!!)
            alert.setTitle(R.string.friend_add)
            alert.setMessage(R.string.friend_add_prompt)
            val input = EditText(activity())
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            alert.setView(input)
            alert.setPositiveButton(R.string.add) { _, _ ->
                try {
                    val value = java.lang.Long.parseLong(input.text.toString())
                    activity()!!.steamFriends.addFriend(SteamID(value))
                } catch (e: NumberFormatException) {
                    activity()!!.steamFriends.addFriend(input.text.toString())
                }
            }
            alert.setNegativeButton(android.R.string.cancel) { _, _ -> }
            val dialog = alert.show()
            val messageView = dialog.findViewById<TextView>(android.R.id.message)
            if (messageView != null)
                messageView.gravity = Gravity.CENTER
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onClick(v: View) {
        if (v.id == R.id.friend_chat_button) {
            val id = v.tag as SteamID
            if (activity()!!.steamFriends.getFriendRelationship(id) == EFriendRelationship.Friend) {
                val fragment = FragmentChat()
                val bundle = Bundle()
                bundle.putLong("steamId", id.convertToLong())
                fragment.arguments = bundle
                activity()!!.browseToFragment(fragment, true)
            }
        }
        if (v.id == R.id.friend_request_accept) {
            val id = v.tag as SteamID
            activity()!!.steamFriends.addFriend(id)
            // accepted friend request
            Toast.makeText(activity(), R.string.friend_request_accept, Toast.LENGTH_SHORT).show()
        }
        if (v.id == R.id.friend_request_reject) {
            val id = v.tag as SteamID
            activity()!!.steamFriends.ignoreFriend(id, false)
            // ignored friend request
            Toast.makeText(activity(), R.string.friend_request_ignore, Toast.LENGTH_SHORT).show()
        }
        if (v.id == R.id.friends_list_item) {
            val id = v.tag as SteamID
            val fragment = FragmentProfile()
            val bundle = Bundle()
            bundle.putLong("steamId", id.convertToLong())
            fragment.arguments = bundle
            activity()!!.browseToFragment(fragment, true)
        }
    }

    override fun receiveChatLine(time: Long, id_us: SteamID, id_them: SteamID, sent_by_us: Boolean, type: Int, message: String): Boolean {
        if (activity() != null) {
            if (!sent_by_us && type == SteamChatManager.CHAT_TYPE_CHAT) {
                activity()!!.runOnUiThread {
                    if (adapter != null) {
                        if (adapter!!.recentChats != null) {
                            adapter!!.recentChats!!.remove(id_them)
                            adapter!!.recentChats!!.add(0, id_them)
                        }
                        adapter!!.update(id_them)
                    }
                }
            }
        }
        return false
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        adapter!!.filter(query)
        return true
    }

    override fun onQueryTextChange(newText: String): Boolean {
        adapter!!.filter(newText)
        return true
    }
}
package com.aegamesi.steamtrade.fragments.adapters

import android.content.Context
import android.graphics.PorterDuff.Mode
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.aegamesi.steamtrade.R
import com.aegamesi.steamtrade.libs.AndroidUtil
import com.aegamesi.steamtrade.steam.SteamService
import com.aegamesi.steamtrade.steam.SteamUtil
import com.bumptech.glide.Glide
import de.hdodenhof.circleimageview.CircleImageView
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EFriendRelationship
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EPersonaState
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.SteamFriends
import uk.co.thomasc.steamkit.types.steamid.SteamID
import java.util.*

class FriendsListAdapter(private val context: Context,
                         private val clickListener: View.OnClickListener,
                         friendsList: List<SteamID>?,
                         private val hasSections: Boolean,
                         private val hideBlockedUsers: Boolean) : RecyclerView.Adapter<ViewHolder>() {

    var recentChats: MutableList<SteamID>? = null
    private var filteredDataset: MutableList<FriendListItem>? = null
    private var dataset: MutableList<FriendListItem>? = null
    private var categoryCounts: MutableMap<FriendListCategory, Int>? = null
    private var filter: String? = null

    private var steamFriends: SteamFriends


    companion object {
        private const val AVATAR_URL_BASE = "http://media.steampowered.com/steamcommunity/public/images/avatars/"
        private const val AVATAR_ALL_ZEROS = "0000000000000000000000000000000000000000"
    }

    init {
        var listFriends = friendsList

        steamFriends = SteamService.singleton!!.steamClient!!.getHandler<SteamFriends>(SteamFriends::class.java)

        if (listFriends == null && SteamService.singleton != null && SteamService.singleton!!.steamClient != null) {
            val steamFriends = SteamService.singleton!!.steamClient!!.getHandler<SteamFriends>(SteamFriends::class.java)

            if (steamFriends != null)
                listFriends = steamFriends.friendList
        }

        createList(listFriends)
    }

    private fun createList(listFriends: List<SteamID>?) {
        dataset = ArrayList()
        filteredDataset = ArrayList()

        // initialize category count list
        categoryCounts = HashMap()
        for (category in FriendListCategory.values()) {
            categoryCounts!![category] = 0
        }

        // populate friends list
        updateList(listFriends!!)

        if (hasSections) {
            // add section headers
            for ((key, value) in categoryCounts!!) {
                if (hideBlockedUsers && key == FriendListCategory.BLOCKED)
                    continue

                if (value > 0) {
                    dataset!!.add(FriendListItem(key))
                }
            }

            (dataset as ArrayList<FriendListItem>).sort()
        }

        notifyDataSetChanged()
    }

    private fun updateList(listFriends: List<SteamID>) {
        for (id in listFriends) {
            val item = FriendListItem(id)

            if (item.category == FriendListCategory.BLOCKED && hideBlockedUsers) {
                continue
            }

            dataset!!.add(item)
            categoryCounts!![item.category!!] = categoryCounts!![item.category!!]!! + 1
        }
    }

    fun hasUserID(id: SteamID): Boolean {
        for (item in dataset!!)
            if (id == item.steamID)
                return true

        return false
    }

    fun add(id: SteamID) {
        val newItem = FriendListItem(id)
        val pos = determineItemPosition(newItem)

        dataset!!.add(pos, newItem)

        notifyItemInserted(pos)
        incrementCategoryCount(newItem.category)
    }

    private fun incrementCategoryCount(category: FriendListCategory?) {
        if (!hasSections)
            return

        if (hideBlockedUsers && category == FriendListCategory.BLOCKED)
            return

        var categoryCount = categoryCounts!![category]!!
        categoryCounts!![category!!] = ++categoryCount

        if (categoryCount == 1) {
            val item = FriendListItem(category)
            val pos = determineItemPosition(item)
            dataset!!.add(pos, item)
            notifyItemInserted(pos)
        }
    }

    private fun deincrementCategoryCount(category: FriendListCategory?) {

        if (!hasSections || hideBlockedUsers && category == FriendListCategory.BLOCKED)
            return

        var categoryCount = categoryCounts!![category]!!
        categoryCounts!![category!!] = --categoryCount

        if (categoryCount == 0) {
            var position = -1
            for (i in dataset!!.indices) {
                if (dataset!![i].steamID == null && dataset!![i].category == category) {
                    position = i
                    break
                }
            }

            if (position != -1) {
                dataset!!.removeAt(position)
                notifyItemRemoved(position)
            }
        }
    }

    private fun determineItemPosition(item: FriendListItem): Int {
        for (i in dataset!!.indices) {
            if (item.compareTo(dataset!![i]) < 1)
                return i
        }
        return dataset!!.size
    }

    fun updateRecentChats(newList: MutableList<SteamID>) {
        val oldList = recentChats
        recentChats = newList

        if (oldList != null) {
            for (id in oldList)
                if (newList.contains(id) != oldList.contains(id))
                    update(id)
            for (id in newList)
                if (newList.contains(id) != oldList.contains(id))
                    update(id)
        } else {
            for (id in newList)
                update(id)
        }
    }

    fun update(id: SteamID) {
        var position = -1
        for (i in dataset!!.indices) {
            if (dataset!![i].steamID != null && dataset!![i].steamID == id) {
                position = i
                break
            }
        }

        if (position != -1) {
            val item = dataset!![position]
            val oldCategory = item.category

            item.update()
            dataset!!.remove(item)

            val newPosition = determineItemPosition(item)
            dataset!!.add(newPosition, item)

            if (newPosition != position)
                notifyItemMoved(position, newPosition)

            Log.i("FriendsListAdapter", "Item (" + item.steamID + "|"
                    + item.name + ") updated from " + position + " to " + newPosition)

            notifyItemChanged(newPosition)

            if (oldCategory != item.category) {
                deincrementCategoryCount(oldCategory)
                incrementCategoryCount(item.category)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == 0) ViewHolderSection(parent) else ViewHolderFriend(parent)
    }

    override fun onBindViewHolder(h: ViewHolder, position: Int) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        val list = if (filter == null) dataset else filteredDataset
        val p = list!![position]
        val type = getItemViewType(position)

        if (type == 0) { // section
            val holder = h as ViewHolderSection

            // if the view below this one isn't the same category (or doesn't exist), this category is empty.
            holder.textTitle.text = p.category!!.text
        }
        if (type == 1) { // friend
            val holder = h as ViewHolderFriend
            val resources = holder.itemView.context.resources
            holder.itemView.tag = p.steamID

            /* DO Populate View */
            if (p.nickname == null) {
                holder.textName.text = p.name
            } else {
                val nick = resources.getString(R.string.friend_holder_nickname, p.name, p.nickname)
                holder.textName.text = nick
            }

            if (p.game != null && p.game!!.isNotEmpty()) {
                // Is playing game?
                val playing = resources.getString(R.string.friend_holder_playing, p.game)
                holder.textStatus.text = playing
            } else if (p.category == FriendListCategory.BLOCKED) {
                // Is blocked?
                holder.textStatus.setText(R.string.friend_holder_blocked)
            } else if (p.category == FriendListCategory.FRIENDREQUEST) {
                // Is friend request?
                holder.textStatus.setText(R.string.friend_holder_request)
            } else if (p.state == EPersonaState.Offline && p.lastOnline != 0L) {
                // Is offline?
                val date = DateUtils.getRelativeTimeSpanString(p.lastOnline, System.currentTimeMillis(), 0, DateUtils.FORMAT_ABBREV_RELATIVE)
                val lastOnline = resources.getString(R.string.friend_holder_lastOnline, date)
                holder.textStatus.text = lastOnline
            } else {
                // All else list their state.
                holder.textStatus.text = p.state!!.toString()
            }

            /* Set player colors, default online|blue*/
            var color = resources.getColor(R.color.steam_online, null)

            if (p.category == FriendListCategory.BLOCKED) {
                // Are they blocked?
                color = resources.getColor(R.color.steam_blocked, null)
            } else if (p.game != null && p.game!!.isNotEmpty()) {
                // Are they in-game?
                color = resources.getColor(R.color.steam_game, null)
            } else if (p.state == EPersonaState.Offline || p.state == null) {
                // Are they offline?
                color = resources.getColor(R.color.steam_offline, null)
            }

            // Apply the colors.
            holder.textName.setTextColor(color)
            holder.textStatus.setTextColor(color)
            holder.imageAvatar.borderColor = color

            if (hasSections) {
                // friend request buttons
                holder.buttonAccept.visibility = if (p.category == FriendListCategory.FRIENDREQUEST) View.VISIBLE else View.GONE
                holder.buttonReject.visibility = if (p.category == FriendListCategory.FRIENDREQUEST) View.VISIBLE else View.GONE

                // friend chat buttons
                holder.buttonChat.visibility = if (p.relationship == EFriendRelationship.Friend) View.VISIBLE else View.GONE

                if (SteamService.singleton!!.chatManager!!.unreadMessages.contains(p.steamID)) {
                    holder.buttonChat.setColorFilter(resources.getColor(R.color.steam_online, null), Mode.MULTIPLY)
                    holder.buttonChat.setImageResource(R.drawable.ic_comment_processing)
                } else {
                    holder.buttonChat.setColorFilter(resources.getColor(R.color.steam_offline, null), Mode.MULTIPLY)
                    holder.buttonChat.setImageResource(R.drawable.ic_comment)
                }

                holder.buttonChat.tag = p.steamID
                holder.buttonChat.setOnClickListener(clickListener)
                holder.buttonChat.isFocusable = false
                holder.buttonAccept.tag = p.steamID
                holder.buttonAccept.setOnClickListener(clickListener)
                holder.buttonAccept.isFocusable = false
                holder.buttonReject.tag = p.steamID
                holder.buttonReject.setOnClickListener(clickListener)
                holder.buttonReject.isFocusable = false
            } else {
                holder.buttonChat.visibility = View.GONE
                holder.buttonAccept.visibility = View.GONE
                holder.buttonReject.visibility = View.GONE
            }

            holder.imageAvatar.setImageResource(R.drawable.default_avatar)
            if (p.avatarUrl != null) {
                Glide.with(context)
                        .load(p.avatarUrl)
                        .into(holder.imageAvatar)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val item = (if (filter == null) dataset else filteredDataset)!![position]
        return if (item.steamID == null) 0 /* section */ else 1 /* friend */
    }

    override fun getItemCount(): Int {
        return if (filter == null) dataset!!.size else filteredDataset!!.size
    }

    fun filter(filter: String?) {
        var filterText = filter
        if (filterText == null || filterText.trim { it <= ' ' }.isEmpty()) {
            filterText = null
        }

        if (filterText != null) {
            filterText = filterText.toLowerCase()
            filteredDataset!!.clear()
            for (item in dataset!!) {
                if (item.name == null || item.name!!.toLowerCase().contains(filterText) || item.nickname != null && item.nickname!!.toLowerCase().contains(filterText)) {
                    filteredDataset!!.add(item)
                }
            }
        }

        if (filterText == null && this.filter != null || filterText != null && this.filter == null || filterText != null && !filterText.equals(this.filter!!, ignoreCase = true)) {
            this.filter = filterText
            notifyDataSetChanged()
        }
    }

    enum class FriendListCategory(val order: Int, val text: String) {
        FRIENDREQUEST(0, "Friend Requests"),
        RECENTCHAT(1, "Recent Chats"),
        INGAME(2, "In-Game"),
        ONLINE(3, "Online"),
        OFFLINE(4, "Offline"),
        REQUESTPENDING(5, "Pending Friend Requests"),
        BLOCKED(6, "Blocked");

        override fun toString(): String {
            return text
        }
    }

    inner class FriendListItem : Comparable<FriendListItem> {
        var category: FriendListCategory? = null
        var state: EPersonaState? = null
        var relationship: EFriendRelationship? = null
        var steamID: SteamID? = null
        var name: String? = null
        var game: String? = null
        var avatarUrl: String? = null
        var nickname: String? = null
        var lastOnline = 0L

        constructor(steamID: SteamID) {
            this.steamID = steamID
            update()
        }

        internal constructor(category: FriendListCategory) {
            this.category = category
        }

        internal fun update() {

            relationship = steamFriends.getFriendRelationship(steamID)
            state = steamFriends.getFriendPersonaState(steamID)
            name = steamFriends.getFriendPersonaName(steamID)
            nickname = steamFriends.getFriendNickname(steamID)
            lastOnline = steamFriends.getFriendLastLogoff(steamID) * 1000L // convert to millis

            game = if (steamFriends.getFriendGamePlayed(steamID).toString().contains("-")) {
                steamFriends.getFriendGamePlayedName(steamID)
            } else {
                SteamService.singleton!!.gameData[steamID]
            }

            category = findCategory()

            if (steamID != null && steamFriends.getFriendAvatar(steamID) != null) {
                val imgHash = SteamUtil.bytesToHex(steamFriends.getFriendAvatar(steamID)).toLowerCase(Locale.US)
                if (imgHash != AVATAR_ALL_ZEROS && imgHash.length == 40)
                    avatarUrl = AVATAR_URL_BASE + imgHash.substring(0, 2) + "/" + imgHash + "_medium.jpg"
            }
        }

        private fun findCategory(): FriendListCategory {
            if (recentChats != null && recentChats!!.contains(steamID) || SteamService.singleton!!.chatManager!!.unreadMessages.contains(steamID))
                return FriendListCategory.RECENTCHAT
            if (relationship == EFriendRelationship.Blocked ||
                    relationship == EFriendRelationship.Ignored || relationship == EFriendRelationship.IgnoredFriend)
                return FriendListCategory.BLOCKED
            if (relationship == EFriendRelationship.RequestRecipient)
                return FriendListCategory.FRIENDREQUEST
            if (relationship == EFriendRelationship.RequestInitiator)
                return FriendListCategory.REQUESTPENDING
            return if (relationship == EFriendRelationship.Friend && state != EPersonaState.Offline) {
                if (game != null && game!!.isNotEmpty())
                    FriendListCategory.INGAME
                else
                    FriendListCategory.ONLINE
            } else FriendListCategory.OFFLINE
        }

        override fun compareTo(other: FriendListItem): Int {
            var compare = AndroidUtil.numCompare(this.category!!.order.toLong(), other.category!!.order.toLong())
            if (compare != 0) return compare

            // these two statements are for separators
            if (this.steamID == null && other.steamID != null) return -1
            if (this.steamID != null && other.steamID == null) return 1

            // next, sort recent chats by time, not alphabetically
            if (category == FriendListCategory.RECENTCHAT && recentChats != null) {
                val aPosition = recentChats!!.indexOf(steamID)
                val bPosition = recentChats!!.indexOf(other.steamID)
                return AndroidUtil.numCompare(aPosition.toLong(), bPosition.toLong())
            }
            // sort offline friends by last time online
            if (category == FriendListCategory.OFFLINE) {
                return -AndroidUtil.numCompare(lastOnline, other.lastOnline)
            }

            compare = this.name!!.toLowerCase(Locale.getDefault()).compareTo(other.name!!.toLowerCase(Locale.getDefault()))
            return compare
        }
    }

    private inner class ViewHolderFriend (parent: ViewGroup) : RecyclerView.ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.fragment_friends_list_item, parent, false)) {
        val imageAvatar: CircleImageView
        val textName: TextView
        val textStatus: TextView
        val buttonChat: ImageButton
        val buttonAccept: ImageButton
        val buttonReject: ImageButton

        init {
            itemView.setOnClickListener(clickListener)

            imageAvatar = itemView.findViewById(R.id.friend_avatar_left)
            textName = itemView.findViewById(R.id.friend_name)
            textStatus = itemView.findViewById(R.id.friend_status)
            buttonChat = itemView.findViewById(R.id.friend_chat_button)
            buttonAccept = itemView.findViewById(R.id.friend_request_accept)
            buttonReject = itemView.findViewById(R.id.friend_request_reject)
        }
    }

    private inner class ViewHolderSection (parent: ViewGroup) : RecyclerView.ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.fragment_friends_list_section, parent, false)) {
        val textTitle: TextView = itemView.findViewById(R.id.section_text)

    }
}

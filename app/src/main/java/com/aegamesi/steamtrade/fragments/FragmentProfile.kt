package com.aegamesi.steamtrade.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.aegamesi.steamtrade.R
import com.aegamesi.steamtrade.libs.GlideImageGetter
import com.aegamesi.steamtrade.steam.SteamService
import com.aegamesi.steamtrade.steam.SteamUtil
import com.aegamesi.steamtrade.steam.SteamWeb
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.fragment_profile.*
import org.json.JSONException
import org.json.JSONObject
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EFriendRelationship
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EPersonaState
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EResult
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.callbacks.IgnoreFriendCallback
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.callbacks.PersonaStateCallback
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.callbacks.ProfileInfoCallback
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.callbacks.SteamLevelCallback
import uk.co.thomasc.steamkit.steam3.steamclient.callbackmgr.CallbackMsg
import uk.co.thomasc.steamkit.types.steamid.SteamID
import uk.co.thomasc.steamkit.util.cSharp.events.ActionT
import java.util.*
import java.util.regex.Pattern

class FragmentProfile : FragmentBase(), View.OnClickListener {
    var id: SteamID? = null
    var steamLevel = -1
    private lateinit var relationship: EFriendRelationship
    private var state: EPersonaState? = null
    lateinit var name: String
    private var game: String? = null
    private var avatar: String? = null
    
    var profileInfo: ProfileInfoCallback? = null
    var personaInfo: PersonaStateCallback? = null

    companion object {
        private const val AVATAR_BASE_URL = "http://media.steampowered.com/steamcommunity/public/images/avatars/"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (abort) return

        Log.i("FragmentProfile", "created")

        if (arguments!!.containsKey("steamId")) {
            id = SteamID(arguments!!.getLong("steamId"))
        } else {
            val url = arguments!!.getString("url")

            if (url != null) {
                var matcher = Pattern.compile("steamcommunity.com/id/([a-zA-Z0-9]+)").matcher(url)
                if (matcher.find()) {
                    id = null
                    val vanity = matcher.group(1)
                    ResolveVanityURLTask().execute(vanity)
                } else {
                    matcher = Pattern.compile("steamcommunity.com/profiles/([0-9]+)").matcher(url)
                    if (matcher.find())
                        id = SteamID(java.lang.Long.parseLong(matcher.group(1)))
                    else
                        id = null
                }
            }
        }

    }

    override fun handleSteamMessage(msg: CallbackMsg) {
        msg.handle(SteamLevelCallback::class.java, object : ActionT<SteamLevelCallback>() {
            override fun call(obj: SteamLevelCallback) {
                if (id != null && obj.levelMap.containsKey(id)) {
                    steamLevel = obj.levelMap[id]!!
                    updateView()
                }
            }
        })
        msg.handle(ProfileInfoCallback::class.java, object : ActionT<ProfileInfoCallback>() {
            override fun call(obj: ProfileInfoCallback) {
                profileInfo = obj
                updateView()
            }
        })
        msg.handle(PersonaStateCallback::class.java, object : ActionT<PersonaStateCallback>() {
            override fun call(obj: PersonaStateCallback) {
                if (id != null && id == obj.friendID) {
                    personaInfo = obj
                    updateView()
                }
            }
        })
        msg.handle(IgnoreFriendCallback::class.java, object : ActionT<IgnoreFriendCallback>() {
            override fun call(obj: IgnoreFriendCallback) {
                val success = obj.result == EResult.OK
                val stringResource = if (success) R.string.action_successful else R.string.action_failed
                Toast.makeText(activity(), stringResource, Toast.LENGTH_SHORT).show()
                updateView()
            }
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        profile_button_chat.setOnClickListener(this)
        profile_button_remove_friend.setOnClickListener(this)
        profile_button_add_friend.setOnClickListener(this)
        profile_button_viewsteam.setOnClickListener(this)
        profile_button_viewsteamrep.setOnClickListener(this)
        profile_button_block_friend.setOnClickListener(this)
        profile_button_unblock_friend.setOnClickListener(this)
        profile_button_library.setOnClickListener(this)

        profile_name.isSelected = true
        profile_status.isSelected = true
        profile_summary.movementMethod = LinkMovementMethod.getInstance()

        updateView()
    }

    override fun onStart() {
        super.onStart()
        requestInfo()
    }

    fun updateView() {
        if (activity() == null)
            return

        relationship = activity()!!.steamFriends.getFriendRelationship(id)
        if (relationship == EFriendRelationship.Friend || personaInfo == null) {
            state = activity()!!.steamFriends.getFriendPersonaState(id)
            relationship = activity()!!.steamFriends.getFriendRelationship(id)
            name = activity()!!.steamFriends.getFriendPersonaName(id)
            game = activity()!!.steamFriends.getFriendGamePlayedName(id)
            avatar = SteamUtil.bytesToHex(activity()!!.steamFriends.getFriendAvatar(id)).toLowerCase(Locale.US)
        } else {
            // use the found persona info stuff
            state = personaInfo!!.state
            relationship = activity()!!.steamFriends.getFriendRelationship(id)
            name = personaInfo!!.name
            game = personaInfo!!.gameName
            avatar = SteamUtil.bytesToHex(personaInfo!!.avatarHash).toLowerCase(Locale.US)
        }

        profile_button_add_friend.setText(if (relationship == EFriendRelationship.RequestRecipient) R.string.friend_accept else R.string.friend_add)
        profile_button_unblock_friend.setText(if (relationship == EFriendRelationship.RequestRecipient) R.string.friend_ignore else R.string.friend_unblock)

        if (profileInfo != null) {
            val summaryRaw = profileInfo!!.summary
            val summary = SteamUtil.parseBBCode(summaryRaw)
            val imageGetter = GlideImageGetter(profile_summary, profile_summary.context)
            profile_summary.text = Html.fromHtml(summary, Html.FROM_HTML_MODE_LEGACY, imageGetter, null)
            profile_summary.movementMethod = LinkMovementMethod()
        }

        if (steamLevel == -1) {
            profile_level.setText(R.string.unknown)
        } else {
            profile_level.text = steamLevel.toString()
        }

        setTitle(name)
        profile_name.text = name

        Glide.with(activity()!!.applicationContext)
                .load(AVATAR_BASE_URL + avatar!!.substring(0, 2) + "/" + avatar + "_full.jpg")
                .placeholder(R.drawable.default_avatar)
                .error(R.drawable.default_avatar)
                .into(profile_avatar)

        if (game!!.isEmpty())
            profile_status.text = String.format(getString(R.string.profile_playing_game), game)
        else
            profile_status.text = state!!.toString()

        var color = ContextCompat.getColor(activity()!!.applicationContext, R.color.steam_online)
        if (relationship == EFriendRelationship.Blocked || relationship == EFriendRelationship.Ignored || relationship == EFriendRelationship.IgnoredFriend)
            color = ContextCompat.getColor(activity()!!.applicationContext, R.color.steam_blocked)
        else if (game != null && game!!.isNotEmpty())
            color = ContextCompat.getColor(activity()!!.applicationContext, R.color.steam_game)
        else if (state == EPersonaState.Offline || state == null)
            color = ContextCompat.getColor(activity()!!.applicationContext, R.color.steam_offline)

        profile_name.setTextColor(color)
        profile_status.setTextColor(color)
        profile_avatar.borderColor = color

        // things to do if we are not friends
        val isFriend = relationship == EFriendRelationship.Friend || relationship == EFriendRelationship.IgnoredFriend
        val isSelf = SteamService.singleton!!.steamClient!!.steamId == id
        val isBlocked = relationship == EFriendRelationship.Blocked || relationship == EFriendRelationship.Ignored || relationship == EFriendRelationship.IgnoredFriend
        val isFriendRequest = relationship == EFriendRelationship.RequestRecipient

        if (!isFriend) {
            profile_status.text = relationship.toString()
            profile_button_add_friend.isEnabled = id != null
        }

        //Fix relationship if you view yourself.
        if (relationship == EFriendRelationship.None && isSelf)
            profile_status.text = state!!.toString()

        // visibility of buttons and stuff
        profile_button_add_friend.visibility = if (!isFriend && !isSelf && !isBlocked) View.VISIBLE else View.GONE
        profile_button_remove_friend.visibility = if (isFriend && !isSelf) View.VISIBLE else View.GONE
        profile_button_chat.visibility = if (isFriend && !isSelf && !isBlocked) View.VISIBLE else View.GONE
        profile_button_block_friend.visibility = if (!isBlocked && !isSelf) View.VISIBLE else View.GONE
        profile_button_unblock_friend.visibility = if (isFriendRequest || isBlocked) View.VISIBLE else View.GONE
    }

    private fun requestInfo() {
        if (id != null) {
            if (profileInfo == null) {
                activity()!!.steamFriends.requestProfileInfo(id!!)
            }

            val requestFlags = 1 or 2 or 4 or 8 or 16 or 32 or 64 or 128 or 256 or 512 or 1024
            relationship = activity()!!.steamFriends.getFriendRelationship(id)
            if (relationship != EFriendRelationship.Friend && personaInfo == null) {
                val myId = SteamService.singleton!!.steamClient!!.steamId
                if (id != myId) {
                    activity()!!.steamFriends.requestFriendInfo(id, requestFlags)
                }
            }

            if (steamLevel == -1) {
                activity()!!.steamFriends.requestSteamLevel(id)
            }
        }
    }

    override fun onClick(view: View) {
        if (view == profile_button_remove_friend) {
            val builder = AlertDialog.Builder(activity()!!)
            builder.setMessage(String.format(getString(R.string.friend_remove_message), activity()!!.steamFriends.getFriendPersonaName(id)))
            builder.setTitle(R.string.friend_remove)
            builder.setPositiveButton(android.R.string.ok) { _, _ ->
                activity()!!.steamFriends.removeFriend(this@FragmentProfile.id!!)
                Toast.makeText(activity(), String.format(getString(R.string.friend_removed), activity()!!.steamFriends.getFriendPersonaName(this@FragmentProfile.id)), Toast.LENGTH_LONG).show()
                activity()!!.browseToFragment(FragmentFriends(), true)
            }
            builder.setNegativeButton(android.R.string.cancel) { _, _ -> }
            builder.create().show()
        }
        if (view == profile_button_add_friend) {
            activity()!!.steamFriends.addFriend(id!!)
            Toast.makeText(activity(), R.string.friend_add_success, Toast.LENGTH_LONG).show()
        }
        if (view == profile_button_chat) {
            val fragment = FragmentChat()
            val bundle = Bundle()
            bundle.putLong("steamId", id!!.convertToLong())
            bundle.putBoolean("fromProfile", true)
            fragment.arguments = bundle
            activity()!!.browseToFragment(fragment, true)
        }
        if (view == profile_button_viewsteam) {
            val url = "http://steamcommunity.com/profiles/" + id!!.convertToLong()
            FragmentWeb.openPage(activity()!!, url, false)
        }
        if (view == profile_button_viewsteamrep) {
            val steamRepUrl = "http://steamrep.com/profiles/" + id!!.convertToLong() + "/"
            val browse = Intent(Intent.ACTION_VIEW, Uri.parse(steamRepUrl))
            startActivity(browse)
        }
        if (view == profile_button_block_friend) {
            activity()!!.steamFriends.ignoreFriend(id, true)
        }
        if (view == profile_button_unblock_friend) {
            activity()!!.steamFriends.ignoreFriend(id, false)
        }
        if (view == profile_button_library) {
            val fragment = FragmentLibrary()
            val bundle = Bundle()
            bundle.putLong("id", id!!.convertToLong())
            fragment.arguments = bundle
            activity()!!.browseToFragment(fragment, true)
        }
    }

    @SuppressLint("StaticFieldLeak")
    private inner class ResolveVanityURLTask : AsyncTask<String, Void, SteamID>() {
        override fun doInBackground(vararg args: String): SteamID? {
            val vanity = args[0]

            val apiUrl = "https://api.steampowered.com/ISteamUser/ResolveVanityURL/v1/?key=" + SteamUtil.webApiKey + "&format=json&vanityurl=" + vanity
            val response = SteamWeb.fetch(apiUrl, "GET", null, "")
            if (response.isEmpty())
                return null
            var responseObj: JSONObject
            try {
                responseObj = JSONObject(response)
                responseObj = responseObj.getJSONObject("response")
                val result = responseObj.getInt("success")
                if (result != 1)
                    return null

                val steamID = responseObj.getString("steamID")
                val longValue = java.lang.Long.parseLong(steamID)
                return SteamID(longValue)
            } catch (e: JSONException) {
                e.printStackTrace()
                return null
            }

        }

        override fun onPostExecute(id: SteamID) {
            this@FragmentProfile.id = id
            requestInfo()
            updateView()
        }
    }
}
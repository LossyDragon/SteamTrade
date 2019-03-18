package com.aegamesi.steamtrade.fragments

import android.os.Bundle
import android.preference.PreferenceManager
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

import com.aegamesi.steamtrade.R
import com.aegamesi.steamtrade.steam.AccountLoginInfo
import com.aegamesi.steamtrade.steam.SteamService
import com.bumptech.glide.Glide

import uk.co.thomasc.steamkit.base.generated.steamlanguage.EPersonaState
import uk.co.thomasc.steamkit.steam3.handlers.steamnotifications.callbacks.NotificationUpdateCallback
import uk.co.thomasc.steamkit.steam3.handlers.steamnotifications.types.NotificationType
import uk.co.thomasc.steamkit.steam3.steamclient.callbackmgr.CallbackMsg
import uk.co.thomasc.steamkit.util.cSharp.events.ActionT

class FragmentMe : FragmentBase(), OnClickListener, OnItemSelectedListener {
    private lateinit var avatarView: ImageView
    private lateinit var nameView: TextView
    private lateinit var statusSpinner: Spinner
    private lateinit var viewProfileButton: Button
    private lateinit var changeNameButton: Button
    private lateinit var buttonTwoFactor: Button

    private lateinit var notifyComments: TextView
    private lateinit var notifyChat: TextView

    private var states = intArrayOf(
            1, //online
            3, //away
            2, //busy
            4, //snooze
            5, //lookingtotrade
            6  //lookingtoplay
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (abort)
            return
        Log.i("FragmentMe", "created")
    }

    override fun handleSteamMessage(msg: CallbackMsg) {
        msg.handle(NotificationUpdateCallback::class.java, object : ActionT<NotificationUpdateCallback>() {
            override fun call(obj: NotificationUpdateCallback) {
                updateView()
            }
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        val view = inflater.inflate(R.layout.fragment_me, container, false)
        avatarView = view.findViewById(R.id.profile_avatar)
        nameView = view.findViewById(R.id.profile_name)
        statusSpinner = view.findViewById(R.id.profile_status_spinner)
        viewProfileButton = view.findViewById(R.id.me_view_profile)
        changeNameButton = view.findViewById(R.id.me_set_name)
        buttonTwoFactor = view.findViewById(R.id.me_two_factor)
        notifyChat = view.findViewById(R.id.me_notify_chat)
        notifyComments = view.findViewById(R.id.me_notify_comments)
        notifyChat.setOnClickListener(this)
        notifyComments.setOnClickListener(this)

        val adapter = ArrayAdapter.createFromResource(activity()!!, R.array.allowed_states, android.R.layout.simple_spinner_item)
        adapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item)
        statusSpinner.adapter = adapter
        statusSpinner.onItemSelectedListener = this
        changeNameButton.setOnClickListener(this)
        viewProfileButton.setOnClickListener(this)
        buttonTwoFactor.setOnClickListener(this)

        updateView()
        return view
    }

    fun updateView() {
        if (activity() == null)
            return
        if (SteamService.singleton == null || SteamService.singleton!!.steamClient == null)
            return

        val state = activity()!!.steamFriends.personaState
        val name = activity()!!.steamFriends.personaName
        val userName = SteamService.singleton!!.username
        val avatar = PreferenceManager.getDefaultSharedPreferences(activity).getString("avatar_" + userName!!, "null")


        setTitle(name)
        nameView.text = name
        statusSpinner.setSelection(stateToIndex(state))

        avatarView.setImageResource(R.drawable.default_avatar)
        Log.i("AvatarView", avatar)
        //FragmentMe profile icon.
        if (avatar != "null" || !avatar.contains("")) {
            Glide.with(activity()!!.applicationContext)
                    .load(avatar)
                    .error(R.drawable.default_avatar)
                    .into(avatarView)
        }

        nameView.setTextColor(ContextCompat.getColor(activity()!!.applicationContext, R.color.steam_online))

        updateNotification(notifyChat, R.plurals.notification_messages, NotificationType.OFFLINE_MSGS)
        updateNotification(notifyComments, R.plurals.notification_comments, NotificationType.COMMENTS)
    }

    private fun updateNotification(textView: TextView, plural: Int, type: NotificationType) {
        val num = activity()!!.steamNotifications.notificationCounts[type]!!
        val text = resources.getQuantityString(plural, num, num)
        textView.text = text
        textView.setTextColor(ContextCompat.getColor(activity()!!.applicationContext, if (num == 0) R.color.notification_text_off else R.color.notification_text_on))
    }

    private fun stateToIndex(state: EPersonaState): Int {
        for (i in states.indices)
            if (states[i] == state.v())
                return i
        return 0
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
        if (parent === statusSpinner) {
            activity()!!.steamFriends.personaState = EPersonaState.f(states[pos])
            updateView()
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>) {}

    override fun onClick(v: View) {
        if (v === viewProfileButton) {
            val fragment = FragmentProfile()
            val bundle = Bundle()
            bundle.putLong("steamId", SteamService.singleton!!.steamClient!!.steamId.convertToLong())
            fragment.arguments = bundle
            activity()!!.browseToFragment(fragment, true)
        }
        if (v === changeNameButton) {
            //SteamFriends f = activity().steamFriends;
            //f.requestFriendInfo(new SteamID(76561198000739785L));
            val alert = AlertDialog.Builder(activity()!!)
            alert.setTitle(R.string.change_display_name)
            alert.setMessage(R.string.change_display_name_prompt)
            val input = EditText(activity())
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            input.setText(activity()!!.steamFriends.personaName)
            alert.setView(input)
            alert.setPositiveButton(R.string.change) { _, _ ->
                val name = input.text.toString().trim { it <= ' ' }
                if (name.isNotEmpty()) {
                    activity()!!.steamFriends.personaName = name
                    updateView()
                }
            }
            alert.setNegativeButton(android.R.string.cancel) { _, _ -> }
            alert.show()
        }
        if (v === buttonTwoFactor) {
            val info = AccountLoginInfo.readAccount(activity()!!, SteamService.singleton!!.username!!)
            if (info?.loginkey == null || info.loginkey!!.isEmpty()) {
                Toast.makeText(activity(), R.string.steamguard_unavailable, Toast.LENGTH_LONG).show()
            } else {
                activity()!!.browseToFragment(FragmentSteamGuard(), true)
            }
        }
        if (v === notifyChat) {
            activity()!!.browseToFragment(FragmentFriends(), true)
        }
        if (v === notifyComments) {
            val myID = SteamService.singleton!!.steamClient!!.steamId.convertToLong()
            val url = "http://steamcommunity.com/profiles/$myID/commentnotifications"
            FragmentWeb.openPage(activity()!!, url, false)
        }
    }
}
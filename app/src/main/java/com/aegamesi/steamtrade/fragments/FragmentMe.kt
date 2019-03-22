package com.aegamesi.steamtrade.fragments

import android.os.Bundle
import android.preference.PreferenceManager
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.aegamesi.steamtrade.R
import com.aegamesi.steamtrade.steam.AccountLoginInfo
import com.aegamesi.steamtrade.steam.SteamService
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.fragment_me.*
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EPersonaState
import uk.co.thomasc.steamkit.steam3.handlers.steamnotifications.callbacks.NotificationUpdateCallback
import uk.co.thomasc.steamkit.steam3.handlers.steamnotifications.types.NotificationType
import uk.co.thomasc.steamkit.steam3.steamclient.callbackmgr.CallbackMsg
import uk.co.thomasc.steamkit.util.cSharp.events.ActionT

class FragmentMe : FragmentBase(), OnClickListener, AdapterView.OnItemSelectedListener {

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

        if (abort) return

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
        return inflater.inflate(R.layout.fragment_me, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val spinnerAdapter = ArrayAdapter(
                context!!,
                android.R.layout.simple_spinner_item,
                resources.getStringArray(R.array.allowed_states)
        )

        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_item)

        profile_status_spinner.adapter = spinnerAdapter
        profile_status_spinner.onItemSelectedListener = this

        me_notify_chat.setOnClickListener(this)
        me_notify_comments.setOnClickListener(this)
        me_set_name.setOnClickListener(this)
        me_view_profile.setOnClickListener(this)
        me_two_factor.setOnClickListener(this)

        updateView()
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
        profile_name.text = name
        profile_status_spinner.setSelection(stateToIndex(state))

        Glide.with(activity()!!.applicationContext)
                .load(avatar)
                .placeholder(R.drawable.default_avatar)
                .error(R.drawable.default_avatar)
                .into(profile_avatar)

        profile_name.setTextColor(ContextCompat.getColor(activity()!!.applicationContext, R.color.steam_online))

        updateNotification(me_notify_chat, R.plurals.notification_messages, NotificationType.OFFLINE_MSGS)
        updateNotification(me_notify_comments, R.plurals.notification_comments, NotificationType.COMMENTS)
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
            activity()!!.steamFriends.personaState = EPersonaState.f(states[pos])
            updateView()
    }

    override fun onNothingSelected(parent: AdapterView<*>) { /*Nothing*/ }

    override fun onClick(v: View) {
        if (v == me_view_profile) {
            val fragment = FragmentProfile()
            val bundle = Bundle()
            bundle.putLong("steamId", SteamService.singleton!!.steamClient!!.steamId.convertToLong())
            fragment.arguments = bundle
            activity()!!.browseToFragment(fragment, true)
        }
        if (v == me_set_name) {
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
        if (v == me_two_factor) {
            val info = AccountLoginInfo.readAccount(activity()!!, SteamService.singleton!!.username!!)
            if (info?.loginkey == null || info.loginkey!!.isEmpty()) {
                Toast.makeText(activity(), R.string.steamguard_unavailable, Toast.LENGTH_LONG).show()
            } else {
                activity()!!.browseToFragment(FragmentSteamGuard(), true)
            }
        }
        if (v == me_notify_chat) {
            activity()!!.browseToFragment(FragmentFriends(), true)
        }
        if (v == me_notify_comments) {
            val myID = SteamService.singleton!!.steamClient!!.steamId.convertToLong()
            val url = "http://steamcommunity.com/profiles/$myID/commentnotifications"
            FragmentWeb.openPage(activity()!!, url, false)
        }
    }
}
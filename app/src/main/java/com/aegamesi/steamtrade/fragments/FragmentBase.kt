package com.aegamesi.steamtrade.fragments

import android.content.Intent
import android.os.Bundle
import androidx.annotation.ColorInt
import androidx.fragment.app.Fragment
import com.aegamesi.steamtrade.MainActivity
import com.aegamesi.steamtrade.R
import com.aegamesi.steamtrade.steam.SteamMessageHandler
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.custom_toolbar.*
import uk.co.thomasc.steamkit.steam3.steamclient.callbackmgr.CallbackMsg

open class FragmentBase : Fragment(), SteamMessageHandler {
    protected var abort = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // make sure we're actually connected to steam...
        abort = !activity()!!.assertSteamConnection()
    }

    override fun onResume() {
        super.onResume()

        // make sure we're actually connected to steam...
        abort = !activity()!!.assertSteamConnection()
    }

    fun activity(): MainActivity? {
        return activity as MainActivity?
    }

    /* Toolbar Status text */
    fun setTitle(title: CharSequence) {
        if (activity() != null) {
            activity()!!.toolbar_status.text = title
        }
    }

    /* Toolbar CircleImageView visibility */
    fun setToolBarAvatar(visibility: Int) {
        if (activity() != null) {
            activity()!!.toolbar_icon_LL.visibility = visibility
        }
    }

    /* Toolbar CircleImageView set color */
    fun setToolBarIconColor(@ColorInt color: Int) {
        if (activity() != null) {
            activity()!!.toolbar_icon.borderColor = color
        }
    }

    /* Toolbar CircleImageView set picture */
    fun setToolBarPicture(source: String) {
        Glide.with(activity()!!.applicationContext)
                .load(source)
                .placeholder(R.drawable.default_avatar)
                .error(R.drawable.default_avatar)
                .into(activity()!!.toolbar_icon)
    }

    override fun handleSteamMessage(msg: CallbackMsg) { /*Nothing*/ }

    open fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent): Boolean {
        return false
    }
}

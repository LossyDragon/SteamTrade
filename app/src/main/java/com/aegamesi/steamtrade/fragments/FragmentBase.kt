package com.aegamesi.steamtrade.fragments

import android.content.Intent
import android.os.Bundle
import androidx.annotation.ColorInt
import androidx.fragment.app.Fragment
import com.aegamesi.steamtrade.MainActivity
import com.aegamesi.steamtrade.R
import com.aegamesi.steamtrade.steam.SteamMessageHandler
import com.bumptech.glide.Glide
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
            activity()!!.toolbarTextView.text = title
        }
    }

    /* Toolbar CircleImageView visibility */
    fun setToolBarAvatar(vis: Int) {
        if (activity() != null) {
            activity()!!.toolbarImageLayout.visibility = vis
        }
    }

    /* Toolbar CircleImageView set color */
    fun setToolBarIconColor(@ColorInt color: Int) {
        if (activity() != null) {
            activity()!!.toolbarImageView.borderColor = color
        }
    }

    /* Toolbar CircleImageView set picture */
    fun setToolBarPicture(uri: String) {
        Glide.with(activity()!!.applicationContext)
                .load(uri)
                .placeholder(R.drawable.default_avatar)
                .error(R.drawable.default_avatar)
                .into(activity()!!.toolbarImageView)
    }

    override fun handleSteamMessage(msg: CallbackMsg) { /*Nothing*/ }

    open fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent): Boolean {
        return false
    }
}

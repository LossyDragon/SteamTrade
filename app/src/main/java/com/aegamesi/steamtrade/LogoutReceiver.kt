package com.aegamesi.steamtrade

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

class LogoutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val activityIntent = Intent(context, MainActivity::class.java)
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val bundle = Bundle()
        bundle.putInt("logout", 1)

        activityIntent.putExtras(bundle)
        context.startActivity(activityIntent)
    }

}
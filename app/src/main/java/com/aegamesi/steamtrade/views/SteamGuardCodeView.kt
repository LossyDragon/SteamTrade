package com.aegamesi.steamtrade.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

import com.aegamesi.steamtrade.R
import com.aegamesi.steamtrade.libs.AndroidUtil
import com.aegamesi.steamtrade.steam.SteamTwoFactor

class SteamGuardCodeView : LinearLayout {
    var timeUntilNextCode: Double = 0.0
    private var textCode: TextView? = null
    private var progressBar: ProgressBar? = null
    private var sharedSecret: ByteArray? = null

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    fun setSharedSecret(shared_secret: ByteArray) {
        this.sharedSecret = shared_secret
    }

    private fun init() {
        View.inflate(context, R.layout.view_steamguard_code, this)
        this.textCode = findViewById(R.id.steamguard_code)
        this.progressBar = findViewById(R.id.steamguard_time)

        timeUntilNextCode = 10000.0
        progressBar!!.max = 3000
        post(object : Runnable {
            override fun run() {
                // update here, change code if necessary...
                if (sharedSecret != null) {
                    val validityTime = 30.0 - SteamTwoFactor.codeValidityTime

                    val progress = (3000.0 * (validityTime / 30.0)).toInt()
                    progressBar!!.progress = progress

                    val newCode = timeUntilNextCode > validityTime
                    timeUntilNextCode = validityTime
                    if (newCode) {
                        val time = SteamTwoFactor.currentTime
                        val authCode = SteamTwoFactor.generateAuthCodeForTime(sharedSecret!!, time)
                        textCode!!.text = authCode
                    }
                }

                postDelayed(this, 50)
            }
        })

        textCode!!.setOnLongClickListener { view ->
            AndroidUtil.copyToClipboard(view.context, textCode!!.text.toString())
            Toast.makeText(view.context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            true
        }
    }
}

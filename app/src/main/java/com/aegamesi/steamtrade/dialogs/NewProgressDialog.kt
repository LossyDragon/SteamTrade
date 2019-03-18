package com.aegamesi.steamtrade.dialogs

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.widget.TextView

import com.aegamesi.steamtrade.R

/**
 * Dialog popup showing logging-in status.
 */
class NewProgressDialog(context: Context) : AlertDialog(context) {

    private var message: CharSequence? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.view_progress_dialog)
        setCanceledOnTouchOutside(false)
        setMessage()
    }

    override fun setMessage(message: CharSequence) {
        this.message = message
        if (isShowing)
            setMessage()
    }

    private fun setMessage() {
        if (message != null && message!!.isNotEmpty()) {
            val textView = findViewById<TextView>(R.id.connection_status)
            textView.text = message
        }
    }

}

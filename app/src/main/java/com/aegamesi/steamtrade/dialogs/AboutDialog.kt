package com.aegamesi.steamtrade.dialogs

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.widget.TextView
import com.aegamesi.steamtrade.R

/**
 * Dialog popup containing about information.
 */
class AboutDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        Log.i(TAG, "--> created")
        @SuppressLint("InflateParams")
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_about, null)
        val textView = view.findViewById<TextView>(R.id.ice_text_about)
        textView.text = Html.fromHtml(getString(R.string.about_info), Html.FROM_HTML_MODE_COMPACT)
        textView.movementMethod = LinkMovementMethod.getInstance()

        return AlertDialog.Builder(activity)
                .setTitle(R.string.nav_about)
                .setView(view)
                .setPositiveButton(getString(android.R.string.ok), null)
                .create()
    }

    companion object {
        const val TAG = "AboutDialog"

        fun newInstance(): AboutDialog {
            return AboutDialog()
        }
    }


}

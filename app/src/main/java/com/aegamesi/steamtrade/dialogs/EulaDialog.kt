package com.aegamesi.steamtrade.dialogs


import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.fragment.app.DialogFragment
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.widget.TextView

import com.aegamesi.steamtrade.BuildConfig
import com.aegamesi.steamtrade.R

/**
 * Dialog popup containing EULA.
 */
class EulaDialog : DialogFragment() {

    private var versionCode: Int = 0

    fun shouldCreateDialog(context: Context): Boolean {
        // the eulaKey changes every time you
        // increment the 'versionCode' in build.gradle (app).
        versionCode = BuildConfig.VERSION_CODE
        val eulaKey = EULA_PREFIX + versionCode

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return !prefs.getBoolean(eulaKey, false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Log.i(TAG, "--> created")

        val eulaKey = EULA_PREFIX + versionCode
        val pref = PreferenceManager.getDefaultSharedPreferences(activity)

        @SuppressLint("InflateParams")
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_eula, null)
        val textView = view.findViewById<TextView>(R.id.eula_text)
        textView.text = Html.fromHtml(getString(R.string.EULA), Html.FROM_HTML_MODE_LEGACY)

        return AlertDialog.Builder(activity)
                .setTitle(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME)
                .setView(view)
                .setPositiveButton(android.R.string.ok) { dialogInterface, _ ->
                    val editor = pref.edit()
                    editor.putBoolean(eulaKey, true)
                    editor.apply()
                    dialogInterface.dismiss()
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    if (activity != null) {
                        activity!!.finish()
                    }
                }
                .setCancelable(false)
                .create()
    }

    companion object {
        private const val TAG = "EulaDialog"
        private const val EULA_PREFIX = "eula__ae-ice__"
    }
}
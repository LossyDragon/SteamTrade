package com.aegamesi.steamtrade.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import android.util.Log

import com.aegamesi.steamtrade.views.SteamGuardCodeView

/**
 * Dialog popup containing Enabled SteamGuard tokens.
 */
class SteamGuardDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        Log.i(TAG, "--> created")

        val codeView = SteamGuardCodeView(context!!)
        codeView.setSharedSecret(TFA_SHAREDSECRET!!)

        return AlertDialog.Builder(activity)
                .setView(codeView)
                .setPositiveButton(getString(android.R.string.ok), null)
                .create()
    }

    companion object {
        const val TAG = "AboutDialog"
        private var TFA_SHAREDSECRET: ByteArray? = null

        fun newInstance(tfa: ByteArray): SteamGuardDialog {
            TFA_SHAREDSECRET = tfa
            return SteamGuardDialog()
        }
    }
}

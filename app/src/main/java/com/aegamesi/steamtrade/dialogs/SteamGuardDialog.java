package com.aegamesi.steamtrade.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.util.Log;

import com.aegamesi.steamtrade.views.SteamGuardCodeView;

/**
 * Dialog popup containing Enabled SteamGuard tokens.
 **/
public class SteamGuardDialog extends DialogFragment {
    public final static String TAG = AboutDialog.class.getSimpleName();
    private static byte[] TFA_SHAREDSECRET;

    public static SteamGuardDialog newInstance(byte[] tfa) {
        TFA_SHAREDSECRET = tfa;
        return new SteamGuardDialog();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        Log.i(TAG, "--> created");

        SteamGuardCodeView codeView = new SteamGuardCodeView(getContext());
        codeView.setSharedSecret(TFA_SHAREDSECRET);

        return new AlertDialog.Builder(getActivity())
                .setView(codeView)
                .setPositiveButton(getString(android.R.string.ok), null)
                .create();
    }
}

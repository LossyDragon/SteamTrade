package com.aegamesi.steamtrade.dialogs;


import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.aegamesi.steamtrade.BuildConfig;
import com.aegamesi.steamtrade.R;

/**
 *  Dialog popup containing EULA.
 **/
public class EulaDialog extends DialogFragment {
    public final static String TAG = EulaDialog.class.getSimpleName();
    private String EULA_PREFIX = "eula__ae-ice__";
    private int VERSION_CODE;

    public boolean shouldCreateDialog(Context context) {
        // the eulaKey changes every time you
        // increment the 'versionCode' in build.gradle (app).
        VERSION_CODE = BuildConfig.VERSION_CODE;
        final String eulaKey = EULA_PREFIX + VERSION_CODE;

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return !prefs.getBoolean(eulaKey, false);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Log.i(TAG, "--> created");

        final String eulaKey = EULA_PREFIX + VERSION_CODE;
        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());

        @SuppressLint("InflateParams")
        final View view = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_eula, null);
        TextView textView = view.findViewById(R.id.eula_text);
        textView.setText(Html.fromHtml(getString(R.string.EULA), Html.FROM_HTML_MODE_LEGACY));

        return new AlertDialog.Builder(getActivity())
                .setTitle(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME)
                .setView(view)
                .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                    SharedPreferences.Editor editor = pref.edit();
                    editor.putBoolean(eulaKey, true);
                    editor.apply();
                    dialogInterface.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> {
                    if (getActivity() != null) {
                        getActivity().finish();
                    }
                })
                .setCancelable(false)
                .create();
    }
}
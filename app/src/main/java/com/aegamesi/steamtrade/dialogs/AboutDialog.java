package com.aegamesi.steamtrade.dialogs;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.aegamesi.steamtrade.R;

/**
 *  Dialog popup containing about information.
 **/
public class AboutDialog extends DialogFragment {
    public final static String TAG = AboutDialog.class.getSimpleName();

    public static AboutDialog newInstance() {
        return new AboutDialog();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        Log.i(TAG, "--> created");
        @SuppressLint("InflateParams")
        final View view = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_about, null);
        TextView textView = view.findViewById(R.id.ice_text_about);
        textView.setText(Html.fromHtml(getString(R.string.about_info), Html.FROM_HTML_MODE_COMPACT));
        textView.setMovementMethod(LinkMovementMethod.getInstance());

        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.nav_about)
                .setView(view)
                .setPositiveButton(getString(android.R.string.ok), null)
                .create();
    }


}

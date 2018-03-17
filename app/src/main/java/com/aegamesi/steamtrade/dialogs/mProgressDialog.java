package com.aegamesi.steamtrade.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.TextView;

import com.aegamesi.steamtrade.R;

/**
 * Dialog popup showing logging-in status.
 **/
public class mProgressDialog extends AlertDialog {

    private CharSequence message;

    public mProgressDialog(Context context){
        super(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.view_progress_dialog);
        setCanceledOnTouchOutside(false);
        setMessage();
    }

    @Override
    public void setMessage(CharSequence message) {
        this.message = message;
        if (isShowing())
            setMessage();
    }

    private void setMessage() {
        if (message != null && message.length() > 0) {
            TextView textView = findViewById(R.id.connection_status);
            textView.setText(message);
        }
    }

}

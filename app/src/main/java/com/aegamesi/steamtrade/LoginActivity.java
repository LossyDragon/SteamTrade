package com.aegamesi.steamtrade;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.aegamesi.steamtrade.dialogs.EulaDialog;
import com.aegamesi.steamtrade.dialogs.SteamGuardDialog;
import com.aegamesi.steamtrade.dialogs.mProgressDialog;
import com.aegamesi.steamtrade.steam.AccountLoginInfo;
import com.aegamesi.steamtrade.steam.SteamConnectionListener;
import com.aegamesi.steamtrade.steam.SteamService;
import com.aegamesi.steamtrade.steam.SteamTwoFactor;
import com.aegamesi.steamtrade.steam.SteamUtil;
import com.bumptech.glide.Glide;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.hdodenhof.circleimageview.CircleImageView;
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EResult;
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EUniverse;

import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;

public class LoginActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_LOAD_MAFILE = 48399;

    public static String username;
    public static String password;
    private boolean need_twofactor = false;

    private CheckBox rememberInfoCheckbox;
    private EditText textUsername;
    private EditText textPassword;
    private EditText textSteamguard;
    private TextInputLayout steamGuardField;
    private View viewSaved;
    private View viewNew;

    private ConnectionListener connectionListener = null;

    mProgressDialog progressDialog = null;
    private boolean active = false;

    /* LoginActivity setup*/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleLegacy();
        setContentView(R.layout.activity_login);

        if (getSupportActionBar() != null)
            getSupportActionBar().hide();

        /* Show the EULA */
        EulaDialog eulaDialog = new EulaDialog();
        if (eulaDialog.shouldCreateDialog(this))
            eulaDialog.show(getSupportFragmentManager(), "tag");

        connectionListener = new ConnectionListener();

        viewNew = findViewById(R.id.layout_new);
        viewSaved = findViewById(R.id.layout_saved);
        final Button headerNew = findViewById(R.id.btn_header_new);
        final Button headerSaved = findViewById(R.id.btn_header_saved);
        Button importAccount = findViewById(R.id.btn_import_account);

        /* OnClickListener for switching between New and Saved Account sections */
        OnClickListener cardListener = view -> {
            boolean isNew = view == headerNew;
            boolean isSaved = view == headerSaved;

            headerNew.setVisibility(isNew ? View.GONE : View.VISIBLE);
            viewNew.setVisibility(isNew ? View.VISIBLE : View.GONE);

            headerSaved.setVisibility(isSaved ? View.GONE : View.VISIBLE);
            viewSaved.setVisibility(isSaved ? View.VISIBLE : View.GONE);
        };

        /* New & Saved button Click Listeners */
        headerNew.setOnClickListener(cardListener);
        headerSaved.setOnClickListener(cardListener);

        RecyclerView accountsList = findViewById(R.id.accounts_list);

        AccountListAdapter accountListAdapter = new AccountListAdapter(this);
        accountsList.setAdapter(accountListAdapter);
        accountsList.setLayoutManager(new LinearLayoutManager(this));
        if (accountListAdapter.getItemCount() == 0) {
            // only show the new one
            cardListener.onClick(headerNew);
            headerSaved.setVisibility(View.GONE);
        } else {
            cardListener.onClick(headerSaved);
        }

        /* Login form preparation */
        rememberInfoCheckbox = (findViewById(R.id.remember));
        textSteamguard = findViewById(R.id.steamguard);
        textSteamguard.setVisibility(View.GONE);
        textUsername = findViewById(R.id.username);
        textPassword = findViewById(R.id.password);
        steamGuardField = findViewById(R.id.steamguard_field);
        steamGuardField.setVisibility(View.INVISIBLE);
        Button buttonSignIn = findViewById(R.id.sign_in_button);

        /* Keyboard Stuff */
        textPassword.setOnEditorActionListener((textView, id, keyEvent) -> {
            if (id == R.id.login || id == EditorInfo.IME_NULL) {
                attemptLogin();
                return true;
            }
            return false;
        });

        /* Show legacy information */
        if (accountListAdapter.getItemCount() == 0) {
            if (getPreferences(MODE_PRIVATE).getBoolean("rememberDetails", true)) {
                textUsername.setText(getPreferences(MODE_PRIVATE).getString("username", ""));
                textPassword.setText(getPreferences(MODE_PRIVATE).getString("password", ""));
            }
        }

        /* Click Listener for signing in */
        buttonSignIn.setOnClickListener(view -> attemptLogin());


        /* Click Listener for .maFile importing */
        importAccount.setOnClickListener(view -> SteamTwoFactor.promptForMafile(LoginActivity.this, REQUEST_CODE_LOAD_MAFILE));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_LOAD_MAFILE) {
            if (resultCode == Activity.RESULT_OK) {
                try {
                    StringBuilder b = new StringBuilder();
                    InputStream is = getContentResolver().openInputStream(data.getData());
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    String s;
                    while ((s = reader.readLine()) != null) {
                        b.append(s);
                        b.append("\n");
                    }

                    final AccountLoginInfo acc = new AccountLoginInfo();
                    acc.importFromJson(b.toString());
                    AccountLoginInfo existing_acc = AccountLoginInfo.readAccount(this, acc.tfa_accountName);
                    if (existing_acc != null) {
                        existing_acc.importFromJson(b.toString());
                        existing_acc.has_authenticator = true;
                        AccountLoginInfo.writeAccount(LoginActivity.this, existing_acc);
                        Toast.makeText(LoginActivity.this, R.string.action_successful, Toast.LENGTH_LONG).show();
                    } else {
                        acc.username = acc.tfa_accountName;
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setTitle(R.string.steamguard_mobile_authenticator);
                        builder.setMessage(String.format(getString(R.string.steamguard_import_password), acc.username));
                        builder.setCancelable(true);
                        final EditText passwordInput = new EditText(this);
                        builder.setView(passwordInput);
                        passwordInput.setInputType(TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_PASSWORD);
                        builder.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                            acc.password = passwordInput.getText().toString();
                            acc.has_authenticator = true;
                            AccountLoginInfo.writeAccount(LoginActivity.this, acc);
                            Toast.makeText(LoginActivity.this, R.string.action_successful, Toast.LENGTH_LONG).show();
                            recreate();
                        });
                        builder.show();
                    }

                } catch (IOException | JSONException | NumberFormatException e) {
                    e.printStackTrace();
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(R.string.error);
                    builder.setMessage(e.toString());
                    builder.setCancelable(true);
                    builder.setNeutralButton(android.R.string.ok, null);
                    builder.show();
                }
            }
        }
    }

    private void handleLegacy() {
        // from version 0.10.4 onwards:
        // convert from old account storage to new account storage
        Set<String> savedAccounts = new HashSet<>();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(LoginActivity.this);
        Map<String, ?> allPrefs = sharedPreferences.getAll();
        for (String key : allPrefs.keySet()) {
            if (key.startsWith("loginkey_"))
                savedAccounts.add(key.substring("loginkey_".length()));
            if (key.startsWith("password_"))
                savedAccounts.add(key.substring("password_".length()));
        }
        SharedPreferences.Editor editor = sharedPreferences.edit();
        for (String savedAccount : savedAccounts) {
            AccountLoginInfo loginInfo = new AccountLoginInfo();
            loginInfo.username = savedAccount;
            loginInfo.password = sharedPreferences.getString("password_" + savedAccount, null);
            loginInfo.loginkey = sharedPreferences.getString("loginkey_" + savedAccount, null);
            loginInfo.avatar = sharedPreferences.getString("avatar_" + savedAccount, null);
            AccountLoginInfo.writeAccount(this, loginInfo);
            editor.remove("loginkey_" + savedAccount);
            editor.remove("password_" + savedAccount);
        }
        editor.apply();
        // end
    }

    public void loginWithSavedAccount(AccountLoginInfo account) {
        // start the logging in progess
        Bundle bundle = new Bundle();
        bundle.putString("username", account.username);
        bundle.putBoolean("remember", true);

        if (account.loginkey != null) {
            bundle.putString("loginkey", account.loginkey);
        } else {
            bundle.putString("password", account.password);
        }

        connectionListener.handle_result = true;
        SteamService.attemptLogon(LoginActivity.this, connectionListener, bundle);
    }

    public void attemptLogin() {
        // TODO do not do this if we're already trying to connect-- fix this
        if (progressDialog != null)
            return;

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = null;
        if (cm != null) {
            activeNetwork = cm.getActiveNetworkInfo();
        }
        if (activeNetwork == null || !activeNetwork.isConnected())
            Toast.makeText(this, R.string.not_connected_to_internet, Toast.LENGTH_LONG).show();

        textUsername.setError(null);
        textPassword.setError(null);
        textSteamguard.setError(null);

        // Store values at the time of the login attempt.
        username = textUsername.getText().toString();
        password = textPassword.getText().toString();
        String steamGuard = textSteamguard.getText().toString();
        steamGuard = steamGuard.trim().toUpperCase(Locale.US);

        boolean cancel = false;
        View focusView = null;

        // Check for a valid password.
        if (TextUtils.isEmpty(password)) {
            textPassword.setError(getString(R.string.error_field_required));
            focusView = textPassword;
            cancel = true;
        }
        // Check for a valid username.
        if (TextUtils.isEmpty(username)) {
            textUsername.setError(getString(R.string.error_field_required));
            focusView = textUsername;
            cancel = true;
        }
        if (TextUtils.isEmpty(steamGuard))
            steamGuard = null;

        if (cancel) {
            focusView.requestFocus();
        } else {
            // log in
            if (progressDialog != null)
                progressDialog.dismiss();

            // start the logging in progess
            Bundle bundle = new Bundle();
            bundle.putString("username", username);
            bundle.putString("password", password);
            bundle.putString("steamguard", steamGuard);
            bundle.putBoolean("remember", rememberInfoCheckbox.isChecked());
            bundle.putBoolean("twofactor", need_twofactor);
            connectionListener.handle_result = true;
            SteamService.attemptLogon(LoginActivity.this, connectionListener, bundle);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        active = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        active = true;

        // go to main activity if already logged in
        if (SteamService.singleton != null && SteamService.singleton.steamClient != null && SteamService.singleton.steamClient.getConnectedUniverse() != null && SteamService.singleton.steamClient.getConnectedUniverse() != EUniverse.Invalid) {
            Intent intent = new Intent(this, MainActivity.class);
            if (getIntent() != null) {
                // forward our intent (there may be a better way to do this)
                intent.setAction(getIntent().getAction());
                intent.setData(getIntent().getData());
            }
            startActivity(intent);
            finish();
            active = false;
            return;
        }

        // set self as the steam connection listener
        SteamService.connectionListener = connectionListener;
    }

    private void showAndFillManualLogin(String username) {

        // Set login view to manual sign in.
        viewNew.setVisibility(View.VISIBLE);
        viewSaved.setVisibility(View.GONE);

        //Get username information.
        AccountLoginInfo info = AccountLoginInfo.readAccount(this, username);
        if (info != null) {
            textUsername.setText(info.username);
            textPassword.setText(info.password);

            if (info.has_authenticator) {
                //Show steamguard field.
                textSteamguard.setVisibility(View.VISIBLE);
                String code = SteamTwoFactor.generateAuthCodeForTime(info.tfa_sharedSecret, SteamTwoFactor.getCurrentTime());
                textSteamguard.setText(code);
            }
        }
        rememberInfoCheckbox.setChecked(true);
    }

    private class ConnectionListener implements SteamConnectionListener {
        private boolean handle_result = true;

        @Override
        public void onConnectionResult(final EResult result) {
            Log.i("ConnectionListener", "Connection result: " + result);

            runOnUiThread(() -> {
                if (!active)
                    return;

                if (progressDialog != null && progressDialog.isShowing())
                    progressDialog.dismiss();
                progressDialog = null;

                if (!handle_result)
                    return;
                handle_result = false;

                if (result == EResult.InvalidPassword) {
                    // maybe change error to "login key expired, log in again" if using loginkey
                    if (SteamService.extras != null && SteamService.extras.getString("loginkey") != null) {
                        Toast.makeText(LoginActivity.this, R.string.error_loginkey_expired, Toast.LENGTH_LONG).show();
                        textPassword.setError(getString(R.string.error_loginkey_expired));

                        String username = SteamService.extras.getString("username");
                        showAndFillManualLogin(username);
                    } else {
                        textPassword.setError(getString(R.string.error_incorrect_password));
                        textPassword.requestFocus();
                    }
                } else if (result == EResult.ConnectFailed) {
                    Toast.makeText(LoginActivity.this, R.string.cannot_connect_to_steam, Toast.LENGTH_SHORT).show();
                } else if (result == EResult.ServiceUnavailable) {
                    Toast.makeText(LoginActivity.this, R.string.cannot_auth_with_steamweb, Toast.LENGTH_LONG).show();
                } else if (result == EResult.AccountLogonDenied || result == EResult.AccountLogonDeniedNoMail || result == EResult.AccountLogonDeniedVerifiedEmailRequired || result == EResult.AccountLoginDeniedNeedTwoFactor) {
                    steamGuardField.setVisibility(View.VISIBLE);
                    textSteamguard.setVisibility(View.VISIBLE);
                    steamGuardField.setError(getString(R.string.error_steamguard_required));
                    textSteamguard.requestFocus();
                    Toast.makeText(LoginActivity.this, "SteamGuard: " + result.name(), Toast.LENGTH_LONG).show();

                    String username = SteamService.extras.getString("username");
                    showAndFillManualLogin(username);

                    need_twofactor = result == EResult.AccountLoginDeniedNeedTwoFactor;
                } else if (result == EResult.InvalidLoginAuthCode || result == EResult.TwoFactorCodeMismatch) {
                    textSteamguard.setVisibility(View.VISIBLE);
                    textSteamguard.setError(getString(R.string.error_incorrect_steamguard));
                    textSteamguard.requestFocus();

                    String username = SteamService.extras.getString("username");
                    showAndFillManualLogin(username);

                    need_twofactor = result == EResult.TwoFactorCodeMismatch;
                } else if (result != EResult.OK) {
                    // who knows what this is. perhaps a bug report will reveal
                    Toast.makeText(LoginActivity.this, "Cannot Login: " + result.toString(), Toast.LENGTH_LONG).show();
                } else {
                    if (SteamUtil.webApiKey.length() == 0) {
                        Toast.makeText(LoginActivity.this, R.string.error_getting_key, Toast.LENGTH_LONG).show();
                    }

                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    intent.putExtra("isLoggingIn", true);
                    LoginActivity.this.startActivity(intent);
                    finish();
                }
            });
        }

        @Override
        public void onConnectionStatusUpdate(final int status) {
            Log.i("ConnectionListener", "Status update: " + status);

            runOnUiThread(() -> {
                if (!active)
                    return;

                if (status != STATUS_CONNECTED && status != STATUS_FAILURE) {
                    if (progressDialog == null || !progressDialog.isShowing()) {
                        progressDialog = new mProgressDialog(LoginActivity.this);
                        progressDialog.setCancelable(true);
                        progressDialog.setOnCancelListener(dialog -> {
                            if (SteamService.singleton != null) {
                                SteamService.singleton.kill();
                            }
                        });
                        progressDialog.show();
                    }
                }

                String[] statuses = getResources().getStringArray(R.array.connection_status);
                if (progressDialog != null)
                    progressDialog.setMessage(statuses[status]);
            });
        }
    }

    private class AccountListAdapter extends RecyclerView.Adapter<AccountListAdapter.AccountViewHolder> {
        List<AccountLoginInfo> accounts;
        Context context;

        AccountListAdapter(Context context) {
            this.context = context;
            accounts = AccountLoginInfo.getAccountList(LoginActivity.this);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public AccountViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new AccountViewHolder(parent);
        }

        @Override
        public void onBindViewHolder(@NonNull AccountViewHolder holder, int position) {
            AccountLoginInfo account = accounts.get(position);
            holder.name.setText(account.username);

            holder.buttonKey.setVisibility(account.has_authenticator ? View.VISIBLE : View.GONE);
            holder.avatar.setImageResource(R.drawable.default_avatar);

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(LoginActivity.this);
            String avatar = sharedPreferences.getString("avatar_" + account.username, "");

            if (!avatar.equals("")) {
                Glide.with(context)
                        .load(avatar)
                        .into(holder.avatar);
            }

            holder.buttonRemove.setTag(position);
            holder.itemView.setTag(position);
            holder.buttonKey.setTag(position);
        }

        @Override
        public int getItemCount() {
            return accounts == null ? 0 : accounts.size();
        }

        class AccountViewHolder extends ViewHolder implements OnClickListener {
            CircleImageView avatar;
            public TextView name;
            ImageButton buttonRemove;
            ImageButton buttonKey;

            AccountViewHolder(ViewGroup parent) {
                super(LayoutInflater.from(parent.getContext()).inflate(R.layout.activity_login_account, parent, false));

                name = itemView.findViewById(R.id.account_name);
                avatar = itemView.findViewById(R.id.account_avatar);
                buttonRemove = itemView.findViewById(R.id.account_delete);
                buttonKey = itemView.findViewById(R.id.account_key);

                itemView.setOnClickListener(this);
                buttonRemove.setOnClickListener(this);
                buttonKey.setOnClickListener(this);
            }

            @Override
            public void onClick(View view) {
                final AccountLoginInfo account = accounts.get(getAdapterPosition());

                if (view.getId() == R.id.account_delete) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(LoginActivity.this);
                    builder.setNegativeButton(android.R.string.cancel, null);
                    builder.setPositiveButton(android.R.string.yes, (dialog, whichButton) -> {
                        accounts.remove(getAdapterPosition());
                        notifyItemRemoved(getAdapterPosition());

                        AccountLoginInfo.removeAccount(LoginActivity.this, account.username);
                    });
                    builder.setMessage(String.format(getString(R.string.login_confirm_delete_account), account.username));
                    builder.show();
                }
                if (view.getId() == R.id.account) {
                    loginWithSavedAccount(account);
                }
                if (view.getId() == R.id.account_key) {
                    SteamGuardDialog.newInstance(account.tfa_sharedSecret).show(getSupportFragmentManager(), SteamGuardDialog.TAG);

                }
            }
        }
    }
}
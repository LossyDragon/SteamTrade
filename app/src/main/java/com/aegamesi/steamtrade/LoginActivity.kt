package com.aegamesi.steamtrade

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Bundle
import android.preference.PreferenceManager
import com.google.android.material.textfield.TextInputLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast

import com.aegamesi.steamtrade.dialogs.EulaDialog
import com.aegamesi.steamtrade.dialogs.SteamGuardDialog
import com.aegamesi.steamtrade.dialogs.NewProgressDialog
import com.aegamesi.steamtrade.steam.AccountLoginInfo
import com.aegamesi.steamtrade.steam.SteamConnectionListener
import com.aegamesi.steamtrade.steam.SteamService
import com.aegamesi.steamtrade.steam.SteamTwoFactor
import com.aegamesi.steamtrade.steam.SteamUtil
import com.bumptech.glide.Glide

import org.json.JSONException

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.HashSet
import java.util.Locale

import de.hdodenhof.circleimageview.CircleImageView
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EResult
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EUniverse

import android.text.InputType.TYPE_CLASS_TEXT
import android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

class LoginActivity : AppCompatActivity() {
    private var needTwoFactor = false

    private var rememberInfoCheckbox: CheckBox? = null
    private var textUsername: EditText? = null
    private var textPassword: EditText? = null
    private var textSteamguard: EditText? = null
    private var steamGuardField: TextInputLayout? = null
    private var viewSaved: View? = null
    private var viewNew: View? = null

    private var connectionListener: ConnectionListener? = null

    internal var progressDialog: NewProgressDialog? = null
    private var active = false

    /* LoginActivity setup*/
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleLegacy()
        setContentView(R.layout.activity_login)

        if (supportActionBar != null)
            supportActionBar!!.hide()

        /* Show the EULA */
        val eulaDialog = EulaDialog()
        if (eulaDialog.shouldCreateDialog(this))
            eulaDialog.show(supportFragmentManager, "tag")

        connectionListener = ConnectionListener()

        viewNew = findViewById(R.id.layout_new)
        viewSaved = findViewById(R.id.layout_saved)
        val headerNew = findViewById<Button>(R.id.btn_header_new)
        val headerSaved = findViewById<Button>(R.id.btn_header_saved)
        val importAccount = findViewById<Button>(R.id.btn_import_account)

        /* OnClickListener for switching between New and Saved Account sections */
        val cardListener: View.OnClickListener = View.OnClickListener { view ->
            val isNew = view === headerNew
            val isSaved = view === headerSaved

            headerNew.visibility = if (isNew) View.GONE else View.VISIBLE
            viewNew!!.visibility = if (isNew) View.VISIBLE else View.GONE

            headerSaved.visibility = if (isSaved) View.GONE else View.VISIBLE
            viewSaved!!.visibility = if (isSaved) View.VISIBLE else View.GONE
        }

        /* New & Saved button Click Listeners */
        headerNew.setOnClickListener(cardListener)
        headerSaved.setOnClickListener(cardListener)

        val accountsList = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.accounts_list)

        val accountListAdapter = AccountListAdapter(this)
        accountsList.adapter = accountListAdapter
        accountsList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        if (accountListAdapter.itemCount == 0) {
            // only show the new one
            cardListener.onClick(headerNew)
            headerSaved.visibility = View.GONE
        } else {
            cardListener.onClick(headerSaved)
        }

        /* Login form preparation */
        rememberInfoCheckbox = findViewById(R.id.remember)
        textSteamguard = findViewById(R.id.steamguard)
        textSteamguard!!.visibility = View.GONE
        textUsername = findViewById(R.id.username)
        textPassword = findViewById(R.id.password)
        steamGuardField = findViewById(R.id.steamguard_field)
        steamGuardField!!.visibility = View.INVISIBLE
        val buttonSignIn = findViewById<Button>(R.id.sign_in_button)

        /* Keyboard Stuff */
        textPassword!!.setOnEditorActionListener { _, id, _ ->
            if (id == R.id.login || id == EditorInfo.IME_NULL) {
                attemptLogin()
                return@setOnEditorActionListener true
            }
            false
        }

        /* Show legacy information */
        if (accountListAdapter.itemCount == 0) {
            if (getPreferences(Context.MODE_PRIVATE).getBoolean("rememberDetails", true)) {
                textUsername!!.setText(getPreferences(Context.MODE_PRIVATE).getString("username", ""))
                textPassword!!.setText(getPreferences(Context.MODE_PRIVATE).getString("password", ""))
            }
        }

        /* Click Listener for signing in */
        buttonSignIn.setOnClickListener {
            attemptLogin()
        }

        /* Click Listener for .maFile importing */
        importAccount.setOnClickListener { SteamTwoFactor.promptForMafile(this@LoginActivity, REQUEST_CODE_LOAD_MAFILE) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_LOAD_MAFILE) {
            if (resultCode == Activity.RESULT_OK) {
                try {
                    val b = StringBuilder()
                    val `is` = contentResolver.openInputStream(data!!.data!!)
                    val reader = BufferedReader(InputStreamReader(`is`))
                    var s: String?

                    do {
                        s = reader.readLine()
                        b.append(s)
                        b.append("\n")
                    } while (s != null)

                    val acc = AccountLoginInfo()
                    acc.importFromJson(b.toString())
                    val existingAccount = AccountLoginInfo.readAccount(this, acc.tfaAccountName)
                    if (existingAccount != null) {
                        existingAccount.importFromJson(b.toString())
                        existingAccount.hasAuthenticator = true
                        AccountLoginInfo.writeAccount(this@LoginActivity, existingAccount)
                        Toast.makeText(this@LoginActivity, R.string.action_successful, Toast.LENGTH_LONG).show()
                    } else {
                        acc.username = acc.tfaAccountName
                        val builder = AlertDialog.Builder(this)
                        builder.setTitle(R.string.steamguard_mobile_authenticator)
                        builder.setMessage(String.format(getString(R.string.steamguard_import_password), acc.username))
                        builder.setCancelable(true)
                        val passwordInput = EditText(this)
                        builder.setView(passwordInput)
                        passwordInput.inputType = TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_PASSWORD
                        builder.setPositiveButton(android.R.string.ok) { _, _ ->
                            acc.password = passwordInput.text.toString()
                            acc.hasAuthenticator = true
                            AccountLoginInfo.writeAccount(this@LoginActivity, acc)
                            Toast.makeText(this@LoginActivity, R.string.action_successful, Toast.LENGTH_LONG).show()
                            recreate()
                        }
                        builder.show()
                    }

                } catch (e: IOException) {
                    e.printStackTrace()
                    val builder = AlertDialog.Builder(this)
                    builder.setTitle(R.string.error)
                    builder.setMessage(e.toString())
                    builder.setCancelable(true)
                    builder.setNeutralButton(android.R.string.ok, null)
                    builder.show()
                } catch (e: JSONException) {
                    e.printStackTrace()
                    val builder = AlertDialog.Builder(this)
                    builder.setTitle(R.string.error)
                    builder.setMessage(e.toString())
                    builder.setCancelable(true)
                    builder.setNeutralButton(android.R.string.ok, null)
                    builder.show()
                } catch (e: NumberFormatException) {
                    e.printStackTrace()
                    val builder = AlertDialog.Builder(this)
                    builder.setTitle(R.string.error)
                    builder.setMessage(e.toString())
                    builder.setCancelable(true)
                    builder.setNeutralButton(android.R.string.ok, null)
                    builder.show()
                }

            }
        }
    }

    private fun handleLegacy() {
        // from version 0.10.4 onwards:
        // convert from old account storage to new account storage
        val savedAccounts = HashSet<String>()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this@LoginActivity)
        val allPrefs = sharedPreferences.all
        for (key in allPrefs.keys) {
            if (key.startsWith("loginkey_"))
                savedAccounts.add(key.substring("loginkey_".length))
            if (key.startsWith("password_"))
                savedAccounts.add(key.substring("password_".length))
        }
        val editor = sharedPreferences.edit()
        for (savedAccount in savedAccounts) {
            val loginInfo = AccountLoginInfo()
            loginInfo.username = savedAccount
            loginInfo.password = sharedPreferences.getString("password_$savedAccount", null)
            loginInfo.loginkey = sharedPreferences.getString("loginkey_$savedAccount", null)
            loginInfo.avatar = sharedPreferences.getString("avatar_$savedAccount", null)
            AccountLoginInfo.writeAccount(this, loginInfo)
            editor.remove("loginkey_$savedAccount")
            editor.remove("password_$savedAccount")
        }
        editor.apply()
        // end
    }

    fun loginWithSavedAccount(account: AccountLoginInfo) {
        // start the logging in progess
        val bundle = Bundle()
        bundle.putString("username", account.username)
        bundle.putBoolean("remember", true)

        if (account.loginkey != null) {
            bundle.putString("loginkey", account.loginkey)
        } else {
            bundle.putString("password", account.password)
        }

        connectionListener!!.handleResult = true
        SteamService.attemptLogon(this@LoginActivity, connectionListener, bundle)
    }

    private fun attemptLogin() {
        // TODO do not do this if we're already trying to connect-- fix this
        if (progressDialog != null)
            return

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val activeNetwork: NetworkInfo?
        activeNetwork = cm.activeNetworkInfo

        if (activeNetwork == null || !activeNetwork.isConnected)
            Toast.makeText(this, R.string.not_connected_to_internet, Toast.LENGTH_LONG).show()

        textUsername!!.error = null
        textPassword!!.error = null
        textSteamguard!!.error = null

        // Store values at the time of the login attempt.
        username = textUsername!!.text.toString()
        password = textPassword!!.text.toString()
        var steamGuard: String? = textSteamguard!!.text.toString()
        steamGuard = steamGuard!!.trim { it <= ' ' }.toUpperCase(Locale.US)

        var cancel = false
        var focusView: View? = null

        // Check for a valid password.
        if (TextUtils.isEmpty(password)) {
            textPassword!!.error = getString(R.string.error_field_required)
            focusView = textPassword
            cancel = true
        }
        // Check for a valid username.
        if (TextUtils.isEmpty(username)) {
            textUsername!!.error = getString(R.string.error_field_required)
            focusView = textUsername
            cancel = true
        }
        if (TextUtils.isEmpty(steamGuard))
            steamGuard = null

        if (cancel) {
            focusView!!.requestFocus()
        } else {
            // log in
            if (progressDialog != null)
                progressDialog!!.dismiss()

            // start the logging in progress
            val bundle = Bundle()
            bundle.putString("username", username)
            bundle.putString("password", password)
            bundle.putString("steamguard", steamGuard)
            bundle.putBoolean("remember", rememberInfoCheckbox!!.isChecked)
            bundle.putBoolean("twofactor", needTwoFactor)
            connectionListener!!.handleResult = true
            SteamService.attemptLogon(this@LoginActivity, connectionListener, bundle)
        }
    }

    override fun onPause() {
        super.onPause()
        active = false
    }

    override fun onResume() {
        super.onResume()
        active = true

        // go to main activity if already logged in
        if (SteamService.singleton != null && SteamService.singleton!!.steamClient != null && SteamService.singleton!!.steamClient!!.connectedUniverse != null && SteamService.singleton!!.steamClient!!.connectedUniverse != EUniverse.Invalid) {
            val intent = Intent(this, MainActivity::class.java)
            if (getIntent() != null) {
                // forward our intent (there may be a better way to do this)
                intent.action = getIntent().action
                intent.data = getIntent().data
            }
            startActivity(intent)
            finish()
            active = false
            return
        }

        // set self as the steam connection listener
        SteamService.connectionListener = connectionListener
    }

    private fun showAndFillManualLogin(username: String?) {

        // Set login view to manual sign in.
        viewNew!!.visibility = View.VISIBLE
        viewSaved!!.visibility = View.GONE

        //Get username information.
        val info = AccountLoginInfo.readAccount(this, username!!)
        if (info != null) {
            textUsername!!.setText(info.username)
            textPassword!!.setText(info.password)

            if (info.hasAuthenticator) {
                //Show steamguard field.
                textSteamguard!!.visibility = View.VISIBLE
                val code = SteamTwoFactor.generateAuthCodeForTime(info.tfaSharedSecret, SteamTwoFactor.currentTime)
                textSteamguard!!.setText(code)
            }
        }
        rememberInfoCheckbox!!.isChecked = true
    }

    private inner class ConnectionListener : SteamConnectionListener {
        var handleResult = true

        override fun onConnectionResult(result: EResult) {
            Log.i("ConnectionListener", "Connection result: $result")

            runOnUiThread {
                if (!active)
                    return@runOnUiThread

                if (progressDialog != null && progressDialog!!.isShowing)
                    progressDialog!!.dismiss()
                progressDialog = null

                if (!handleResult)
                    return@runOnUiThread
                handleResult = false

                if (result == EResult.InvalidPassword) {
                    // maybe change error to "login key expired, log in again" if using loginkey
                    if (SteamService.extras != null && SteamService.extras!!.getString("loginkey") != null) {
                        Toast.makeText(this@LoginActivity, R.string.error_loginkey_expired, Toast.LENGTH_LONG).show()
                        textPassword!!.error = getString(R.string.error_loginkey_expired)

                        val username = SteamService.extras!!.getString("username")
                        showAndFillManualLogin(username)
                    } else {
                        textPassword!!.error = getString(R.string.error_incorrect_password)
                        textPassword!!.requestFocus()
                    }
                } else if (result == EResult.ConnectFailed) {
                    Toast.makeText(this@LoginActivity, R.string.cannot_connect_to_steam, Toast.LENGTH_SHORT).show()
                } else if (result == EResult.ServiceUnavailable) {
                    Toast.makeText(this@LoginActivity, R.string.cannot_auth_with_steamweb, Toast.LENGTH_LONG).show()
                } else if (result == EResult.AccountLogonDenied || result == EResult.AccountLogonDeniedNoMail || result == EResult.AccountLogonDeniedVerifiedEmailRequired || result == EResult.AccountLoginDeniedNeedTwoFactor) {
                    steamGuardField!!.visibility = View.VISIBLE
                    textSteamguard!!.visibility = View.VISIBLE
                    steamGuardField!!.error = getString(R.string.error_steamguard_required)
                    textSteamguard!!.requestFocus()
                    Toast.makeText(this@LoginActivity, "SteamGuard: " + result.name, Toast.LENGTH_LONG).show()

                    val username = SteamService.extras!!.getString("username")
                    showAndFillManualLogin(username)

                    needTwoFactor = result == EResult.AccountLoginDeniedNeedTwoFactor
                } else if (result == EResult.InvalidLoginAuthCode || result == EResult.TwoFactorCodeMismatch) {
                    textSteamguard!!.visibility = View.VISIBLE
                    textSteamguard!!.error = getString(R.string.error_incorrect_steamguard)
                    textSteamguard!!.requestFocus()

                    val username = SteamService.extras!!.getString("username")
                    showAndFillManualLogin(username)

                    needTwoFactor = result == EResult.TwoFactorCodeMismatch
                } else if (result != EResult.OK) {
                    // who knows what this is. perhaps a bug report will reveal
                    Toast.makeText(this@LoginActivity, "Cannot Login: $result", Toast.LENGTH_LONG).show()
                } else {
                    if (SteamUtil.webApiKey!!.isEmpty()) {
                        Toast.makeText(this@LoginActivity, R.string.error_getting_key, Toast.LENGTH_LONG).show()
                    }

                    val intent = Intent(this@LoginActivity, MainActivity::class.java)
                    intent.putExtra("isLoggingIn", true)
                    this@LoginActivity.startActivity(intent)
                    finish()
                }
            }
        }

        override fun onConnectionStatusUpdate(status: Int) {
            Log.i("ConnectionListener", "Status update: $status")

            runOnUiThread {
                if (!active)
                    return@runOnUiThread

                if (status != SteamConnectionListener.STATUS_CONNECTED && status != SteamConnectionListener.STATUS_FAILURE) {
                    if (progressDialog == null || !progressDialog!!.isShowing) {
                        progressDialog = NewProgressDialog(this@LoginActivity)
                        progressDialog!!.setCancelable(true)
                        progressDialog!!.setOnCancelListener {
                            if (SteamService.singleton != null) {
                                SteamService.singleton!!.kill()
                            }
                        }
                        progressDialog!!.show()
                    }
                }

                val statuses = resources.getStringArray(R.array.connection_status)
                if (progressDialog != null)
                    progressDialog!!.setMessage(statuses[status])
            }
        }
    }

    private inner class AccountListAdapter internal constructor(internal var context: Context) : androidx.recyclerview.widget.RecyclerView.Adapter<AccountListAdapter.AccountViewHolder>() {
        internal var accounts: MutableList<AccountLoginInfo>? = null

        init {
            accounts = AccountLoginInfo.getAccountList(this@LoginActivity)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
            return AccountViewHolder(parent)
        }

        override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
            val account = accounts!![position]
            holder.name.text = account.username

            holder.buttonKey.visibility = if (account.hasAuthenticator) View.VISIBLE else View.GONE
            holder.avatar.setImageResource(R.drawable.default_avatar)

            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this@LoginActivity)
            val avatar = sharedPreferences.getString("avatar_" + account.username!!, "")

            if (avatar != "") {
                Glide.with(context)
                        .load(avatar)
                        .into(holder.avatar)
            }

            holder.buttonRemove.tag = position
            holder.itemView.tag = position
            holder.buttonKey.tag = position
        }

        override fun getItemCount(): Int {
            return if (accounts == null) 0 else accounts!!.size
        }

        internal inner class AccountViewHolder(parent: ViewGroup) : ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.activity_login_account, parent, false)), OnClickListener {
            var avatar: CircleImageView = itemView.findViewById(R.id.account_avatar)
            var name: TextView = itemView.findViewById(R.id.account_name)
            var buttonRemove: ImageButton = itemView.findViewById(R.id.account_delete)
            var buttonKey: ImageButton = itemView.findViewById(R.id.account_key)

            init {

                itemView.setOnClickListener(this)
                buttonRemove.setOnClickListener(this)
                buttonKey.setOnClickListener(this)
            }

            override fun onClick(view: View) {
                val account = accounts!![adapterPosition]

                if (view.id == R.id.account_delete) {
                    val builder = AlertDialog.Builder(this@LoginActivity)
                    builder.setNegativeButton(android.R.string.cancel, null)
                    builder.setPositiveButton(android.R.string.yes) { _, _ ->
                        accounts!!.removeAt(adapterPosition)
                        notifyItemRemoved(adapterPosition)

                        AccountLoginInfo.removeAccount(this@LoginActivity, account.username!!)
                    }
                    builder.setMessage(String.format(getString(R.string.login_confirm_delete_account), account.username))
                    builder.show()
                }
                if (view.id == R.id.account) {
                    loginWithSavedAccount(account)
                }
                if (view.id == R.id.account_key) {
                    SteamGuardDialog.newInstance(account.tfaSharedSecret).show(supportFragmentManager, SteamGuardDialog.TAG)

                }
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_LOAD_MAFILE = 48399

        lateinit var username: String
        lateinit var password: String
    }
}
package com.aegamesi.steamtrade.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.aegamesi.steamtrade.R
import com.aegamesi.steamtrade.libs.AndroidUtil
import com.aegamesi.steamtrade.steam.AccountLoginInfo
import com.aegamesi.steamtrade.steam.SteamService
import com.aegamesi.steamtrade.steam.SteamTwoFactor
import kotlinx.android.synthetic.main.fragment_steamguard.*
import org.json.JSONException
import uk.co.thomasc.steamkit.base.generated.SteammessagesTwofactorSteamclient.CTwoFactor_AddAuthenticator_Response
import uk.co.thomasc.steamkit.base.generated.SteammessagesTwofactorSteamclient.CTwoFactor_FinalizeAddAuthenticator_Response
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EResult
import uk.co.thomasc.steamkit.steam3.handlers.steamunifiedmessages.callbacks.UnifiedMessageResponseCallback
import uk.co.thomasc.steamkit.steam3.steamclient.callbackmgr.CallbackMsg
import uk.co.thomasc.steamkit.util.cSharp.events.ActionT
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

class FragmentSteamGuard : FragmentBase(), OnClickListener {

    var authenticatorFinalizeAttempts = 30
    private var authenticatorFinalizeTime: Long = 0
    private var authenticatorFinalizeCode: String? = null
    private var authenticatorFinalizeSecret: ByteArray? = null

    companion object {
        private const val REQUEST_CODE_LOAD = 48235
    }

    private val accountLoginInfo: AccountLoginInfo?
        get() {
            val username = SteamService.singleton!!.username
            return AccountLoginInfo.readAccount(activity()!!, username!!)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (abort) return

        Log.i("FragmentSteamGuard", "created")
    }

    override fun handleSteamMessage(msg: CallbackMsg) {
        msg.handle(UnifiedMessageResponseCallback::class.java, object : ActionT<UnifiedMessageResponseCallback>() {
            override fun call(obj: UnifiedMessageResponseCallback) {
                /*if (obj.getMethodName().equals("TwoFactor.Status#1")) {
					CTwoFactor_Status_Response response = obj.getProtobuf(CTwoFactor_Status_Response.class);
				}*/

                if (obj.methodName == "TwoFactor.AddAuthenticator#1") {
                    val response = obj.getProtobuf<CTwoFactor_AddAuthenticator_Response>(CTwoFactor_AddAuthenticator_Response::class.java)
                    Log.d("FragmentSteamGuard", response.toString())

                    val status = EResult.f(response.status)
                    if (status == EResult.OK) {
                        // don't set "has authenticator" but save all of the data
                        val info = accountLoginInfo
                        info!!.tfaSharedSecret = response.sharedSecret
                        info.tfaSerialNumber = java.lang.Long.toString(response.serialNumber)
                        info.tfaRevocationCode = response.revocationCode
                        info.tfaUri = response.uri
                        info.tfaServerTime = response.serverTime
                        info.tfaAccountName = response.accountName
                        info.tfaTokenGid = response.tokenGid
                        info.tfaIdentitySecret = response.identitySecret
                        info.tfaSecret1 = response.secret1
                        saveAccountLoginInfo(info)

                        authenticatorFinalizeSecret = response.sharedSecret

                        val alert = AlertDialog.Builder(activity()!!)
                        alert.setTitle(R.string.steamguard_mobile_authenticator)
                        alert.setMessage(R.string.steamguard_instructions_finalize)
                        val input = EditText(activity())
                        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                        alert.setView(input)
                        alert.setPositiveButton(R.string.add) { _, _ ->
                            authenticatorFinalizeCode = input.text.toString().trim { it <= ' ' }
                            authenticatorFinalizeAttempts = 30
                            authenticatorFinalizeTime = SteamTwoFactor.currentTime
                            finalizeAuthenticator()
                        }
                        alert.setNegativeButton(android.R.string.cancel,
                                null)
                        alert.show()
                    } else {
                        var errorMessage = String.format(getString(R.string.steamguard_enable_failure), status.name)
                        if (status == EResult.DuplicateRequest) {
                            errorMessage = getString(R.string.steamguard_duplicate)
                        }

                        AndroidUtil.showBasicAlert(activity()!!, getString(R.string.error), errorMessage, null)
                    }
                }

                if (obj.methodName == "TwoFactor.FinalizeAddAuthenticator#1") {
                    val response = obj.getProtobuf<CTwoFactor_FinalizeAddAuthenticator_Response>(CTwoFactor_FinalizeAddAuthenticator_Response::class.java)

                    if (response.serverTime != 0L)
                        authenticatorFinalizeTime = response.serverTime

                    val status = EResult.f(response.status)
                    Log.i("FragmentSteamGuard", "FinalizeAddAuthenticator: $status")
                    if (response.success) {
                        if (response.wantMore) {
                            authenticatorFinalizeAttempts--
                            authenticatorFinalizeTime += 30
                            finalizeAuthenticator()
                        } else {
                            // success!
                            val info = accountLoginInfo
                            info!!.hasAuthenticator = true
                            saveAccountLoginInfo(info)
                            updateView()


                            AndroidUtil.showBasicAlert(activity()!!,
                                    getString(R.string.steamguard_mobile_authenticator),
                                    String.format(getString(R.string.steamguard_enable_success), info.tfaRevocationCode), null)
                        }
                    } else {
                        AndroidUtil.showBasicAlert(activity()!!,
                                getString(R.string.error),
                                String.format(getString(R.string.steamguard_enable_failure), status.name), null)
                    }
                }
            }
        })
    }

    private fun finalizeAuthenticator() {
        val code = SteamTwoFactor.generateAuthCodeForTime(authenticatorFinalizeSecret!!, authenticatorFinalizeTime)
        Log.i("FragmentSteamGuard", "Attempting finalization with code $code")
        activity()!!.steamUser.finalizeTwoFactor(authenticatorFinalizeCode, code)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_steamguard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        steamguard_manage.setOnClickListener(this)
        steamguard_revocation.setOnClickListener(this)
        steamguard_port.setOnClickListener(this)

        updateView()
    }

    fun updateView() {

        if (activity() == null ||SteamService.singleton == null || SteamService.singleton!!.steamClient == null)
            return

        if (hasAuthenticator()) {
            steamguard_status.visibility = View.GONE
            steamguard_code_card.visibility = View.VISIBLE
            steamguard_manage.setText(R.string.steamguard_manage_authenticator)
            steamguard_port.setText(R.string.steamguard_export_authenticator)
            steamguard_revocation.visibility = View.VISIBLE

            steamguard_code_view.setSharedSecret(accountLoginInfo!!.tfaSharedSecret)
        } else {
            steamguard_status.visibility = View.VISIBLE
            steamguard_code_card.visibility = View.GONE
            steamguard_manage.setText(R.string.steamguard_enable_authenticator)
            steamguard_port.setText(R.string.steamguard_import_authenticator)
            steamguard_revocation.visibility = View.GONE
        }
    }

    private fun saveAccountLoginInfo(info: AccountLoginInfo) {
        AccountLoginInfo.writeAccount(activity()!!, info)
    }

    private fun hasAuthenticator(): Boolean {
        val accountLoginInfo = accountLoginInfo
        return accountLoginInfo != null && accountLoginInfo.hasAuthenticator
    }

    override fun onClick(v: View) {
        if (v == steamguard_manage) {
            if (hasAuthenticator()) {
                //Go to: https://store.steampowered.com/twofactor/manage
                val url = "https://store.steampowered.com/twofactor/manage/"
                FragmentWeb.openPage(activity()!!, url, true)
            } else {
                val deviceId = SteamTwoFactor.generateDeviceID(SteamService.singleton!!.steamClient!!.steamId)
                activity()!!.steamUser.enableTwoFactor(deviceId)
                // activity().steamUser.requestTwoFactorStatus();
            }
        }
        if (v == steamguard_revocation) {
            val info = accountLoginInfo

            if (info != null) {
                val builder = AlertDialog.Builder(activity()!!)
                builder.setTitle(R.string.steamguard_mobile_authenticator)
                builder.setMessage(String.format(getString(R.string.steamguard_revocation_code), info.tfaRevocationCode))
                builder.setCancelable(true)
                builder.setNeutralButton(android.R.string.ok, null)
                builder.show()
            }
        }
        if (v == steamguard_port) {
            if (hasAuthenticator()) {
                val info = accountLoginInfo
                val id = SteamService.singleton!!.steamClient!!.steamId

                val json = info!!.exportToJson(id)
                val filename = "mafiles/" + id.convertToLong() + ".mafile"
                try {
                    AndroidUtil.createCachedFile(activity()!!, filename, json)
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                val mafile = File(activity()!!.cacheDir, filename)
                val contentUri = FileProvider.getUriForFile(activity()!!.applicationContext, "com.aegamesi.steamtrade.fileprovider", mafile)

                // export
                val sendIntent = Intent()
                sendIntent.action = Intent.ACTION_SEND
                sendIntent.putExtra(Intent.EXTRA_STREAM, contentUri)
                sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                sendIntent.type = "application/json"
                startActivity(Intent.createChooser(sendIntent, resources.getText(R.string.steamguard_export_authenticator)))
            } else {
                SteamTwoFactor.promptForMafile(activity()!!, REQUEST_CODE_LOAD)
            }
        }
    }

    override fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent): Boolean {
        if (requestCode == REQUEST_CODE_LOAD) {
            var success = false
            if (resultCode == Activity.RESULT_OK) {

                try {
                    val b = StringBuilder()
                    val `is` = activity()!!.contentResolver.openInputStream(data.data!!)
                    val reader = BufferedReader(InputStreamReader(`is`))
                    var s: String?

                    do {
                        s = reader.readLine()
                        b.append(s)
                        b.append("\n")
                    } while (s != null)

                    val accountLoginInfo = accountLoginInfo
                    if (accountLoginInfo != null) {
                        accountLoginInfo.importFromJson(b.toString())

                        if (accountLoginInfo.tfaAccountName == accountLoginInfo.username) {
                            accountLoginInfo.hasAuthenticator = true
                            saveAccountLoginInfo(accountLoginInfo)

                            val builder = AlertDialog.Builder(activity()!!)
                            builder.setTitle(R.string.steamguard_mobile_authenticator)
                            builder.setMessage(String.format(getString(R.string.steamguard_enable_success), accountLoginInfo.tfaRevocationCode))
                            builder.setCancelable(true)
                            builder.setNeutralButton(android.R.string.ok, null)
                            builder.show()
                            success = true

                            updateView()
                        } else {
                            val builder = AlertDialog.Builder(activity()!!)
                            builder.setTitle(R.string.steamguard_mobile_authenticator)
                            builder.setMessage(String.format(getString(R.string.steamguard_import_error_wrong_account), accountLoginInfo.tfaAccountName))
                            builder.setCancelable(true)
                            builder.setNeutralButton(android.R.string.ok, null)
                            builder.show()
                        }
                    }
                } catch (e: Exception) {
                    when(e){
                        is IOException -> {
                            e.printStackTrace()
                            showErrorDialog(e.toString())
                        }
                        is JSONException -> {
                            e.printStackTrace()
                            showErrorDialog(e.toString())
                        }
                        is NumberFormatException -> {
                            e.printStackTrace()
                            showErrorDialog(e.toString())
                        }
                    }
                }
            }
            if (!success) {
                Toast.makeText(activity(), R.string.error, Toast.LENGTH_SHORT).show()
                Log.d("ImportAuthenticator", "import failed? $resultCode")
            }
        }
        return false
    }

    private fun showErrorDialog(e: String) {
        val builder = AlertDialog.Builder(activity()!!)
        builder.setTitle(R.string.error)
        builder.setMessage(e)
        builder.setCancelable(true)
        builder.setNeutralButton(android.R.string.ok, null)
        builder.show()
    }
}
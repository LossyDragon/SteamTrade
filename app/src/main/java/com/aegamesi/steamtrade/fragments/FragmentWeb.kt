package com.aegamesi.steamtrade.fragments

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.*

import com.aegamesi.steamtrade.MainActivity
import com.aegamesi.steamtrade.R
import com.aegamesi.steamtrade.steam.SteamService
import com.aegamesi.steamtrade.steam.SteamTwoFactor
import kotlinx.android.synthetic.main.fragment_web.*

import java.util.ArrayList
import java.util.Collections

class FragmentWeb : FragmentBase() {
    private var url: String? = null
    private var loadedPage = false
    private var headless = false
    private var forceDesktop = false

    private var steamGuardJavascriptInterface: SteamGuardJavascriptInterface? = null

    companion object {

        private const val PROFILE_BASE_URL = "https://steamcommunity.com/profiles/"

        fun openPage(activity: MainActivity, url: String, headless: Boolean) {
            openPageWithTabs(activity, url, headless)
        }

        private fun openPageWithTabs(activity: MainActivity, url: String, headless: Boolean) {
            val args = Bundle()
            args.putBoolean("headless", headless)
            args.putString("url", url)
            val fragment = FragmentWeb()
            fragment.arguments = args
            activity.browseToFragment(fragment, true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (abort) return

        setHasOptionsMenu(true)
        Log.i("FragmentWeb", "created")

        val args = arguments
        if (args != null) {
            if (args.containsKey("url"))
                url = arguments!!.getString("url")

            headless = args.getBoolean("headless", false)
        }
    }

    override fun onResume() {
        super.onResume()
        setTitle(getString(R.string.nav_browser))

        forceDesktop = PreferenceManager.getDefaultSharedPreferences(activity()).getBoolean("pref_desktop_mode", false)
        updateCookies()

        if (!loadedPage) {
            if (url.isNullOrEmpty()) {
                web_view!!.loadUrl( PROFILE_BASE_URL + SteamService.singleton!!.steamClient!!.steamId.convertToLong())
            } else {
                web_view!!.loadUrl(url)
            }

            loadedPage = true
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return activity()!!.layoutInflater.inflate(R.layout.fragment_web, container, false)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        web_view!!.webViewClient = SteamWebViewClient()
        web_view!!.webChromeClient = SteamWebChromeClient()
        web_view!!.addJavascriptInterface(SteamGuardJavascriptInterface(), "SGHandler")
        val webSettings = web_view!!.settings
        webSettings.javaScriptEnabled = true
        webSettings.builtInZoomControls = true
    }

    override fun onPause() {
        super.onPause()

        if (activity() != null) {
            activity()!!.progressBar.visibility = View.GONE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        inflater!!.inflate(R.menu.fragment_web, menu)

        if (headless) {
            menu!!.findItem(R.id.web_toggle_view).isVisible = false
            menu.findItem(R.id.web_community).isVisible = false
            menu.findItem(R.id.web_store).isVisible = false
        } else {
            val desktopMode = PreferenceManager.getDefaultSharedPreferences(activity()).getBoolean("pref_desktop_mode", false)
            menu!!.findItem(R.id.web_toggle_view).isChecked = desktopMode
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
            R.id.web_back -> {
                web_view!!.goBack()
                return true
            }
            R.id.web_forward -> {
                web_view!!.goForward()
                return true
            }
            R.id.web_refresh -> {
                web_view!!.reload()
                return true
            }
            R.id.web_community -> {
                web_view!!.loadUrl("https://steamcommunity.com/profiles/" + SteamService.singleton!!.steamClient!!.steamId.convertToLong())
                return true
            }
            R.id.web_store -> {
                web_view!!.loadUrl("https://store.steampowered.com/")
                return true
            }
            R.id.web_toggle_view -> {
                // switch
                item.isChecked = !item.isChecked
                forceDesktop = item.isChecked
                updateCookies()
                PreferenceManager.getDefaultSharedPreferences(activity()).edit().putBoolean("pref_desktop_mode", forceDesktop).apply()
                web_view!!.reload()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun updateCookies() {
        val cookies = ArrayList<String>()
        Collections.addAll(cookies, *SteamService.generateSteamWebCookies().split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
        cookies.add("forceMobile=" + if (!forceDesktop || headless) 1 else 0)
        cookies.add("dob=1") // age check
        cookies.add("mobileClient=" + if (headless) "android" else "")
        if (headless)
            cookies.add("mobileClientVersion=3125579+%282.1.4%29")

        val cookieManager = CookieManager.getInstance()
        for (cookie in cookies) {
            cookieManager.setCookie("store.steampowered.com", cookie)
            cookieManager.setCookie("steamcommunity.com", cookie)
        }
    }

    fun onBackPressed(): Boolean {
        if (web_view != null && web_view!!.canGoBack()) {
            web_view!!.goBack()
            return true
        }

        return false
    }

    private inner class SteamWebViewClient : WebViewClient() {
        // Need to figure this out a bit.
        override fun shouldOverrideUrlLoading(view: WebView, url: WebResourceRequest): Boolean {
            val command: String?
            Log.i("FragmentWeb", "WebResourceRequest --> " + Uri.parse(url.url.toString()))
            Log.i("FragmentWeb", "Scheme -->" + Uri.parse(url.url.toString()).scheme!!.equals("steammobile", ignoreCase = true))
            Log.d("FragmentWeb", "Command --> " + Uri.parse(url.url.toString()).host!!)
            Log.d("FragmentWeb", "setTitle -->" + url.url.queryParameterNames)

            if (Uri.parse(url.url.toString()).scheme!!.equals("steammobile", ignoreCase = true)) {
                Log.d("FragmentWeb", "Captured url: " + Uri.parse(url.url.toString()))
                command = Uri.parse(url.url.toString()).host

                if (command!!.equals("settitle", ignoreCase = true)) {
                    setTitle(Uri.parse(url.url.toString()).getQueryParameter("title")!!)

                }
                if (command.equals("openurl", ignoreCase = true)) {
                    view.loadUrl(Uri.parse(url.url.toString()).getQueryParameter("url"))
                }
                if (command.equals("reloadpage", ignoreCase = true)) {
                    view.reload()
                }

                if (command.equals("steamguard", ignoreCase = true) || Uri.parse(view.url).host!!.equals("steamcommunity", ignoreCase = true)) {
                    val op = Uri.parse(url.url.toString()).getQueryParameter("op")
                    if (op!!.equals("conftag", ignoreCase = true)) {
                        val tag = Uri.parse(url.url.toString()).getQueryParameter("arg1")
                        val go = SteamTwoFactor.generateConfirmationParameters(activity()!!, tag!!)

                        if (go.isEmpty())
                            steamGuardJavascriptInterface!!.setResultError()
                        else
                            steamGuardJavascriptInterface!!.setResultOkay(go)
                    }
                }
                return true
            }

            view.loadUrl(url.url.toString())
            return true
        }
    }

    private inner class SteamWebChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView, progress: Int) {
            if (activity() != null) {
                val loadingBar = activity()!!.progressBar
                if (progress < 100 && loadingBar.visibility != View.VISIBLE)
                    loadingBar.visibility = View.VISIBLE
                if (progress == 100)
                    loadingBar.visibility = View.GONE

                loadingBar.progress = progress
            }
        }
    }

    private inner class SteamGuardJavascriptInterface {
        @get:JavascriptInterface
        var resultCode = ""
            private set
        @get:JavascriptInterface
        var resultStatus = ""
            private set
        private var returnValue = ""

        //val resultValue: String
        //    @JavascriptInterface
        //    get() {
        //        val `val` = returnValue
        //        setResultBusy()
        //        return `val`
        //    }

        internal fun setResultOkay(value: String?) {
            resultStatus = "ok"
            returnValue = value ?: ""
        }

        internal fun setResultError() {
            resultStatus = "error"
            returnValue = ""
            resultCode = "" + -1
        }

        //internal fun setResultBusy() {
        //    returnValue = ""
        //    resultStatus = "busy"
        //}
    }
}
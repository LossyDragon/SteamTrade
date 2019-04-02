package com.aegamesi.steamtrade

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.aegamesi.steamtrade.dialogs.AboutDialog
import com.aegamesi.steamtrade.dialogs.NewProgressDialog
import com.aegamesi.steamtrade.fragments.*
import com.aegamesi.steamtrade.steam.SteamMessageHandler
import com.aegamesi.steamtrade.steam.SteamService
import com.aegamesi.steamtrade.steam.SteamUtil
import com.bumptech.glide.Glide
import com.google.android.material.navigation.NavigationView
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main_content.*
import kotlinx.android.synthetic.main.custom_toolbar.*
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EResult
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.SteamFriends
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.callbacks.FriendAddedCallback
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.callbacks.PersonaStateCallback
import uk.co.thomasc.steamkit.steam3.handlers.steamnotifications.SteamNotifications
import uk.co.thomasc.steamkit.steam3.handlers.steamnotifications.callbacks.NotificationUpdateCallback
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.SteamUser
import uk.co.thomasc.steamkit.steam3.steamclient.callbackmgr.CallbackMsg
import uk.co.thomasc.steamkit.steam3.steamclient.callbacks.DisconnectedCallback
import uk.co.thomasc.steamkit.util.cSharp.events.ActionT
import java.util.*


class MainActivity : AppCompatActivity(), SteamMessageHandler {
    var isActive = false

    var steamFriends: SteamFriends
    var steamUser: SteamUser
    var steamNotifications: SteamNotifications

    //NavigationDrawer
    private lateinit var drawerAvatar: ImageView
    private lateinit var drawerName: TextView
    private lateinit var drawerStatus: TextView
    private lateinit var drawerNotifications: TextView
    private lateinit var drawerCard: CardView

    companion object {
        private const val TAG = "MainActivity"
    }

    init {
        // get the standard steam handlers
        SteamService.singleton!!.messageHandler = this
        steamUser = SteamService.singleton!!.steamClient!!.getHandler(SteamUser::class.java)
        steamFriends = SteamService.singleton!!.steamClient!!.getHandler(SteamFriends::class.java)
        steamNotifications = SteamService.singleton!!.steamClient!!.getHandler(SteamNotifications::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!assertSteamConnection()) return

        setContentView(R.layout.activity_main)

        Log.d(TAG, "created")

        // inform the user about SteamGuard restrictions
        if (SteamService.extras != null &&
                SteamService.extras!!.getBoolean("alertSteamGuard", false)) {

            val builder = AlertDialog.Builder(this)
            builder.setNeutralButton(android.R.string.ok) { _, _ ->
                SteamService.extras!!.putBoolean("alertSteamGuard", false)
            }
            builder.setMessage(R.string.steamguard_new)
            builder.show()
        }

        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeButtonEnabled(true)
        supportActionBar!!.setHomeAsUpIndicator(R.drawable.ic_menu)

        toolbar_icon_LL.visibility = View.GONE

        val navigationView: NavigationView = findViewById(R.id.nav_view)
        val header = navigationView.getHeaderView(0)

        drawerAvatar = header.findViewById(R.id.drawer_avatar)
        drawerName = header.findViewById(R.id.drawer_name)
        drawerStatus = header.findViewById(R.id.drawer_status)
        drawerNotifications = header.findViewById(R.id.notify_text)
        drawerCard = header.findViewById(R.id.notify_card)

        navigationView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.nav_friends -> browseToFragment(FragmentFriends(), true)
                R.id.nav_games -> browseToFragment(FragmentLibrary(), true)
                R.id.nav_browser -> browseToFragment(FragmentWeb(), true)
                R.id.nav_settings -> {
                    setTitle(R.string.nav_settings)
                    browseToFragment(FragmentSettings(), true)
                }
                R.id.nav_about -> AboutDialog.newInstance().show(supportFragmentManager, AboutDialog.TAG)
                R.id.nav_signout -> disconnectWithDialog(this, getString(R.string.signingout))
            }
            true
        }

        // set up
        header.findViewById<View>(R.id.drawer_profile).setOnClickListener {
            for (x in 0 until navigationView.menu.size())
                navigationView.menu.getItem(x).isChecked = false

            browseToFragment(FragmentMe(), true)
        }

        if (savedInstanceState == null)
            browseToFragment(FragmentMe(), false)

        // handle our URL stuff
        if (intent != null && (intent.action != null &&
                        intent.action == Intent.ACTION_VIEW || intent.getStringExtra("url") != null)) {

            var url = intent.getStringExtra("url")

            if (url == null)
                url = intent.data!!.toString()

            Log.d(TAG, "Received url: $url")

            if (url.contains("steamcommunity.com/linkfilter/?url=")) {
                // don't filter these...
                val newUrl = url.substring(url.indexOf("/linkfilter/?url=") + "/linkfilter/?url=".length)
                Log.d("Ice", "Passing through linkfilter url: '$newUrl'")
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(newUrl))
                startActivity(browserIntent)
            } else if (url.contains("steamcommunity.com/id/") || url.contains("steamcommunity.com/profiles/")) {
                val fragment = FragmentProfile()
                val bundle = Bundle()
                bundle.putString("url", url)
                fragment.arguments = bundle
                browseToFragment(fragment, true)
            } else {
                // default to steam browser
                FragmentWeb.openPage(this, url, false)
            }
        }

        if (intent.extras?.getInt("logout") == 1)
            disconnectWithDialog(this, getString(R.string.signingout))
    }

    @Suppress("UNCHECKED_CAST")
    override fun onStart() {
        super.onStart()

        // fragments from intent
        val fragmentName = intent.getStringExtra("fragment")
        if (fragmentName != null) {
            var fragmentClass: Class<out Fragment>? = null
            try {
                fragmentClass = Class.forName(fragmentName) as Class<out Fragment> //Unchecked Cast
            } catch (e: ClassNotFoundException) {
                e.printStackTrace()
            }

            if (fragmentClass != null) {
                var fragment: Fragment? = null
                try {
                    fragment = fragmentClass.newInstance()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (fragment != null) {
                    val arguments = intent.getBundleExtra("arguments")
                    if (arguments != null)
                        fragment.arguments = arguments
                    browseToFragment(fragment, intent.getBooleanExtra("fragment_subfragment", true))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        //Should stop the drawer from blanking out
        updateDrawerProfile()

        isActive = true
    }

    override fun onPause() {
        super.onPause()
        isActive = false
    }

    fun assertSteamConnection(): Boolean {
        val abort = SteamService.singleton == null ||
                SteamService.singleton!!.steamClient == null ||
                SteamService.singleton!!.steamClient!!.steamId == null

        if (abort) {
            // something went wrong. Go to login to be safe
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

        return !abort
    }

    fun browseToFragment(fragment: Fragment, addToBackStack: Boolean) {
        val transaction = supportFragmentManager.beginTransaction()

        if (addToBackStack)
            transaction.addToBackStack(null)

        transaction.replace(R.id.content_frame, fragment, fragment.javaClass.name).commit()

        drawer_layout!!.closeDrawers()
    }

    private fun updateDrawerProfile() {

        val avatarURL = SteamUtil.getAvatar(steamFriends.getFriendAvatar(SteamService.singleton!!.steamClient!!.steamId))

        if (SteamService.extras != null && SteamService.extras!!.containsKey("username")) {
            val key = "avatar_" + SteamService.extras!!.getString("username")!!
            PreferenceManager.getDefaultSharedPreferences(this).edit().putString(key, avatarURL).apply()
        }

        //Glide -> java.lang.IllegalArgumentException: You cannot start a load for a destroyed activity
        if (this.isDestroyed) {
            Log.w(TAG, "Activity Destroyed when calling updateDrawerProfile()")
        } else {
            Glide.with(this)
                    .load(avatarURL)
                    .into(drawerAvatar)

            SteamService.singleton!!.myAvatar = avatarURL
        }

        drawerName.text = steamFriends.personaName
        drawerStatus.text = resources.getStringArray(R.array.persona_states)[steamFriends.personaState.v()]
        drawerName.setTextColor(resources.getColor(R.color.steam_online, null))
        drawerStatus.setTextColor(resources.getColor(R.color.steam_online, null))

        val notifications = steamNotifications.totalNotificationCount
        drawerNotifications.text = String.format(Locale.US, "%1\$d", notifications)

        drawerCard.setCardBackgroundColor(resources.getColor(
                if (notifications == 0) R.color.notification_off
                else R.color.notification_on, null))

    }

    override fun handleSteamMessage(msg: CallbackMsg) {
        msg.handle(DisconnectedCallback::class.java, object : ActionT<DisconnectedCallback>() {
            override fun call(obj: DisconnectedCallback) {
                // go back to the login screen
                // only if currently active
                if (isActive) {
                    val intent = Intent(this@MainActivity, LoginActivity::class.java)
                    this@MainActivity.startActivity(intent)
                    Toast.makeText(this@MainActivity, R.string.error_disconnected, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        })
        msg.handle(PersonaStateCallback::class.java, object : ActionT<PersonaStateCallback>() {
            override fun call(obj: PersonaStateCallback) {
                if (obj.friendID == steamUser.steamId) {
                    steamFriends.cache.localUser.avatarHash = obj.avatarHash
                    updateDrawerProfile()
                }
            }
        })
        msg.handle(FriendAddedCallback::class.java, object : ActionT<FriendAddedCallback>() {
            override fun call(obj: FriendAddedCallback) {
                if (obj.result != EResult.OK) {
                    Toast.makeText(this@MainActivity, String.format(getString(R.string.friend_add_fail), obj.result.toString()), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, getString(R.string.friend_add_success), Toast.LENGTH_LONG).show()
                }
            }
        })
        msg.handle(NotificationUpdateCallback::class.java, object : ActionT<NotificationUpdateCallback>() {
            override fun call(obj: NotificationUpdateCallback) {
                updateDrawerProfile()
            }
        })


        // Now, we find the fragments and pass the message on that way
        val fragmentManager = supportFragmentManager
        for (fragment in fragmentManager.fragments) {
            if (fragment is SteamMessageHandler) {
                (fragment as SteamMessageHandler).handleSteamMessage(msg)
            }
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {

        when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                toggleDrawer()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                val activeFragment = supportFragmentManager.findFragmentById(R.id.content_frame)
                if (activeFragment is FragmentWeb) {
                    // go *back* if possible
                    if (activeFragment.onBackPressed())
                        return true
                }
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            toggleDrawer()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun toggleDrawer() {
        if (drawer_layout!!.isDrawerOpen(GravityCompat.START)) {
            drawer_layout!!.closeDrawers()
        } else {
            // hide IME
            val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            if (this.currentFocus != null)
                inputManager.hideSoftInputFromWindow(this.currentFocus!!.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)

            drawer_layout!!.openDrawer(GravityCompat.START)
        }
    }

    private fun disconnectWithDialog(context: Context, message: String) {
        @SuppressLint("StaticFieldLeak")
        class SteamDisconnectTask : AsyncTask<Void, Void, Void>() {
            private var dialog: NewProgressDialog? = null

            override fun doInBackground(vararg params: Void): Void? {
                // this is really goddamn slow
                steamUser.logOff()
                SteamService.attemptReconnect = false
                SteamService.singleton?.disconnect()

                return null
            }

            override fun onPreExecute() {
                super.onPreExecute()
                dialog = NewProgressDialog(context)
                dialog!!.setCancelable(false)
                dialog!!.setMessage(message)
                dialog!!.show()
            }

            override fun onPostExecute(result: Void?) {
                super.onPostExecute(result)

                dialog!!.dismiss()

                // go back to login screen
                //val intent = Intent(this@MainActivity, LoginActivity::class.java)
                //this@MainActivity.startActivity(intent)
                Toast.makeText(this@MainActivity, R.string.signed_out, Toast.LENGTH_LONG).show()
                finish()
            }
        }
        SteamDisconnectTask().execute()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Fragment> getFragmentByClass(clazz: Class<T>): T? {
        val fragment = supportFragmentManager.findFragmentByTag(clazz.name)
        return if (fragment == null) null else fragment as T?
    }
}

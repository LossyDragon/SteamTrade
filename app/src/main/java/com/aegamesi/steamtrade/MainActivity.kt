package com.aegamesi.steamtrade

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.preference.PreferenceManager
import com.google.android.material.navigation.NavigationView
import com.google.android.material.navigation.NavigationView.OnNavigationItemSelectedListener
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.appcompat.widget.Toolbar
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

import com.aegamesi.steamtrade.dialogs.AboutDialog
import com.aegamesi.steamtrade.dialogs.NewProgressDialog
import com.aegamesi.steamtrade.fragments.FragmentFriends
import com.aegamesi.steamtrade.fragments.FragmentLibrary
import com.aegamesi.steamtrade.fragments.FragmentMe
import com.aegamesi.steamtrade.fragments.FragmentProfile
import com.aegamesi.steamtrade.fragments.FragmentSettings
import com.aegamesi.steamtrade.fragments.FragmentWeb
import com.aegamesi.steamtrade.steam.SteamMessageHandler
import com.aegamesi.steamtrade.steam.SteamService
import com.aegamesi.steamtrade.steam.SteamUtil
import com.bumptech.glide.Glide

import java.util.Locale

import de.hdodenhof.circleimageview.CircleImageView
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


class MainActivity : AppCompatActivity(), SteamMessageHandler, OnNavigationItemSelectedListener {
    private var instance: MainActivity? = null
    var isActive = false

    lateinit var steamFriends: SteamFriends
    lateinit var steamUser: SteamUser
    lateinit var steamNotifications: SteamNotifications
    lateinit var toolbar: Toolbar
    lateinit var toolbarTextView: TextView
    lateinit var toolbarImageView: CircleImageView
    lateinit var toolbarImageLayout: LinearLayout
    lateinit var progressBar: ProgressBar
    private var drawerLayout: androidx.drawerlayout.widget.DrawerLayout? = null
    private var drawerAvatar: ImageView? = null
    private var drawerName: TextView? = null
    private var drawerStatus: TextView? = null
    private var drawerNotifyCard: androidx.cardview.widget.CardView? = null
    private var drawerNotifyText: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!assertSteamConnection())
            return

        setContentView(R.layout.activity_main)
        instance = this

        // inform the user about SteamGuard restrictions
        if (SteamService.extras != null && SteamService.extras!!.getBoolean("alertSteamGuard", false)) {

            val builder = AlertDialog.Builder(this)
            builder.setNeutralButton(android.R.string.ok) { _, _ ->
                if (SteamService.extras != null)
                    SteamService.extras!!.putBoolean("alertSteamGuard", false)
            }
            builder.setMessage(R.string.steamguard_new)
            builder.show()
        }

        // get the standard steam handlers
        SteamService.singleton!!.messageHandler = this
        steamUser = SteamService.singleton!!.steamClient!!.getHandler(SteamUser::class.java)
        steamFriends = SteamService.singleton!!.steamClient!!.getHandler(SteamFriends::class.java)
        steamNotifications = SteamService.singleton!!.steamClient!!.getHandler(SteamNotifications::class.java)

        // set up the progressBar and toolbar
        progressBar = findViewById(R.id.progress_bar)
        toolbar = findViewById(R.id.toolbar)
        //Custom toolbar layout.
        toolbarTextView = findViewById(R.id.toolbar_status)
        toolbarImageView = findViewById(R.id.toolbar_icon)
        toolbarImageLayout = findViewById(R.id.toolbar_icon_LL)
        toolbarImageLayout.visibility = View.GONE

        setSupportActionBar(toolbar)

        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setHomeButtonEnabled(true)

            drawerLayout = findViewById(R.id.drawer_layout)
            val toggle = object : ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close) {
                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                    super.onDrawerSlide(drawerView, 0f)
                }
            }
            drawerLayout!!.addDrawerListener(toggle)
            toggle.syncState()

            val navigationView = findViewById<NavigationView>(R.id.nav_view)
            navigationView.setNavigationItemSelectedListener(this)

            // set up
            val drawerHeaderView = navigationView.getHeaderView(0)
            drawerAvatar = drawerHeaderView.findViewById(R.id.drawer_avatar)
            drawerName = drawerHeaderView.findViewById(R.id.drawer_name)
            drawerStatus = drawerHeaderView.findViewById(R.id.drawer_status)
            drawerNotifyCard = drawerHeaderView.findViewById(R.id.notify_card)
            drawerNotifyText = drawerHeaderView.findViewById(R.id.notify_text)
            drawerHeaderView.findViewById<View>(R.id.drawer_profile).setOnClickListener {
                unCheckAllMenuItems(navigationView.menu)
                browseToFragment(FragmentMe(), true)
            }
        }

        // set up the nav drawer
        updateDrawerProfile()

        if (savedInstanceState == null) {
            browseToFragment(FragmentMe(), false)
        }

        // handle our URL stuff
        if (intent != null && (intent.action != null && intent.action == Intent.ACTION_VIEW || intent.getStringExtra("url") != null)) {
            var url: String?
            url = intent.getStringExtra("url")
            if (url == null) {
                url = intent.data!!.toString()
            }

            Log.d("Ice", "Received url: $url")

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
    }

    /* Un-check NavDrawer selected items */
    private fun unCheckAllMenuItems(menu: Menu) {
        val size = menu.size()
        for (i in 0 until size) {
            val item = menu.getItem(i)
            if (item.hasSubMenu()) {
                unCheckAllMenuItems(item.subMenu)
            } else {
                item.isChecked = false
            }
        }
    }

    fun assertSteamConnection(): Boolean {
        val abort = SteamService.singleton == null || SteamService.singleton!!.steamClient == null || SteamService.singleton!!.steamClient!!.steamId == null
        if (abort) {
            // something went wrong. Go to login to be safe
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
        return !abort
    }

    fun browseToFragment(fragment: androidx.fragment.app.Fragment, addToBackStack: Boolean) {
        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction()

        if (addToBackStack)
            transaction.addToBackStack(null)

        transaction.setCustomAnimations(R.anim.enter_from_left, R.anim.exit_to_right)
        transaction.replace(R.id.content_frame, fragment, fragment.javaClass.name).commit()

        drawerLayout!!.closeDrawer(GravityCompat.START)
    }

    private fun updateDrawerProfile() {
        val state = steamFriends.personaState
        val name = steamFriends.personaName
        val avatar = SteamUtil.bytesToHex(steamFriends.getFriendAvatar(SteamService.singleton!!.steamClient!!.steamId)).toLowerCase(Locale.US)

        drawerName!!.text = name
        drawerStatus!!.text = resources.getStringArray(R.array.persona_states)[state.v()]
        drawerName!!.setTextColor(ContextCompat.getColor(this, R.color.steam_online))
        drawerStatus!!.setTextColor(ContextCompat.getColor(this, R.color.steam_online))

        val notifications = steamNotifications.totalNotificationCount
        drawerNotifyText!!.text = String.format(Locale.US, "%1\$d", notifications)
        drawerNotifyCard!!.setCardBackgroundColor(ContextCompat.getColor(this, if (notifications == 0) R.color.notification_off else R.color.notification_on))

        drawerAvatar!!.setImageResource(R.drawable.default_avatar)
        if (avatar != "0000000000000000000000000000000000000000") {
            val avatarURL = String.format(Locale.US,
                    "http://cdn.akamai.steamstatic.com/steamcommunity/public/images/avatars/%s/%s_full.jpg",
                    avatar.substring(0, 2),
                    avatar)

            //Drawer Profile picture.
            Glide.with(this)
                    .load(avatarURL)
                    .into(drawerAvatar!!)

            if (SteamService.extras != null && SteamService.extras!!.containsKey("username")) {
                val key = "avatar_" + SteamService.extras!!.getString("username")!!
                PreferenceManager.getDefaultSharedPreferences(this).edit().putString(key, avatarURL).apply()
            }
        }
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
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            toggleDrawer()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val activeFragment = supportFragmentManager.findFragmentById(R.id.content_frame)
            if (activeFragment is FragmentWeb) {
                // go *back* if possible
                if (activeFragment.onBackPressed())
                    return true
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
        if (drawerLayout!!.isDrawerOpen(GravityCompat.START)) {
            drawerLayout!!.closeDrawer(GravityCompat.START)
        } else {
            // hide IME
            val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            if (this.currentFocus != null)
                inputManager.hideSoftInputFromWindow(this.currentFocus!!.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)

            drawerLayout!!.openDrawer(GravityCompat.START)
        }
    }

    override fun onNavigationItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.nav_friends -> browseToFragment(FragmentFriends(), true)
            R.id.nav_games -> browseToFragment(FragmentLibrary(), true)
            R.id.nav_browser -> browseToFragment(FragmentWeb(), true)
            R.id.nav_settings -> {
                //Settings doesn't utilize Fragmentbase, force title.
                setTitle(R.string.nav_settings)
                browseToFragment(FragmentSettings(), true)
            }
            R.id.nav_about -> AboutDialog.newInstance().show(supportFragmentManager, AboutDialog.TAG)
            R.id.nav_signout -> {
                disconnectWithDialog(this, getString(R.string.signingout))
                return true
            }
            else -> return true
        }

        drawerLayout!!.closeDrawer(GravityCompat.START)
        return true
    }

    private fun disconnectWithDialog(context: Context, message: String) {
        @SuppressLint("StaticFieldLeak")
        class SteamDisconnectTask : AsyncTask<Void, Void, Void>() {
            private var dialog: NewProgressDialog? = null

            override fun doInBackground(vararg params: Void): Void? {
                // this is really goddamn slow
                steamUser.logOff()
                SteamService.attemptReconnect = false
                if (SteamService.singleton != null) {
                    SteamService.singleton!!.disconnect()
                }

                return null
            }

            override fun onPreExecute() {
                super.onPreExecute()
                dialog = NewProgressDialog(context)
                dialog!!.setCancelable(false)
                dialog!!.setMessage(message)
                dialog!!.show()
            }

            override fun onPostExecute(result: Void) {
                super.onPostExecute(result)
                try {
                    dialog!!.dismiss()
                } catch (e: IllegalArgumentException) {
                    e.printStackTrace()
                }

                // go back to login screen
                val intent = Intent(this@MainActivity, LoginActivity::class.java)
                this@MainActivity.startActivity(intent)
                Toast.makeText(this@MainActivity, R.string.signed_out, Toast.LENGTH_LONG).show()
                finish()
            }
        }
        SteamDisconnectTask().execute()
    }

    @Suppress("UNCHECKED_CAST")
    override fun onStart() {
        super.onStart()

        // fragments from intent
        val fragmentName = intent.getStringExtra("fragment")
        if (fragmentName != null) {
            var fragmentClass: Class<out androidx.fragment.app.Fragment>? = null
            try {
                fragmentClass = Class.forName(fragmentName) as Class<out androidx.fragment.app.Fragment> //Unchecked Cast
            } catch (e: ClassNotFoundException) {
                e.printStackTrace()
            }

            if (fragmentClass != null) {
                var fragment: androidx.fragment.app.Fragment? = null
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

    override fun onPause() {
        super.onPause()
        isActive = false
    }

    override fun onResume() {
        super.onResume()
        isActive = true
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : androidx.fragment.app.Fragment> getFragmentByClass(clazz: Class<T>): T? {
        val fragment = supportFragmentManager.findFragmentByTag(clazz.name)
        return if (fragment == null) null else fragment as T?
    }
}

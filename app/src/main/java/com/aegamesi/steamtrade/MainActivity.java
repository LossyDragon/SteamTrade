package com.aegamesi.steamtrade;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.design.widget.NavigationView.OnNavigationItemSelectedListener;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.aegamesi.steamtrade.fragments.FragmentAbout;
import com.aegamesi.steamtrade.fragments.FragmentFriends;
import com.aegamesi.steamtrade.fragments.FragmentLibrary;
import com.aegamesi.steamtrade.fragments.FragmentMe;
import com.aegamesi.steamtrade.fragments.FragmentProfile;
import com.aegamesi.steamtrade.fragments.FragmentSettings;
import com.aegamesi.steamtrade.fragments.FragmentWeb;
import com.aegamesi.steamtrade.steam.SteamMessageHandler;
import com.aegamesi.steamtrade.steam.SteamService;
import com.aegamesi.steamtrade.steam.SteamUtil;
import com.squareup.picasso.Picasso;

import java.util.Locale;

import uk.co.thomasc.steamkit.base.generated.steamlanguage.EPersonaState;
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EResult;
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.SteamFriends;
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.callbacks.FriendAddedCallback;
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.callbacks.PersonaStateCallback;
import uk.co.thomasc.steamkit.steam3.handlers.steamgamecoordinator.SteamGameCoordinator;
import uk.co.thomasc.steamkit.steam3.handlers.steamnotifications.SteamNotifications;
import uk.co.thomasc.steamkit.steam3.handlers.steamnotifications.callbacks.NotificationUpdateCallback;
import uk.co.thomasc.steamkit.steam3.handlers.steamtrading.SteamTrading;
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.SteamUser;
import uk.co.thomasc.steamkit.steam3.steamclient.callbackmgr.CallbackMsg;
import uk.co.thomasc.steamkit.steam3.steamclient.callbacks.DisconnectedCallback;
import uk.co.thomasc.steamkit.util.cSharp.events.ActionT;

//TODO: Interactive Notifications?
//TODO: fromHtml deprecated
//TODO: oops, broke emojies (Preening old libraries)

public class MainActivity extends AppCompatActivity implements SteamMessageHandler, OnNavigationItemSelectedListener {
	public /*static*/ MainActivity instance = null;
	public boolean isActive = false;

	public SteamFriends steamFriends;
	public SteamTrading steamTrade;
	public SteamGameCoordinator steamGC;
	public SteamUser steamUser;
	public SteamNotifications steamNotifications;
	public Toolbar toolbar;
	public ProgressBar progressBar;
	private DrawerLayout drawerLayout;
	private ImageView drawerAvatar;
	private TextView drawerName;
	private TextView drawerStatus;
	private CardView drawerNotifyCard;
	private TextView drawerNotifyText;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (!assertSteamConnection())
			return;

		setContentView(R.layout.activity_main);
		instance = this;

		// inform the user about SteamGuard restrictions
		if (SteamService.extras != null && SteamService.extras.getBoolean("alertSteamGuard", false)) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					if (SteamService.extras != null)
						SteamService.extras.putBoolean("alertSteamGuard", false);
				}
			});
			builder.setMessage(R.string.steamguard_new);
			builder.show();
		}

		// get the standard steam handlers
		SteamService.singleton.messageHandler = this;
		steamTrade = SteamService.singleton.steamClient.getHandler(SteamTrading.class);
		steamUser = SteamService.singleton.steamClient.getHandler(SteamUser.class);
		steamFriends = SteamService.singleton.steamClient.getHandler(SteamFriends.class);
		steamGC = SteamService.singleton.steamClient.getHandler(SteamGameCoordinator.class);
		steamNotifications = SteamService.singleton.steamClient.getHandler(SteamNotifications.class);

		// set up the toolbar
		progressBar = findViewById(R.id.progress_bar);
		toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setHomeButtonEnabled(true);

			drawerLayout = findViewById(R.id.drawer_layout);
			ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close){
				@Override
				public void onDrawerSlide(View drawerView, float slideOffset) {
					super.onDrawerSlide(drawerView, 0);
				}
			};
			drawerLayout.addDrawerListener(toggle);
			toggle.syncState();

			NavigationView navigationView = findViewById(R.id.nav_view);
			navigationView.setNavigationItemSelectedListener(this);

			// set up
			View drawerHeaderView = navigationView.getHeaderView(0);
			drawerAvatar = drawerHeaderView.findViewById(R.id.drawer_avatar);
			drawerName = drawerHeaderView.findViewById(R.id.drawer_name);
			drawerStatus = drawerHeaderView.findViewById(R.id.drawer_status);
			drawerNotifyCard = drawerHeaderView.findViewById(R.id.notify_card);
			drawerNotifyText = drawerHeaderView.findViewById(R.id.notify_text);
			drawerHeaderView.findViewById(R.id.drawer_profile).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					browseToFragment(new FragmentMe(), true);
				}
			});
		}

		// set up the nav drawer
		updateDrawerProfile();
		updateDrawerProfile();

		if (savedInstanceState == null) {
			browseToFragment(new FragmentMe(), false);
		}

		// handle our URL stuff
		if (getIntent() != null && ((getIntent().getAction() != null && getIntent().getAction().equals(Intent.ACTION_VIEW)) || getIntent().getStringExtra("url") != null)) {
			String url;
			url = getIntent().getStringExtra("url");
			if (url == null) {
				url = getIntent().getData().toString();
			}/* else {
				url = url.substring(url.indexOf("steamcommunity.com") + ("steamcommunity.com".length()));
			}*/
			Log.d("Ice", "Received url: " + url);

			if (url.contains("steamcommunity.com/linkfilter/?url=")) {
				// don't filter these...
				String new_url = url.substring(url.indexOf("/linkfilter/?url=") + "/linkfilter/?url=".length());
				Log.d("Ice", "Passing through linkfilter url: '" + new_url + "'");
				Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(new_url));
				startActivity(browserIntent);
			}else if (url.contains("steamcommunity.com/id/") || url.contains("steamcommunity.com/profiles/")) {
				Fragment fragment = new FragmentProfile();
				Bundle bundle = new Bundle();
				bundle.putString("url", url);
				fragment.setArguments(bundle);
				browseToFragment(fragment, true);
			} else {
				// default to steam browser
				FragmentWeb.openPage(this, url, false);
			}
		}
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		// Sync the toggle state after onRestoreInstanceState has occurred.
		//drawerToggle.syncState();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean assertSteamConnection() {
		boolean abort = SteamService.singleton == null || SteamService.singleton.steamClient == null || SteamService.singleton.steamClient.getSteamId() == null;
		if (abort) {
			// something went wrong. Go to login to be safe
			Intent intent = new Intent(this, LoginActivity.class);
			startActivity(intent);
			finish();
		}
		return !abort;
	}

	public void browseToFragment(Fragment fragment, boolean addToBackStack) {
		FragmentManager fragmentManager = getSupportFragmentManager();
		FragmentTransaction transaction = fragmentManager.beginTransaction();
		if (addToBackStack)
			transaction.addToBackStack(null);
		//else
		//	fragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);*/
		transaction.replace(R.id.content_frame, fragment, fragment.getClass().getName()).commit();
		drawerLayout.closeDrawer(GravityCompat.START);
	}

	private void updateDrawerProfile() {
		EPersonaState state = steamFriends.getPersonaState();
		String name = steamFriends.getPersonaName();
		String avatar = SteamUtil.bytesToHex(steamFriends.getFriendAvatar(SteamService.singleton.steamClient.getSteamId())).toLowerCase(Locale.US);

		drawerName.setText(name);
		drawerStatus.setText(getResources().getStringArray(R.array.persona_states)[state.v()]);
		drawerName.setTextColor(ContextCompat.getColor(this, R.color.steam_online));
		drawerStatus.setTextColor(ContextCompat.getColor(this, R.color.steam_online));

		int notifications = steamNotifications.getTotalNotificationCount();
		drawerNotifyText.setText(String.format(Locale.US, "%1$d", notifications));
		drawerNotifyCard.setCardBackgroundColor(ContextCompat.getColor(this, notifications == 0 ? R.color.notification_off : R.color.notification_on));

		if (!avatar.equals("0000000000000000000000000000000000000000")) {
			String avatarURL = "http://media.steampowered.com/steamcommunity/public/images/avatars/" + avatar.substring(0, 2) + "/" + avatar + "_full.jpg";

			//Drawer Profile picture.
			Picasso.with(this)
					.load(avatarURL)
					.placeholder(R.drawable.default_avatar)
					.into(drawerAvatar);

			if (SteamService.extras != null && SteamService.extras.containsKey("username")) {
				String key = "avatar_" + SteamService.extras.getString("username");
				PreferenceManager.getDefaultSharedPreferences(this).edit().putString(key, avatarURL).apply();
			}
		}
	}

	@Override
	public void handleSteamMessage(CallbackMsg msg) {
		msg.handle(DisconnectedCallback.class, new ActionT<DisconnectedCallback>() {
			@Override
			public void call(DisconnectedCallback obj) {
				// go back to the login screen
				// only if currently active
				if (isActive) {
					Intent intent = new Intent(MainActivity.this, LoginActivity.class);
					MainActivity.this.startActivity(intent);
					//Toast.makeText(MainActivity.this, R.string.error_disconnected, Toast.LENGTH_LONG).show();
					Snackbar.make(findViewById(R.id.main), R.string.error_disconnected, Snackbar.LENGTH_LONG).show();
					finish();
				}
			}
		});
		msg.handle(PersonaStateCallback.class, new ActionT<PersonaStateCallback>() {
			@Override
			public void call(PersonaStateCallback obj) {
				if (obj.getFriendID().equals(steamUser.getSteamId())) {
					// update current user avatar / drawer
					steamFriends.cache.getLocalUser().avatarHash = obj.getAvatarHash();
					updateDrawerProfile();
				}
			}
		});
		msg.handle(FriendAddedCallback.class, new ActionT<FriendAddedCallback>() {
			@Override
			public void call(FriendAddedCallback obj) {
				if (obj.getResult() != EResult.OK) {
					//Toast.makeText(MainActivity.this, String.format(getString(R.string.friend_add_fail), obj.getResult().toString()), Toast.LENGTH_LONG).show();
					Snackbar.make(findViewById(R.id.main), String.format(getString(R.string.friend_add_fail), obj.getResult().toString()), Snackbar.LENGTH_LONG).show();
				} else {
					//Toast.makeText(MainActivity.this, getString(R.string.friend_add_success), Toast.LENGTH_LONG).show();
					Snackbar.make(findViewById(R.id.main), getString(R.string.friend_add_success), Snackbar.LENGTH_LONG).show();

				}
			}
		});
		msg.handle(NotificationUpdateCallback.class, new ActionT<NotificationUpdateCallback>() {
			@Override
			public void call(NotificationUpdateCallback obj) {
				updateDrawerProfile();
			}
		});


		// Now, we find the fragments and pass the message on that way
		FragmentManager fragmentManager = getSupportFragmentManager();
		for (Fragment fragment : fragmentManager.getFragments()) {
			if (fragment instanceof SteamMessageHandler) {
				((SteamMessageHandler) fragment).handleSteamMessage(msg);
			}
		}
	}

	@Override
	public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_MENU) {
			toggleDrawer();
			return true;
		}
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			Fragment activeFragment = getSupportFragmentManager().findFragmentById(R.id.content_frame);
			if (activeFragment instanceof FragmentWeb) {
				// go *back* if possible
				if (((FragmentWeb) activeFragment).onBackPressed())
					return true;
			}
		}

		return super.onKeyUp(keyCode, event);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				toggleDrawer();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	//Toggle the drawer and hide the keyboard.
	public void toggleDrawer() {
		if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
			drawerLayout.closeDrawer(GravityCompat.START);
		} else {
			drawerLayout.openDrawer(GravityCompat.START);
		}

		// hide IME
		InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		if (inputManager != null && this.getCurrentFocus() != null)
			inputManager.hideSoftInputFromWindow(this.getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
	}

	@Override //New NavBar selector.
	public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
		int id = menuItem.getItemId();

		if (id == R.id.nav_friends){
			browseToFragment(new FragmentFriends(), true);
		}else if (id == R.id.nav_games){
			browseToFragment(new FragmentLibrary(), true);
		}else if (id == R.id.nav_browser){
			browseToFragment(new FragmentWeb(), true);
		}else if (id == R.id.nav_settings){
			browseToFragment(new FragmentSettings(), true);
		}else if (id == R.id.nav_about){
			browseToFragment(new FragmentAbout(), true);
		}else if (id == R.id.nav_signout){
			disconnectWithDialog(this, getString(R.string.signingout));
		}
		toolbar.setTitle(menuItem.getTitle());
		return true;
	}

	//TODO ProgressBar deprecated...
	private void disconnectWithDialog(final Context context, final String message) {
		@SuppressLint("StaticFieldLeak")
		class SteamDisconnectTask extends AsyncTask<Void, Void, Void> {
			private ProgressDialog dialog;

			@Override
			protected Void doInBackground(Void... params) {
				//this is really goddamn slow
				steamUser.logOff();
				SteamService.attemptReconnect = false;
				if (SteamService.singleton != null) {
					SteamService.singleton.disconnect();
				}

				return null;
			}

			@Override
			protected void onPreExecute() {
				super.onPreExecute();
				dialog = new ProgressDialog(context);
				dialog.setCancelable(false);
				dialog.setMessage(message);
				dialog.show();
			}

			@Override
			protected void onPostExecute(Void result) {
				super.onPostExecute(result);
				try {
					dialog.dismiss();
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
				}

				// go back to login screen
				Intent intent = new Intent(MainActivity.this, LoginActivity.class);
				MainActivity.this.startActivity(intent);
				Snackbar.make(findViewById(R.id.main), R.string.signed_out, Snackbar.LENGTH_LONG).show();
				finish();
			}
		}
		new SteamDisconnectTask().execute();
	}

	@Override
	protected void onPause() {
		super.onPause();
		isActive = false;
	}

	@Override
	protected void onResume() {
		super.onResume();
		isActive = true;
	}

	@Override
	protected void onStart() {
		super.onStart();
		// fragments from intent
		String fragmentName = getIntent().getStringExtra("fragment");
		if (fragmentName != null) {
			Class<? extends Fragment> fragmentClass = null;
			try {
				fragmentClass = (Class<? extends Fragment>) Class.forName(fragmentName);//No touchy
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
			if (fragmentClass != null) {
				Fragment fragment = null;
				try {
					fragment = fragmentClass.newInstance();
				} catch (Exception e) {
					e.printStackTrace();
				}

				if (fragment != null) {
					Bundle arguments = getIntent().getBundleExtra("arguments");
					if (arguments != null)
						fragment.setArguments(arguments);
					browseToFragment(fragment, getIntent().getBooleanExtra("fragment_subfragment", true));
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	public <T extends Fragment> T getFragmentByClass(Class<T> clazz) {
		Fragment fragment = getSupportFragmentManager().findFragmentByTag(clazz.getName());
		return fragment == null ? null : (T) fragment;
	}
}
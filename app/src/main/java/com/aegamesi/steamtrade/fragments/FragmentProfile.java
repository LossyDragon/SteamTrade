package com.aegamesi.steamtrade.fragments;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.aegamesi.steamtrade.R;
import com.aegamesi.steamtrade.lib.android.PicassoImageGetter;
import com.aegamesi.steamtrade.steam.SteamService;
import com.aegamesi.steamtrade.steam.SteamUtil;
import com.aegamesi.steamtrade.steam.SteamWeb;
import com.squareup.picasso.Picasso;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.hdodenhof.circleimageview.CircleImageView;
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EFriendRelationship;
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EPersonaState;
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EResult;
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.callbacks.IgnoreFriendCallback;
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.callbacks.PersonaStateCallback;
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.callbacks.ProfileInfoCallback;
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.callbacks.SteamLevelCallback;
import uk.co.thomasc.steamkit.steam3.steamclient.callbackmgr.CallbackMsg;
import uk.co.thomasc.steamkit.types.steamid.SteamID;
import uk.co.thomasc.steamkit.util.cSharp.events.ActionT;

public class FragmentProfile extends FragmentBase implements View.OnClickListener {
	public SteamID id;
	public int steam_level = -1;
	public EFriendRelationship relationship;
	public EPersonaState state;
	public String name;
	public String game;
	public String avatar;

	public CircleImageView avatarView;
	public TextView nameView;
	public TextView statusView;
	public TextView summaryView;
	public TextView levelView;
	public Button chatButton;
	public Button addFriendButton;
	public Button removeFriendButton;
	public Button blockFriendButton;
	public Button unblockFriendButton;
	public Button viewSteamButton;
	public Button viewSteamRepButton;
	public Button viewLibraryButton;

	public ProfileInfoCallback profile_info = null;
	public PersonaStateCallback persona_info = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (abort)
			return;

		if (getArguments().containsKey("steamId")) {
			id = new SteamID(getArguments().getLong("steamId"));
		} else {
			String url = getArguments().getString("url");

			if (url != null) {
				Matcher matcher = Pattern.compile("steamcommunity.com/id/([a-zA-Z0-9]+)").matcher(url);
				if (matcher.find()) {
					id = null;
					String vanity = matcher.group(1);
					(new ResolveVanityURLTask()).execute(vanity);
				} else {
					matcher = Pattern.compile("steamcommunity.com/profiles/([0-9]+)").matcher(url);
					if (matcher.find())
						id = new SteamID(Long.parseLong(matcher.group(1)));
					else
						id = null;
				}
			}
		}

	}

	@Override
	public void handleSteamMessage(CallbackMsg msg) {
		msg.handle(SteamLevelCallback.class, new ActionT<SteamLevelCallback>() {
			@Override
			public void call(SteamLevelCallback obj) {
				if (id != null && obj.getLevelMap().containsKey(id)) {
					steam_level = obj.getLevelMap().get(id);
					updateView();
				}
			}
		});
		msg.handle(ProfileInfoCallback.class, new ActionT<ProfileInfoCallback>() {
			@Override
			public void call(ProfileInfoCallback obj) {
				profile_info = obj;
				updateView();
			}
		});
		msg.handle(PersonaStateCallback.class, new ActionT<PersonaStateCallback>() {
			@Override
			public void call(PersonaStateCallback obj) {
				if (id != null && id.equals(obj.getFriendID())) {
					persona_info = obj;
					updateView();
				}
			}
		});
		msg.handle(IgnoreFriendCallback.class, new ActionT<IgnoreFriendCallback>() {
			@Override
			public void call(IgnoreFriendCallback obj) {
				boolean success = obj.getResult() == EResult.OK;
				int stringResource = success ? R.string.action_successful : R.string.action_failed;
				Toast.makeText(activity(), stringResource, Toast.LENGTH_SHORT).show();
				updateView();
			}
		});
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		inflater = activity().getLayoutInflater();
		View view = inflater.inflate(R.layout.fragment_profile, container, false);

		avatarView = view.findViewById(R.id.profile_avatar);
		nameView = view.findViewById(R.id.profile_name);
		statusView = view.findViewById(R.id.profile_status);
		summaryView = view.findViewById(R.id.profile_summary);
		levelView = view.findViewById(R.id.profile_level);
		chatButton = view.findViewById(R.id.profile_button_chat);
		removeFriendButton = view.findViewById(R.id.profile_button_remove_friend);
		addFriendButton = view.findViewById(R.id.profile_button_add_friend);
		viewSteamButton = view.findViewById(R.id.profile_button_viewsteam);
		viewSteamRepButton = view.findViewById(R.id.profile_button_viewsteamrep);
		blockFriendButton = view.findViewById(R.id.profile_button_block_friend);
		unblockFriendButton = view.findViewById(R.id.profile_button_unblock_friend);
		viewLibraryButton = view.findViewById(R.id.profile_button_library);

		chatButton.setOnClickListener(this);
		removeFriendButton.setOnClickListener(this);
		addFriendButton.setOnClickListener(this);
		viewSteamButton.setOnClickListener(this);
		viewSteamRepButton.setOnClickListener(this);
		blockFriendButton.setOnClickListener(this);
		unblockFriendButton.setOnClickListener(this);
		viewLibraryButton.setOnClickListener(this);

		nameView.setSelected(true);
		statusView.setSelected(true);
		summaryView.setMovementMethod(LinkMovementMethod.getInstance());

		updateView();
		return view;
	}

	@Override
	public void onStart() {
		super.onStart();

		requestInfo();
		// fetch the data
		//(new ProfileFetchTask()).execute();
	}

	public void updateView() {
		if (activity() == null || activity().steamFriends == null)
			return;

		relationship = activity().steamFriends.getFriendRelationship(id);
		if (relationship == EFriendRelationship.Friend || persona_info == null) {
			state = activity().steamFriends.getFriendPersonaState(id);
			relationship = activity().steamFriends.getFriendRelationship(id);
			name = activity().steamFriends.getFriendPersonaName(id);
			game = activity().steamFriends.getFriendGamePlayedName(id);
			avatar = SteamUtil.bytesToHex(activity().steamFriends.getFriendAvatar(id)).toLowerCase(Locale.US);
		} else {
			// use the found persona info stuff
			state = persona_info.getState();
			relationship = activity().steamFriends.getFriendRelationship(id);
			name = persona_info.getName();
			game = persona_info.getGameName();
			avatar = SteamUtil.bytesToHex(persona_info.getAvatarHash()).toLowerCase(Locale.US);
		}

		addFriendButton.setText((relationship == EFriendRelationship.RequestRecipient) ? R.string.friend_accept : R.string.friend_add);
		unblockFriendButton.setText((relationship == EFriendRelationship.RequestRecipient) ? R.string.friend_ignore : R.string.friend_unblock);

		if (profile_info != null) {
			String summary_raw = profile_info.getSummary();
			String summary = SteamUtil.parseBBCode(summary_raw);
			Html.ImageGetter imageGetter = new PicassoImageGetter(summaryView, summaryView.getContext());
			summaryView.setText(Html.fromHtml(summary, Html.FROM_HTML_MODE_LEGACY, imageGetter, null));
			summaryView.setMovementMethod(new LinkMovementMethod());
		}

		if (steam_level == -1) {
			levelView.setText(R.string.unknown);
		} else {
			levelView.setText(String.valueOf(steam_level));
		}

		setTitle(name);
		nameView.setText(name);

		//Profile loader for friend/user.
		//avatarView.setImageResource(R.drawable.default_avatar);
		if (avatar != null && avatar.length() == 40 && !avatar.equals("0000000000000000000000000000000000000000")) {
			Picasso.with(getContext())
					.load("http://media.steampowered.com/steamcommunity/public/images/avatars/" + avatar.substring(0, 2) + "/" + avatar + "_full.jpg")
					.into(avatarView);
		}

		if (game != null && game.length() > 0)
			statusView.setText(String.format(getString(R.string.profile_playing_game), game));
		else
			statusView.setText(state.toString());

		int color = ContextCompat.getColor(getContext(), R.color.steam_online);
		if (relationship == EFriendRelationship.Blocked || relationship == EFriendRelationship.Ignored || relationship == EFriendRelationship.IgnoredFriend)
			color = ContextCompat.getColor(getContext(), R.color.steam_blocked);
		else if (game != null && game.length() > 0)
			color = ContextCompat.getColor(getContext(), R.color.steam_game);
		else if (state == EPersonaState.Offline || state == null)
			color = ContextCompat.getColor(getContext(), R.color.steam_offline);

		nameView.setTextColor(color);
		statusView.setTextColor(color);
		avatarView.setBorderColor(color);

		// things to do if we are not friends
		boolean isFriend = relationship == EFriendRelationship.Friend || relationship == EFriendRelationship.IgnoredFriend;
		boolean isSelf = SteamService.singleton.steamClient.getSteamId().equals(id);
		boolean isBlocked = relationship == EFriendRelationship.Blocked || relationship == EFriendRelationship.Ignored || relationship == EFriendRelationship.IgnoredFriend;
		boolean isFriendRequest = relationship == EFriendRelationship.RequestRecipient;

		if (!isFriend) {
			statusView.setText(relationship.toString());
			addFriendButton.setEnabled(id != null);
		}

		//Fix relationship if you view yourself.
		if (relationship == EFriendRelationship.None && isSelf )
			statusView.setText(state.toString());

		// visibility of buttons and stuff
		addFriendButton.setVisibility((!isFriend && !isSelf && !isBlocked) ? View.VISIBLE : View.GONE);
		removeFriendButton.setVisibility((isFriend && !isSelf) ? View.VISIBLE : View.GONE);
		chatButton.setVisibility((isFriend && !isSelf && !isBlocked) ? View.VISIBLE : View.GONE);
		blockFriendButton.setVisibility((!isBlocked && !isSelf) ? View.VISIBLE : View.GONE);
		unblockFriendButton.setVisibility((isFriendRequest || isBlocked) ? View.VISIBLE : View.GONE);
	}

	private void requestInfo() {
		if (id != null) {
			if (profile_info == null) {
				activity().steamFriends.requestProfileInfo(id);
			}

			int request_flags = 1 | 2 | 4 | 8 | 16 | 32 | 64 | 128 | 256 | 512 | 1024;
			relationship = activity().steamFriends.getFriendRelationship(id);
			if (relationship != EFriendRelationship.Friend && persona_info == null) {
				SteamID myId = SteamService.singleton.steamClient.getSteamId();
				if (!id.equals(myId)) {
					activity().steamFriends.requestFriendInfo(id, request_flags);
				}
			}

			if (steam_level == -1) {
				activity().steamFriends.requestSteamLevel(id);
			}
		}
	}

	@Override
	public void onClick(View view) {
		if (view == removeFriendButton) {
			AlertDialog.Builder builder = new AlertDialog.Builder(activity());
			builder.setMessage(String.format(getString(R.string.friend_remove_message), activity().steamFriends.getFriendPersonaName(id)));
			builder.setTitle(R.string.friend_remove);
			builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					activity().steamFriends.removeFriend(FragmentProfile.this.id);
					Toast.makeText(activity(), String.format(getString(R.string.friend_removed), activity().steamFriends.getFriendPersonaName(FragmentProfile.this.id)), Toast.LENGTH_LONG).show();
					activity().browseToFragment(new FragmentFriends(), true);
				}
			});
			builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
				}
			});
			builder.create().show();
		}
		if (view == addFriendButton) {
			activity().steamFriends.addFriend(id);
			Toast.makeText(activity(), R.string.friend_add_success, Toast.LENGTH_LONG).show();
		}
		if (view == chatButton) {
			Fragment fragment = new FragmentChat();
			Bundle bundle = new Bundle();
			bundle.putLong("steamId", id.convertToLong());
			bundle.putBoolean("fromProfile", true);
			fragment.setArguments(bundle);
			activity().browseToFragment(fragment, true);
		}
		if (view == viewSteamButton) {
			String url = "http://steamcommunity.com/profiles/" + id.convertToLong();
			FragmentWeb.openPage(activity(), url, false);
		}
		if (view == viewSteamRepButton) {
			String steamRepUrl = "http://steamrep.com/profiles/" + id.convertToLong() + "/";
			Intent browse = new Intent(Intent.ACTION_VIEW, Uri.parse(steamRepUrl));
			startActivity(browse);
		}
		if (view == blockFriendButton) {
			activity().steamFriends.ignoreFriend(id, true);
		}
		if (view == unblockFriendButton) {
			activity().steamFriends.ignoreFriend(id, false);
		}
		if (view == viewLibraryButton) {
			Fragment fragment = new FragmentLibrary();
			Bundle bundle = new Bundle();
			bundle.putLong("id", id.convertToLong());
			fragment.setArguments(bundle);
			activity().browseToFragment(fragment, true);
		}
	}

	@SuppressLint("StaticFieldLeak")
	private class ResolveVanityURLTask extends AsyncTask<String, Void, SteamID> {
		@Override
		protected SteamID doInBackground(String... args) {
			String vanity = args[0];

			String api_url = "https://api.steampowered.com/ISteamUser/ResolveVanityURL/v1/?key=" + SteamUtil.webApiKey + "&format=json&vanityurl=" + vanity;
			String response = SteamWeb.fetch(api_url, "GET", null, "");
			if (response.length() == 0)
				return null;
			JSONObject response_obj;
			try {
				response_obj = new JSONObject(response);
				response_obj = response_obj.getJSONObject("response");
				int result = response_obj.getInt("success");
				if (result != 1)
					return null;

				String steam_id = response_obj.getString("steamid");
				long long_value = Long.parseLong(steam_id);
				return new SteamID(long_value);
			} catch (JSONException e) {
				e.printStackTrace();
				return null;
			}
		}

		@Override
		protected void onPostExecute(SteamID id) {
			FragmentProfile.this.id = id;
			requestInfo();
			updateView();
		}
	}
}
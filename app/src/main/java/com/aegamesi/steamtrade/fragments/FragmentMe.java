package com.aegamesi.steamtrade.fragments;

import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.aegamesi.steamtrade.R;
import com.aegamesi.steamtrade.steam.AccountLoginInfo;
import com.aegamesi.steamtrade.steam.SteamService;
import com.squareup.picasso.Picasso;

import uk.co.thomasc.steamkit.base.generated.steamlanguage.EPersonaState;
import uk.co.thomasc.steamkit.steam3.handlers.steamnotifications.callbacks.NotificationUpdateCallback;
import uk.co.thomasc.steamkit.steam3.handlers.steamnotifications.types.NotificationType;
import uk.co.thomasc.steamkit.steam3.steamclient.callbackmgr.CallbackMsg;
import uk.co.thomasc.steamkit.util.cSharp.events.ActionT;

public class FragmentMe extends FragmentBase implements OnClickListener, OnItemSelectedListener {
	public ImageView avatarView;
	public TextView nameView;
	public Spinner statusSpinner;
	public Button viewProfileButton;
	public Button changeNameButton;
	public Button changeGameButton;
	public Button buttonTwoFactor;
	public TextView notifyComments;
	public TextView notifyChat;

	public int[] states = new int[]{1, 3, 2, 4, 5, 6}; // online, away, busy, snooze, lookingtotrade, lookingtoplay

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (abort)
			return;
		Log.i("FragmentMe", "created");
	}

	@Override
	public void handleSteamMessage(CallbackMsg msg) {
		msg.handle(NotificationUpdateCallback.class, new ActionT<NotificationUpdateCallback>() {
			@Override
			public void call(NotificationUpdateCallback obj) {
				updateView();
			}
		});
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		inflater = activity().getLayoutInflater();
		View view = inflater.inflate(R.layout.fragment_me, container, false);
		avatarView = view.findViewById(R.id.profile_avatar);
		nameView = view.findViewById(R.id.profile_name);
		statusSpinner = view.findViewById(R.id.profile_status_spinner);
		viewProfileButton = view.findViewById(R.id.me_view_profile);
		changeNameButton = view.findViewById(R.id.me_set_name);
		changeGameButton = view.findViewById(R.id.me_set_game);
		buttonTwoFactor = view.findViewById(R.id.me_two_factor);
        notifyChat = view.findViewById(R.id.me_notify_chat);
		notifyComments = view.findViewById(R.id.me_notify_comments);
		notifyChat.setOnClickListener(this);
		notifyComments.setOnClickListener(this);

		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(activity(), R.array.allowed_states, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
		statusSpinner.setAdapter(adapter);
		statusSpinner.setOnItemSelectedListener(this);
		changeNameButton.setOnClickListener(this);
		changeGameButton.setOnClickListener(this);
		viewProfileButton.setOnClickListener(this);
		buttonTwoFactor.setOnClickListener(this);
		updateView();
		return view;
	}

	public void updateView() {
		if (activity() == null || activity().steamFriends == null)
			return;
		if (SteamService.singleton == null || SteamService.singleton.steamClient == null)
			return;

		EPersonaState state = activity().steamFriends.getPersonaState();
		String name = activity().steamFriends.getPersonaName();
		String userName = SteamService.singleton.username;
		String avatar = PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("avatar_" + userName, "null");

		setTitle(name);
		nameView.setText(name);
		statusSpinner.setSelection(stateToIndex(state));

		//FragmentMe profile icon.
		if (!avatar.equals("null"))
		{
			Picasso.with(getContext())
					.load(avatar)
					.placeholder(R.drawable.default_avatar)
					.into(avatarView);
		}


		nameView.setTextColor(ContextCompat.getColor(getContext(), R.color.steam_online));

		updateNotification(notifyChat, R.plurals.notification_messages, NotificationType.OFFLINE_MSGS);
		updateNotification(notifyComments, R.plurals.notification_comments, NotificationType.COMMENTS);
	}

	private void updateNotification(TextView textView, int plural, NotificationType type) {
		int num = activity().steamNotifications.getNotificationCounts().get(type);
		String text = getResources().getQuantityString(plural, num, num);
		textView.setText(text);
		textView.setTextColor(ContextCompat.getColor(getContext(), num == 0 ? R.color.notification_text_off : R.color.notification_text_on));
	}

	public int stateToIndex(EPersonaState state) {
		for (int i = 0; i < states.length; i++)
			if (states[i] == state.v())
				return i;
		return 0;
	}

	@Override
	public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
		if (parent == statusSpinner) {
			activity().steamFriends.setPersonaState(EPersonaState.f(states[pos]));
			updateView();
		}
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {

	}

	@Override
	public void onClick(View v) {
		if (v == viewProfileButton) {
			Fragment fragment = new FragmentProfile();
			Bundle bundle = new Bundle();
			bundle.putLong("steamId", SteamService.singleton.steamClient.getSteamId().convertToLong());
			fragment.setArguments(bundle);
			activity().browseToFragment(fragment, true);
		}
		if (v == changeNameButton) {
			//SteamFriends f = activity().steamFriends;
			//f.requestFriendInfo(new SteamID(76561198000739785L));
			AlertDialog.Builder alert = new AlertDialog.Builder(activity());
			alert.setTitle(R.string.change_display_name);
			alert.setMessage(R.string.change_display_name_prompt);
			final EditText input = new EditText(activity());
			input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
			input.setText(activity().steamFriends.getPersonaName());
			alert.setView(input);
			alert.setPositiveButton(R.string.change, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					String name = input.getText().toString().trim();
					if (name.length() != 0) {
						activity().steamFriends.setPersonaName(name);
						updateView();
					}
				}
			});
			alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
				}
			});
			alert.show();
		}

		if (v == buttonTwoFactor) {
			AccountLoginInfo info = AccountLoginInfo.readAccount(activity(), SteamService.singleton.username);
			if (info == null || info.loginkey == null || info.loginkey.isEmpty()) {
				Toast.makeText(activity(), R.string.steamguard_unavailable, Toast.LENGTH_LONG).show();
			} else {
				activity().browseToFragment(new FragmentSteamGuard(), true);
			}
		}
		if (v == notifyChat) {
			activity().browseToFragment(new FragmentFriends(), true);
		}
		if (v == notifyComments) {
			long my_id = SteamService.singleton.steamClient.getSteamId().convertToLong();
			String url = "http://steamcommunity.com/profiles/" + my_id + "/commentnotifications";
			FragmentWeb.openPage(activity(), url, false);
		}
	}
}
package com.aegamesi.steamtrade.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.v4.app.Fragment;

import com.aegamesi.steamtrade.MainActivity;
import com.aegamesi.steamtrade.R;
import com.aegamesi.steamtrade.steam.SteamMessageHandler;
import com.bumptech.glide.Glide;

import uk.co.thomasc.steamkit.steam3.steamclient.callbackmgr.CallbackMsg;

public class FragmentBase extends Fragment implements SteamMessageHandler {
	protected boolean abort = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// make sure we're actually connected to steam...
		abort = !activity().assertSteamConnection();
	}

	@Override
	public void onResume() {
		super.onResume();
		// make sure we're actually connected to steam...
		abort = !activity().assertSteamConnection();
	}

	public final MainActivity activity() {
		return (MainActivity) getActivity();
	}

	/* Toolbar Status text */
	public void setTitle(CharSequence title) {
		if (activity() != null && activity().toolbar != null) {
			activity().toolbarTextView.setText(title);
		}
	}

	/* Toolbar CircleImageView visibility */
	public void setToolBarAvatar(int vis){
		if (activity() != null && activity().toolbar != null) {
			activity().toolbarImageLayout.setVisibility(vis);
		}
	}

	/* Toolbar CircleImageView set color */
	public void setToolBarIconColor(@ColorInt int color) {
		if (activity() != null && activity().toolbar != null) {
			activity().toolbarImageView.setBorderColor(color);
		}
	}

	/* Toolbar CircleImageView set picture */
	public void setToolBarPicture(String uri) {
		if (activity() != null && activity().toolbar != null) {
			Glide.with(activity().getApplicationContext())
					.load(uri)
					.error(R.drawable.default_avatar)
					.into(activity().toolbarImageView);
		}
	}

	@Override
	public void handleSteamMessage(CallbackMsg msg) {
		// by default, do nothing
	}

	@SuppressWarnings({"SameReturnValue", "unused"})
	public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
		return false;
	}
}

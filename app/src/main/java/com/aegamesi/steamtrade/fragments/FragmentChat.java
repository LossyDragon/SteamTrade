package com.aegamesi.steamtrade.fragments;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.aegamesi.steamtrade.R;
import com.aegamesi.steamtrade.fragments.support.ChatAdapter;
import com.aegamesi.steamtrade.steam.DBHelper.ChatEntry;
import com.aegamesi.steamtrade.steam.SteamChatManager;
import com.aegamesi.steamtrade.steam.SteamChatManager.ChatReceiver;
import com.aegamesi.steamtrade.steam.SteamService;

import uk.co.thomasc.steamkit.base.generated.steamlanguage.EChatEntryType;
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.callbacks.FriendMsgCallback;
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.callbacks.FriendMsgHistoryCallback;
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.callbacks.PersonaStateCallback;
import uk.co.thomasc.steamkit.steam3.steamclient.callbackmgr.CallbackMsg;
import uk.co.thomasc.steamkit.types.steamid.SteamID;
import uk.co.thomasc.steamkit.util.cSharp.events.ActionT;

public class FragmentChat extends FragmentBase implements ChatReceiver {
    public SteamID ourID;
    public SteamID chatID;
    public ChatAdapter adapter;
    public RecyclerView chatList;
    public LinearLayoutManager layoutManager;
    public Cursor cursor = null;

    public View chatViewMain;
    public EditText chatInput;
    public ImageButton chatButton;
    public TextView chat_typing;
    public Handler typingHandler;
    public Runnable typingRunnable = null;
    private long time_last_read;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (abort)
            return;

        setHasOptionsMenu(true);

        ourID = SteamService.singleton.steamClient.getSteamId();
        chatID = new SteamID(getArguments().getLong("steamId"));
        time_last_read = activity().getPreferences(Context.MODE_PRIVATE).getLong("chat_read_" + ourID.convertToLong() + "_" + chatID.convertToLong(), 0);

        if (SteamService.singleton.chatManager.unreadMessages.contains(chatID)) {
            SteamService.singleton.chatManager.unreadMessages.remove(chatID);
            SteamService.singleton.chatManager.updateNotification();

            if (activity().getFragmentByClass(FragmentFriends.class) != null)
                activity().getFragmentByClass(FragmentFriends.class).adapter.notifyDataSetChanged();
        }

        // typing timer
        typingHandler = new Handler();
        typingRunnable = new Runnable() {
            @Override
            public void run() {
                if (chat_typing != null)
                    chat_typing.setVisibility(View.GONE);
            }
        };

        //Get message history from Steam.
        activity().steamFriends.requestFriendMessageHistory(chatID);
    }

    @Override
    public void onResume() {
        super.onResume();

        String friendName;
        friendName = activity().steamFriends.getFriendPersonaName(chatID);
        setTitle(String.format(getString(R.string.chat_with), friendName));


        // set up the cursor
        adapter.changeCursor(cursor = fetchCursor());

        activity().getPreferences(Context.MODE_PRIVATE).edit().putLong("chat_read_" + ourID.convertToLong() + "_" + chatID.convertToLong(), System.currentTimeMillis()).apply();

        if (SteamService.singleton != null && SteamService.singleton.chatManager != null && SteamService.singleton.chatManager.receivers != null)
            SteamService.singleton.chatManager.receivers.add(0, this);
    }

    @Override
    public void handleSteamMessage(CallbackMsg msg) {
        msg.handle(FriendMsgCallback.class, new ActionT<FriendMsgCallback>() {
            @Override
            public void call(FriendMsgCallback callback) {
                final EChatEntryType type = callback.getEntryType();

                if (type == EChatEntryType.Typing && callback.getSender().equals(chatID)) {
                    // set a timer for the thing
                    Log.d("SteamKit", "User is typing a message...");
                    if (chat_typing != null)
                        chat_typing.setVisibility(View.VISIBLE);
                    if (typingHandler != null) {
                        typingHandler.removeCallbacks(typingRunnable);
                        typingHandler.postDelayed(typingRunnable, 15 * 1000L);
                    }
                }
            }
        });
        msg.handle(PersonaStateCallback.class, new ActionT<PersonaStateCallback>() {
            @Override
            public void call(PersonaStateCallback obj) {
                if (chatID != null && chatID.equals(obj.getFriendID()))
                    updateView();
            }
        });
        msg.handle(FriendMsgHistoryCallback.class, new ActionT<FriendMsgHistoryCallback>() {
            @Override
            public void call(FriendMsgHistoryCallback obj) {
                if (obj.getSteamId().equals(chatID)) {
                    // updated list (already received...)
                    // scroll to bottom
                    if (cursor != null)
                        chatList.scrollToPosition(cursor.getCount() - 1);
                }
            }
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        inflater = activity().getLayoutInflater();
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        chatList = view.findViewById(R.id.chat);
        chatInput = view.findViewById(R.id.chat_input);
        chatButton = view.findViewById(R.id.chat_button);
        chat_typing = view.findViewById(R.id.chat_typing);
        chat_typing.setVisibility(View.GONE);

        chatInput.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (event == null)
                    return false;
                if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                    String message;
                    if ((message = chatInput.getText().toString().trim()).length() == 0)
                        return false;
                    chatInput.setText("");
                    SteamService.singleton.chatManager.sendMessage(chatID, message);
                    return true;
                }
                return false;
            }
        });
        chatButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                String message;
                if ((message = chatInput.getText().toString().trim()).length() == 0)
                    return;
                chatInput.setText("");
                SteamService.singleton.chatManager.sendMessage(chatID, message);
            }
        });

        boolean isCompact = PreferenceManager.getDefaultSharedPreferences(activity()).getBoolean("pref_chat_compact", false);
        adapter = new ChatAdapter(cursor, isCompact);
        adapter.time_last_read = time_last_read;
        layoutManager = new LinearLayoutManager(activity());
        layoutManager.setStackFromEnd(true);
        chatList.setHasFixedSize(true);
        chatList.setLayoutManager(layoutManager);
        chatList.setAdapter(adapter);

        chatViewMain = view.findViewById(R.id.chat_main);

        updateView();
        return view;
    }

    @Override   //Menu OnCreate, shows menu to View Friends profile.
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        String friendName = activity().steamFriends.getFriendPersonaName(chatID);
        inflater.inflate(R.menu.fragment_chat, menu);
        menu.findItem(R.id.friend_profile_page).setTitle(String.format(getString(R.string.friend_profile_page), friendName));
    }

    @Override //Menu onOptions selection, navigate to chosen profile fragment.
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.friend_profile_page) {
            SteamID id = (chatID);
            Fragment fragment = new FragmentProfile();
            Bundle bundle = new Bundle();
            bundle.putLong("steamId", id.convertToLong());
            fragment.setArguments(bundle);
            activity().browseToFragment(fragment, true);
        }
        return true;
    }


    //TODO: onPause or onDestroy is naughty. blanks chat when youtube PiP is focused (android M+)
    @Override
    public void onPause() {
        super.onPause();

        adapter.changeCursor(null);

        if (SteamService.singleton != null && SteamService.singleton.chatManager != null && SteamService.singleton.chatManager.receivers != null)
            SteamService.singleton.chatManager.receivers.remove(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Nullable
    private Cursor fetchCursor() {
        if (SteamService.singleton != null) {
            return SteamService.singleton.db().query(
                    ChatEntry.TABLE,                    // The table to query
                    new String[]{ChatEntry._ID, ChatEntry.COLUMN_TIME, ChatEntry.COLUMN_MESSAGE, ChatEntry.COLUMN_SENDER},
                    ChatEntry.COLUMN_OUR_ID + " = ? AND " + ChatEntry.COLUMN_OTHER_ID + " = ? AND " + ChatEntry.COLUMN_TYPE + " = ?",
                    new String[]{"" + ourID.convertToLong(), "" + chatID.convertToLong(), "" + SteamChatManager.CHAT_TYPE_CHAT},
                    null, // don't group the rows
                    null, // don't filter by row groups
                    ChatEntry.COLUMN_TIME + " ASC"
            );
        }
        return null;
    }

    public void updateView() {
        if (activity() == null || activity().steamFriends == null)
            return;

        String friendPersonaName = activity().steamFriends.getFriendPersonaName(chatID);
        adapter.setPersonaNames(activity().steamFriends.getPersonaName(), friendPersonaName);

        // do colors for profile view
        adapter.color_default = ContextCompat.getColor(getContext(), R.color.steam_online);

    }

    @Override
    public boolean receiveChatLine(long time, SteamID id_us, SteamID id_them, final boolean sent_by_us, int type, String message) {
        if (id_them.equals(chatID)) {
            activity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    adapter.changeCursor(cursor = fetchCursor());
                    // now scroll to bottom (if already near the bottom)
                    if (layoutManager.findLastVisibleItemPosition() > cursor.getCount() - 3)
                        chatList.scrollToPosition(cursor.getCount() - 1);

                    if (!sent_by_us && chat_typing != null)
                        chat_typing.setVisibility(View.GONE);
                }
            });
            return true;
        }
        return false;
    }
}
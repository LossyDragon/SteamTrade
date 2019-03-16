package com.aegamesi.steamtrade.fragments;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import com.aegamesi.steamtrade.MainActivity;
import com.aegamesi.steamtrade.R;
import com.aegamesi.steamtrade.steam.SteamService;
import com.aegamesi.steamtrade.steam.SteamTwoFactor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FragmentWeb extends FragmentBase {
    public WebView web_view;
    public String url = null;
    private boolean loaded_page = false;
    private boolean headless = false;
    private boolean forceDesktop = false;

    private SteamGuardJavascriptInterface steamGuardJavascriptInterface;

    public static void openPage(MainActivity activity, String url, boolean headless) {
        openPageWithTabs(activity, url, headless);
    }

    public static void openPageWithTabs(MainActivity activity, String url, boolean headless) {
        Bundle args = new Bundle();
        args.putBoolean("headless", headless);
        args.putString("url", url);
        FragmentWeb fragment = new FragmentWeb();
        fragment.setArguments(args);
        activity.browseToFragment(fragment, true);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (abort)
            return;

        setHasOptionsMenu(true);

        Bundle args = getArguments();
        if (args != null) {
            if (args.containsKey("url"))
                url = getArguments().getString("url");
            headless = args.getBoolean("headless", false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        setTitle(getString(R.string.nav_browser));

        if (web_view != null) {
            forceDesktop = PreferenceManager.getDefaultSharedPreferences(activity()).getBoolean("pref_desktop_mode", false);
            updateCookies();

            if (!loaded_page) {
                if (url == null) {
                    web_view.loadUrl("https://steamcommunity.com/profiles/" + SteamService.singleton.steamClient.getSteamId().convertToLong());
                } else {
                    web_view.loadUrl(url);
                }

                loaded_page = true;
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        inflater = activity().getLayoutInflater();
        View view = inflater.inflate(R.layout.fragment_web, container, false);
        web_view = view.findViewById(R.id.web_view);

        web_view.setWebViewClient(new SteamWebViewClient());
        web_view.setWebChromeClient(new SteamWebChromeClient());
        web_view.addJavascriptInterface(steamGuardJavascriptInterface = new SteamGuardJavascriptInterface(), "SGHandler");
        WebSettings web_settings = web_view.getSettings();
        web_settings.setJavaScriptEnabled(true);
        web_settings.setBuiltInZoomControls(true);

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();

        if (activity() != null) {
            //if (activity().tabs != null) {
            //	activity().tabs.setVisibility(View.GONE);
            //	activity().tabs.setOnTabSelectedListener(null);
            //	activity().tabs.removeAllTabs();
            //}

            if (activity().progressBar != null) {
                activity().progressBar.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_web, menu);

        if (headless) {
            menu.findItem(R.id.web_toggle_view).setVisible(false);
            menu.findItem(R.id.web_community).setVisible(false);
            menu.findItem(R.id.web_store).setVisible(false);
        } else {
            boolean desktop_mode = PreferenceManager.getDefaultSharedPreferences(activity()).getBoolean("pref_desktop_mode", false);
            menu.findItem(R.id.web_toggle_view).setChecked(desktop_mode);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.web_back:
                web_view.goBack();
                return true;
            case R.id.web_forward:
                web_view.goForward();
                return true;
            case R.id.web_refresh:
                web_view.reload();
                return true;
            case R.id.web_community:
                web_view.loadUrl("https://steamcommunity.com/profiles/" + SteamService.singleton.steamClient.getSteamId().convertToLong());
                return true;
            case R.id.web_store:
                web_view.loadUrl("https://store.steampowered.com/");
                return true;
            case R.id.web_toggle_view:
                // switch
                item.setChecked(!item.isChecked());
                forceDesktop = item.isChecked();
                updateCookies();
                PreferenceManager.getDefaultSharedPreferences(activity()).edit().putBoolean("pref_desktop_mode", forceDesktop).apply();
                web_view.reload();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void updateCookies() {
        List<String> cookies = new ArrayList<>();
        Collections.addAll(cookies, SteamService.generateSteamWebCookies().split(";"));
        cookies.add("forceMobile=" + ((!forceDesktop || headless) ? 1 : 0));
        cookies.add("dob=1"); // age check
        cookies.add("mobileClient=" + (headless ? "android" : ""));
        if (headless)
            cookies.add("mobileClientVersion=3125579+%282.1.4%29");

        CookieManager cookieManager = CookieManager.getInstance();
        for (String cookie : cookies) {
            cookieManager.setCookie("store.steampowered.com", cookie);
            cookieManager.setCookie("steamcommunity.com", cookie);
        }
    }

    public boolean onBackPressed() {
        if (web_view != null && web_view.canGoBack()) {
            web_view.goBack();
            return true;
        }

        return false;
    }

    private class SteamWebViewClient extends WebViewClient {
        // Need to figure this out a bit.
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest url) {
            String command;
            Log.i("FragmentWeb", "WebResourceRequest --> " + Uri.parse(url.getUrl().toString()));
            Log.i("FragmentWeb", "Scheme -->" + Uri.parse(url.getUrl().toString()).getScheme().equalsIgnoreCase("steammobile"));
            Log.d("FragmentWeb", "Command --> " + Uri.parse(url.getUrl().toString()).getHost());
            Log.d("FragmentWeb", "setTitle -->" + url.getUrl().getQueryParameterNames());

            if (Uri.parse(url.getUrl().toString()).getScheme().equalsIgnoreCase("steammobile")) {
                Log.d("FragmentWeb", "Captured url: " + Uri.parse(url.getUrl().toString()));
                command = Uri.parse(url.getUrl().toString()).getHost();

                if (command.equalsIgnoreCase("settitle")) {
                    setTitle(Uri.parse(url.getUrl().toString()).getQueryParameter("title"));

                }
                if (command.equalsIgnoreCase("openurl")) {
                    view.loadUrl(Uri.parse(url.getUrl().toString()).getQueryParameter("url"));
                }
                if (command.equalsIgnoreCase("reloadpage")) {
                    view.reload();
                }

                if (command.equalsIgnoreCase("steamguard") || Uri.parse(view.getUrl()).getHost().equalsIgnoreCase("steamcommunity")) {
                    String op = Uri.parse(url.getUrl().toString()).getQueryParameter("op");
                    if (op.equalsIgnoreCase("conftag")) {
                        String tag = Uri.parse(url.getUrl().toString()).getQueryParameter("arg1");
                        String go = SteamTwoFactor.generateConfirmationParameters(activity(), tag);

                        if (go.length() == 0)
                            steamGuardJavascriptInterface.setResultError();
                        else
                            steamGuardJavascriptInterface.setResultOkay(go);
                    }
                }
                return true;
            }

            view.loadUrl(url.getUrl().toString());
            return true;
        }
    }

    private class SteamWebChromeClient extends WebChromeClient {
        public void onProgressChanged(WebView view, int progress) {
            if (activity() != null && activity().progressBar != null) {
                ProgressBar loading_bar = activity().progressBar;
                if (progress < 100 && loading_bar.getVisibility() != View.VISIBLE)
                    loading_bar.setVisibility(View.VISIBLE);
                if (progress == 100)
                    loading_bar.setVisibility(View.GONE);

                loading_bar.setProgress(progress);
            }
        }
    }

    private class SteamGuardJavascriptInterface {
        private String returnCode = "";
        private String returnStatus = "";
        private String returnValue = "";

        void setResultOkay(String value) {
            returnStatus = "ok";
            returnValue = value == null ? "" : value;
        }

        void setResultError() {
            returnStatus = "error";
            returnValue = "";
            returnCode = "" + -1;
        }

        void setResultBusy() {
            returnValue = "";
            returnStatus = "busy";
        }

        @JavascriptInterface
        public String getResultCode() {
            return returnCode;
        }

        @JavascriptInterface
        public String getResultStatus() {
            return returnStatus;
        }

        @JavascriptInterface
        public String getResultValue() {
            String val = returnValue;
            setResultBusy();
            return val;
        }
    }
}
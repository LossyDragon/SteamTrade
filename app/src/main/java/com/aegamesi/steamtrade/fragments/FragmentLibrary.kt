package com.aegamesi.steamtrade.fragments

import android.annotation.SuppressLint
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.aegamesi.steamtrade.R
import com.aegamesi.steamtrade.fragments.adapters.LibraryAdapter
import com.aegamesi.steamtrade.steam.SteamService
import com.aegamesi.steamtrade.steam.SteamUtil
import com.aegamesi.steamtrade.steam.SteamWeb
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

class FragmentLibrary : FragmentBase(), View.OnClickListener {
    var adapterLibrary: LibraryAdapter? = null
    private lateinit var listGames: RecyclerView
    private var steamID: Long = 0

    lateinit var loadingView: View
    var games: ArrayList<LibraryEntry>? = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (abort)
            return

        setHasOptionsMenu(true)

        steamID = if (arguments != null && arguments!!.containsKey("id"))
            arguments!!.getLong("id")
        else
            SteamService.singleton!!.steamClient!!.steamId.convertToLong()
    }

    override fun onResume() {
        super.onResume()
        setTitle(getString(R.string.nav_games))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_library, container, false)

        loadingView = view.findViewById(R.id.offers_loading)
        listGames = view.findViewById(R.id.games_list)

        listGames.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        listGames.itemAnimator = DefaultItemAnimator()

        adapterLibrary = LibraryAdapter(activity()!!.applicationContext, this)
        adapterLibrary!!.setGames(games!!, adapterLibrary!!.currentSort)
        listGames.setHasFixedSize(true)

        listGames.adapter = adapterLibrary

        return view
    }

    override fun onStart() {
        super.onStart()

        if (SteamUtil.webApiKey != null && SteamUtil.webApiKey!!.isNotEmpty())
            FetchLibraryTask().execute()
        else
            Toast.makeText(activity(), R.string.api_key_not_loaded, Toast.LENGTH_LONG).show()
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        inflater!!.inflate(R.menu.search, menu)
        inflater.inflate(R.menu.fragment_library, menu)

        val search = menu!!.findItem(R.id.action_search)
        val searchView = search.actionView as SearchView

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return onQueryTextChange(query)
            }

            override fun onQueryTextChange(newText: String): Boolean {
                adapterLibrary!!.filter(newText)
                return true
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item!!.itemId) {
            R.id.menu_library_sort_name -> {
                if (adapterLibrary != null && adapterLibrary!!.currentSort != LibraryAdapter.SORT_ALPHABETICAL)
                    adapterLibrary!!.setGames(games!!, LibraryAdapter.SORT_ALPHABETICAL)
                true
            }

            R.id.menu_library_sort_playtime -> {
                if (adapterLibrary != null && adapterLibrary!!.currentSort != LibraryAdapter.SORT_PLAYTIME)
                    adapterLibrary!!.setGames(games!!, LibraryAdapter.SORT_PLAYTIME)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onClick(view: View) {
        if (view.id == R.id.game_card) {
            val entry = view.tag as LibraryEntry
            val appId = entry.appId

            val popup = PopupMenu(activity()!!, view)
            popup.setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.menu_library_store_page) {
                    val url = String.format(Locale.US, "http://store.steampowered.com/app/%d/", appId)
                    FragmentWeb.openPage(activity()!!, url, true)
                }
                true
            }
            popup.inflate(R.menu.fragment_library_action)
            popup.show()
        }
    }

    private fun fetchLibrary(): ArrayList<LibraryEntry> {
        var webApiUrl = "https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/?key=%s&format=json&steamID=%d&include_appinfo=1&include_played_free_games=1"
        webApiUrl = String.format(Locale.US, webApiUrl, SteamUtil.webApiKey, steamID)
        val response = SteamWeb.fetch(webApiUrl, "GET", null, "")

        return try {
            val games = ArrayList<LibraryEntry>()
            val json = JSONObject(response)
            if (json.has("response")) {
                val jsonGames = json.getJSONObject("response").getJSONArray("games")
                for (i in 0 until jsonGames.length()) {
                    games.add(LibraryEntry(jsonGames.getJSONObject(i)))
                }
            }
            games
        } catch (e: JSONException) {
            e.printStackTrace()
            ArrayList()
        }

    }

    class LibraryEntry internal constructor(json: JSONObject) {
        var appId: Int = 0
        var name: String
        var playtime2weeks: Double = 0.toDouble()
        var playtimeForever: Double = 0.toDouble()
        var imgLogoUrl: String
        //var imgIconUrl: String
        //var hasCommunityVisibleStats: Boolean = false

        init {
            appId = json.optInt("appid", 0)
            name = json.optString("name", "Unknown")
            playtime2weeks = json.optInt("playtime_2weeks", 0).toDouble() / 60.0
            playtimeForever = json.optInt("playtime_forever", 0).toDouble() / 60.0
            imgLogoUrl = json.optString("img_logo_url")
            //imgIconUrl = json.optString("img_icon_url")
            //hasCommunityVisibleStats = json.optBoolean("has_community_visible_stats", false)
        }
    }

    @SuppressLint("StaticFieldLeak")
    private inner class FetchLibraryTask : AsyncTask<Void, Void, ArrayList<LibraryEntry>>() {
        override fun doInBackground(vararg args: Void): ArrayList<LibraryEntry> {
            return fetchLibrary()
        }

        override fun onPreExecute() {
            loadingView.visibility = View.VISIBLE
        }

        override fun onPostExecute(result: ArrayList<LibraryEntry>) {
            if (activity() == null)
                return

            games = result
            adapterLibrary!!.setGames(games!!, adapterLibrary!!.currentSort)

            // get rid of UI stuff,
            loadingView.visibility = View.GONE
        }
    }
}
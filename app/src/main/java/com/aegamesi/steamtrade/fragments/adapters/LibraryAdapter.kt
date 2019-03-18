package com.aegamesi.steamtrade.fragments.adapters

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.aegamesi.steamtrade.R
import com.aegamesi.steamtrade.fragments.FragmentLibrary
import com.bumptech.glide.Glide
import java.util.ArrayList
import java.util.Locale
import kotlin.Comparator

class LibraryAdapter(private val context: Context,
                     private val fragment: FragmentLibrary) : RecyclerView.Adapter<LibraryAdapter.ViewHolderGame>() {

    var currentSort = SORT_ALPHABETICAL
    private var games: List<FragmentLibrary.LibraryEntry>? = null
    private val filteredList: MutableList<FragmentLibrary.LibraryEntry>?

    companion object {
        const val SORT_ALPHABETICAL = 0
        const val SORT_PLAYTIME = 1
    }

    init {
        filteredList = ArrayList()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderGame {
        return ViewHolderGame(parent)
    }

    override fun onBindViewHolder(h: ViewHolderGame, position: Int) {
        val entry = filteredList!![position]

        h.itemView.tag = entry
        h.imageHeader.setImageResource(R.drawable.default_game)

        if (entry.imgLogoUrl.isNotEmpty()) {
            val imageURL = "http://media.steampowered.com/steamcommunity/public/images/apps/%d/%s.jpg"
            val formattedURL = String.format(Locale.US, imageURL, entry.appId, entry.imgLogoUrl)

            Glide.with(context)
                    .load(formattedURL)
                    .into(h.imageHeader)
        }

        h.textName.text = entry.name

        var playtimeText = ""
        if (entry.playtimeForever > 0.0 && entry.playtime2weeks == 0.0) {
            val baseString = h.itemView.resources.getString(R.string.library_playtime)
            playtimeText = String.format(baseString, entry.playtimeForever)
        } else if (entry.playtimeForever > 0.0 && entry.playtime2weeks > 0.0) {
            val baseString = h.itemView.resources.getString(R.string.library_playtime_full)
            playtimeText = String.format(baseString, entry.playtime2weeks, entry.playtimeForever)
        }
        h.textPlaytime.text = playtimeText
        h.textPlaytime.visibility = if (playtimeText.isNotEmpty()) View.VISIBLE else View.GONE
    }

    override fun getItemViewType(position: Int): Int {
        return 0
    }

    override fun getItemCount(): Int {
        return filteredList?.size ?: 0
    }

    fun filter(by: String?) {
        filteredList!!.clear()
        if (games == null)
            return

        if (by == null || by.trim { it <= ' ' }.isEmpty()) {
            filteredList.addAll(games!!)
        } else {
            for (game in games!!)
                if (game.name.toLowerCase(Locale.ENGLISH).contains(by.toLowerCase(Locale.ENGLISH)))
                    filteredList.add(game)
        }

        notifyDataSetChanged()
    }

    fun setGames(list: MutableList<FragmentLibrary.LibraryEntry>, sort: Int) {

        if(sort == SORT_ALPHABETICAL) {
            Log.d("Lib Adapter", "sort() -> l1, l2 = A-Z sort")
            list.sortWith(Comparator {
                l1,
                l2 ->
                l1.name.compareTo(l2.name)
            })
        }
        else if (sort == SORT_PLAYTIME) {
            Log.d("Lib Adapter", "sort() -> l2, l1 = 9-0 sort.")
            list.sortWith(Comparator {
                l2,
                l1 ->
                l1.playtimeForever.compareTo(l2.playtimeForever)
            })
        }

        //list?.sortWith(Comparator { _, _ ->
        //    if (sort == SORT_ALPHABETICAL)
        //        list.sortedWith(Comparator {l1, l2-> l1.name.compareTo(l2.name) })
        //    if (sort == SORT_PLAYTIME)
        //        list.sortedWith(Comparator {l2, l1-> l1.playtimeForever.compareTo(l2.playtimeForever)})
        //    0
        //})
        currentSort = sort
        games = list
        filter("")
    }

    inner class ViewHolderGame(parent: ViewGroup) : ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.fragment_library_item, parent, false)) {
        var imageHeader: ImageView = itemView.findViewById(R.id.game_banner)
        var textName: TextView = itemView.findViewById(R.id.game_name)
        var textPlaytime: TextView = itemView.findViewById(R.id.game_playtime)

        init {
            itemView.setOnClickListener(fragment)
        }
    }
}

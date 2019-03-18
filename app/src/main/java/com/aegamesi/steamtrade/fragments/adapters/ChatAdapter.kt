package com.aegamesi.steamtrade.fragments.adapters

import android.database.Cursor
import android.graphics.PorterDuff.Mode
import androidx.recyclerview.widget.RecyclerView
import android.text.Html
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

import com.aegamesi.steamtrade.libs.AndroidUtil
import com.aegamesi.steamtrade.libs.CursorRecyclerAdapter
import com.aegamesi.steamtrade.libs.GlideImageGetter
import com.aegamesi.steamtrade.R
import com.aegamesi.steamtrade.steam.SteamUtil

class ChatAdapter(cursor: Cursor?, private val compact: Boolean) : CursorRecyclerAdapter<ChatAdapter.ViewHolder>(cursor), OnLongClickListener {
    //private static final String compactDateFormat = "yyyy-MM-dd HH:mm:ss a";
    var timeLastRead = 0L
    var defaultColor = 0
    private var nameUs: String? = null
    private var nameThem: String? = null

    fun setPersonaNames(name_us: String, name_them: String) {
        this.nameUs = name_us
        this.nameThem = name_them
    }

    override fun onBindViewHolder(holder: ChatAdapter.ViewHolder, cursor: Cursor) {
        // get the information from the cursor
        val columnTime = cursor.getLong(1)
        var columnMessage = cursor.getString(2)
        val columnSentByUs = cursor.getInt(3) == 0
        val expandedDividerTimeCutoff = (1000 * 60 * 10).toLong() // 10 minutes
        var expandedDivider = false
        var hideTime = false

        if (!cursor.isFirst) {
            cursor.moveToPrevious()
            val previousColumnTime = cursor.getLong(1)
            val previousColumnSentByUs = cursor.getInt(3) == 0

            expandedDivider = previousColumnSentByUs != columnSentByUs || columnTime - previousColumnTime > expandedDividerTimeCutoff
            cursor.moveToNext()
        }

        if (!cursor.isLast) {
            cursor.moveToNext()
            val nextColumnTime = cursor.getLong(1)
            val nextColumnSentByUs = cursor.getInt(3) == 0

            hideTime = nextColumnSentByUs == columnSentByUs && nextColumnTime - columnTime <= expandedDividerTimeCutoff
        }

        holder.itemView.tag = columnMessage
        // coloring
        val colorOffline = holder.itemView.resources.getColor(R.color.steam_offline, null)
        val colorOnline = holder.itemView.resources.getColor(R.color.steam_online, null)
        val bgColor = if (columnTime < timeLastRead) colorOffline else if (!columnSentByUs) defaultColor else colorOnline

        if (compact) {
            val personName: String = if (nameUs != null && nameThem != null)
                if (columnSentByUs) nameUs!! else nameThem!!
            else
                holder.itemView.resources.getString(if (columnSentByUs) R.string.chat_you else R.string.chat_them)

            columnMessage = TextUtils.htmlEncode(columnMessage) // escape html

            val date = AndroidUtil.getChatStyleTimeAgo(holder.itemView.context, columnTime, System.currentTimeMillis()).toString()
            val chatLine = String.format(compactLineFormat, bgColor and 0xFFFFFF, date, personName, columnMessage)

            holder.viewDivider!!.visibility = if (expandedDivider) View.VISIBLE else View.GONE

            val imageGetter = GlideImageGetter(holder.textMessage, holder.textMessage.context)
            val message = SteamUtil.parseEmoticons(chatLine)

            holder.textMessage.text = Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY, imageGetter, null)

        } else {
            // next adjust the layout
            val bubbleDrawable: Int = if (columnSentByUs)
                if (expandedDivider) R.drawable.chat_bubble_right else R.drawable.chat_bubble_right_min
            else
                if (expandedDivider) R.drawable.chat_bubble_left else R.drawable.chat_bubble_left_min

            holder.viewBubble!!.setBackgroundResource(bubbleDrawable)
            val bubbleParams = holder.viewBubble!!.layoutParams as FrameLayout.LayoutParams
            bubbleParams.gravity = if (columnSentByUs) Gravity.END else Gravity.START
            bubbleParams.leftMargin = holder.itemView.resources.getDimensionPixelSize(if (!columnSentByUs) R.dimen.chat_margin_minor else R.dimen.chat_margin_major)
            bubbleParams.rightMargin = holder.itemView.resources.getDimensionPixelSize(if (columnSentByUs) R.dimen.chat_margin_minor else R.dimen.chat_margin_major)
            bubbleParams.topMargin = holder.itemView.resources.getDimensionPixelSize(if (expandedDivider) R.dimen.chat_vertical_margin_major else R.dimen.chat_vertical_margin_minor)

            holder.viewBubble!!.background.setColorFilter(bgColor, Mode.MULTIPLY)

            val imageGetter = GlideImageGetter(holder.textMessage, holder.textMessage.context)

            columnMessage = TextUtils.htmlEncode(columnMessage) // escape html
            val message = SteamUtil.parseEmoticons(columnMessage)
            holder.textMessage.text = Html.fromHtml(message, Html.FROM_HTML_MODE_COMPACT, imageGetter, null)

            if (!hideTime) {
                holder.textStatus!!.text = AndroidUtil.getChatStyleTimeAgo(holder.textStatus!!.context, columnTime, System.currentTimeMillis())
                holder.textStatus!!.visibility = View.VISIBLE
            } else {
                holder.textStatus!!.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatAdapter.ViewHolder {
        return ViewHolder(parent)
    }

    override fun onLongClick(view: View): Boolean {
        if (view.tag != null) {
            val message = view.tag as String
            AndroidUtil.copyToClipboard(view.context, message)
            Toast.makeText(view.context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            return true
        }
        return false
    }

    inner class ViewHolder (parent: ViewGroup) : RecyclerView.ViewHolder(LayoutInflater.from(parent.context).inflate(if (compact) R.layout.fragment_chat_item_compact else R.layout.fragment_chat_item, parent, false)) {
        val textMessage: TextView = itemView.findViewById(R.id.chat_message)
        var textStatus: TextView? = null
        var viewBubble: View? = null
        var viewDivider: View? = null

        init {

            if (!compact) {
                textStatus = itemView.findViewById(R.id.chat_status)
                viewBubble = itemView.findViewById(R.id.chat_bubble)
            } else {
                viewDivider = itemView.findViewById(R.id.chat_divider)
            }

            itemView.tag = this
            itemView.setOnLongClickListener(this@ChatAdapter)
        }
    }

    companion object {
        private const val compactLineFormat = "<font color=\"#%06X\"><i>[%s]</i> <b>%s</b>:</font> %s"
    }
}
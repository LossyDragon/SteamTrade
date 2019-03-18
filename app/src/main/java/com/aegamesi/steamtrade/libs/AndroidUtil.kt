package com.aegamesi.steamtrade.libs

import android.content.Context
import android.content.DialogInterface
import android.graphics.Rect
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import android.text.format.DateFormat
import android.util.Base64
import android.view.View

import com.aegamesi.steamtrade.R
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.UnsupportedEncodingException
import java.lang.reflect.Type
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Calendar

object AndroidUtil {

    fun numCompare(x: Double, y: Double): Int {
        return java.lang.Double.compare(x, y)
    }

    fun numCompare(x: Long, y: Long): Int {
        return java.lang.Long.compare(x, y)
    }

    fun copyToClipboard(context: Context, str: String) {
        val sdk = android.os.Build.VERSION.SDK_INT
        if (sdk >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Chat Line", str)
            clipboard.primaryClip = clip
        } else {
            val clipboard = (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
            @Suppress("DEPRECATION")
            clipboard.text = str
        }
    }

    fun getChatStyleTimeAgo(context: Context, time_then: Long, time_now: Long): CharSequence {
        val calendarThen = Calendar.getInstance()
        val calendarNow = Calendar.getInstance()
        calendarThen.timeInMillis = time_then
        calendarNow.timeInMillis = time_now

        val timeDiff = ((time_now - time_then) / 1000).toInt()
        val resources = context.resources

        // in the future
        if (timeDiff < 0) {
            return resources.getString(R.string.time_in_future)
        }

        // less than 1 minute -> "Now"
        if (timeDiff < 60) {
            return resources.getString(R.string.time_now)
        }

        // between 1 minute and 60 minutes -> "x mins"
        if (timeDiff < 60 * 60) {
            val mins = timeDiff / 60
            return resources.getQuantityString(R.plurals.time_mins, mins, mins)
        }


        if (calendarThen.get(Calendar.YEAR) == calendarNow.get(Calendar.YEAR)) {
            // more than 60 minutes, but on the same day -> "X:XX P/AM"
            if (calendarThen.get(Calendar.DAY_OF_YEAR) == calendarNow.get(Calendar.DAY_OF_YEAR)) {
                return DateFormat.format("h:mm a", time_then)
            }

            // not the same day, but the same week --> "DAY X:XX P/AM"
            return if (calendarThen.get(Calendar.WEEK_OF_YEAR) == calendarNow.get(Calendar.WEEK_OF_YEAR)) {
                DateFormat.format("EEE h:mm a", time_then)
            } else DateFormat.format("MMM d, h:mm a", time_then)

            // same year --> "Month Day, time"
        }

        // default to "month day year, time"
        return DateFormat.format("MMM d yyyy, h:mm a", time_then)
    }

    fun createURIDataString(data: Map<String, Any>?): String {

        val dataStringBuffer = StringBuilder()
        if (data != null) {
            try {
                for ((key, value) in data) {
                    dataStringBuffer.append(
                            URLEncoder.encode(key, "UTF-8"))
                            .append("=").append(
                                    URLEncoder.encode(value.toString(), "UTF-8"))
                            .append("&")
                }
            } catch (e: UnsupportedEncodingException) {
                return ""
            }

        }
        return dataStringBuffer.toString()
    }

    @Throws(IOException::class)
    fun createCachedFile(context: Context, fileName: String, content: String) {
        val cacheFile = File(context.cacheDir.toString() + File.separator + fileName)
        cacheFile.parentFile.mkdirs()
        cacheFile.createNewFile()
        val fos = FileOutputStream(cacheFile)
        val osw = OutputStreamWriter(fos, StandardCharsets.UTF_8)
        val pw = PrintWriter(osw)
        pw.println(content)
        pw.flush()
        pw.close()
    }

    fun showBasicAlert(context: Context, title: String, message: String, callback: DialogInterface.OnClickListener?) {
        val alert = AlertDialog.Builder(context)
        alert.setTitle(title)
        alert.setMessage(message)
        alert.setNeutralButton(android.R.string.ok, callback)
        alert.setNegativeButton(android.R.string.cancel) { _, _ -> }
        alert.show()
    }


    // From https://gist.github.com/orip/3635246
    class ByteArrayToBase64TypeAdapter : JsonSerializer<ByteArray>, JsonDeserializer<ByteArray> {
        @Throws(JsonParseException::class)
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ByteArray {
            return Base64.decode(json.asString, Base64.NO_WRAP)
        }

        override fun serialize(src: ByteArray, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            return JsonPrimitive(Base64.encodeToString(src, Base64.NO_WRAP))
        }
    }

    //UI based functions, below.

    //RecyclerView spacer.
    class RecyclerViewSpacer(private val spacing: Int) : androidx.recyclerview.widget.RecyclerView.ItemDecoration() {

        override fun getItemOffsets(outRect: Rect, view: View, parent: androidx.recyclerview.widget.RecyclerView, state: androidx.recyclerview.widget.RecyclerView.State) {

            val position = parent.getChildAdapterPosition(view)
            outRect.left = spacing
            outRect.right = spacing
            outRect.bottom = spacing
            if (position < 1) {
                outRect.top = spacing
            }
        }
    }
}

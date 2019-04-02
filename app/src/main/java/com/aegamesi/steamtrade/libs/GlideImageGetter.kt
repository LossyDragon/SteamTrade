package com.aegamesi.steamtrade.libs

/*
https://github.com/bumptech/glide/issues/3328
 */

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Html
import android.util.DisplayMetrics
import android.view.View
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.Request
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.SizeReadyCallback
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition

//ImageGetter for chat emoticons.
class GlideImageGetter(t: View, private val context: Context) : Html.ImageGetter {
    private val textView: TextView = t as TextView
    private val pixelsToDp: Float

    init {
        val resources = context.resources
        val metrics = resources.displayMetrics
        pixelsToDp = metrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT
    }

    override fun getDrawable(source: String): Drawable {

        //Let's make the emoticons a bit larger. Was: 16.0f
        val size = (24.0f * pixelsToDp).toInt()
        val drawable = BitmapDrawablePlaceholder()

        Glide.with(context)
                .asBitmap()
                .load(source)
                .apply(RequestOptions().override(size, size))
                .into(drawable)

        return drawable
    }

    private inner class BitmapDrawablePlaceholder internal constructor() : BitmapDrawable(context.resources, Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)), Target<Bitmap> {

        var bitMapDrawable: Drawable? = null

        override fun draw(canvas: Canvas) {
            if (bitMapDrawable != null) {
                bitMapDrawable!!.draw(canvas)
            }
        }

        private fun setDrawable(drawable: Drawable) {
            this.bitMapDrawable = drawable
            val drawableWidth = drawable.intrinsicWidth
            val drawableHeight = drawable.intrinsicHeight
            val maxWidth = textView.measuredWidth
            if (drawableWidth > maxWidth) {
                val calculatedHeight = maxWidth * drawableHeight / drawableWidth
                drawable.setBounds(0, 0, maxWidth, calculatedHeight)
                setBounds(0, 0, maxWidth, calculatedHeight)
            } else {
                drawable.setBounds(0, 0, drawableWidth, drawableHeight)
                setBounds(0, 0, drawableWidth, drawableHeight)
            }

            textView.text = textView.text
        }

        override fun onLoadStarted(placeholderDrawable: Drawable?) {
            if (placeholderDrawable != null) {
                setDrawable(placeholderDrawable)
            }
        }

        override fun onLoadFailed(errorDrawable: Drawable?) {
            if (errorDrawable != null) {
                setDrawable(errorDrawable)
            }
        }

        override fun onResourceReady(bitmap: Bitmap, transition: Transition<in Bitmap>?) {
            setDrawable(BitmapDrawable(context.resources, bitmap))
        }

        override fun onLoadCleared(placeholderDrawable: Drawable?) {
            if (placeholderDrawable != null) {
                setDrawable(placeholderDrawable)
            }
        }

        override fun getSize(cb: SizeReadyCallback) {
            textView.post { cb.onSizeReady(textView.width, textView.height) }
        }

        override fun removeCallback(cb: SizeReadyCallback) {}

        override fun setRequest(request: Request?) {}

        override fun getRequest(): Request? {
            return null
        }

        override fun onStart() {}

        override fun onStop() {}

        override fun onDestroy() {}

    }
}
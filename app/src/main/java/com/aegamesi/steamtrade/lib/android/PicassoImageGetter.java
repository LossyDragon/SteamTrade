package com.aegamesi.steamtrade.lib.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

//ImageGetter for chat emoticons.
public class PicassoImageGetter implements Html.ImageGetter {
    private Context context;
    private TextView textView;

    public PicassoImageGetter(View t, Context context) {
        this.context = context;
        this.textView = (TextView) t;
    }

    @Override
    public Drawable getDrawable(String source) {
        Log.i("PicassoImageGetter", "Loading Emoticon: " + source);

        BitmapDrawablePlaceHolder drawable = new BitmapDrawablePlaceHolder();

        Picasso.with(context)
                .load(source)
                //TODO: Scale the resize?
                .resize(50, 50)
                .into(drawable);

        return drawable;
    }

    @SuppressWarnings("deprecation")
    private class BitmapDrawablePlaceHolder extends BitmapDrawable implements Target {
        protected Drawable drawable;

        @Override
        public void draw(final Canvas canvas) {
            if (drawable != null) {
                drawable.draw(canvas);
            }
        }

        public void setDrawable(Drawable drawable) {
            this.drawable = drawable;
            int width = drawable.getIntrinsicWidth();
            int height = drawable.getIntrinsicHeight();
            drawable.setBounds(0, 0, width, height);
            setBounds(0, 0, width, height);
            if (textView != null) {
                textView.setText(textView.getText());
            }
        }

        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
            setDrawable(new BitmapDrawable(context.getResources(), bitmap));
        }

        @Override
        public void onBitmapFailed(Drawable errorDrawable) {
            //Nothing
        }

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {
            //Nothing
        }
    }
}
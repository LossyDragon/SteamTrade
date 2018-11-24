package com.aegamesi.steamtrade.libs;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateFormat;
import android.util.Base64;
import android.view.View;

import com.aegamesi.steamtrade.R;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.squareup.picasso.Transformation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.Map;

public class AndroidUtil {

	public static int numCompare(double x, double y) {
		return Double.compare(x, y);
	}

	public static int numCompare(long x, long y) {
		return Long.compare(x, y);
	}

	@SuppressWarnings("deprecation")
	public static void copyToClipboard(Context context, String str) {
		int sdk = android.os.Build.VERSION.SDK_INT;
		if (sdk >= android.os.Build.VERSION_CODES.HONEYCOMB) {
			android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
			android.content.ClipData clip = android.content.ClipData.newPlainText("Chat Line", str);
			assert clipboard != null;
			clipboard.setPrimaryClip(clip);
		} else {
			android.text.ClipboardManager clipboard = (android.text.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
			assert clipboard != null;
			clipboard.setText(str);
		}
	}

	public static CharSequence getChatStyleTimeAgo(Context context, long time_then, long time_now) {
		Calendar cal_then = Calendar.getInstance();
		Calendar cal_now = Calendar.getInstance();
		cal_then.setTimeInMillis(time_then);
		cal_now.setTimeInMillis(time_now);

		int time_diff = (int) ((time_now - time_then) / 1000);
		Resources resources = context.getResources();

		// in the future
		if (time_diff < 0) {
			return resources.getString(R.string.time_in_future);
		}

		// less than 1 minute -> "Now"
		if (time_diff < 60) {
			return resources.getString(R.string.time_now);
		}

		// between 1 minute and 60 minutes -> "x mins"
		if (time_diff < 60 * 60) {
			int mins = time_diff / 60;
			return resources.getQuantityString(R.plurals.time_mins, mins, mins);
		}


		if (cal_then.get(Calendar.YEAR) == cal_now.get(Calendar.YEAR)) {
			// more than 60 minutes, but on the same day -> "X:XX P/AM"
			if (cal_then.get(Calendar.DAY_OF_YEAR) == cal_now.get(Calendar.DAY_OF_YEAR)) {
				return DateFormat.format("h:mm a", time_then);
			}

			// not the same day, but the same week --> "DAY X:XX P/AM"
			if (cal_then.get(Calendar.WEEK_OF_YEAR) == cal_now.get(Calendar.WEEK_OF_YEAR)) {
				return DateFormat.format("EEE h:mm a", time_then);
			}

			// same year --> "Month Day, time"
			return DateFormat.format("MMM d, h:mm a", time_then);
		}

		// default to "month day year, time"
		return DateFormat.format("MMM d yyyy, h:mm a", time_then);
	}

	public static String createURIDataString(Map<String, Object> data) {

		StringBuilder dataStringBuffer = new StringBuilder();
		if (data != null) {
			try {
				for (Map.Entry<String, Object> entry : data.entrySet()) {
					dataStringBuffer.append(
							URLEncoder.encode(entry.getKey(), "UTF-8"))
							.append("=").append(
							URLEncoder.encode(String.valueOf(entry.getValue()), "UTF-8"))
							.append("&");
				}
			} catch (UnsupportedEncodingException e) {
				return "";
			}
		}
		return dataStringBuffer.toString();
	}

	public static void createCachedFile(Context context, String fileName, String content) throws IOException {
		File cacheFile = new File(context.getCacheDir() + File.separator + fileName);
		cacheFile.getParentFile().mkdirs();
		cacheFile.createNewFile();
		FileOutputStream fos = new FileOutputStream(cacheFile);
		OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF8");
		PrintWriter pw = new PrintWriter(osw);
		pw.println(content);
		pw.flush();
		pw.close();
	}

	public static void showBasicAlert(Context context, String title, String message, DialogInterface.OnClickListener callback) {
		AlertDialog.Builder alert = new AlertDialog.Builder(context);
		alert.setTitle(title);
		alert.setMessage(message);
		alert.setNeutralButton(android.R.string.ok, callback);
		alert.setNegativeButton(android.R.string.cancel, (dialog, id) -> {
		});
		alert.show();
	}


	// From https://gist.github.com/orip/3635246
	public static class ByteArrayToBase64TypeAdapter implements JsonSerializer<byte[]>, JsonDeserializer<byte[]> {
		public byte[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			return Base64.decode(json.getAsString(), Base64.NO_WRAP);
		}

		public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
			return new JsonPrimitive(Base64.encodeToString(src, Base64.NO_WRAP));
		}
	}

	//UI based functions, below.

	//RecyclerView spacer.
	@SuppressWarnings("SameParameterValue")
	public static class RecyclerViewSpacer extends RecyclerView.ItemDecoration {

		final int spacing;
		public RecyclerViewSpacer(int i) {
			this.spacing = i;
		}

		public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state){

			if( outRect != null && parent != null) {
				int position = parent.getChildAdapterPosition(view);
				outRect.left = spacing;
				outRect.right = spacing;
				outRect.bottom = spacing;
				if (position < 1) {
					outRect.top = spacing;
				}
			}
		}
	}

	//Create a circled bitmap.
	public static class CircleTransform implements Transformation {
		@Override
		public Bitmap transform(Bitmap source) {
			final Bitmap output = Bitmap.createBitmap(source.getWidth(),
					source.getHeight(), Bitmap.Config.ARGB_8888);
			final Canvas canvas = new Canvas(output);

			final int color = Color.RED;
			final Paint paint = new Paint();
			final Rect rect = new Rect(0, 0, source.getWidth(), source.getHeight());
			final RectF rectF = new RectF(rect);

			paint.setAntiAlias(true);
			canvas.drawARGB(0, 0, 0, 0);
			paint.setColor(color);
			canvas.drawOval(rectF, paint);

			paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
			canvas.drawBitmap(source, rect, rect, paint);

			source.recycle();

			return output;
		}

		@Override
		public String key() {
			return "circle";
		}
	}
}

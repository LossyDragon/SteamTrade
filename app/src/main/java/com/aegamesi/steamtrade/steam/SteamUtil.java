package com.aegamesi.steamtrade.steam;

import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public class SteamUtil {
	private final static char[] hexArray = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
	private final static HashMap<String, String> bbCodeMap = new HashMap<>();
	public static String webApiKey = null; // kept in secret.xml

	static {
		bbCodeMap.put("(?i)\\[b\\](.+?)\\[/b\\]", "<b>$1</b>");
		bbCodeMap.put("(?i)\\[i\\](.+?)\\[/i\\]", "<i>$1</i>");
		bbCodeMap.put("(?i)\\[u\\](.+?)\\[/u\\]", "<u>$1</u>");
		bbCodeMap.put("(?i)\\[h1\\](.+?)\\[/h1\\]", "<h5>$1</h5>");
		bbCodeMap.put("(?i)\\[spoiler\\](.+?)\\[/spoiler\\]", "[SPOILER: $1]");
		bbCodeMap.put("(?i)\\[strike\\](.+?)\\[/strike\\]", "<strike>$1</strike>");
		bbCodeMap.put("(?i)\\[url\\](.+?)\\[/url\\]", "<a href=\"$1\">$1</a>");
		//noparse still parses inner tags.
		bbCodeMap.put("(?i)\\[noparse\\](.+?)\\[/noparse\\]", "$1");
		//Purposely expose URL(Security), but have URL-Summary in brackets.
		bbCodeMap.put("(?i)\\[url=(.+?)\\](.+?)\\[/url\\]", "[$2] $1");
	}

	static byte[] calculateSHA1(byte[] data) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA1");
			return md.digest(data);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static String bytesToHex(byte[] bytes) {
		if (bytes == null)
			return "0000000000000000000000000000000000000000";
		char[] hexChars = new char[bytes.length * 2];
		int v;
		for (int j = 0; j < bytes.length; j++) {
			v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	public static String parseBBCode(String source) {
		source = parseEmoticons(source);

		for (Map.Entry entry : bbCodeMap.entrySet())
			source = source.replaceAll(entry.getKey().toString(), entry.getValue().toString());

		Log.d("BB", source);
		return source;
	}

	public static String parseEmoticons(String source) {
		return source.replaceAll("\u02D0([a-zA-Z0-9_]+)\u02D0", "<img src=\"http://steamcommunity-a.akamaihd.net/economy/emoticon/$1\">").replaceAll("(\r\n|\r|\n|\n\r)", "<br/>");
	}

}

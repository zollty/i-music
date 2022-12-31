package ch.blinkenlights.android.vanilla;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.io.UnsupportedEncodingException;

public class AndroidUtils {

	public static PendingIntent getActivity(Context context, Intent intent) {
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
			return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
		}
		return PendingIntent.getActivity(context, 0, intent, 0);
	}

	public static PendingIntent getService(Context context, Intent intent) {
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
			return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
		}
		return PendingIntent.getService(context, 0, intent, 0);
	}

	/**
	 * 字符串编码转换
	 * @param newCharset 新编码，例如UTF-8
	 */
	public static String changeEncode(final String str, final String newCharset) {
		if (str == null || str.length() == 0)
			return str;
		try {
			String iso = new String(str.getBytes(newCharset), "ISO-8859-1"); // ISO-8859-1
			return new String(iso.getBytes("ISO-8859-1"), newCharset);
		}
		catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
}

/*
 * Copyright (C) 2013-2019 Adrian Ulrich <adrian@blinkenlights.ch>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>. 
 */
 
package ch.blinkenlights.android.vanilla;

import android.util.Log;
import android.util.LruCache;
import ch.blinkenlights.bastp.Bastp;
import java.util.ArrayList;
import java.util.HashMap;

public class BastpUtil {
	/**
	 * Our global instance cache
	 */
	private RGLruCache rgCache;
	/**
	 * What we return & cache
	 */
	public class GainValues {
		public float album;
		public float track;
		public boolean found;
		public int seekStart;
		public int seekEnd;
	}
	/**
	 * LRU cache for ReplayGain values
	 */
	private class RGLruCache extends LruCache<String, GainValues> {
		public RGLruCache(int size) {
			super(size);
		}
	}


	public BastpUtil() {
		rgCache = new RGLruCache(64);
	}

	/**
	 * Returns a GainValues object for `path'
	 */
	public GainValues getReplayGainValues(String path) {
		if(path == null) {
			// path must not be null
			path = "//null\\";
		}

		GainValues cached = rgCache.get(path);
		if(cached == null) {
			cached = getReplayGainValuesFromFile(path);
			rgCache.put(path, cached);
		}
		return cached;
	}

	/**
	 *  Parse given file and return track,album replay gain values
	 */
	private GainValues getReplayGainValuesFromFile(String path) {
		HashMap tags  = (new Bastp()).getTags(path);
		GainValues gv = new GainValues();

		// normal replay gain
		if(tags.containsKey("REPLAYGAIN_TRACK_GAIN")) {
			gv.track = getFloatFromString((String)((ArrayList)tags.get("REPLAYGAIN_TRACK_GAIN")).get(0));
			gv.found = true;
		}
		if(tags.containsKey("REPLAYGAIN_ALBUM_GAIN")) {
			gv.album = getFloatFromString((String)((ArrayList)tags.get("REPLAYGAIN_ALBUM_GAIN")).get(0));
			gv.found = true;
		}

		// R128 replay gain
		boolean r128 = false;
		if(tags.containsKey("R128_BASTP_BASE_GAIN")) {
			// This is the gain found in the opus header which automatically gets applied by the media framework.
			// We therefore do not need to include it in our calculation, but we set the 'found' bit and reset
			// both album and track gain information as an opus file should only ever contain r128 gain infos.
			float base = getFloatFromString((String)((ArrayList)tags.get("R128_BASTP_BASE_GAIN")).get(0)) / 256.0f;
			if (base != 0.0f) {
				gv.track = 0;
				gv.album = 0;
				gv.found = true;
			}
		}
		if(tags.containsKey("R128_TRACK_GAIN")) {
			gv.track += getFloatFromString((String)((ArrayList)tags.get("R128_TRACK_GAIN")).get(0)) / 256.0f;
			gv.found = true;
			r128 = true;
		}
		if(tags.containsKey("R128_ALBUM_GAIN")) {
			gv.album += getFloatFromString((String)((ArrayList)tags.get("R128_ALBUM_GAIN")).get(0)) / 256.0f;
			gv.found = true;
			r128 = true;
		}

		if (r128) {
			gv.track += 5.0f;
			gv.album += 5.0f;
		}

		// Log.e("VanillaMusic", path + " tags: " + tags);
		if(tags.containsKey("COMMENT")) {
			final String[] comments = ((String)((ArrayList)tags.get("COMMENT")).get(0)).split(",",2);
			String[] comment = new String[comments.length];
			for(int i=0; i<comments.length; i++) {
				comment[i] = codeName(comments[i]);
				// Log.e("VanillaMusic", path + " tags: codeName " + comment[i]);
			}
			String seek=null, gain=null;
			if(comment[0].startsWith("SEEK")) {
				seek = comment[0].trim();
			} else if(comment.length>1 && comment[1].startsWith("SEEK")) {
				seek = comment[1].trim();
			}
			if(comment[0].startsWith("GAIN")) {
				gain = comment[0].trim();
			} else if(comment.length>1 && comment[1].startsWith("GAIN")) {
				gain = comment[1].trim();
			}
			if(seek != null) {
				String[] seeks = seek.split("-",2);
				try {
					gv.seekStart = Integer.valueOf(seeks[0].substring(4))*1000;
					if(seeks.length>1) {
						gv.seekEnd = Integer.valueOf(seeks[1])*1000;
					}
				} catch (Exception e) {
					// ignoring
				}
			}
			if(gain != null) {
				try {
					int customGain = Integer.valueOf(gain.substring(4));
					gv.track += customGain;
					gv.album += customGain;
				} catch (Exception e) {
					// ignoring
				}
			}
		}

		if(Math.abs(gv.track+gv.album)>0.0001) {
			gv.found = true;
		}

		return gv;
	}

	/**
	 * Parses common replayGain string values
	 */
	private float getFloatFromString(String rg_raw) {
		float rg_float = 0f;
		try {
			String nums = rg_raw.replaceAll("[^0-9.-]","");
			rg_float = Float.parseFloat(nums);
		} catch(Exception e) {}
		return rg_float;
	}

	// add by zollty
	private static String codeName(String s) {
		try {
			if (s.equals(new String(s.getBytes("ISO-8859-1"), "ISO-8859-1"))) {
				return new String (s.getBytes("ISO-8859-1"),"GBK");
			}
		} catch (Exception e) {
			Log.e("VanillaMusic", "DECODE: can't decode name " + s, e);
		}
		return s;
	}

}


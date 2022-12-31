/*
 * Copyright (C) 2016 - 2022 Adrian Ulrich <adrian@blinkenlights.ch>
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

package ch.blinkenlights.android.medialibrary;

import ch.blinkenlights.bastp.Bastp;
import android.media.MediaMetadataRetriever;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.io.FileInputStream;
import java.io.IOException;

import android.util.Log;
public class MediaMetadataExtractor extends HashMap<String, ArrayList<String>> {
	// Well known tags
	public final static String ALBUM        = "ALBUM";
	public final static String ALBUMARTIST  = "ALBUM_ARTIST";
	public final static String ARTIST       = "ARTIST";
	public final static String BITRATE      = "BITRATE";
	public final static String COMPOSER     = "COMPOSER";
	public final static String DISC_COUNT   = "DISC_COUNT";
	public final static String DISC_NUMBER  = "DISC_NUMBER";
	public final static String DURATION     = "DURATION";
	public final static String GENRE        = "GENRE";
	public final static String MIME_TYPE    = "MIME";
	public final static String TRACK_COUNT  = "TRACK_COUNT";
	public final static String TRACK_NUMBER = "TRACK_NUM";
	public final static String TITLE        = "TITLE";
	public final static String YEAR         = "YEAR";

	/**
	 * Regexp used to match a year in a date field
	 */
	private static final Pattern sFilterYear = Pattern.compile(".*(\\d{4}).*");
	/**
	 * Regexp matching the first lefthand integer
	 */
	private static final Pattern sFilterLeftInt = Pattern.compile("^0*(\\d+).*$");
	/**
	 * Regexp matching anything
	 */
	private static final Pattern sFilterAny = Pattern.compile("^([\\s\\S]*)$");
	/**
	 * Regexp matching possible genres
	 */
	private static final Pattern sFilterGenre = Pattern.compile("^\\s*\\(?(\\d+)\\)?\\s*$");
	/**
	 * Genres as defined by androids own MediaScanner.java
	 */
	private static final String[] ID3_GENRES = {
		"Blues",
		"Classic Rock",
		"Country",
		"Dance",
		"Disco",
		"Funk",
		"Grunge",
		"Hip-Hop",
		"Jazz",
		"Metal",
		"New Age",
		"Oldies",
		"Other",
		"Pop",
		"R&B",
		"Rap",
		"Reggae",
		"Rock",
		"Techno",
		"Industrial",
		"Alternative",
		"Ska",
		"Death Metal",
		"Pranks",
		"Soundtrack",
		"Euro-Techno",
		"Ambient",
		"Trip-Hop",
		"Vocal",
		"Jazz+Funk",
		"Fusion",
		"Trance",
		"Classical",
		"Instrumental",
		"Acid",
		"House",
		"Game",
		"Sound Clip",
		"Gospel",
		"Noise",
		"AlternRock",
		"Bass",
		"Soul",
		"Punk",
		"Space",
		"Meditative",
		"Instrumental Pop",
		"Instrumental Rock",
		"Ethnic",
		"Gothic",
		"Darkwave",
		"Techno-Industrial",
		"Electronic",
		"Pop-Folk",
		"Eurodance",
		"Dream",
		"Southern Rock",
		"Comedy",
		"Cult",
		"Gangsta",
		"Top 40",
		"Christian Rap",
		"Pop/Funk",
		"Jungle",
		"Native American",
		"Cabaret",
		"New Wave",
		"Psychadelic",
		"Rave",
		"Showtunes",
		"Trailer",
		"Lo-Fi",
		"Tribal",
		"Acid Punk",
		"Acid Jazz",
		"Polka",
		"Retro",
		"Musical",
		"Rock & Roll",
		"Hard Rock",
		// The following genres are Winamp extensions
		"Folk",
		"Folk-Rock",
		"National Folk",
		"Swing",
		"Fast Fusion",
		"Bebob",
		"Latin",
		"Revival",
		"Celtic",
		"Bluegrass",
		"Avantgarde",
		"Gothic Rock",
		"Progressive Rock",
		"Psychedelic Rock",
		"Symphonic Rock",
		"Slow Rock",
		"Big Band",
		"Chorus",
		"Easy Listening",
		"Acoustic",
		"Humour",
		"Speech",
		"Chanson",
		"Opera",
		"Chamber Music",
		"Sonata",
		"Symphony",
		"Booty Bass",
		"Primus",
		"Porn Groove",
		"Satire",
		"Slow Jam",
		"Club",
		"Tango",
		"Samba",
		"Folklore",
		"Ballad",
		"Power Ballad",
		"Rhythmic Soul",
		"Freestyle",
		"Duet",
		"Punk Rock",
		"Drum Solo",
		"A capella",
		"Euro-House",
		"Dance Hall",
		"Goa",
		"Drum & Bass",
		"Club-House",
		"Hardcore",
		"Terror",
		"Indie",
		"Britpop",
		"Negerpunk",
		"Polsk Punk",
		"Beat",
		"Christian Gangsta",
		"Heavy Metal",
		"Black Metal",
		"Crossover",
		"Contemporary Christian",
		"Christian Rock",
		"Merengue",
		"Salsa",
		"Thrash Metal",
		"Anime",
		"JPop",
		"Synthpop",
		// 148 goes here, Winamp 5.6 would have 148 -> 191 these ¯\_(ツ)_/¯
	};

	/**
	 * True if we consider the file to be a good media item
	 */
	private boolean mIsMediaFile = false;
	/**
	 * True if we should try bastp for 'experimental' formats
	 */
	private boolean mForceBastp = false;

	/**
	 * Constructor for MediaMetadataExtractor
	 *
	 * @param path the path to scan
	 */
	public MediaMetadataExtractor(String path) {
		this(path, false);
	}

	/**
	 * Constructor for MediaMetadataExtractor
	 *
	 * @param path the path to scan
	 * @param forceBastp always prefer bastp if possible
	 */
	public MediaMetadataExtractor(String path, boolean forceBastp) {
		mForceBastp = forceBastp;
		extractMetadata2(path);
	}

	/**
	 * Returns the first element matching this key, null on if not found
	 *
	 * @param key the key to look up
	 * @return the value of the first entry, null if the key was not found
	 */
	public String getFirst(String key) {
		String result = null;
		if (containsKey(key))
			result = get(key).get(0);
		return result;
	}

	/**
	 * @return a human-friendly description of the mime type and bit rate
	 */
	public String getFormat() {
		StringBuilder sb = new StringBuilder(18);
		sb.append(decodeMimeType(getFirst(MIME_TYPE)));
		String bitrate = getFirst(BITRATE);
		if (bitrate != null && bitrate.length() > 3) {
			sb.append(' ')
				.append(bitrate.substring(0, bitrate.length() - 3))
				.append("kbps");
		}
		return sb.toString();
	}

	/**
	 * Returns true if this file contains any (interesting) data
	 * @return true if file is considered to be media data
	 */
	public boolean isMediaFile() {
		return mIsMediaFile;
	}

	/**
	 * Attempts to populate this instance with tags found in given path
	 *
	 * @param path the path to parse
	 */
	private void extractMetadata(String path) {
		if (!isEmpty())
			throw new IllegalStateException("Expected to be called on a clean HashMap");

		Log.v("VanillaMusic", "Extracting tags from "+path);

		HashMap bastpTags = (new Bastp()).getTags(path);
		MediaMetadataRetriever mediaTags = new MediaMetadataRetriever();
		boolean nativelyReadable = false;

		try {
			FileInputStream fis = new FileInputStream(path);
			try {
				mediaTags.setDataSource(fis.getFD());
				nativelyReadable = true;
			} catch (Exception e) {
				Log.v("VanillaMusic", "Error calling setDataSource for "+path+": "+e);
			}
			fis.close();
		} catch (Exception e) {
			nativelyReadable = false;
			Log.v("VanillaMusic", "Error creating fis for "+path+": "+e);
		}

		// Check if this is a usable audio file
		if (!nativelyReadable ||
		    mediaTags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == null ||
		    mediaTags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != null ||
		    mediaTags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) == null) {
			try {
				mediaTags.release();
			} catch (Exception e) {
				Log.v("VanillaMusic", "mediaTags.release() failed: " + e);
			}
			return;
		}

		// Bastp can not read the duration and bitrates, so we always get it from the system
		ArrayList<String> duration = new ArrayList<>(1);
		duration.add(mediaTags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
		this.put(DURATION, duration);

		ArrayList<String> bitrate = new ArrayList<>(1);
		bitrate.add(mediaTags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));
		this.put(BITRATE, bitrate);

		ArrayList<String> mime = new ArrayList<>(1);
		mime.add(mediaTags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE));
		this.put(MIME_TYPE, mime);


		// ...but we are using bastp for FLAC, OGG and OPUS as it handles them well
		// Everything else goes to the framework (such as pcm, m4a and mp3)
		String bastpType = (bastpTags.containsKey("type") ? (String)bastpTags.get("type") : "");
		switch (bastpType) {
			case "FLAC":
			case "OGG":
			case "OPUS":
				populateSelf(bastpTags);
				break;
			case "MP3/ID3v2":
			case "MP3/Lame":
			case "MP4":
				// ^-- these tagreaders are not fully stable, but can be enabled on demand
				if(mForceBastp) {
					populateSelf(bastpTags);
					break;
				}
				// else: fallthrough
			default:
				populateSelf(mediaTags);
		}
		convertNumericGenre();

		// We consider this a media file if it has some common tags OR
		// if bastp was able to parse it (which is stricter than Android's own parser)
		mIsMediaFile = (containsKey(TITLE) || containsKey(ALBUM) || containsKey(ARTIST) || !bastpType.equals(""));

		try {
			mediaTags.release();
		} catch (Exception e) {
			Log.v("VanillaMusic", "mediaTags.release() failed: " + e);
		}
	}


	private void extractMetadata2(String path) {
		if (!isEmpty())
			throw new IllegalStateException("Expected to be called on a clean HashMap");

		Log.v("VanillaMusic", "Extracting tags from "+path);

		HashMap bastpTags = (new Bastp()).getTags(path);
		MediaMetadataRetriever mediaTags = new MediaMetadataRetriever();
		boolean nativelyReadable = false;

		try {
			FileInputStream fis = new FileInputStream(path);
			try {
				mediaTags.setDataSource(fis.getFD());
				nativelyReadable = true;
			} catch (Exception e) {
				// IGNORE...
				// Log.v("VanillaMusic", "Error calling setDataSource for "+path+": "+e);
			}
			fis.close();
		} catch (Exception e) {
			nativelyReadable = false;
			Log.v("VanillaMusic", "Error creating fis for "+path+": "+e);
		}

		// modified by zollty 12 lines. fix mp3 can not be recognized bug, since KEY_HAS_AUDIO maybe null
		if (!nativelyReadable) {
			try {
				mediaTags.release();
			} catch (Exception e) {
				Log.v("VanillaMusic", "mediaTags.release() failed: " + e);
			}
			return;
		}
		//Log.v("VanillaMusic", "87RE path= " + path);
		//Log.v("VanillaMusic", "87RE METADATA_KEY_HAS_AUDIO " + mediaTags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));
		//Log.v("VanillaMusic", "87RE METADATA_KEY_HAS_VIDEO " + mediaTags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO));
		//Log.v("VanillaMusic", "87RE METADATA_KEY_DURATION " + mediaTags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
		// Check if this is a usable audio file
		if (mediaTags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != null ||
		    mediaTags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) == null) {
			try {
				mediaTags.release();
			} catch (Exception e) {
				Log.v("VanillaMusic", "mediaTags.release() failed: " + e);
			}
			//Log.i("VanillaMusic", "E05 MIME_TYPE = " + mediaTags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) + ", PATH = " + path);
			Log.e("VanillaMusic", "E05 " + path + ", not a usable audio file, will return directly");
			return;
		}

		// Bastp can not read the duration and bitrates, so we always get it from the system
		ArrayList<String> duration = new ArrayList<>(1);
		duration.add(mediaTags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
		this.put(DURATION, duration);

		ArrayList<String> bitrate = new ArrayList<>(1);
		bitrate.add(mediaTags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));
		this.put(BITRATE, bitrate);

		ArrayList<String> mime = new ArrayList<>(1);
		mime.add(mediaTags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE));
		this.put(MIME_TYPE, mime);


		// ...but we are using bastp for FLAC, OGG and OPUS as it handles them well
		// Everything else goes to the framework (such as pcm, m4a and mp3)
		String bastpType = (bastpTags.containsKey("type") ? (String)bastpTags.get("type") : "");
		switch (bastpType) {
			case "FLAC":
			case "OGG":
			case "OPUS":
				populateSelf(bastpTags);
				break;
			case "MP3/ID3v2":
			case "MP3/Lame":
			case "MP4":
				// ^-- these tagreaders are not fully stable, but can be enabled on demand
				if(mForceBastp) {
					populateSelf(bastpTags);
					break;
				}
				// else: fallthrough
			default:
				populateSelf(mediaTags);
		}
		convertNumericGenre();

		// We consider this a media file if it has some common tags OR
		// if bastp was able to parse it (which is stricter than Android's own parser)
		mIsMediaFile = (containsKey(TITLE) || containsKey(ALBUM) || containsKey(ARTIST) || !bastpType.equals(""));

		// add by zollty 2 lines, solve the non ISO-8859-1 character encoding error problem
		Log.v("VanillaMusic", "V01 mIsMediaFile = " + mIsMediaFile + ", PATH = " + path);
		handleMP3TagEncoding(path);

		try {
			mediaTags.release();
		} catch (Exception e) {
			Log.v("VanillaMusic", "mediaTags.release() failed: " + e);
		}
	}

	// add by zollty, solve the non ISO-8859-1 character encoding error problem
	private void handleMP3TagEncoding(String path) {
		if(path.endsWith(".mp3")) {
			String title = getFirst(MediaMetadataExtractor.TITLE);
			//Log.v("VanillaMusic", "HJD title = " + title + ", PATH = " + path);
			String album = getFirst(MediaMetadataExtractor.ALBUM);
			//Log.v("VanillaMusic", "HJD album = " + album + ", PATH = " + path);
			String artist = getFirst(MediaMetadataExtractor.ARTIST);
			//Log.v("VanillaMusic", "HJD artist = " + artist + ", PATH = " + path);
			if (!isEmpty(title)) {
				title = codeName(title);
				updateFirst(MediaMetadataExtractor.TITLE, title);
				//Log.v("VanillaMusic", "ds title = " + codeName(title) + ", PATH = " + path);
			}
			if (!isEmpty(album)) {
				album = codeName(album);
				updateFirst(MediaMetadataExtractor.ALBUM, album);
				//Log.v("VanillaMusic", "ds album = " + codeName(album) + ", PATH = " + path);
			}
			if (!isEmpty(artist)) {
				artist = codeName(artist);
				updateFirst(MediaMetadataExtractor.ARTIST, artist);
				//Log.v("VanillaMusic", "ds artist = " + codeName(artist) + ", PATH = " + path);
			}
		}
	}
	
	// add by zollty
	private void updateFirst(String key, String value) {
		ArrayList<String> md = this.get(key);
		md.clear();
		md.add(value);
	}
	
	// add by zollty
	private static boolean isEmpty(String s) {
		return s == null || s.trim().isEmpty();
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

	/**
	 * Populates `this' with tags read from bastp
	 *
	 * @param bastp A hashmap as returned by bastp
	 */
	private void populateSelf(HashMap bastp) {
		// mapping between vorbiscomment -> constant
		String[] map = new String[]{ "TITLE", TITLE, "ARTIST", ARTIST, "ALBUM", ALBUM, "ALBUMARTIST", ALBUMARTIST, "COMPOSER", COMPOSER, "GENRE", GENRE,
		                             "TRACKNUMBER", TRACK_NUMBER, "TRACKTOTAL", TRACK_COUNT, "DISCNUMBER", DISC_NUMBER, "DISCTOTAL", DISC_COUNT,
		                             "YEAR", YEAR };
		// switch to integer filter if i >= x
		int filterByIntAt = 12;
		// the filter we are normally using
		Pattern filter = sFilterAny;

		for (int i=0; i<map.length; i+=2) {
			if (i >= filterByIntAt)
				filter = sFilterLeftInt;

			if (bastp.containsKey(map[i])) {
				addFiltered(filter, map[i+1], (ArrayList<String>)bastp.get(map[i]));
			}
		}

		// If only one of (ARTIST, ALBUMARTIST) is present, populate the other
		if (!containsKey(ARTIST) && containsKey(ALBUMARTIST)) {
			put(ARTIST, get(ALBUMARTIST));
		}
		if (containsKey(ARTIST) && !containsKey(ALBUMARTIST)) {
			put(ALBUMARTIST, get(ARTIST));
		}

		// Try to guess YEAR from date field if only DATE was specified
		// We expect it to match \d{4}
		if (!containsKey(YEAR) && bastp.containsKey("DATE")) {
			addFiltered(sFilterYear, YEAR, (ArrayList<String>)bastp.get("DATE"));
		}

	}

	/**
	 * Populates `this' with tags read from the MediaMetadataRetriever
	 *
	 * @param tags a MediaMetadataRetriever object
	 */
	private void populateSelf(MediaMetadataRetriever tags) {
		int[] mediaMap = new int[] { MediaMetadataRetriever.METADATA_KEY_TITLE, MediaMetadataRetriever.METADATA_KEY_ARTIST, MediaMetadataRetriever.METADATA_KEY_ALBUM,
		                             MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, MediaMetadataRetriever.METADATA_KEY_COMPOSER, MediaMetadataRetriever.METADATA_KEY_GENRE,
		                             MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER, MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER, MediaMetadataRetriever.METADATA_KEY_YEAR };
		String[] selfMap = new String[]{  TITLE, ARTIST, ALBUM, ALBUMARTIST, COMPOSER, GENRE, DISC_NUMBER, TRACK_NUMBER, YEAR };
		int filterByIntAt = 6;
		Pattern filter = sFilterAny;

		for (int i=0; i<selfMap.length; i++) {
			String data = tags.extractMetadata(mediaMap[i]);
			if (i >= filterByIntAt)
				filter = sFilterLeftInt;

			if (data != null) {
				ArrayList<String> md = new ArrayList<String>(1);
				md.add(data);
				addFiltered(filter, selfMap[i], md);
			}
		}
	}

	/**
	 * Matches all elements of `data' with `filter' and adds the result as `key'
	 *
	 * @param filter the pattern to use, result is expected to be in capture group 1
	 * @param key the key to use for the data to put
	 * @param data the array list to inspect
	 */
	private void addFiltered(Pattern filter, String key, ArrayList<String> data) {
		ArrayList<String> list = new ArrayList<>();
		for (String s : data) {
			Matcher matcher = filter.matcher(s);
			if (matcher.matches()) {
				list.add(matcher.group(1).trim());
			}
		}
		if (list.size() > 0)
			put(key, list);
	}

	/**
	 * Detects legacy numeric-genre definitions and
	 * replaces them with one of ID3_GENRES[]
	 */
	private void convertNumericGenre() {
		int genreIdx = -1;
		String rawGenre = getFirst(GENRE);

		if (rawGenre == null)
			return; // no genre, nothing to do

		Matcher matcher = sFilterGenre.matcher(rawGenre);
		if (matcher.matches()) {
			try {
				genreIdx = Integer.parseInt(matcher.group(1));
			} catch (NumberFormatException e) {} // ignored
		}

		if (genreIdx >= 0 && genreIdx < ID3_GENRES.length) {
			ArrayList<String> data = new ArrayList<String>(1);
			data.add(ID3_GENRES[genreIdx]);
			remove(GENRE);
			addFiltered(sFilterAny, GENRE, data);
		}
	}

	/**
	 * Decode the given MIME type into a more human-friendly description.
	 *
	 * @return a human-friendly description of the MIME type
	 */
	private static String decodeMimeType(String mime)
	{
		if ("audio/mpeg".equals(mime)) {
			return "MP3";
		} else if ("audio/mp4".equals(mime)) {
			return "AAC";
		} else if ("audio/vorbis".equals(mime)) {
			return "Ogg Vorbis";
		} else if ("application/ogg".equals(mime)) {
			return "Ogg Vorbis";
		} else if ("audio/flac".equals(mime)) {
			return "FLAC";
		}
		return mime;
	}
}

/*
 * Copyright (C) 2015-2017 Adrian Ulrich <adrian@blinkenlights.ch>
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

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import ch.blinkenlights.android.medialibrary.MediaLibrary;


public class CoverCache {
	/**
	 * Display metrics as reported by the system during class creation.
	 */
	private final static DisplayMetrics METRICS = Resources.getSystem().getDisplayMetrics();
	/**
	 * Returned size of small album covers
	 * 44sp is the width & height of a library row
	 */
	public final static int SIZE_SMALL = (int)(44 * METRICS.density);
	/**
	 * Cover to use in remote views with a medium size.
	 */
	public final static int SIZE_MEDIUM = (int)(240 * METRICS.density);
	/**
	 * Cover to use in the highest quality possible (full cover view).
	 */
	public final static int SIZE_LARGE = (METRICS.heightPixels > METRICS.widthPixels ? METRICS.widthPixels : METRICS.heightPixels);
	/**
	 * Use all cover providers to load cover art
	 */
	public static final int COVER_MODE_ALL = 0xF;
	/**
	 * Use androids builtin cover mechanism to load covers
	 */
	public static final int COVER_MODE_ANDROID = 0x1;
	/**
	 * Use vanilla musics cover load mechanism
	 */
	public static final int COVER_MODE_VANILLA = 0x2;
	/**
	 * Use vanilla musics SHADOW cover load mechanism
	 */
	public static final int COVER_MODE_SHADOW = 0x4;
	/**
	 * Use vanilla musics INLINE cover load mechanism
	 */
	public static final int COVER_MODE_INLINE = 0x8;
	/**
	 * Shared on-disk cache class
	 */
	private static BitmapDiskCache sBitmapDiskCache;
	/**
	 * Bitmask on how we are going to load coverart
	 */
	public static int mCoverLoadMode = 0;
	/**
	 * The public downloads directory of this device
	 */
	private static final File sDownloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

	private static int month = -1;

	/**
	 * Constructs a new BitmapCache object
	 * Will initialize the internal LRU cache on first call
	 *
	 * @param context A context to use
	 */
	public CoverCache(Context context) {
		if (sBitmapDiskCache == null) {
		    // modified by zollty 4 lines.
			//sBitmapDiskCache = new BitmapDiskCache(context.getApplicationContext(), 25*1024*1024);
			sBitmapDiskCache = new BitmapDiskCache(context.getApplicationContext(), 300*1024*1024); // 300MB
			File path = context.getApplicationContext().getDatabasePath("covercache.db");
			Log.v("VanillaMusic", "the covercache.db is " + path.getAbsolutePath());
		}
		if (month == -1) {
			Calendar cal = Calendar.getInstance();
			month = cal.get(Calendar.MONTH);
		}
	}

	/**
	 * Returns a (possibly uncached) cover for the song - will return null if the song has no cover
	 *
	 * @param ctx The context to retrieve the bitmap from cache via external content uri
	 * @param song The song used to identify the artwork to load
	 * @return a bitmap or null if no artwork was found
	 */
	public Bitmap getCoverFromSong(Context ctx, Song song, int size) {
		// modified by zollty 2 lines. generate key with song.id instead of albumId
//		CoverKey key = new CoverCache.CoverKey(MediaUtils.TYPE_ALBUM, song.albumId, size);
		CoverKey key = new CoverCache.CoverKey(MediaUtils.TYPE_SONG, song.id, size);
		Bitmap cover = getStoredCover(key);
		if (cover == null) {
			cover = sBitmapDiskCache.createBitmap(ctx, song, size);
			if (cover != null) {
				storeCover(key, cover);
				cover = getStoredCover(key); // return lossy version to avoid random quality changes
			}
		}
		return cover;
	}

	public Bitmap getFontCoverFromSong(Context ctx, Song song, int size) {
		CoverKey key = new CoverCache.CoverKey(20, song.id, size);
		Bitmap cover = getStoredCover(key);
		if (cover == null) {
			cover = CoverBitmap.generatePlaceholderCover(ctx, CoverCache.SIZE_SMALL, CoverCache.SIZE_SMALL, song.title);
			if (cover != null) {
				storeCover(key, cover);
			}
		}
		return cover;
	}

	/**
	 * Returns the on-disk cached version of the cover.
	 * Should only be used on a background thread
	 *
	 * @param key The cache key to use
	 * @return bitmap or null on cache miss
	 */
	private Bitmap getStoredCover(CoverKey key) {
		return sBitmapDiskCache.get(key);
	}

	/**
	 * Stores a new entry in the on-disk cache
	 * Use getStoredCover to read the cached contents back
	 *
	 * @param key The cache key to use
	 * @param cover The bitmap to store
	 */
	private void storeCover(CoverKey key, Bitmap cover) {
		sBitmapDiskCache.put(key, cover);
	}

	/**
	 * Deletes all items hold in the cover caches
	 */
	public static void evictAll() {
		if (sBitmapDiskCache != null) {
			sBitmapDiskCache.evictAll();
		}
	}

	public Bitmap getCoverFromSong2(Context ctx, Song song, int size) {
		Bitmap cover = getCoverFromDir(ctx, song, size);
		if(cover != null) {
			return cover;
		}
		return getCoverFromSong(ctx, song, size);
	}

	private int getSongIdHash(Song song, int picListSize) {
		return (int) ((song.id + month) % picListSize);
	}

	public CoverKey getPicCoverKey(int picIndex, int size) {
		return new CoverCache.CoverKey(19, picIndex, size);
	}

	public Bitmap getCoverFromDir(Context ctx, Song song, int size) {
		// 1、查询缓存，必须要先获取key，key是根据歌曲文件路径和图片文件size计算出来的
		SharedPreferences settings = SharedPrefHelper.getSettings(ctx);
		int msize = settings.getInt("mpic_size", 0);
		if(msize > 0) {
			// each song map a fixed value
			int k = getSongIdHash(song, msize); // song.id = MediaLibrary.hash63(song.path)
			// key is the id (base on k with song.path and pic files size)
			CoverKey picKey = getPicCoverKey(k, size);//new CoverCache.CoverKey(0, k, size);
			// Log.e("VanillaMusic", song.path + ": hash=" + picKey.hashCode() + ", picsize="+msize +", k="+k);
			Bitmap cover = getStoredCover(picKey);
			if (cover != null) {
				// Log.e("VanillaMusic", k + " load picture from cache. " + song.path);
				return cover;
			}
		}
		if(!song.path.contains("10-MUSIC")) {
			return null;
		}
		// 2、缓存中没有，则需要重新获取图片文件列表，再从中选取一张。
		// at this point, it means not scanned or the cache is flushed
		// so do scan and cache the picture
		// Path Like：
		// xxxx/10-Music/music-gentle/S.H.E - 半糖主义.mp3
		// xxxx/10-MPIC/
		String picPathStr = song.path.substring(0, song.path.indexOf("10-MUSIC")) + "10-MPIC";
		final File picPath = new File(picPathStr);
		File[] files = picPath.listFiles();
		if(files == null || files.length==0) {
			Log.e("VanillaMusic", picPathStr + ": this picPath not find or is empty.");
			return null;
		}
		List<File> fileList = new ArrayList<>();
		for(File f: files) {
			String tmp = f.getAbsolutePath();
			if (tmp.endsWith(".jpg")
					|| tmp.endsWith(".jpeg")
					|| tmp.endsWith(".png")) {
				fileList.add(f);
			}
		}
		if(fileList.isEmpty()) {
			Log.e("VanillaMusic", picPathStr + ": this picPath is empty.");
			return null;
		}
		// sort list
		Collections.sort(fileList, new Comparator<File>() {
			@Override
			public int compare(File o1, File o2) {
				return o1.getName().compareTo(o2.getName());
			}
		});
		msize = fileList.size();

		// each song map a fixed value
		int k = getSongIdHash(song, msize); // song.id = MediaLibrary.hash63(song.path)
		File picFile = fileList.get(k);
		// key is the id (base on k with song.path and pic files size)
		CoverKey picKey = getPicCoverKey(k, size);//new CoverCache.CoverKey(0, k, size);
		Bitmap cover = null;
		try {
			//Log.v("VanillaMusic", k + " load picture "+ tmp +" for " + song.path);
			//Log.e("VanillaMusic", song.path + ":1 hash=" + picKey.hashCode() + ", picsize="+msize +", k="+k);
			cover = sBitmapDiskCache.stream2Bitmap(new FileInputStream(picFile), new FileInputStream(picFile), size);
			if (cover != null) {
				//Log.i("VanillaMusic", song.path + ": new " + key.hashCode());
				storeCover(picKey, cover);
			}
		} catch (Exception e) {
			Log.v("VanillaMusic", "Loading coverart for "+song+" failed with exception " + e);
		}
		SharedPreferences.Editor ed = settings.edit();
		ed.putInt("mpic_size", msize);
		ed.apply();
		return cover;
	}

	/**
	 * Object used as cache key. Objects with the same
	 * media type, id and size are considered to be equal
	 */
	public static class CoverKey {
		public final int coverSize;
		public final int mediaType;
		public final long mediaId;
		public final int hashCode;

		CoverKey(int mediaType, long mediaId, int size) {
			this.mediaType = mediaType;
			this.mediaId = mediaId;
			this.coverSize = size2Type(size); // coverSize<=646
			// Integer.MAX_VALUE - 2147483647, mediaType=0~114, coverSize=0~646, mediaId=0~13646
			// mediaId<=13646, mediaType<=114
			hashCode = (100+this.mediaType)*(int)1e7+(100+this.coverSize)*(int)1e4+(int)this.mediaId;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof CoverKey
			    && this.mediaId   == ((CoverKey)obj).mediaId
			    && this.mediaType == ((CoverKey)obj).mediaType
			    && this.coverSize == ((CoverKey)obj).coverSize) {
				return true;
			}
			return false;
		}

		@Override
		public int hashCode() {
//			return (int)this.mediaId + this.mediaType*(int)1e4 + this.coverSize * (int)1e5;
			return hashCode;
		}

		@Override
		public String toString() {
			return "CoverKey_i"+this.mediaId+"_t"+this.mediaType+"_s"+this.coverSize;
		}

		private int size2Type(int coverSize) {
			if (coverSize==CoverCache.SIZE_LARGE) {
				return 0;
			} else if (coverSize==CoverCache.SIZE_MEDIUM) {
				return 1;
			} else if (coverSize==CoverCache.SIZE_SMALL) {
				return 2;
			} else {
				throw new IllegalStateException("not support this size!");
			}
		}

	}

	public void evictExpiredCover() {
		sBitmapDiskCache.evictExpired();
	}

	private static class BitmapDiskCache extends SQLiteOpenHelper {
		/**
		 * Maximal cache size to use in bytes
		 */
		private final long mCacheSize;
		/**
		 * SQLite table to use
		 */
		private final static String TABLE_NAME = "covercache";
		/**
		 * Priority-ordered list of possible cover names
		 */
		private final static Pattern[] COVER_MATCHES = {
			    Pattern.compile("(?i).+/(COVER|ALBUM)\\.(JPE?G|PNG|WEBP)$"),
			    Pattern.compile("(?i).+/ALBUMART(_\\{[-0-9A-F]+\\}_LARGE)?\\.(JPE?G|PNG|WEBP)$"),
			    Pattern.compile("(?i).+/(CD|FRONT|ARTWORK|FOLDER)\\.(JPE?G|PNG|WEBP)$"),
			    Pattern.compile("(?i).+\\.(JPE?G|PNG|WEBP)$") };
		/**
		 * Projection of all columns in the database
		 */
		private final static String[] FULL_PROJECTION = {"id", "size", "expires", "blob"};
		/**
		 * Projection of metadata-only columns
		 */
		private final static String[] META_PROJECTION = {"id", "size", "expires"};
		/**
		 * Restrict lifetime of cached objects to, at most, OBJECT_TTL
		 */
		private final static int OBJECT_TTL = 86400*8;

		/**
		 * Creates a new BitmapDiskCache instance
		 *
		 * @param context The context to use
		 * @param cacheSize The maximal amount of disk space to use in bytes
		 */
		public BitmapDiskCache(Context context, long cacheSize) {
			super(context, "covercache.db", null, 1 /* version */);
			mCacheSize = cacheSize;
		}

		/**
		 * Called by SQLiteOpenHelper to create the database schema
		 */
		@Override
		public void onCreate(SQLiteDatabase dbh) {
			dbh.execSQL("CREATE TABLE "+TABLE_NAME+" (id INTEGER, expires INTEGER, size INTEGER, blob BLOB);");
			dbh.execSQL("CREATE UNIQUE INDEX idx ON "+TABLE_NAME+" (id);");
		}

		/**
		 * Called by SqLiteOpenHelper if the database needs an upgrade
		 */
		@Override
		public void onUpgrade(SQLiteDatabase dbh, int oldVersion, int newVersion) {
			// first db -> nothing to upgrade
		}

		/**
		 * Trims the on disk cache to given size
		 *
		 * @param maxCacheSize Trim cache to this many bytes
		 */
		private void trim(long maxCacheSize) {
			SQLiteDatabase dbh = getWritableDatabase();
			long availableSpace = maxCacheSize - getUsedSpace();

			if (maxCacheSize == 0) {
				// Just drop the whole database (probably a call from evictAll)
				dbh.delete(TABLE_NAME, "1", null);
			} else if (availableSpace < 0) {
				// Try to evict all expired entries first
				int affected = dbh.delete(TABLE_NAME, "expires < ?", new String[] { Long.toString(getUnixTime())});
				if (affected > 0)
					availableSpace = maxCacheSize - getUsedSpace();

				if (availableSpace < 0) {
					// still not enough space: purge by expire date (this kills random rows as expire times are random)
					Cursor cursor = dbh.query(TABLE_NAME, META_PROJECTION, null, null, null, null, "expires ASC");
					if (cursor != null) {
						while (cursor.moveToNext() && availableSpace < 0) {
							int id = cursor.getInt(0);
							int size = cursor.getInt(1);
							dbh.delete(TABLE_NAME, "id=?", new String[] { Long.toString(id) });
							availableSpace += size;
						}
						cursor.close();
					}
				}
			}
		}

		/**
		 * Deletes all cached elements from the on-disk cache
		 */
		public void evictAll() {
			// purge all cached entries
			trim(0);
			// and release the dbh
			getWritableDatabase().close();
		}

		/**
		 * evict all cache before this month.
		 * e.g. a song Expired is 11.29+31d = 12.30, now is 12.1, we will evict it,
		 * we use time 12.1+31d = 01.01 as the min expired time, all cache before 01.01 will be evicted!
		 */
		public void evictExpired() {
			Calendar cal = Calendar.getInstance();
			cal.set(Calendar.DAY_OF_MONTH, 1);
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.MILLISECOND, 0);
			long firstTimeOfThisMonth = cal.getTimeInMillis() / 1000l;
			SQLiteDatabase dbh = getWritableDatabase();
			// direct delete regardless of capacity (mCacheSize)
			dbh.delete(TABLE_NAME, "expires < ?", new String[] { Long.toString(firstTimeOfThisMonth + OBJECT_TTL)});
		}

		/**
		 * Checks if given stamp is considered to be expired
		 *
		 * @param stamp The timestamp to check
		 * @return boolean true if stamp is expired
		 */
		private boolean isExpired(long stamp) {
			return (getUnixTime() > stamp);
		}

		/**
		 * Returns the current unix timestamp
		 *
		 * @return long unix seconds since epoc
		 */
		private long getUnixTime() {
			return System.currentTimeMillis() / 1000L;
		}

		/**
		 * Calculates the space used by the sqlite database
		 *
		 * @return long the space used in bytes
		 */
		private long getUsedSpace() {
			long usedSpace = -1;
			SQLiteDatabase dbh = getWritableDatabase();
			Cursor cursor = dbh.query(TABLE_NAME, new String[]{"SUM(size)"}, null, null, null, null, null);
			if (cursor != null) {
				if (cursor.moveToNext())
					usedSpace = cursor.getLong(0);
				cursor.close();
			}
			return usedSpace;
		}

		/**
		 * Stores a bitmap in the disk cache, does not update existing objects
		 *
		 * @param key The cover key to use
		 * @param cover The cover to store as bitmap
		 */
		public void put(CoverKey key, Bitmap cover) {
			SQLiteDatabase dbh = getWritableDatabase();

			// Ensure that there is some space left
			trim(mCacheSize);

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			// We store a lossy version as this image was
			// created from the original source (and will not be re-compressed)
			cover.compress(Bitmap.CompressFormat.JPEG, 85, out);

			// modified by zollty, make cache last 31 days.
//			Random rnd = new Random();
//			long ttl = getUnixTime() + rnd.nextInt(OBJECT_TTL);
			long ttl = getUnixTime() + OBJECT_TTL;

			ContentValues values = new ContentValues();
			values.put("id"     , key.hashCode());
			values.put("expires", ttl);
			values.put("size"   , out.size());
			values.put("blob"   , out.toByteArray());

			dbh.insert(TABLE_NAME, null, values);
		}

		/**
		 * Returns a cached bitmap
		 *
		 * @param key The key to lookup
		 * @return a cached bitmap, null on cache miss
		 */
		public Bitmap get(CoverKey key) {
			Bitmap cover = null;

			SQLiteDatabase dbh = getWritableDatabase(); // may also delete
			String selection = "id=?";
			String[] selectionArgs = { Long.toString(key.hashCode()) };
			Cursor cursor = dbh.query(TABLE_NAME, FULL_PROJECTION, selection, selectionArgs, null, null, null);
			if (cursor != null) {
				if (cursor.moveToFirst()) {
					long expires = cursor.getLong(2);
					byte[] blob = cursor.getBlob(3);

					if (isExpired(expires)) {
						dbh.delete(TABLE_NAME, selection, selectionArgs);
					} else {
						ByteArrayInputStream stream = new ByteArrayInputStream(blob);
						cover = BitmapFactory.decodeStream(stream);
					}
				}
				cursor.close();
			}

			return cover;
		}

		/**
		 * Attempts to create a new bitmap object for given song.
		 * Returns null if no cover art was found
		 *
		 * @param ctx The context to read the external content uri of the given song
		 * @param song the function will search for artwork of this object
		 * @param maxPxCount the maximum amount of pixels to return (30*30 = 900)
		 */
		public Bitmap createBitmap(Context ctx, Song song, long size) {
			if (song.id < 0)
				throw new IllegalArgumentException("song id is < 0: " + song.id);

			try {
				InputStream inputStream = null;
				InputStream sampleInputStream = null; // same as inputStream but used for getSampleSize

				// modified by zollty 37 lines. get song's cover picture from the external picture file(png,jpg...) instead of the track file(mp3 flac ape...)
				if ((CoverCache.mCoverLoadMode & CoverCache.COVER_MODE_VANILLA) != 0) {
					final File baseFile = new File(song.path);  // File object of queried song
					// Only start search if the base directory of this file is NOT the public
					// downloads folder: Picking files from there would lead to a false positive
					// in most cases
					if (baseFile.getParentFile().equals(sDownloadsDir) == false) {
						for (final File entry : baseFile.getParentFile().listFiles()) {
							String tmp = entry.getAbsolutePath();
							int extIndex = tmp.lastIndexOf('.');
							tmp = tmp.substring(0, extIndex);
							extIndex = song.path.lastIndexOf('.');
							String tmp2 = song.path.substring(0, extIndex);

							if (tmp2.equals(tmp) &&
								(entry.getAbsolutePath().endsWith(".jpg")
									|| entry.getAbsolutePath().endsWith(".jpeg")
									|| entry.getAbsolutePath().endsWith(".png"))) {
								inputStream = new FileInputStream(entry);
								sampleInputStream = new FileInputStream(entry);
								break;
							}
						}
					}
				}

				if (inputStream == null && (CoverCache.mCoverLoadMode & CoverCache.COVER_MODE_INLINE) != 0) {
					MediaMetadataRetriever mmr = new MediaMetadataRetriever();
					mmr.setDataSource(song.path);

					byte[] data = mmr.getEmbeddedPicture();
					if (data != null) {
						sampleInputStream = new ByteArrayInputStream(data);
						inputStream = new ByteArrayInputStream(data);
					}
					mmr.release();
				}

				return stream2Bitmap(inputStream, sampleInputStream, size);
			} catch (Exception e) {
				// no cover art found
				Log.v("VanillaMusic", "Loading coverart for "+song+" failed with exception "+e);
			}
			// failed!
			return null;
		}

		/**
		 * Guess a good sampleSize value for given inputStream
		 *
		 * @param inputStream the input stream to read from
		 * @param bopts the bitmap options to use
		 * @param maxPxCount how many pixels we are returning at most
		 */
		private static int getSampleSize(InputStream inputStream, BitmapFactory.Options bopts, long size) {
			int sampleSize = 1;     /* default sample size                   */
			long maxPxCount = size*size;
			BitmapFactory.decodeStream(inputStream, null, bopts);
			long hasPixels = bopts.outHeight * bopts.outWidth;
			if(hasPixels > maxPxCount) {
				sampleSize = Math.round((int)Math.sqrt((float) hasPixels / (float) maxPxCount));
			}
			// Log.e("VanillaMusic", "sampleSize: " + sampleSize + ", hasPixels: " + hasPixels + ", maxPxCount: " + maxPxCount);
			return sampleSize;
		}

		public Bitmap stream2Bitmap(InputStream inputStream, InputStream sampleInputStream, long size) {
			if (inputStream != null) {
				BitmapFactory.Options bopts = new BitmapFactory.Options();
				bopts.inPreferredConfig  = Bitmap.Config.RGB_565;
				bopts.inJustDecodeBounds = true;

				final int inSampleSize   = getSampleSize(sampleInputStream, bopts, size);
				/* reuse bopts: we are now REALLY going to decode the image */
				bopts.inJustDecodeBounds = false;
				// modified by zollty 17 lines. just compress on need.
				if(CoverCache.SIZE_MEDIUM<=size) { // 最多压缩2倍：1/4
					if(inSampleSize > 2) {
						bopts.inSampleSize = 2;
					}
				} else  { // SIZE_SMALL 最多压缩4倍：1/16*2
					if(inSampleSize > 4) {
						bopts.inSampleSize = 4;
					}
				}
				Bitmap bitmap = null;
				if(inSampleSize > 1) {
					// Log.v("VanillaMusic", "[maxPxCount=" + maxPxCount + "] compress cover for " + song.title + ", inSampleSize=" + inSampleSize);
					bopts.inSampleSize = inSampleSize;
					bitmap = BitmapFactory.decodeStream(inputStream, null, bopts);
				} else {
					bitmap = BitmapFactory.decodeStream(inputStream);
				}
				try {
					sampleInputStream.close();
					inputStream.close();
				} catch (Exception e) {
					// ignore...
				}
				return bitmap;
			}

			return null;
		}

	}
}

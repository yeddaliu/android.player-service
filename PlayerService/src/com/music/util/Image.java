package com.music.util;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import com.music.helper.AppConf;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.util.Log;

public class Image {

	public static Bitmap scaleImg(Bitmap bm, int newWidth, int newHeight) {
		int width = bm.getWidth();
		int height = bm.getHeight();
		int newWidth1 = newWidth;
		int newHeight1 = newHeight;
		float scaleWidth = ((float) newWidth1) / width;
		float scaleHeight = ((float) newHeight1) / height;

		Matrix matrix = new Matrix();
		matrix.postScale(scaleWidth, scaleHeight);
		Bitmap newbm = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, true);
		return newbm;
	}

	public static Bitmap makeReflectionBitmap(Bitmap newBm, int height) {
		int alpha = 0x00000000;
		int bmpWidth = newBm.getWidth();
		int bmpHeight = newBm.getHeight();
		int[] pixels = new int[bmpWidth * bmpHeight * 4];

		newBm.getPixels(pixels, 0, bmpWidth, 0, 0, bmpWidth, bmpHeight);
		Bitmap reverseBitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888);
		for (int y = 0; y < bmpHeight; y++) {
			reverseBitmap.setPixels(pixels, y * bmpWidth, bmpWidth, 0, bmpHeight - y - 1, bmpWidth, 1);
		}
		reverseBitmap.getPixels(pixels, 0, bmpWidth, 0, 0, bmpWidth, bmpHeight);
		Bitmap reflectionBitmap = Bitmap.createBitmap(bmpWidth, height, Bitmap.Config.ARGB_8888);

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < bmpWidth; x++) {
				int index = y * bmpWidth + x;
				int r = (pixels[index] >> 16) & 0xff;
				int g = (pixels[index] >> 8) & 0xff;
				int b = pixels[index] & 0xff;
				pixels[index] = alpha | (r << 16) | (g << 8) | b;
				reflectionBitmap.setPixel(x, y, pixels[index]);
			}
			alpha = alpha + 0x01000000;
		}
		return reflectionBitmap;
	}

	public static Bitmap getImageBitMap(String url, Drawable defaultImg) {
		Bitmap bm = null;
		try {
			URL aURL = new URL(url);
			bm = com.music.util.Image.getRemoteImageBitMap(aURL, defaultImg);
		} catch (MalformedURLException e) {
			bm = com.music.util.Image.getLocalImageBitMap(new File(url), defaultImg);
		} catch (Exception e) {
			Log.e(AppConf.LOG_TAG, "getImageBitMap exception.", e);
			bm = drawableToBitmap(defaultImg);
		}

		return bm;
	}

	public static Bitmap getRemoteImageBitMap(URL aURL, Drawable defaultImg) {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inPurgeable = true;

		Bitmap bm = null;
		try {
			URLConnection conn = aURL.openConnection();
			conn.connect();
			InputStream is = conn.getInputStream();
			FlushedInputStream bis = new FlushedInputStream(is);
			bm = BitmapFactory.decodeStream(bis, null, options);
			bis.close();
			is.close();
		} catch (Exception e) {
			Log.e(AppConf.LOG_TAG, "getRemoteImageBitMap exception.", e);
			/* Reset to Default image on any error. */
			bm = drawableToBitmap(defaultImg);
		}
		return bm;
	}

	public static Bitmap getLocalImageBitMap(File aFile, Drawable defaultImg) {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inPurgeable = true;

		Bitmap bm = null;
		if (aFile.exists() && aFile.isFile()) {
			bm = BitmapFactory.decodeFile(aFile.getAbsolutePath(), options);
		} else {
			Log.d(AppConf.LOG_TAG, "getLocalImageBitMap: file not exist.");
			bm = drawableToBitmap(defaultImg);
		}
		return bm;
	}

	public static Bitmap drawableToBitmap(Drawable drawable) {
		Bitmap.Config conf = drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888
				: Bitmap.Config.RGB_565;
		Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), conf);

		Canvas canvas = new Canvas(bitmap);
		drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
		drawable.draw(canvas);
		return bitmap;
	}

	public static int computeSampleSize(BitmapFactory.Options options, int minSideLength, int maxNumOfPixels) {
		int initialSize = computeInitialSampleSize(options, minSideLength, maxNumOfPixels);
		int roundedSize;
		if (initialSize <= 8) {
			roundedSize = 1;
			while (roundedSize < initialSize) {
				roundedSize <<= 1;
			}
		} else {
			roundedSize = (initialSize + 7) / 8 * 8;
		}

		return roundedSize;
	}

	private static int computeInitialSampleSize(BitmapFactory.Options options, int minSideLength, int maxNumOfPixels) {
		double w = options.outWidth;
		double h = options.outHeight;
		int lowerBound = (maxNumOfPixels == -1) ? 1 : (int) Math.ceil(Math.sqrt(w * h / maxNumOfPixels));
		int upperBound = (minSideLength == -1) ? 128
				: (int) Math.min(Math.floor(w / minSideLength), Math.floor(h / minSideLength));

		if (upperBound < lowerBound) {
			return lowerBound;
		}

		if ((maxNumOfPixels == -1) && (minSideLength == -1)) {
			return 1;
		} else if (minSideLength == -1) {
			return lowerBound;
		} else {
			return upperBound;
		}
	}

	private static Bitmap shrinkBitmap(InputStream is, int defaultScale) {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inPurgeable = true;
		options.inSampleSize = com.music.util.Image.computeSampleSize(options, defaultScale,
				defaultScale * defaultScale);
		options.inJustDecodeBounds = false;

		Bitmap bm = BitmapFactory.decodeStream(is, null, options);
		return bm;
	}
}

/*
 * inner class for bitmap stream
 */
class FlushedInputStream extends FilterInputStream {
	public FlushedInputStream(InputStream inputStream) {
		super(inputStream);
	}

	@Override
	public long skip(long n) throws IOException {
		long totalBytesSkipped = 0L;
		while (totalBytesSkipped < n) {
			long bytesSkipped = in.skip(n - totalBytesSkipped);
			if (bytesSkipped == 0L) {
				int bytes = read();
				if (bytes < 0) {
					break;
				} else {
					bytesSkipped = 1;
				}
			}
			totalBytesSkipped += bytesSkipped;
		}
		return totalBytesSkipped;
	}
}

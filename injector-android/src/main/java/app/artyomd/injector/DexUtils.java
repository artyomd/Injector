package app.artyomd.injector;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class DexUtils {
	private static final String TAG = DexUtils.class.getSimpleName();
	private static final int BUFFER_SIZE = 8 * 1024;

	/**
	 * load dex
	 *
	 * @param context context
	 * @param list    list with dex files
	 */
	public static void loadDex(Context context, List<File> list) {
		try {
			File optimizedDir = context.getDir("outdex", Context.MODE_PRIVATE);
			DexInjector.installSecondaryDexes(context.getClassLoader(), optimizedDir, list);
		} catch (Exception ex) {
			Log.e(TAG, ex.toString());
		}
	}

	/**
	 * copy dex from asset files into device's internal storage
	 *
	 * @param context                context
	 * @param dexInternalStoragePath path to copy
	 * @param secondaryDexName       asset file name
	 */
	public static void prepareDex(Context context, File dexInternalStoragePath, String secondaryDexName) {
		BufferedInputStream bufferedInputStream = null;
		OutputStream dexWriter = null;
		if (!dexInternalStoragePath.exists()) {
			dexInternalStoragePath.getParentFile().mkdirs();
			try {
				dexInternalStoragePath.createNewFile();
			} catch (IOException e) {
				Log.e(TAG, e.toString());
			}
		}
		try {
			bufferedInputStream = new BufferedInputStream(context.getAssets().open(secondaryDexName));
			dexWriter = new BufferedOutputStream(new FileOutputStream(dexInternalStoragePath));
			byte[] buf = new byte[BUFFER_SIZE];
			int len;
			while ((len = bufferedInputStream.read(buf, 0, BUFFER_SIZE)) > 0) {
				dexWriter.write(buf, 0, len);
			}
			dexWriter.flush();
		} catch (IOException e) {
			Log.e(TAG, e.toString());
		} finally {
			if (dexWriter != null) {
				try {
					dexWriter.close();
				} catch (IOException ex) {
					Log.e(TAG, ex.toString());
				}
			}
			if (bufferedInputStream != null) {
				try {
					bufferedInputStream.close();
				} catch (IOException ex) {
					Log.e(TAG, ex.toString());
				}
			}
		}
	}
}

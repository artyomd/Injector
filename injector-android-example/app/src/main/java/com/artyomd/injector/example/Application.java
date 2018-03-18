package com.artyomd.injector.example;

import com.artyomd.injector.DexUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Application extends android.app.Application {
	@Override
	public void onCreate() {
		super.onCreate();
		File dexPath = new File(getFilesDir() + "/dex", "test.zip");
		if (!dexPath.exists()) {
			DexUtils.prepareDex(getApplicationContext(), dexPath, "test.zip");
		}
		File dexPath2 = new File(getFilesDir() + "/dex", "lottie.zip");
		if (!dexPath2.exists()) {
			DexUtils.prepareDex(getApplicationContext(), dexPath2, "lottie.zip");
		}
		List<File> dexs = new ArrayList<>();
		dexs.add(dexPath);
		dexs.add(dexPath2);
		DexUtils.loadDex(getApplicationContext(), dexs);
	}
}

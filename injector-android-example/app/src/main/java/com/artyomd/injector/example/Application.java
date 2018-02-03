package com.artyomd.injector.example;

import com.artyomd.injector.DexUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Application extends android.app.Application {
	@Override
	public void onCreate() {
		super.onCreate();
		File dexPath = new File(getFilesDir() + "/dex", "lottie.dex");
		if (!dexPath.exists()) {
			DexUtils.prepareDex(getApplicationContext(), dexPath, "lottie.dex");
		}
		List<File> dexs = new ArrayList<>();
		dexs.add(dexPath);
		DexUtils.loadDex(getApplicationContext(), dexs);
	}
}

package app.artyomd.injector.example;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import app.artyomd.injector.DexUtils;

public class Application extends android.app.Application {
	@Override
	public void onCreate() {
		super.onCreate();
		File dexPath = new File(getFilesDir() + "/dex", "inject.zip");
		if (!dexPath.exists()) {
			DexUtils.prepareDex(getApplicationContext(), dexPath, "inject.zip");
		}
		List<File> dexs = new ArrayList<>();
		dexs.add(dexPath);
		try {
			DexUtils.loadDex(getApplicationContext(), dexs);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}


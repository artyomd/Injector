package app.artyomd.injector.example;

import app.artyomd.injector.DexUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

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


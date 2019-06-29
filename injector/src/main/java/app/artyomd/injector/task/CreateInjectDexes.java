package app.artyomd.injector.task;

import app.artyomd.injector.extension.InjectorExtension;
import app.artyomd.injector.model.AndroidArchiveLibrary;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.utils.ThreadUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.JavaVersion;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class CreateInjectDexes extends DefaultTask {

	private static final String[] EMPTY_STRING_ARRAY = new String[0];
	private JavaVersion sourceCompatibilityVersion;
	private JavaVersion targetCompatibilityVersion;
	private int minApiLevel;
	private String variantName;
	private String projectPackageName;
	private String buildDirectory;
	private Set<? extends AndroidArchiveLibrary> androidArchiveLibraries;
	private Set<? extends ResolvedArtifact> jarFiles;
	private InjectorExtension extension;

	@Inject
	public CreateInjectDexes(InjectorExtension extension,
	                         String projectPackageName,
	                         String variantName,
	                         String buildDirectory,
	                         Set<? extends AndroidArchiveLibrary> androidArchiveLibraries,
	                         Set<? extends ResolvedArtifact> jarFiles,
	                         int minApiLevel,
	                         JavaVersion sourceCompatibilityVersion,
	                         JavaVersion targetCompatibilityVersion) {
		this.sourceCompatibilityVersion = sourceCompatibilityVersion;
		this.targetCompatibilityVersion = targetCompatibilityVersion;
		this.minApiLevel = minApiLevel;
		this.variantName = variantName;
		this.projectPackageName = projectPackageName;
		this.buildDirectory = buildDirectory;
		this.androidArchiveLibraries = new HashSet<>(androidArchiveLibraries);
		this.jarFiles = new HashSet<>(jarFiles);
		this.extension = extension;
	}

	@TaskAction
	void createDex() {
		if (!extension.isEnabled()) {
			return;
		}
		processRSources();
		List<ResolvedArtifact> artifacts = new ArrayList<>(androidArchiveLibraries);
		artifacts.addAll(jarFiles);
		Map<String, List<ResolvedArtifact>> dexs = extension.getDexes(artifacts);
		List<List<String>> dexOptions = new ArrayList<>();
		String outFilePath = buildDirectory + extension.getDexLocation();
		dexs.forEach((key, value) -> {
			if (!value.isEmpty()) {
				String outPutDex = outFilePath + key + ".zip";
				List<String> dexOption = new ArrayList<>();
				dexOption.add("--release");
				dexOption.add("--output");
				dexOption.add(outPutDex);
				dexOption.add("--min-api");
				dexOption.add(Integer.toString(minApiLevel));
				value.forEach(resolvedArtifact -> {
					if (resolvedArtifact instanceof AndroidArchiveLibrary) {
						File classesJar = ((AndroidArchiveLibrary) resolvedArtifact).getClassesJarFile();
						if (classesJar.exists()) {
							dexOption.add(classesJar.getAbsolutePath());
						}
					} else {
						File classesJar = resolvedArtifact.getFile();
						if (classesJar.exists()) {
							dexOption.add(classesJar.getAbsolutePath());
						}
					}
				});
				dexOptions.add(dexOption);
			}
		});
		File outFile = new File(outFilePath);
		if (!outFile.exists()) {
			outFile.mkdirs();
		}

		for (List<String> dexOption : dexOptions) {
			D8Command command;
			ExecutorService executor = ThreadUtils.getExecutorService(-1);
			try {
				command = D8Command.parse(dexOption.toArray(EMPTY_STRING_ARRAY), CommandLineOrigin.INSTANCE).build();
				D8.run(command, executor);
			} catch (CompilationFailedException e) {
				e.printStackTrace();
			} finally {
				executor.shutdown();
			}
			try {
				executor.awaitTermination(30, TimeUnit.MINUTES);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * generate R.java
	 */
	private void processRSources() {
		androidArchiveLibraries.forEach(resolvedArtifact -> {
			try {
				RSourceGenerator.generate(resolvedArtifact, projectPackageName, buildDirectory, variantName, sourceCompatibilityVersion, targetCompatibilityVersion);
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}
}
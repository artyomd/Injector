package app.artyomd.injector.task;

import app.artyomd.injector.model.AndroidArchiveLibrary;
import app.artyomd.injector.util.Utils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class ExtractAarTask extends DefaultTask {

	private Set<? extends AndroidArchiveLibrary> androidArchiveLibraries = new HashSet<>();

	public void setAndroidArchiveLibraries(Set<? extends AndroidArchiveLibrary> androidArchiveLibraries) {
		this.androidArchiveLibraries = new HashSet<>(androidArchiveLibraries);
	}

	@TaskAction
	void extractAArs() {
		androidArchiveLibraries.forEach((Consumer<AndroidArchiveLibrary>) resolvedArtifact -> {
			String extractedAarPath = resolvedArtifact.getRootFolder().getAbsolutePath();
			File extractedAar = new File(extractedAarPath);
			if (!extractedAar.exists()) {
				try {
					Utils.unzip(resolvedArtifact.getFile(), extractedAarPath);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
	}
}

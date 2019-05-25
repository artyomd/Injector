package app.artyomd.injector.task;

import app.artyomd.injector.model.AndroidArchiveLibrary;
import app.artyomd.injector.util.Utils;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class ExtractAarTask extends DefaultTask {

	private Set<AndroidArchiveLibrary> androidArchiveLibraries = new HashSet<>();

	public void setAndroidArchiveLibraries(Set<AndroidArchiveLibrary> androidArchiveLibraries) {
		this.androidArchiveLibraries = androidArchiveLibraries;
	}

	@TaskAction
	void extractAArs() {
		androidArchiveLibraries.forEach((Consumer<ResolvedArtifact>) resolvedArtifact -> {
			String extractedAarPath = ((AndroidArchiveLibrary) resolvedArtifact).getRootFolder().getAbsolutePath();
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

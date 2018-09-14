package app.artyomd.injector;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@SuppressWarnings("unused")
public class AndroidArchiveLibrary implements ResolvedArtifact {

	private final Project project;
	private final ResolvedArtifact artifact;

	public AndroidArchiveLibrary(Project project, ResolvedArtifact artifact) {
		if (!"aar".equals(artifact.getType())) {
			throw new IllegalArgumentException("artifact must be aar type");
		}
		this.project = project;
		this.artifact = artifact;
	}

	public File getRootFolder() {
		File explodedRootDir = Utils.getWorkingDir(project);
		ModuleVersionIdentifier id = artifact.getModuleVersion().getId();
		return project.file(explodedRootDir + "/" + id.getGroup() + "/" + id.getName() + "/" + id.getVersion());
	}

	private File getJarsRootFolder() {
		return new File(getRootFolder(), "jars");
	}

	public File getAidlFolder() {
		return new File(getRootFolder(), "aidl");
	}

	public File getAssetsFolder() {
		return new File(getRootFolder(), "assets");
	}

	public File getClassesJarFile() {
		return new File(getRootFolder(), "classes.jar");
	}

	public Collection<File> getLocalJars() {
		List<File> localJars = new ArrayList<>();
		File[] jarList = new File(getJarsRootFolder(), "libs").listFiles();
		if (jarList != null) {
			for (File jars : jarList) {
				if (jars.isFile() && jars.getName().endsWith(".jar")) {
					localJars.add(jars);
				}
			}
		}

		return localJars;
	}

	public File getJniFolder() {
		return new File(getRootFolder(), "jni");
	}

	public File getResFolder() {
		return new File(getRootFolder(), "res");
	}

	public File getManifest() {
		return new File(getRootFolder(), "AndroidManifest.xml");
	}

	public File getLintJar() {
		return new File(getJarsRootFolder(), "lint.jar");
	}

	public File getProguardRules() {
		return new File(getRootFolder(), "proguard.txt");
	}

	public File getSymbolFile() {
		return new File(getRootFolder(), "R.txt");
	}

	@NotNull
	@Override
	public File getFile() {
		return artifact.getFile();
	}

	@NotNull
	@Override
	public ResolvedModuleVersion getModuleVersion() {
		return artifact.getModuleVersion();
	}

	@NotNull
	@Override
	public String getType() {
		return artifact.getType();
	}

	@NotNull
	@Override
	public String getName() {
		return artifact.getModuleVersion().getId().getName();
	}

	@NotNull
	@Override
	public String getExtension() {
		return artifact.getExtension();
	}

	@Nullable
	@Override
	public String getClassifier() {
		return artifact.getClassifier();
	}

	@NotNull
	@Override
	public ComponentArtifactIdentifier getId() {
		return artifact.getId();
	}
}

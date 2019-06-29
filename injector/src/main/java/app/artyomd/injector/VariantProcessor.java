package app.artyomd.injector;

import app.artyomd.injector.extension.InjectorExtension;
import app.artyomd.injector.model.AndroidArchiveLibrary;
import app.artyomd.injector.task.CreateInjectDexes;
import app.artyomd.injector.util.Utils;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.tasks.MergeFileTask;
import com.android.build.gradle.tasks.InvokeManifestMerger;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.google.common.collect.Iterables;
import groovy.util.XmlSlurper;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.internal.file.collections.ImmutableFileCollection;
import org.gradle.api.tasks.TaskProvider;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("WeakerAccess")
class VariantProcessor {

	private final Project project;
	private final BaseExtension androidExtension;

	private final BaseVariant variant;

	private JavaVersion sourceCompatibilityVersion;
	private JavaVersion targetCompatibilityVersion;

	private Set<? extends AndroidArchiveLibrary> androidArchiveLibraries;
	private Set<? extends ResolvedArtifact> jarFiles;

	private String variantName;
	private String projectPackageName;

	VariantProcessor(Project project, BaseVariant variant) {
		this.project = project;
		this.variant = variant;
		this.androidExtension = (BaseExtension) project.getExtensions().getByName("android");
		this.variantName = variant.getName();
		this.variantName = variantName.substring(0, 1).toUpperCase() + variantName.substring(1);
		try {
			projectPackageName = new XmlSlurper().parse(androidExtension.getSourceSets().getByName("main").getManifest()
					.getSrcFile()).getProperty("@package").toString();
		} catch (IOException | SAXException | ParserConfigurationException e) {
			e.printStackTrace();
		}
	}

	public void setAndroidArchiveLibraries(Set<? extends AndroidArchiveLibrary> androidArchiveLibraries) {
		this.androidArchiveLibraries = new HashSet<>(androidArchiveLibraries);
	}

	public void setJarFiles(Set<? extends ResolvedArtifact> jarFiles) {
		this.jarFiles = new HashSet<>(jarFiles);
	}

	public void processVariant(InjectorExtension extension) {
		CompileOptions compileOptions = androidExtension.getCompileOptions();
		sourceCompatibilityVersion = compileOptions.getSourceCompatibility();
		targetCompatibilityVersion = compileOptions.getTargetCompatibility();
		if (!androidArchiveLibraries.isEmpty()) {
			extractAARs();
			processManifest();
			processResourcesAndR();
			processAssets();
			processJniLibs();
		}
		processProguardTxt();
		createDexTask(extension);
	}

	/**
	 * merge manifests
	 */
	private void processManifest() {
		TaskProvider<InvokeManifestMerger> invokeManifestMergerTaskProvider = project.getTasks().register("merge" + variantName + "Manifest", InvokeManifestMerger.class);

		TaskProvider<ManifestProcessorTask> processManifestTaskTaskProvider = Iterables.get(variant.getOutputs(), 0).getProcessManifestProvider();
		processManifestTaskTaskProvider.configure(manifestProcessorTask -> manifestProcessorTask.finalizedBy(invokeManifestMergerTaskProvider));

		invokeManifestMergerTaskProvider.configure(manifestsMergeTask -> {
			manifestsMergeTask.setVariantName(variant.getName());
			List<File> list = new ArrayList<>();
			androidArchiveLibraries.forEach(resolvedArtifact -> list.add((resolvedArtifact).getManifest()));
			manifestsMergeTask.setSecondaryManifestFiles(list);

			manifestsMergeTask.setMainManifestFile(processManifestTaskTaskProvider.get().getAaptFriendlyManifestOutputFile());
			manifestsMergeTask.setOutputFile(new File(processManifestTaskTaskProvider.get().getManifestOutputDirectory().get().getAsFile(), "AndroidManifest.xml"));
			manifestsMergeTask.dependsOn(processManifestTaskTaskProvider);
		});
	}

	/**
	 * extract aar
	 */
	private void extractAARs() {
		project.getTasks().named("preBuild").configure(task ->
				task.finalizedBy(project.getTasks().named(InjectorPlugin.EXTRACT_AARS_TASK_NAME)));
	}

	/**
	 * merge resources
	 */
	private void processResourcesAndR() {
		project.getTasks().named("generate" + variantName + "Resources").configure(task -> {
			androidArchiveLibraries.forEach(resolvedArtifact -> {
				File resFolder = (resolvedArtifact).getResFolder();
				if (resFolder.exists()) {
					task.getInputs().dir(resFolder);
				}
			});
			task.doFirst(task1 -> androidArchiveLibraries.forEach(resolvedArtifact -> {
				if ((resolvedArtifact).getResFolder().exists()) {
					androidExtension.getSourceSets().getByName("main").getRes().srcDir((resolvedArtifact).getResFolder());
				}
			}));
		});
	}

	/**
	 * merge assets
	 */
	private void processAssets() {
		variant.getMergeAssetsProvider().configure(mergeSourceSetFolders -> {
			androidArchiveLibraries.forEach(resolvedArtifact -> {
				File assetsFolder = resolvedArtifact.getAssetsFolder();
				if ((assetsFolder.exists())) {
					mergeSourceSetFolders.getInputs().dir(assetsFolder);
				}
			});
			mergeSourceSetFolders.doFirst(task -> androidArchiveLibraries.forEach(resolvedArtifact -> {
				File assetsFolder = (resolvedArtifact).getAssetsFolder();
				if (assetsFolder.exists()) {
					androidExtension.getSourceSets().getByName("main").getAssets().srcDir(assetsFolder);
				}
			}));
		});
	}

	/**
	 * merge jniLibs
	 */
	private void processJniLibs() {
		project.getTasks().named("merge" + variantName + "JniLibFolders").configure(mergeJniLibsTask -> {
			androidArchiveLibraries.forEach(resolvedArtifact -> {
				File jniFolder = resolvedArtifact.getJniFolder();
				if (jniFolder.exists()) {
					mergeJniLibsTask.getInputs().dir(jniFolder);
				}
			});
			mergeJniLibsTask.doFirst(task -> androidArchiveLibraries.forEach(resolvedArtifact -> {
				File jniFolder = resolvedArtifact.getJniFolder();
				if (jniFolder.exists()) {
					androidExtension.getSourceSets().getByName("main").getJniLibs().srcDir(jniFolder);
				}
			}));
		});
	}

	@Nonnull
	private File getExternalLibsProguard() {
		File workingDir = Utils.getWorkingDir(project);
		File proguardFile = new File(workingDir, "libs.txt");
		if (proguardFile.exists()) {
			proguardFile.delete();
		}
		try {
			proguardFile.getParentFile().mkdirs();
			proguardFile.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
		try (FileWriter fw = new FileWriter(proguardFile.getAbsolutePath(), true);
		     BufferedWriter bw = new BufferedWriter(fw);
		     PrintWriter out = new PrintWriter(bw)) {
			androidArchiveLibraries.forEach(resolvedArtifact -> out.println("\n-libraryjars " + resolvedArtifact.getFile().getAbsolutePath()));
			jarFiles.forEach(resolvedArtifact -> out.println("\n-libraryjars " + resolvedArtifact.getFile().getAbsolutePath()));
			out.println("-keep class " + projectPackageName + ".R" + " { *; }");
			out.println("-keep class " + projectPackageName + ".R$*" + " { *; }");
		} catch (IOException e) {
			e.printStackTrace();
		}
		return proguardFile;
	}

	/**
	 * merge proguard.txt
	 */
	private void processProguardTxt() {
		project.getTasks().named("merge" + variantName + "ConsumerProguardFiles", MergeFileTask.class).configure(mergeFileTask -> mergeFileTask.doFirst(task -> {
			Set<File> inputFiles = new HashSet<>(mergeFileTask.getInputFiles().getFiles());
			androidArchiveLibraries.forEach(androidArchiveLibrary -> {
				File thirdProguard = androidArchiveLibrary.getProguardRules();
				if (!thirdProguard.exists()) {
					return;
				}
				inputFiles.add(thirdProguard);
			});
			inputFiles.add(getExternalLibsProguard());
			mergeFileTask.setInputFiles(ImmutableFileCollection.of(inputFiles));
		}));
	}

	private void createDexTask(InjectorExtension extension) {
		TaskProvider<CreateInjectDexes> taskProvider = project.getTasks().register("createInject" + variantName + "Dexes",
				CreateInjectDexes.class, extension, projectPackageName, variantName, project.getBuildDir().getAbsolutePath(),
				androidArchiveLibraries, jarFiles, variant.getMergedFlavor().getMinSdkVersion().getApiLevel(),
				sourceCompatibilityVersion, targetCompatibilityVersion);

		taskProvider.configure(createInjectDexes -> {
			TaskProvider<?> extractAARsTask = project.getTasks().named(InjectorPlugin.EXTRACT_AARS_TASK_NAME);
			TaskProvider<?> assembleTask = project.getTasks().named("assemble" + variantName);
			TaskProvider<?> rGenerationTask = project.getTasks().named("generate" + variantName + "RFile");
			createInjectDexes.dependsOn(extractAARsTask);
			createInjectDexes.dependsOn(rGenerationTask);
			createInjectDexes.dependsOn(assembleTask);
		});
	}
}

package com.artyomd.injector;

import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.tasks.MergeFileTask;
import com.android.build.gradle.tasks.InvokeManifestMerger;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.google.common.collect.Iterables;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.util.*;

class VariantProcessor {

    private final Project project;
    private final BaseExtension androidExtension;

    //LibraryVariant
    private final BaseVariant variant;

    private JavaVersion sourceCompatibilityVersion;
    private JavaVersion targetCompatibilityVersion;

    private List<ResolvedArtifact> androidArchiveLibraries = new ArrayList<>();
    private List<ResolvedArtifact> jarFiles = new ArrayList<>();

    private String variantName;

    VariantProcessor(Project project, BaseVariant variant) {
        this.project = project;
        this.variant = variant;
        this.androidExtension = (BaseExtension) project.getExtensions().getByName("android");
        this.variantName = variant.getName();
        this.variantName = variantName.substring(0, 1).toUpperCase() + variantName.substring(1);
    }

    void addAndroidArchiveLibrary(AndroidArchiveLibrary library) {
        androidArchiveLibraries.add(library);
    }

    void addJarFile(ResolvedArtifact jar) {
        jarFiles.add(jar);
    }

    private static void checkArtifacts(List<ResolvedArtifact> artifactsList) {
        Map<String, Map<String, ResolvedArtifact>> artifacts = new HashMap<>();
        for (ResolvedArtifact artifact : artifactsList) {
            ModuleVersionIdentifier id = artifact.getModuleVersion().getId();
            String name = id.getName();
            String group = id.getGroup();
            String version = id.getVersion();
            if (artifacts.containsKey(group)) {
                Map<String, ResolvedArtifact> names = artifacts.get(group);
                if (names.containsKey(name)) {
                    ResolvedArtifact old = names.get(name);
                    if (Utils.cmp(old.getModuleVersion().getId().getVersion(), version)) {
                        names.put(name, artifact);
                        artifactsList.remove(old);
                    } else {
                        artifactsList.remove(artifact);
                    }
                } else {
                    names.put(name, artifact);
                }
            } else {
                Map<String, ResolvedArtifact> names = new HashMap<>();
                names.put(name, artifact);
                artifacts.put(group, names);
            }
        }
    }

    public void processVariant(InjectorExtension configs) {
        CompileOptions compileOptions = androidExtension.getCompileOptions();
        sourceCompatibilityVersion = compileOptions.getSourceCompatibility();
        targetCompatibilityVersion = compileOptions.getTargetCompatibility();

        checkArtifacts(jarFiles);
        checkArtifacts(androidArchiveLibraries);

        ProcessAndroidResources processAndroidResources = Iterables.get(variant.getOutputs(), 0).getProcessResources();

        if (!androidArchiveLibraries.isEmpty()) {
            extractAARs();
            processManifest();
            processResourcesAndR();
            if (configs.isEnabled()) {
                processRSources(processAndroidResources);
            }
            processAssets();
            processJniLibs();
        }
        processProguardTxt();
        if (configs.isEnabled()) {
            createDex(configs, processAndroidResources);
        }
    }

    /**
     * merge manifests
     */
    private void processManifest() {
        Class<InvokeManifestMerger> invokeManifestTaskClazz = null;
        String className = "com.android.build.gradle.tasks.InvokeManifestMerger";
        try {
            invokeManifestTaskClazz = (Class<InvokeManifestMerger>) Class.forName(className);
        } catch (ClassNotFoundException ignored) {
        }
        if (invokeManifestTaskClazz == null) {
            throw new RuntimeException("Can not find class " + className);
        }
        ManifestProcessorTask processManifestTask = Iterables.get(variant.getOutputs(), 0).getProcessManifest();
        InvokeManifestMerger manifestsMergeTask = project.getTasks().create("merge" + variantName + "Manifest", invokeManifestTaskClazz);
        manifestsMergeTask.setVariantName(variant.getName());
        manifestsMergeTask.setMainManifestFile(androidExtension.getSourceSets().getByName("main").getManifest().getSrcFile());
        List<File> list = new ArrayList<>();
        androidArchiveLibraries.forEach(resolvedArtifact -> list.add(((AndroidArchiveLibrary) resolvedArtifact).getManifest()));
        manifestsMergeTask.setSecondaryManifestFiles(list);
        manifestsMergeTask.setOutputFile(new File(processManifestTask.getManifestOutputDirectory(), "AndroidManifest.xml"));
        manifestsMergeTask.dependsOn(processManifestTask);
        processManifestTask.finalizedBy(manifestsMergeTask);
    }

    /**
     * extract aar
     */
    private void extractAARs() {
        String taskPath = "preBuild";
        Task preBuildTask = project.getTasks().findByPath(taskPath);
        assert preBuildTask != null;
        preBuildTask.doFirst(task -> androidArchiveLibraries.forEach(resolvedArtifact -> {
            try {
                String extractedAarPath = ((AndroidArchiveLibrary) resolvedArtifact).getRootFolder().getAbsolutePath();
                File extractedAar = new File(extractedAarPath);
                if (!extractedAar.exists()) {
                    ZipFile zipFile = new ZipFile(resolvedArtifact.getFile());
                    zipFile.extractAll(extractedAarPath);
                }
            } catch (ZipException e) {
                e.printStackTrace();
            }
        }));
    }

    /**
     * merge resources
     */
    private void processResourcesAndR() {
        String taskPath = "generate" + variantName + "Resources";
        Task resourceGenTask = project.getTasks().findByPath(taskPath);
        if (resourceGenTask == null) {
            throw new RuntimeException("Can not find task " + taskPath);
        }
        resourceGenTask.doFirst(task -> androidArchiveLibraries.forEach(resolvedArtifact -> {
            if (((AndroidArchiveLibrary) resolvedArtifact).getResFolder().exists()) {
                androidExtension.getSourceSets().getByName("main").getRes().srcDir(((AndroidArchiveLibrary) resolvedArtifact).getResFolder());
            }
        }));
    }

    /**
     * generate R.java
     */
    private void processRSources(ProcessAndroidResources processAndroidResources) {
        processAndroidResources.doLast(task -> androidArchiveLibraries.forEach(resolvedArtifact -> {
            try {
                RSourceGenerator.generate(((AndroidArchiveLibrary) resolvedArtifact), sourceCompatibilityVersion, targetCompatibilityVersion);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));
    }

    /**
     * merge assets
     */
    private void processAssets() {
        MergeSourceSetFolders assetsTask = variant.getMergeAssets();
        if (assetsTask == null) {
            throw new RuntimeException("Can not find task in variant.getMergeAssets()!");
        }
        androidArchiveLibraries.forEach(resolvedArtifact -> {
            File assetsFolder = ((AndroidArchiveLibrary) resolvedArtifact).getAssetsFolder();
            if ((assetsFolder.exists())) {
                assetsTask.getInputs().dir(assetsFolder);
            }
        });
        assetsTask.doFirst(task -> androidArchiveLibraries.forEach(resolvedArtifact -> {
            File assetsFolder = ((AndroidArchiveLibrary) resolvedArtifact).getAssetsFolder();
            if (assetsFolder.exists()) {
                androidExtension.getSourceSets().getByName("main").getAssets().srcDir(assetsFolder);
            }
        }));
    }

    /**
     * merge jniLibs
     */
    private void processJniLibs() {
        String taskPath = "merge" + variantName + "JniLibFolders";
        Task mergeJniLibsTask = project.getTasks().findByPath(taskPath);
        if (mergeJniLibsTask == null) {
            throw new RuntimeException("Can not find task " + taskPath);
        }
        androidArchiveLibraries.forEach(resolvedArtifact -> {
            File jniFolder = ((AndroidArchiveLibrary) resolvedArtifact).getJniFolder();
            if (jniFolder.exists()) {
                mergeJniLibsTask.getInputs().dir(jniFolder);
            }
        });
        mergeJniLibsTask.doFirst(task -> androidArchiveLibraries.forEach(resolvedArtifact -> {
            File jniFolder = ((AndroidArchiveLibrary) resolvedArtifact).getJniFolder();
            if (jniFolder.exists()) {
                androidExtension.getSourceSets().getByName("main").getJniLibs().srcDir(jniFolder);
            }
        }));
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
        } catch (IOException e) {
            e.printStackTrace();
        }
        return proguardFile;
    }

    /**
     * merge proguard.txt
     */
    private void processProguardTxt() {
        String taskPath = "merge" + variantName + "ConsumerProguardFiles";
        MergeFileTask mergeFileTask = (MergeFileTask) project.getTasks().findByPath(taskPath);
        if (mergeFileTask == null) {
            throw new RuntimeException("Can not find task " + taskPath);
        }
        mergeFileTask.doFirst(task -> {
            androidArchiveLibraries.forEach(resolvedArtifact -> {
                File thirdProguard = ((AndroidArchiveLibrary) resolvedArtifact).getProguardRules();
                if (!thirdProguard.exists()) {
                    return;
                }
                mergeFileTask.getInputFiles().add(thirdProguard);
            });
            mergeFileTask.getInputFiles().add(getExternalLibsProguard());
        });
    }

    /**
     * generate dex file
     * getting path to current build tools version dx tool(in android sdk)
     * and executing command dx --dex --output=/outputs/inject/toInject.dex jars
     */
    private void createDex(InjectorExtension extension, ProcessAndroidResources processAndroidResources) {
        Properties properties = new Properties();
        String sdkDir;
        if (project.getRootProject().file("local.properties").exists()) {
            File file = project.getRootProject().file("local.properties");
            DataInputStream dataIn;
            try {
                dataIn = new DataInputStream(new FileInputStream(file));
                properties.load(dataIn);
            } catch (IOException e) {
                e.printStackTrace();
            }
            sdkDir = properties.getProperty("sdk.dir");
        } else {
            sdkDir = System.getenv().get("ANDROID_HOME");
        }
        String buildToolsVersion = androidExtension.getBuildToolsVersion();
        String pathToDx = sdkDir + "/build-tools/" + buildToolsVersion + "/dx --dex";

        String outFile = project.getBuildDir().getAbsolutePath() + Iterables.get(variant.getOutputs(), 0).getDirName() + extension.getDexLocation();
        List<ResolvedArtifact> artifacts = new ArrayList<>();
        artifacts.addAll(androidArchiveLibraries);
        artifacts.addAll(jarFiles);
        Map<String, List<ResolvedArtifact>> dexs = extension.getDexes(artifacts);
        List<String> commands = new ArrayList<>();
        dexs.forEach((key, value) -> {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(pathToDx);
            String outPutDex = outFile + key + ".zip";
            stringBuilder.append(" --output=").append(outPutDex);
            if (!value.isEmpty()) {
                value.forEach(resolvedArtifact -> {
                    if (resolvedArtifact instanceof AndroidArchiveLibrary) {
                        File classesJar = ((AndroidArchiveLibrary) resolvedArtifact).getClassesJarFile();
                        if (classesJar.exists()) {
                            stringBuilder.append(" ").append(classesJar.getAbsolutePath());
                        }
                    } else {
                        File classesJar = resolvedArtifact.getFile();
                        if (classesJar.exists()) {
                            stringBuilder.append(" ").append(classesJar.getAbsolutePath());
                        }
                    }
                });
                commands.add(stringBuilder.toString());
            }
        });
        String taskPath = "assemble" + variantName;
        Task assembleTask = project.getTasks().findByPath(taskPath);
        assert assembleTask != null;
        assembleTask.doLast(task -> commands.forEach(s -> {
            try {
                (new File(outFile)).mkdirs();
                Utils.execCommand(s);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));
        assembleTask.dependsOn(processAndroidResources);
    }
}

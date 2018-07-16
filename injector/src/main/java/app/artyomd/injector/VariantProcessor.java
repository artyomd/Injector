package app.artyomd.injector;

import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.tasks.MergeFileTask;
import com.android.build.gradle.tasks.InvokeManifestMerger;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.collect.Iterables;
import groovy.util.XmlSlurper;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

class VariantProcessor {

    private final Project project;
    private final BaseExtension androidExtension;

    //LibraryVariant
    private final BaseVariant variant;

    private JavaVersion sourceCompatibilityVersion;
    private JavaVersion targetCompatibilityVersion;

    private Set<AndroidArchiveLibrary> androidArchiveLibraries;
    private Set<ResolvedArtifact> jarFiles;

    private String variantName;
    private String projectPackageName;

    VariantProcessor(Project project, BaseVariant variant) {
        this.project = project;
        this.variant = variant;
        this.androidExtension = (BaseExtension) project.getExtensions().getByName("android");
        this.variantName = variant.getName();
        this.variantName = variantName.substring(0, 1).toUpperCase() + variantName.substring(1);
        try {
            projectPackageName = new XmlSlurper().parse(androidExtension.getSourceSets().getByName("main").getManifest().getSrcFile()).getProperty("@package").toString();
        } catch (IOException | SAXException | ParserConfigurationException e) {
            e.printStackTrace();
        }
    }

    public void setAndroidArchiveLibraries(Set<AndroidArchiveLibrary> androidArchiveLibraries) {
        this.androidArchiveLibraries = androidArchiveLibraries;
    }

    public void setJarFiles(Set<ResolvedArtifact> jarFiles) {
        this.jarFiles = jarFiles;
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
        manifestsMergeTask.setMainManifestFile(processManifestTask.getAaptFriendlyManifestOutputFile());
        List<File> list = new ArrayList<>();
        androidArchiveLibraries.forEach(resolvedArtifact -> list.add((resolvedArtifact).getManifest()));
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
        preBuildTask.finalizedBy(project.getTasks().findByPath(InjectorPlugin.EXTRACT_AARS_TASK_NAME));
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
        androidArchiveLibraries.forEach(resolvedArtifact -> {
            File resFolder = (resolvedArtifact).getResFolder();
            if (resFolder.exists()) {
                resourceGenTask.getInputs().dir(resFolder);
            }
        });
        resourceGenTask.doFirst(task -> androidArchiveLibraries.forEach(resolvedArtifact -> {
            if ((resolvedArtifact).getResFolder().exists()) {
                androidExtension.getSourceSets().getByName("main").getRes().srcDir((resolvedArtifact).getResFolder());
            }
        }));
    }

    /**
     * generate R.java
     */
    private void processRSources() {
        androidArchiveLibraries.forEach(resolvedArtifact -> {
            try {
                RSourceGenerator.generate((resolvedArtifact), projectPackageName, project.getBuildDir().getAbsolutePath(), variantName, sourceCompatibilityVersion, targetCompatibilityVersion);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
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
            File assetsFolder = resolvedArtifact.getAssetsFolder();
            if ((assetsFolder.exists())) {
                assetsTask.getInputs().dir(assetsFolder);
            }
        });
        assetsTask.doFirst(task -> androidArchiveLibraries.forEach(resolvedArtifact -> {
            File assetsFolder = (resolvedArtifact).getAssetsFolder();
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
        String taskPath = "merge" + variantName + "ConsumerProguardFiles";
        MergeFileTask mergeFileTask = (MergeFileTask) project.getTasks().findByPath(taskPath);
        if (mergeFileTask == null) {
            throw new RuntimeException("Can not find task " + taskPath);
        }
        mergeFileTask.doFirst(task -> {
            Set<File> inputFiles = new HashSet<>(mergeFileTask.getInputFiles());
            androidArchiveLibraries.forEach(androidArchiveLibrary -> {
                File thirdProguard = androidArchiveLibrary.getProguardRules();
                if (!thirdProguard.exists()) {
                    return;
                }
                inputFiles.add(thirdProguard);
            });
            inputFiles.add(getExternalLibsProguard());
            mergeFileTask.setInputFiles(inputFiles);
        });
    }

    private void createDexTask(InjectorExtension extension) {
        Task createDexesTask = project.getTasks().create("createInject" + variantName + "Dexes", Task.class);
        createDexesTask.doFirst(task -> {
            if (!extension.isEnabled()) {
                return;
            }
            processRSources();
            List<ResolvedArtifact> artifacts = new ArrayList<>(androidArchiveLibraries);
            artifacts.addAll(jarFiles);
            Map<String, List<ResolvedArtifact>> dexs = extension.getDexes(artifacts);
            List<List<String>> dexOptions = new ArrayList<>();
            String outFilePath = project.getBuildDir().getAbsolutePath() + extension.getDexLocation();
            dexs.forEach((key, value) -> {
                if (!value.isEmpty()) {
                    String outPutDex = outFilePath + key + ".zip";
                    List<String> dexOption = new ArrayList<>();
                    dexOption.add("--release");
                    dexOption.add("--output");
                    dexOption.add(outPutDex);
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
                    command = D8Command.parse(dexOption.toArray(new String[0]), CommandLineOrigin.INSTANCE).build();
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
        });

        Task extractAARsTask = project.getTasks().findByPath(InjectorPlugin.EXTRACT_AARS_TASK_NAME);
        Task assembleTask = project.getTasks().findByPath("assemble" + variantName);
        createDexesTask.dependsOn(extractAARsTask);
        createDexesTask.dependsOn(assembleTask);
    }
}

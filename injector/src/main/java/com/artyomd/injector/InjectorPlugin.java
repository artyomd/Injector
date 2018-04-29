package com.artyomd.injector;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.api.BaseVariant;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

public class InjectorPlugin implements Plugin<Project> {

    public static final String EXTRACT_AARS_TASK_NAME = "extractAARs";

    private Project project;
    private Configuration injectConf;

    private InjectorExtension extension;
    private Set<ResolvedArtifact> jars;
    private Set<AndroidArchiveLibrary> aars;


    @Override
    public void apply(@NotNull Project project) {
        this.project = project;
        extension = project.getExtensions().create("injectConfig", InjectorExtension.class);
        createConfiguration();
        project.afterEvaluate(project1 -> {
            resolveArtifacts();
            removeOldVersions(jars);
            removeOldVersions(aars);
            createExtractAARsTask();
            Object extension = project1.getExtensions().getByName("android");
            if (extension instanceof LibraryExtension) {
                ((LibraryExtension) extension).getLibraryVariants().all(this::processVariant);
            } else if (extension instanceof AppExtension) {
                ((AppExtension) extension).getApplicationVariants().all(this::processVariant);
            }
        });
    }

    public void createExtractAARsTask() {
        Task extractAars = project.getTasks().create(EXTRACT_AARS_TASK_NAME, Task.class);
        extractAars.doFirst(task -> aars.forEach((Consumer<ResolvedArtifact>) resolvedArtifact -> {
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

    private void createConfiguration() {
        injectConf = project.getConfigurations().create("inject");
        injectConf.setVisible(false);
        injectConf.setTransitive(true);
        project.getGradle().addListener(new DependencyResolutionListener() {
            @Override
            public void beforeResolve(@NotNull ResolvableDependencies dependencies) {
                injectConf.getDependencies().forEach(dependency -> {
                    if (extension.checkGroup(dependency.getGroup()) && extension.checkName(dependency.getName())) {
                        project.getDependencies().add("compileOnly", dependency);
                    } else if (!extension.checkForceExcluded(dependency)) {
                        project.getDependencies().add("implementation", dependency);
                    }
                });
                project.getGradle().removeListener(this);
            }

            @Override
            public void afterResolve(@NotNull ResolvableDependencies dependencies) {

            }
        });
    }

    private void resolveArtifacts() {
        Set<ResolvedArtifact> jars = new HashSet<>();
        Set<AndroidArchiveLibrary> aars = new HashSet<>();
        injectConf.getResolvedConfiguration().getResolvedArtifacts().forEach(resolvedArtifact -> {
            if (extension.checkArtifact(resolvedArtifact) && !extension.checkForceExcluded(resolvedArtifact)) {
                System.out.println("inject-->[injection detected][" + resolvedArtifact.getType() + ']' + resolvedArtifact.getModuleVersion().getId());
                if ("jar".equals(resolvedArtifact.getType())) {
                    jars.add(resolvedArtifact);
                } else if ("aar".equals(resolvedArtifact.getType())) {
                    aars.add(new AndroidArchiveLibrary(project, resolvedArtifact));
                }
            }
        });
        this.jars = Collections.unmodifiableSet(jars);
        this.aars = Collections.unmodifiableSet(aars);
    }

    private void processVariant(BaseVariant variant) {
        VariantProcessor processor = new VariantProcessor(project, variant);
        processor.setJarFiles(jars);
        processor.setAndroidArchiveLibraries(aars);
        processor.processVariant(extension);
    }


    private static void removeOldVersions(Set<? extends ResolvedArtifact> artifactsList) {
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
}

package com.artyomd.injector;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.api.BaseVariant;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencyResolutionListener;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class InjectorPlugin implements Plugin<Project> {

    private Project project;
    private Configuration injectConf;

    private InjectorExtension extension;
    private Set<ResolvedArtifact> artifacts;

    @Override
    public void apply(@NotNull Project project) {
        this.project = project;
        extension = project.getExtensions().create("injectConfig", InjectorExtension.class);
        createConfiguration();
        project.afterEvaluate(project1 -> {
            resolveArtifacts();
            Object extension = project1.getExtensions().getByName("android");
            if (extension instanceof LibraryExtension) {
                ((LibraryExtension) extension).getLibraryVariants().all(this::processVariant);
            } else if (extension instanceof AppExtension) {
                ((AppExtension) extension).getApplicationVariants().all(this::processVariant);
            }
        });
    }

    private void createConfiguration() {
        injectConf = project.getConfigurations().create("inject");
        injectConf.setVisible(false);
        injectConf.setTransitive(true);
        project.getGradle().addListener(new DependencyResolutionListener() {
            @Override
            public void beforeResolve(@NotNull ResolvableDependencies dependencies) {
                injectConf.getDependencies().forEach(dependency -> {
                    if  (extension.checkGroup(dependency.getGroup()) && extension.checkName(dependency.getName())) {
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
        Set<ResolvedArtifact> set = new HashSet<>();
        injectConf.getResolvedConfiguration().getResolvedArtifacts().forEach(resolvedArtifact -> {
            if ((("aar".equals(resolvedArtifact.getType()) || "jar".equals(resolvedArtifact.getType())) && extension.checkArtifact(resolvedArtifact) && !extension.checkForceExcluded(resolvedArtifact))) {
                System.out.println("inject-->[injection detected][" + resolvedArtifact.getType() + ']' + resolvedArtifact.getModuleVersion().getId());
                set.add(resolvedArtifact);
            }
        });
        artifacts = Collections.unmodifiableSet(set);
    }

    private void processVariant(BaseVariant variant) {
        VariantProcessor processor = new VariantProcessor(project, variant);
        artifacts.forEach(resolvedArtifact -> {
            if ("aar".equals(resolvedArtifact.getType())) {
                if (extension.checkArtifact(resolvedArtifact)) {
                    AndroidArchiveLibrary library = new AndroidArchiveLibrary(project, resolvedArtifact);
                    processor.addAndroidArchiveLibrary(library);
                }
            }
            if ("jar".equals(resolvedArtifact.getType())) {
                if (extension.checkArtifact(resolvedArtifact)) {
                    processor.addJarFile(resolvedArtifact);
                }
            }
        });
        processor.processVariant(extension);
    }
}

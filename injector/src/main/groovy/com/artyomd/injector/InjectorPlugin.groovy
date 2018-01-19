package com.artyomd.injector

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.ResolvedArtifact

public class InjectorPlugin implements Plugin<Project> {

    private Project project
    private Configuration injectConf

    private InjectorExtension extension;
    private Set<ResolvedArtifact> artifacts

    @Override
    void apply(Project project) {
        this.project = project
        extension = project.extensions.create('injectConfig', InjectorExtension)
        createConfiguration()
        project.afterEvaluate {
            resolveArtifacts()
            if (project.android.hasProperty("libraryVariants")) {
                project.android.libraryVariants.all {
                    variant -> processVariant(variant)
                }
            } else {
                project.android.applicationVariants.all {
                    variant -> processVariant(variant)
                }
            }
        }
    }

    private void createConfiguration() {
        injectConf = project.configurations.create('inject')
        injectConf.visible = false
        project.gradle.addListener(new DependencyResolutionListener() {
            @Override
            void beforeResolve(ResolvableDependencies resolvableDependencies) {
                injectConf.dependencies.each { dependency ->
                    if (!extension.checkArtifact(dependency.group)) {
                        project.dependencies.add('implementation', dependency)
                    } else {
                        project.dependencies.add('compileOnly', dependency)
                    }
                }
                project.gradle.removeListener(this)
            }

            @Override
            void afterResolve(ResolvableDependencies resolvableDependencies) {}
        })
    }

    private void resolveArtifacts() {
        Set set = new HashSet<>()
        injectConf.resolvedConfiguration.resolvedArtifacts.each { artifact ->
            if (('aar' == artifact.type || 'jar' == artifact.type) && extension.checkArtifact(artifact.moduleVersion.id.group)) {
                println 'inject-->[injection detected][' + artifact.type + ']' + artifact.moduleVersion.id
                set.add(artifact)
            }
        }
        artifacts = Collections.unmodifiableSet(set)
    }

    private void processVariant(variant) {
        VariantProcessor processor = new VariantProcessor(project, variant)
        artifacts.each { artifact ->
            if ('aar' == artifact.type) {
                if (extension.checkArtifact(artifact.moduleVersion.id.group)) {
                    AndroidArchiveLibrary library = new AndroidArchiveLibrary(project, artifact)
                    processor.addAndroidArchiveLibrary(library)
                }
            }
            if ('jar' == artifact.type) {
                if (extension.checkArtifact(artifact.getModuleVersion().id.group)) {
                    processor.addJarFile(artifact)
                }
            }
        }
        processor.processVariant(extension)
    }
}

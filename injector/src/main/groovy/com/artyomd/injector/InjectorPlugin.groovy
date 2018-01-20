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
        injectConf.setTransitive(true)
        project.gradle.addListener(new DependencyResolutionListener() {
            @Override
            void beforeResolve(ResolvableDependencies resolvableDependencies) {
                injectConf.dependencies.each { dependency ->
                    if (extension.checkGroup(dependency.group) && extension.checkName(dependency.name)) {
                        project.dependencies.add('compileOnly', dependency)
                    } else if(!extension.checkForceExcluded(dependency)){
                        project.dependencies.add('implementation', dependency)

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
            if (('aar' == artifact.type || 'jar' == artifact.type) && extension.checkArtifact(artifact) && !extension.checkForceExcluded(artifact)) {
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
                if (extension.checkArtifact(artifact)) {
                    AndroidArchiveLibrary library = new AndroidArchiveLibrary(project, artifact)
                    processor.addAndroidArchiveLibrary(library)
                }
            }
            if ('jar' == artifact.type) {
                if (extension.checkArtifact(artifact)) {
                    processor.addJarFile(artifact)
                }
            }
        }
        processor.processVariant(extension)
    }
}

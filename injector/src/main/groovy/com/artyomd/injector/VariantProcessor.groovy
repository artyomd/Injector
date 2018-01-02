package com.artyomd.injector

import org.gradle.api.Project
import org.gradle.api.Task

class VariantProcessor {

    private final Project project

    private final def variant

    private Collection<AndroidArchiveLibrary> androidArchiveLibraries = new ArrayList<>()

    private Collection<File> jarFiles = new ArrayList<>()

    public VariantProcessor(Project project, variant) {
        this.project = project
        this.variant = variant
    }

    public void addAndroidArchiveLibrary(AndroidArchiveLibrary library) {
        androidArchiveLibraries.add(library)
    }

    public void addJarFile(File jar) {
        jarFiles.add(jar)
    }

    public void processVariant(String dexLocation) {
        if (!androidArchiveLibraries.isEmpty()) {
            extractAARs()
            processManifest()
            processResourcesAndR()
            processRSources()
            processAssets()
            processJniLibs()
        }
        createDex(dexLocation)
    }

    /**
     * merge manifests
     */
    private void processManifest() {
        Class invokeManifestTaskClazz = null
        String className = 'com.android.build.gradle.tasks.InvokeManifestMerger'
        try {
            invokeManifestTaskClazz = Class.forName(className)
        } catch (ClassNotFoundException ignored) {
        }
        if (invokeManifestTaskClazz == null) {
            throw new RuntimeException("Can not find class ${className}!")
        }
        Task processManifestTask = variant.getOutputs()[0].getProcessManifest()
        Task manifestsMergeTask = project.tasks.create('merge' + variant.name.capitalize() + 'Manifest', invokeManifestTaskClazz) as Task
        manifestsMergeTask.setVariantName(variant.name)
        manifestsMergeTask.setMainManifestFile(project.android.sourceSets.main.manifest.srcFile)
        List<File> list = new ArrayList<>()
        for (archiveLibrary in androidArchiveLibraries) {
            list.add(archiveLibrary.getManifest())
        }
        manifestsMergeTask.setSecondaryManifestFiles(list)
        manifestsMergeTask.setOutputFile(new File(processManifestTask.getManifestOutputDirectory(), "AndroidManifest.xml"))
        manifestsMergeTask.dependsOn processManifestTask
        processManifestTask.finalizedBy manifestsMergeTask
    }

    /**
     * extract aar
     */
    private void extractAARs() {
        String taskPath = 'preBuild'
        Task preBuildTask = project.tasks.findByPath(taskPath)
        preBuildTask.doFirst {
            for (AndroidArchiveLibrary library : androidArchiveLibraries) {
                //we are relying on ant builder to extract aars
                def ant = new AntBuilder()
                ant.unzip(src: library.getArtifactFile().getAbsolutePath(),
                        dest: library.getRootFolder(),
                        overwrite: "false")
            }
        }
    }

    /**
     * merge resources
     * */
    private void processResourcesAndR() {
        String taskPath = 'generate' + variant.name.capitalize() + 'Resources'
        Task resourceGenTask = project.tasks.findByPath(taskPath)
        if (resourceGenTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }
        resourceGenTask.doFirst {
            for (archiveLibrary in androidArchiveLibraries) {
                project.android.sourceSets."main".res.srcDir(archiveLibrary.resFolder)
            }
        }
    }

    /**
     * generate R.java
     */
    private void processRSources() {
        def processResourcesTask = variant.getOutputs()[0].getProcessResources()
        processResourcesTask.doLast {
            for (archiveLibrary in androidArchiveLibraries) {
                RSourceGenerator.generate(archiveLibrary)
            }
        }
    }

    /**
     * merge assets
     */
    private void processAssets() {
        def assetsTask = variant.getMergeAssets()
        if (assetsTask == null) {
            throw new RuntimeException("Can not find task in variant.getMergeAssets()!")
        }
        for (archiveLibrary in androidArchiveLibraries) {
            assetsTask.getInputs().dir(archiveLibrary.assetsFolder)
        }
        assetsTask.doFirst {
            for (archiveLibrary in androidArchiveLibraries) {
                project.android.sourceSets."main".assets.srcDir(archiveLibrary.assetsFolder)
            }
        }
    }

    /**
     * merge jniLibs
     */
    private void processJniLibs() {
        String taskPath = 'merge' + variant.name.capitalize() + 'JniLibFolders'
        Task mergeJniLibsTask = project.tasks.findByPath(taskPath)
        if (mergeJniLibsTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }
        for (archiveLibrary in androidArchiveLibraries) {
            mergeJniLibsTask.getInputs().dir(archiveLibrary.jniFolder)
        }
        mergeJniLibsTask.doFirst {
            for (archiveLibrary in androidArchiveLibraries) {
                project.android.sourceSets."main".jniLibs.srcDir(archiveLibrary.jniFolder)
            }
        }
    }

    /**
     * generate dex file
     * getting path to current build tools version dx tool(in android sdk)
     * and executing command dx --dex --output=/outputs/inject/toInject.dex jars
     * */
    private void createDex(String dexLocation) {
        Properties properties = new Properties()
        properties.load(project.rootProject.file('local.properties').newDataInputStream())
        def sdkDir = properties.getProperty('sdk.dir')
        def buildToolsVersion = project.android.buildToolsVersion
        String pathToDx = sdkDir + "/build-tools/" + buildToolsVersion + "/dx --dex"
        StringBuilder stringBuilder = new StringBuilder()
        stringBuilder.append(pathToDx)
        String outPutDex = project.buildDir.absolutePath + variant.outputs[0].getDirName() + dexLocation
        stringBuilder.append(" --output=" + outPutDex)
        for (AndroidArchiveLibrary library : androidArchiveLibraries) {
            stringBuilder.append(" " + library.classesJarFile.absolutePath)
        }
        for (File jar : jarFiles) {
            stringBuilder.append(" " + jar.absolutePath)
        }
        String taskPath = 'assemble' + variant.name.capitalize()
        Task assembleTask = project.tasks.findByPath(taskPath)
        assembleTask.doLast {
            new File(outPutDex).getParentFile().mkdirs();
            Utils.execCommand(stringBuilder.toString())
        }
    }
}

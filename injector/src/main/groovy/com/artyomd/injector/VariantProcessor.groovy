package com.artyomd.injector

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ResolvedArtifact

class VariantProcessor {

    private final Project project

    private final def variant

    private Collection<AndroidArchiveLibrary> androidArchiveLibraries = new ArrayList<>()

    private Collection<ResolvedArtifact> jarFiles = new ArrayList<>()

    public VariantProcessor(Project project, variant) {
        this.project = project
        this.variant = variant
    }

    public void addAndroidArchiveLibrary(AndroidArchiveLibrary library) {
        androidArchiveLibraries.add(library)
    }

    public void addJarFile(ResolvedArtifact jar) {
        jarFiles.add(jar)
    }

    public void processVariant(InjectorExtension configs) {
        if (!androidArchiveLibraries.isEmpty()) {
            extractAARs()
            processManifest()
            processResourcesAndR()
            processRSources()
            processAssets()
            processJniLibs()
        }
        createDex(configs)
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
        androidArchiveLibraries.each { archiveLibrary ->
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
            androidArchiveLibraries.each { AndroidArchiveLibrary library ->
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
            androidArchiveLibraries.each { archiveLibrary ->
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
            androidArchiveLibraries.each { archiveLibrary ->
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
        androidArchiveLibraries.each { archiveLibrary ->
            assetsTask.getInputs().dir(archiveLibrary.assetsFolder)
        }
        assetsTask.doFirst {
            androidArchiveLibraries.each { archiveLibrary ->
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
        androidArchiveLibraries.each { archiveLibrary ->
            mergeJniLibsTask.getInputs().dir(archiveLibrary.jniFolder)
        }
        mergeJniLibsTask.doFirst {
            androidArchiveLibraries.each { archiveLibrary ->
                project.android.sourceSets."main".jniLibs.srcDir(archiveLibrary.jniFolder)
            }
        }
    }

    /**
     * generate dex file
     * getting path to current build tools version dx tool(in android sdk)
     * and executing command dx --dex --output=/outputs/inject/toInject.dex jars
     * */
    private void createDex(InjectorExtension extension) {
        Properties properties = new Properties()
        properties.load(project.rootProject.file('local.properties').newDataInputStream())
        def sdkDir = properties.getProperty('sdk.dir')
        def buildToolsVersion = project.android.buildToolsVersion
        String pathToDx = sdkDir + "/build-tools/" + buildToolsVersion + "/dx --dex"
        String outFile = project.buildDir.absolutePath + variant.outputs[0].getDirName() + extension.dexLoaction
        List artifacts = new ArrayList();
        artifacts.addAll(androidArchiveLibraries)
        artifacts.addAll(jarFiles)
        Map<String, List> dexs = extension.getDexes(artifacts)
        List<String> commands = new ArrayList<>();
        dexs.entrySet().each { Map.Entry<String, List> entry ->
            StringBuilder stringBuilder = new StringBuilder()
            stringBuilder.append(pathToDx)
            String outPutDex = outFile + entry.key
            stringBuilder.append(" --output=" + outPutDex)
            entry.value.each {artifact ->
                if(artifact instanceof AndroidArchiveLibrary){
                    stringBuilder.append(" " + artifact.classesJarFile.absolutePath)
                }else{
                    stringBuilder.append(" " + jar.file.absolutePath)
                }
            }
            commands.add(stringBuilder.toString())
        }
        String taskPath = 'assemble' + variant.name.capitalize()
        Task assembleTask = project.tasks.findByPath(taskPath)
        assembleTask.doLast {
            new File(outFile).getParentFile().mkdirs();
            commands.each {command ->
                Utils.execCommand(command)
            }
        }
    }
}

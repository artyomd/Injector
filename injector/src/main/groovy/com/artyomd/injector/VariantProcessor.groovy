package com.artyomd.injector

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ResolvedArtifact

class VariantProcessor {

    private final Project project

    //LibraryVariant
    private final def variant

    private JavaVersion sourceCompatibilityVersion
    private JavaVersion targetCompatibilityVersion

    private List<AndroidArchiveLibrary> androidArchiveLibraries = []

    private List<ResolvedArtifact> jarFiles = []

    VariantProcessor(Project project, variant) {
        this.project = project
        this.variant = variant
    }

    void addAndroidArchiveLibrary(AndroidArchiveLibrary library) {
        androidArchiveLibraries.add(library)
    }

    void addJarFile(ResolvedArtifact jar) {
        jarFiles.add(jar)
    }

    private static void checkArtifacts(List artifactsList) {
        Map<String, Map<String, ResolvedArtifact>> artifacts = new HashMap()
        for (def artifactDef : artifactsList) {
            if (artifactDef instanceof AndroidArchiveLibrary) {
                artifactDef = artifactDef.artifact
            }
            ResolvedArtifact artifact = artifactDef as ResolvedArtifact
            String name = artifact.moduleVersion.id.name
            String group = artifact.moduleVersion.id.group
            String version = artifact.moduleVersion.id.version;
            if (artifacts.containsKey(group)) {
                Map<String, ResolvedArtifact> names = artifacts.get(group)
                if (names.containsKey(name)) {
                    ResolvedArtifact old = names.get(name)
                    if (old.moduleVersion.id.version < version) {
                        names.put(name, artifact)
                        artifactsList.remove(old)
                    } else {
                        artifactsList.remove(artifact)
                    }
                } else {
                    names.put(name, artifact)
                }
            } else {
                Map<String, ResolvedArtifact> names = new HashMap<>();
                names.put(name, artifact)
                artifacts.put(group, names)
            }
        }

    }

    void processVariant(InjectorExtension configs) {
        def compileOptions = project.android.getCompileOptions()
        sourceCompatibilityVersion = compileOptions.sourceCompatibility
        targetCompatibilityVersion = compileOptions.targetCompatibility
        System.print("source" + sourceCompatibilityVersion)

        checkArtifacts(jarFiles)
        checkArtifacts(androidArchiveLibraries)

        if (!androidArchiveLibraries.isEmpty()) {
            extractAARs()
            processManifest()
            processResourcesAndR()
            processRSources()
            processAssets()
            processJniLibs()
        }
        processProguardTxt()
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
        Task manifestsMergeTask = project.tasks.create('merge' + (variant.name.capitalize() as String) + 'Manifest', invokeManifestTaskClazz) as Task
        manifestsMergeTask.setVariantName(variant.name)
        manifestsMergeTask.setMainManifestFile(project.android.sourceSets.main.manifest.srcFile)
        List<File> list = []
        androidArchiveLibraries.each { archiveLibrary ->
            list.add(archiveLibrary.getManifest())
        }
        manifestsMergeTask.setSecondaryManifestFiles(list)
        manifestsMergeTask.setOutputFile(new File((processManifestTask.getManifestOutputDirectory() as File), "AndroidManifest.xml"))
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
        String taskPath = 'generate' + (variant.name.capitalize() as String) + 'Resources'
        Task resourceGenTask = project.tasks.findByPath(taskPath)
        if (resourceGenTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }
        resourceGenTask.doFirst {
            androidArchiveLibraries.each { archiveLibrary ->
                if (archiveLibrary.resFolder.exists()) {
                    project.android.sourceSets."main".res.srcDir(archiveLibrary.resFolder)
                }
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
                RSourceGenerator.generate(archiveLibrary, sourceCompatibilityVersion, targetCompatibilityVersion)
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
            if (archiveLibrary.assetsFolder.exists()) {
                assetsTask.getInputs().dir(archiveLibrary.assetsFolder)
            }
        }
        assetsTask.doFirst {
            androidArchiveLibraries.each { archiveLibrary ->
                if (archiveLibrary.assetsFolder.exists()) {
                    project.android.sourceSets."main".assets.srcDir(archiveLibrary.assetsFolder)
                }
            }
        }
    }

    /**
     * merge jniLibs
     */
    private void processJniLibs() {
        String taskPath = 'merge' + (variant.name.capitalize() as String) + 'JniLibFolders'
        Task mergeJniLibsTask = project.tasks.findByPath(taskPath)
        if (mergeJniLibsTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }
        androidArchiveLibraries.each { archiveLibrary ->
            if (archiveLibrary.jniFolder.exists()) {
                mergeJniLibsTask.getInputs().dir(archiveLibrary.jniFolder)
            }
        }
        mergeJniLibsTask.doFirst {
            androidArchiveLibraries.each { archiveLibrary ->
                if (archiveLibrary.jniFolder.exists()) {
                    project.android.sourceSets."main".jniLibs.srcDir(archiveLibrary.jniFolder)
                }
            }
        }
    }

    private File getExternalLibsProguard() {
        File workingDir = Utils.getWorkingDir(project)
        File proguardFile = new File(workingDir, "libs.txt")
        if (proguardFile.exists()) {
            proguardFile.delete()
        }
        proguardFile.createNewFile()
        androidArchiveLibraries.each { AndroidArchiveLibrary lib ->
            proguardFile.append("\n-libraryjars " + lib.artifact.file.getAbsolutePath())
        }
        jarFiles.each { ResolvedArtifact lib ->
            proguardFile.append("\n-libraryjars " + lib.file.getAbsolutePath())
        }
        return proguardFile
    }
    /**
     * merge proguard.txt
     */
    private void processProguardTxt() {
        String mergeProguardFileTaskPath = 'merge' + (variant.name.capitalize() as String) + 'ConsumerProguardFiles'
        Task mergeProguardFileTask = project.tasks.findByPath(mergeProguardFileTaskPath)
        if (mergeProguardFileTask == null) {
            throw new RuntimeException("Can not find task ${mergeProguardFileTaskPath}!")
        }
        mergeProguardFileTask.doFirst {
            Collection proguardFiles = mergeProguardFileTask.getInputFiles()
            androidArchiveLibraries.each { archiveLibrary ->
                File thirdProguard = archiveLibrary.proguardRules
                if (thirdProguard.exists()) {
                    proguardFiles.add(thirdProguard)
                }
            }
            proguardFiles.add(getExternalLibsProguard())
        }
    }

    /**
     * generate dex file
     * getting path to current build tools version dx tool(in android sdk)
     * and executing command dx --dex --output=/outputs/inject/toInject.dex jars
     * */
    private void createDex(InjectorExtension extension) {
        Properties properties = new Properties()
        def sdkDir
        if (project.rootProject.file('local.properties').exists()) {
            properties.load(project.rootProject.file('local.properties').newDataInputStream())
            sdkDir = properties.getProperty('sdk.dir')
        } else {
            sdkDir = "$System.env.ANDROID_HOME"
        }
        String buildToolsVersion = project.android.buildToolsVersion
        String pathToDx = sdkDir + "/build-tools/" + buildToolsVersion + "/dx --dex"
        String outFile = project.buildDir.absolutePath + (variant.outputs[0].getDirName() as String) + extension.dexLoaction
        List artifacts = []
        artifacts.addAll(androidArchiveLibraries)
        artifacts.addAll(jarFiles)
        Map<String, List> dexs = extension.getDexes(artifacts)
        List<String> commands = []
        dexs.entrySet().each { Map.Entry<String, List> entry ->
            StringBuilder stringBuilder = new StringBuilder()
            stringBuilder.append(pathToDx)
            String outPutDex = outFile + entry.key
            stringBuilder.append(" --output=" + outPutDex)
            entry.value.each {artifact ->
                if(artifact instanceof AndroidArchiveLibrary){
                    File classesJar = artifact.classesJarFile
                    if (classesJar.exists()) {
                    stringBuilder.append(" " + artifact.classesJarFile.absolutePath)
                    }
                }else{
                    File classesJar = artifact.file
                    if (classesJar.exists()) {
                        stringBuilder.append(" " + (artifact.file.absolutePath as String))
                    }
                }
            }
            commands.add(stringBuilder.toString())
        }
        String taskPath = 'assemble' + (variant.name.capitalize() as String)
        Task assembleTask = project.tasks.findByPath(taskPath)
        assembleTask.doLast {
            new File(outFile).mkdirs()
            commands.each {command ->
                Utils.execCommand(command)
            }
        }
    }
}

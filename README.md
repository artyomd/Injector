<p align="center">
<img src="https://raw.githubusercontent.com/jbeguna04/Injector/master/LogoDesigns/logotype1blue.png" width=610.082 align="center">
</p>

# Injector
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/app.artyomd.injector/injector/badge.svg)](https://maven-badges.herokuapp.com/maven-central/app.artyomd.injector/injector)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/f9c01ceb05ef4949a9c9869f22a5524d)](https://app.codacy.com/app/artyomd/Injector?utm_source=github.com&utm_medium=referral&utm_content=artyomd/Injector&utm_campaign=badger)
<a href='https://travis-ci.com/artyomd/Injector/builds'><img src='https://travis-ci.com/artyomd/Injector.svg?branch=master'></a>
[![Known Vulnerabilities](https://snyk.io/test/github/artyomd/Injector/badge.svg?targetFile=injector%2Fbuild.gradle)](https://snyk.io/test/github/artyomd/Injector?targetFile=injector%2Fbuild.gradle)

Injector is a gradle plugin for android projects which helps to make third-party android libraries downloadable. Injector supports android gradle plugin **3.1.0+**. For more information and background of Injector you can read this 
[article](https://medium.com/@artyomdangizyan/aar-to-dex-loading-and-running-code-at-runtime-in-android-application-69089a30c715).
# How it works
Injector plugin extracts all aar files into build/exploded-aar directory then, merges all manifests to your project's manifest, copies all resources into your project's resource directory, generates R.java for aar libraries, compiles them and injects classes into library's class.jar and then creates dex files from jar files.
# How to use
Injector consists of two parts: gradle plugin (to create dex files from aar and jar files) and android library (to load dex files)
## How to create dex files
Add maven central repository to your project's build script and add injector library to your classpath **app.artyomd.injector:injector:{latest-version}** . Your build script should look like the following
```
buildscript {
    repositories {
        google()
	mavenCentral()
        jcenter()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:3.1.4'
        classpath 'app.artyomd.injector:injector:{latest-version}'
    }
}
```
After this, you can just **apply plugin: 'app.artyomd.injector'** in you android library module and use **inject** configuration to inject other libraries into your project. Injector also provides a configuration closure to exclude libraries with group/name and specify dex file location. Default location of dex file is build/outputs/inject/inject.dex. To generate dex file you must run of the following tasks **createInjectDebugDexes** or **createInjectReleaseDexes**
```
injectConfig{
    enabled = true
	excludeGroups = ["com.foo.*", "bar.foo.*"]
	groups = [
            "X": ["foo.bar.*"]
    ]
    dexLocation = "/outputs/inject/inject.dex"
}
```
By default, we are excluding android libs such as com.android.*, android.arch.*, etc.

With this inject config you will get X.dex and inject.dex files in /outputs/inject/ directory and that dex files will not contain classes which packages are "com.foo.*", "bar.foo.*" and X.dex will only conatn classes which packages are "foo.bar.*".

## How to load dex files at runtime
Injector also provides android helper library to inject dex files. Add maven  central repository to your project dependencies and add **"app.artyomd.injector:injector-android:{latest-version}"** in your dependencies
```
repositories {
        google()
	mavenCentral()
        jcenter()
    }
dependencies {
    implementation "app.artyomd.injector:injector-android:{latest-version}"
}
```
You can upload dex file to somewhere and then at runtime download and load it or copy dex file to assets folder then at runtime copy the file into internal storage and then load dex file. If your dex files are in the assets dir just use **DexUtils.prepareDex** to copy dex files into internal storage. Using **DexUtils.loadDex** you can load a list of dex files into your application at runtime.

## Disclaimer

> An app distributed via Google Play may not modify, replace, or update itself using any method other than Google Play's update mechanism. Likewise, an app may not download executable code (e.g. dex, JAR, .so files) from a source other than Google Play. This restriction does not apply to code that runs in a virtual machine and has limited access to Android APIs (such as JavaScript in a webview or browser).
[Source](https://play.google.com/about/privacy-security-deception/malicious-behavior/)

Be aware that downloading executable code from an application may affect the removal of your application from Google Play.

<p align="center">
<img src="https://raw.githubusercontent.com/jbeguna04/Injector/master/LogoDesigns/logotype1blue.png" width=610.082 align="center">
</p>

# Injector
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/app.artyomd.injector/injector/badge.svg)](https://maven-badges.herokuapp.com/maven-central/app.artyomd.injector/injector)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/f9c01ceb05ef4949a9c9869f22a5524d)](https://app.codacy.com/app/artyomd/Injector?utm_source=github.com&utm_medium=referral&utm_content=artyomd/Injector&utm_campaign=badger)
<a href='https://travis-ci.org/artyomd/Injector/builds'><img src='https://travis-ci.org/artyomd/Injector.svg?branch=master'></a>
[![Known Vulnerabilities](https://snyk.io/test/github/artyomd/Injector/badge.svg?targetFile=injector%2Fbuild.gradle)](https://snyk.io/test/github/artyomd/Injector?targetFile=injector%2Fbuild.gradle)

Injector is a gradle plugin for android projects which helps making third party android libraries downloadable. Injector supports android gradle plugin **3.1.0+**.
# How it works
Injector plugin extracts all aar files into build/exploded-aar directory then, merges all manifests to your project's manifest, copies all resources into your project's resource dir, generates R.java for aar libs, compiles them and injects classes into lib's class.jar and then creates dex from jars.
# How to use
Add  maven central repository your project's buildscript repositories and add injector lib to you classpath **app.artyomd.injector:injector:0.3** . Your buildscript should look like this.
```
buildscript {

    repositories {
        google()
	mavenCentral()
        jcenter()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:3.1.2'
        classpath 'app.artyomd.injector:injector:0.3'
    }
}
```
After this you can just **apply plugin: 'app.artyomd.injector'** in you android library module and use **inject** to inject other libraries into your project. We also provide a configuration closure to exclude libs with group and specify dex location. (Default dex location is build/outputs/inject/inject.dex)
```
injectConfig{
	excludeGroups = ["com.foo.*", "bar.foo.*"]
	dexLocation = "/outputs/inject/inject.dex"
}
```
By default we are excluding android libs such as com.android.*, android.arch.*, etc.

We also provide android helper lib to injext dex files. Just again add the maven central repo to your repositories and add this in your dependencies
```
	implementation "app.artyomd.injector:injector-android:0.3@aar"
```
Using DexUtils.loadDex you can inject list of dex files into your project at runtime or if your dex files are in the assets dir just use DexUtils.prepareDex to copy dex files into internal storage and then loadDex

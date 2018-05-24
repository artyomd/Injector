# Injector
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

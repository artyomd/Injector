# Injector
Injector is just a gradle plugin for android projects which helps making third party android libraries downloadable. Injector supports android gradle plugin **3.0.0** and higher.
This project was inspired by [fat-aar-plugin](https://github.com/Vigi0303/fat-aar-plugin)
# How it works
Injector plugin extracts all aar files into build/exploded-aar directory then, merges all manifests to your project's manifest, copies all resources into your project's resource dir, generates R.java for aar libs, compiles them and injects classes into lib's class.jar and then creates dex from jars by using dx tool.
# How to use
Add this maven repo url to your project's buildscript repositories **https://mymavenrepo.com/repo/QdmWCKUvofW6L7vIcGYp/** and add injector lib to you classpath **com.artyomd.injector:injector:0.2** . Your buildscript should look like this.
```
buildscript {

    repositories {
        google()
        jcenter()
        maven { url "https://mymavenrepo.com/repo/QdmWCKUvofW6L7vIcGYp/" }
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:3.0.1'
        classpath 'com.artyomd.injector:injector:0.2'
    }
}
```
After this you can just **apply plugin: 'com.artyomd.injector'** in you android library module and use **inject** to inject other libraries into your project. We also provide a configuration closure to exclude libs with group and specify dex location. (Default dex location is build/outputs/inject/inject.dex)
```
injectConfig{
	excludeGroups = ["com.foo.*", "bar.foo.*"]
    dexLocation = "/outputs/inject/inject.dex"
}
```
By default we are excluding android libs such as com.android.*, android.arch.*, etc.

We also provide android helper lib to injext dex files. Just add the same repo to your repositories and add this in your dependencies
```
	implementation "com.artyomd.injector:injector-android:0.2@aar"
```
Using DexUtils.loadDex you can inject list of dex files into your project at runtime or if your dex files are in the assets dir just use DexUtils.prepareDex to copy dex files into internal storage and then loadDex



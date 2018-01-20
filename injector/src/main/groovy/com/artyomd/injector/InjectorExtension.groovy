package com.artyomd.injector

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedArtifact

class InjectorExtension {
    private def defaultExcludeGroups = ["com.android.*", "android.arch.*"]
    def excludeGroups = []
    def excludeNames = []
    def forceExcludeNames = []
    def fprceExcludeGroups = []
    def dexLoaction = "/outputs/inject/"
    def defaultDexName = "inject.dex"
    def groups = [:]

    boolean checkArtifact(ResolvedArtifact artifact) {
        if (!checkGroup(artifact.moduleVersion.id.group)) {
            return false
        }

        if (!checkName(artifact.moduleVersion.id.name)) {
            return false
        }

        return true
    }

    boolean checkForceExcluded(ResolvedArtifact artifact) {
        String name = artifact.moduleVersion.id.name
        for (String excludeName : forceExcludeNames) {
            if (name ==~ excludeName) {
                return true
            }
        }
        String group = artifact.moduleVersion.id.group
        for (String excludeName : fprceExcludeGroups) {
            if (group ==~ excludeName) {
                return true
            }
        }
        return false
    }

    boolean checkForceExcluded(Dependency dependency) {
        for (String excludeName : forceExcludeNames) {
            if (dependency.name ==~ excludeName) {
                return true
            }
        }
        for (String excludeName : fprceExcludeGroups) {
            if (dependency.group ==~ excludeName) {
                return true
            }
        }
        return false
    }

    boolean checkName(String name) {
        for (String excludeName : excludeNames) {
            if (name ==~ excludeName) {
                return false
            }
        }
        return true
    }

    boolean checkGroup(String group) {
        for (String excludeGroup : defaultExcludeGroups) {
            if (group ==~ excludeGroup) {
                return false
            }
        }

        for (String excludeGroup : excludeGroups) {
            if (group ==~ excludeGroup) {
                return false
            }
        }
        return true
    }

    Map<String, List> getDexes(List artifacts) {
        Map<String, List> map = new HashMap<>()
        artifacts.each { artifact ->
            List<String> names = getDexName(artifact)
            names.each { name ->
                if (map.containsKey(name)) {
                    map.get(name).add(artifact)
                } else {
                    List list = []
                    list.add(artifact)
                    map.put(name, list)
                }
            }
        }
        return map
    }

    List<String> getDexName(def artifact) {
        List<String> names = []
        for (Map.Entry<String, List<String>> entry : (groups as Map<String, List<String>>)) {
            checkContains(artifact, entry.value)
            names.add(entry.key)
        }
        if (names.isEmpty()) {
            names.add(defaultDexName)
        }
        return names
    }

    static boolean checkContains(def artifact, List<String> data) {
        if (artifact instanceof AndroidArchiveLibrary) {
            artifact = artifact.getArtifact()
        }
        return data.contains(artifact.moduleVersion.id.group) || data.contains(artifact.moduleVersion.id.name)
    }

}

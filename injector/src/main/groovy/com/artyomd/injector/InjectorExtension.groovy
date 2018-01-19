package com.artyomd.injector

import org.gradle.api.artifacts.ResolvedArtifact

class InjectorExtension {
    private def defaultExcludeGroups = ["com.android.*", "android.arch.*"]
    def excludeGroups = []
    def dexLoaction = "/outputs/inject/"
    def defaultDexName = "inject.dex"
    def dexGroups = []

    boolean checkArtifact(String group) {
        defaultExcludeGroups.each { str ->
            if (group ==~ str) {
                return false
            }
        }
        excludeGroups.each { str ->
            if (group ==~ str) {
                return false
            }
        }
        return true
    }

    Map<String, List> getDexes(List artifacts) {
        Map<String, List> map = new HashMap<>();
        artifacts.each { artifact ->
            List<String> names = getDexName(artifact)
            names.each { name ->
                if (map.containsKey(name)) {
                    map.get(name).add(artifact);
                } else {
                    List list = new ArrayList();
                    list.add(artifact);
                    map.put(name, list);
                }
            }
        }
        return map;

    }

    List<String> getDexName(def artifact) {
        List<String> names = new ArrayList<>();
        dexGroups.each { group ->
            if (group.checkArtifact(artifact)) {
                names.add(group.name)
            }
        }
        if (names.isEmpty()) {
            names.add(defaultDexName)
        }
        return names;
    }
}

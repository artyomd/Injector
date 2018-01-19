package com.artyomd.injector

class InjectorExtensionGroup {
    def name
    def groups = []
    def artifacts = []

    boolean checkArtifact(def artifact) {
        if(artifact instanceof AndroidArchiveLibrary){
            artifact = artifact.getArtifact()
        }
        return groups.contains(artifact.moduleVersion.id.group) || artifacts.contains(artifact.moduleVersion.id.name)
    }
}

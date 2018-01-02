package com.artyomd.injector

class InjectorExtension {
    private def defaultExcludeGroups = ["com.android.*", "android.arch.*"]
    def excludeGroups = []
    def dexLocation = "/outputs/inject/inject.dex"

    boolean checkArtifact(String group) {
        for (def str : defaultExcludeGroups) {
            if (group ==~ str) {
                return false
            }
        }
        for (def str : excludeGroups) {
            if (group ==~ str) {
                return false
            }
        }
        return true
    }
}

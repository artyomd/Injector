package app.artyomd.injector.extension;

import app.artyomd.injector.util.Utils;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class InjectorExtension {
	private boolean enabled = true;
	private List<String> defaultExcludeGroups = new ArrayList<>();
	private List<String> excludeGroups = new ArrayList<>();
	private List<String> excludeNames = new ArrayList<>();
	private List<String> forceExcludeNames = new ArrayList<>();
	private List<String> forceExcludeGroups = new ArrayList<>();
	private String dexLocation = "/outputs/inject/";
	private String defaultDexName = "inject";
	private Map<String, List<String>> groups = new HashMap<>();

	public InjectorExtension() {
		defaultExcludeGroups.add("com.android.*");
		defaultExcludeGroups.add("androidx.*");
		defaultExcludeGroups.add("android.arch.*");
	}

	private static boolean checkContains(ResolvedArtifact artifact, List<String> data) {
		return data.contains(artifact.getModuleVersion().getId().getGroup()) || data.contains(artifact.getModuleVersion().getId().getName());
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getDexLocation() {
		return dexLocation;
	}

	public void setDexLocation(String dexLocation) {
		this.dexLocation = dexLocation;
	}

	public void setExcludeGroups(List<String> excludeGroups) {
		this.excludeGroups = new ArrayList<>(excludeGroups);
	}

	public void setExcludeNames(List<String> excludeNames) {
		this.excludeNames = new ArrayList<>(excludeNames);
	}

	public void setForceExcludeNames(List<String> forceExcludeNames) {
		this.forceExcludeNames = new ArrayList<>(forceExcludeNames);
	}

	public void setForceExcludeGroups(List<String> forceExcludeGroups) {
		this.forceExcludeGroups = new ArrayList<>(forceExcludeGroups);
	}

	public void setDefaultDexName(String defaultDexName) {
		this.defaultDexName = defaultDexName;
	}

	public void setGroups(Map<String, ? extends List<String>> groups) {
		this.groups = new HashMap<>(groups);
	}

	public boolean isExcluded(ResolvedArtifact artifact) {
		ModuleVersionIdentifier id = artifact.getModuleVersion().getId();
		return isGroupExcluded(id.getGroup()) || isNameExcluded(id.getName());
	}

	public boolean isExcluded(Dependency dependency) {
		return isGroupExcluded(dependency.getGroup()) || isNameExcluded(dependency.getName());
	}

	private boolean isNameExcluded(String name) {
		return Utils.listContainsMatcher(name, excludeNames);
	}

	private boolean isGroupExcluded(String group) {
		return group != null && (Utils.listContainsMatcher(group, defaultExcludeGroups)
				|| Utils.listContainsMatcher(group, excludeGroups));
	}

	public boolean isForceExcluded(ResolvedArtifact artifact) {
		ModuleVersionIdentifier id = artifact.getModuleVersion().getId();
		return isGroupForceExcluded(id.getGroup()) || isNameForceExcluded(id.getName());
	}

	public boolean isForceExcluded(Dependency dependency) {
		return isGroupForceExcluded(dependency.getGroup()) || isNameForceExcluded(dependency.getName());
	}

	private boolean isNameForceExcluded(String name) {
		return Utils.listContainsMatcher(name, forceExcludeNames);
	}

	private boolean isGroupForceExcluded(String group) {
		return group != null && Utils.listContainsMatcher(group, forceExcludeGroups);
	}

	public Map<String, List<ResolvedArtifact>> getDexes(List<? extends ResolvedArtifact> artifacts) {
		Map<String, List<ResolvedArtifact>> dexMap = new HashMap<>();
		artifacts.forEach(resolvedArtifact -> {
			List<String> names = getDexName(resolvedArtifact);
			names.forEach(name -> {
				if (dexMap.containsKey(name)) {
					dexMap.get(name).add(resolvedArtifact);
				} else {
					List<ResolvedArtifact> list = new ArrayList<>();
					list.add(resolvedArtifact);
					dexMap.put(name, list);
				}
			});
		});
		return dexMap;
	}

	private List<String> getDexName(ResolvedArtifact artifact) {
		List<String> names = new ArrayList<>();
		for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
			if (checkContains(artifact, entry.getValue())) {
				names.add(entry.getKey());
			}
		}
		if (names.isEmpty()) {
			names.add(defaultDexName);
		}
		return names;
	}
}

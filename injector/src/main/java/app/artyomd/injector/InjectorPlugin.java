package app.artyomd.injector;

import app.artyomd.injector.extension.InjectorExtension;
import app.artyomd.injector.model.AndroidArchiveLibrary;
import app.artyomd.injector.task.ExtractAarTask;
import app.artyomd.injector.util.Utils;
import com.android.build.gradle.AppExtension;
import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.api.BaseVariant;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class InjectorPlugin implements Plugin<Project> {

	static final String EXTRACT_AARS_TASK_NAME = "extractAARs";

	private Project project;
	private Configuration injectConf;

	private InjectorExtension extension;
	private Set<ResolvedArtifact> jars;
	private Set<AndroidArchiveLibrary> aars;

	@Override
	public void apply(@NotNull Project target) {
		this.project = target;
		createExtension();
		createConfiguration();
		createExtractAARsTask();
		target.afterEvaluate(project1 -> {
			System.out.println("After evaluate");
			resolveArtifacts();
			if (jars.isEmpty() && aars.isEmpty()) {
				return;
			}
			Utils.removeOldArtifacts(jars);
			Utils.removeOldArtifacts(aars);

			Object extension = project1.getExtensions().getByName("android");
			if (extension instanceof LibraryExtension) {
				((LibraryExtension) extension).getLibraryVariants().all(this::processVariant);
			} else if (extension instanceof AppExtension) {
				((AppExtension) extension).getApplicationVariants().all(this::processVariant);
			}
		});
	}

	private void createExtension() {
		extension = new InjectorExtension();
		project.getExtensions().add(InjectorExtension.class, "injectConfig", extension);
	}

	private void createConfiguration() {
		injectConf = project.getConfigurations().create("inject");
		injectConf.setVisible(false);
		injectConf.setTransitive(true);
	}

	private void createExtractAARsTask() {
		TaskProvider<ExtractAarTask> extractAarTaskTaskProvider = project.getTasks().register(EXTRACT_AARS_TASK_NAME, ExtractAarTask.class);
		extractAarTaskTaskProvider.configure(extractAarTask -> extractAarTask.setAndroidArchiveLibraries(aars));
	}

	private void resolveArtifacts() {
		Set<ResolvedArtifact> jars = new HashSet<>();
		Set<AndroidArchiveLibrary> aars = new HashSet<>();

		injectConf.getResolvedConfiguration().getResolvedArtifacts().forEach(resolvedArtifact -> {
			Dependency dependency = Utils.createDependencyFrom(resolvedArtifact);
			if (!extension.isExcluded(resolvedArtifact) && !extension.isForceExcluded(resolvedArtifact)) {
				System.out.println("inject-->[injection detected][" + resolvedArtifact.getType() + ']' + resolvedArtifact.getModuleVersion().getId());
				if ("jar".equals(resolvedArtifact.getType())) {
					jars.add(resolvedArtifact);
				} else if ("aar".equals(resolvedArtifact.getType())) {
					aars.add(new AndroidArchiveLibrary(project, resolvedArtifact));
				}

				System.out.println("compileOnly:" + resolvedArtifact.getName());
				project.getDependencies().add("compileOnly", dependency);
			} else {
				System.out.println("inject-->[injection skipped][" + resolvedArtifact.getType() + ']' + resolvedArtifact.getModuleVersion().getId());

				if (extension.isForceExcluded(dependency)) {
					System.out.println("implementation:" + dependency.getName());
					project.getDependencies().add("implementation", dependency);
				} else {
					System.out.println("compileOnly:" + resolvedArtifact.getName());
					project.getDependencies().add("compileOnly", dependency);
				}
			}
		});
		this.jars = Collections.unmodifiableSet(jars);
		this.aars = Collections.unmodifiableSet(aars);
	}

	private void processVariant(BaseVariant variant) {
		VariantProcessor processor = new VariantProcessor(project, variant);
		processor.setJarFiles(jars);
		processor.setAndroidArchiveLibraries(aars);
		processor.processVariant(extension);
	}
}

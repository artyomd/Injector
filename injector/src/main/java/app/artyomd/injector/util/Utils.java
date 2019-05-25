package app.artyomd.injector.util;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Utils {
	public static void execCommand(String command, String... commands) throws IOException {
		System.out.print("executing command: " + command + " ");
		Runtime rt = Runtime.getRuntime();
		Process process = rt.exec(command);

		BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));

		BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));

		if (commands != null) {
			DataOutputStream outputStream = new DataOutputStream(process.getOutputStream());
			for (String subCommand : commands) {
				System.out.print(subCommand + " ");
				outputStream.writeBytes(subCommand + "\n");
				outputStream.flush();
			}
			System.out.print("\n");
			outputStream.writeBytes("exit\n");
			outputStream.flush();

		}

		String s;
		System.out.println("output of the command:\n");
		while ((s = stdInput.readLine()) != null) {
			System.out.println(s);
		}

		System.out.println("error of the command (if any):\n");
		while ((s = stdError.readLine()) != null) {
			System.out.println(s);
		}

		try {
			process.waitFor();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public static File getWorkingDir(Project project) {
		return project.file(project.getBuildDir() + "/exploded-aar/");
	}


	public static boolean cmp(String v1, String v2) {
		String[] numbers1 = v1.split(".");
		String[] numbers2 = v2.split(".");
		int minSize = numbers1.length;
		if (numbers2.length < minSize) {
			minSize = numbers2.length;
		}
		for (int i = 0; i < minSize; i++) {
			int num1 = Integer.parseInt(numbers1[i]);
			int num2 = Integer.parseInt(numbers2[i]);
			if (num1 > num2) {
				return true;
			} else if (num1 < num2) {
				return false;
			}
		}
		return numbers1.length >= numbers2.length;
	}

	public static boolean contains(File file, String string) {
		try (Scanner scanner = new Scanner(file)) {
			while (scanner.hasNextLine()) {
				if (scanner.nextLine().contains(string)) {
					return true;
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return false;
	}

	public static String getFilePackageName(File file) {
		try (Scanner scanner = new Scanner(file)) {
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				if (line.matches("package\\s.*;")) {
					return line.split("package")[1].split(";")[0].replaceAll("\\s", "");
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return "";
	}

	public static void unzip(File zipFile, String destDirectory) throws IOException {
		File destDir = new File(destDirectory);
		if (!destDir.exists()) {
			destDir.mkdirs();
		}
		ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFile));
		ZipEntry entry = zipIn.getNextEntry();
		while (entry != null) {
			String filePath = destDirectory + File.separator + entry.getName();
			if (!entry.isDirectory()) {
				File destinationFile = new File(filePath);
				destinationFile.createNewFile();
				BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(destinationFile));
				byte[] bytesIn = new byte[4096];
				int read;
				while ((read = zipIn.read(bytesIn)) != -1) {
					bos.write(bytesIn, 0, read);
				}
				bos.close();
			} else {
				File dir = new File(filePath);
				dir.mkdir();
			}
			zipIn.closeEntry();
			entry = zipIn.getNextEntry();
		}
		zipIn.close();
	}

	public static boolean listContainsMatcher(String str, List<String> matchers) {
		for (String matcher : matchers) {
			if (str.matches(matcher)) {
				return true;
			}
		}
		return false;
	}

	public static void removeOldArtifacts(Set<? extends ResolvedArtifact> artifactsList) {
		Map<String, Map<String, ResolvedArtifact>> artifacts = new HashMap<>();
		for (ResolvedArtifact artifact : artifactsList) {
			ModuleVersionIdentifier id = artifact.getModuleVersion().getId();
			String name = id.getName();
			String group = id.getGroup();
			String version = id.getVersion();
			if (artifacts.containsKey(group)) {
				Map<String, ResolvedArtifact> names = artifacts.get(group);
				if (names.containsKey(name)) {
					ResolvedArtifact old = names.get(name);
					if (Utils.cmp(old.getModuleVersion().getId().getVersion(), version)) {
						names.put(name, artifact);
						artifactsList.remove(old);
					} else {
						artifactsList.remove(artifact);
					}
				} else {
					names.put(name, artifact);
				}
			} else {
				Map<String, ResolvedArtifact> names = new HashMap<>();
				names.put(name, artifact);
				artifacts.put(group, names);
			}
		}
	}
}

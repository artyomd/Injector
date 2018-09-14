package app.artyomd.injector;

import org.gradle.api.Project;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

class Utils {
	static void execCommand(String command, String... commands) throws IOException {
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

	static File getWorkingDir(Project project) {
		return project.file(project.getBuildDir() + "/exploded-aar/");
	}


	static boolean cmp(String v1, String v2) {
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

	static boolean contains(File file, String string) {
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
}

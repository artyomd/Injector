package app.artyomd.injector;

import org.gradle.api.Project;

import java.io.*;
import java.util.Scanner;

public class Utils {
    public static void execCommand(String command, String... commands) throws IOException {
        System.out.print("executing command: " + command + " ");
        Runtime rt = Runtime.getRuntime();
        Process proc = rt.exec(command);

        BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));

        BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

        if (commands != null) {
            DataOutputStream outputStream = new DataOutputStream(proc.getOutputStream());
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
            proc.waitFor();
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
}

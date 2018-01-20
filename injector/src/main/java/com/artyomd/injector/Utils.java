package com.artyomd.injector;

import org.gradle.api.Project;

import java.io.*;

public class Utils {
    public static void execCommand(String command, String... commands) throws IOException {
        System.out.println("executing command: " + command);
        Runtime rt = Runtime.getRuntime();
        Process proc = rt.exec(command);

        BufferedReader stdInput = new BufferedReader(new
                InputStreamReader(proc.getInputStream()));

        BufferedReader stdError = new BufferedReader(new
                InputStreamReader(proc.getErrorStream()));

        if (commands != null) {
            DataOutputStream outputStream = new DataOutputStream(proc.getOutputStream());
            for (String sCommand : commands) {
                System.out.println("executing command: " + sCommand);
                outputStream.writeBytes(sCommand + "\n");
                outputStream.flush();
            }

            outputStream.writeBytes("exit\n");
            outputStream.flush();
        }

        System.out.println("Here is the standard output of the command:\n");
        String s;
        while ((s = stdInput.readLine()) != null) {
            System.out.println(s);
        }

        System.out.println("Here is the standard error of the command (if any):\n");
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
}

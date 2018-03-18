package com.artyomd.injector;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import org.apache.commons.io.FileUtils;
import org.gradle.api.JavaVersion;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import javax.lang.model.element.Modifier;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class RSourceGenerator {

    public static void generate(@Nonnull AndroidArchiveLibrary androidLibrary, @Nonnull String libPackageName, @Nonnull String buildDir, @Nonnull String variant, JavaVersion projectSourceVersion, JavaVersion projectTargetVersion) throws IOException {
        File symbolFile = androidLibrary.getSymbolFile();
        File manifestFile = androidLibrary.getManifest();
        if (!symbolFile.exists()) {
            return;
        }
        if (!manifestFile.exists()) {
            throw new RuntimeException("Can not find " + manifestFile);
        }
        // read R.txt
        List<String> lines = Files.readLines(symbolFile, Charsets.UTF_8);
        Map<String, List<TextSymbolItem>> symbolItemsMap = Maps.newHashMap();
        for (String line : lines) {
            String[] strings = line.split(" ", 4);
            TextSymbolItem symbolItem = new TextSymbolItem(strings[0], strings[1], strings[2], strings[3]);
            List<TextSymbolItem> symbolItems = symbolItemsMap.computeIfAbsent(symbolItem.getClazz(), k -> Lists.newArrayList());
            symbolItems.add(symbolItem);
        }
        if (symbolItemsMap.isEmpty()) {
            // empty R.txt
            return;
        }

        // parse package name
        String packageName = null;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            Document doc = dbf.newDocumentBuilder().parse(manifestFile);
            Element element = doc.getDocumentElement();
            packageName = element.getAttribute("package");
        } catch (SAXException | ParserConfigurationException e) {
            e.printStackTrace();
        }
        if (Strings.isNullOrEmpty(packageName)) {
            throw new RuntimeException("Parse package from " + manifestFile + " error!");
        }

        // write R.java
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder("R")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        for (String clazz : symbolItemsMap.keySet()) {
            TypeSpec.Builder icb = TypeSpec.classBuilder(clazz).addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);
            List<TextSymbolItem> textSymbolItems = symbolItemsMap.get(clazz);
            for (TextSymbolItem item : textSymbolItems) {
                TypeName typeName = null;
                if ("int".equals(item.getType())) {
                    typeName = TypeName.INT;
                } else if ("int[]".equals(item.getType())) {
                    typeName = TypeName.get(int[].class);
                }
                if (typeName == null) {
                    throw new RuntimeException("Unknown class type in " + symbolFile);
                }
                FieldSpec fieldSpec = FieldSpec.builder(typeName, item.getName())
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer(libPackageName+".R."+item.getClazz()+"."+item.getName())
                        .build();
                icb.addField(fieldSpec);
            }
            classBuilder.addType(icb.build());
        }
        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build()).build();
        File outputDir = new File(androidLibrary.getRootFolder() + "/R");
        if (!outputDir.exists()) {
            outputDir.mkdir();
        }
        javaFile.writeTo(outputDir);

        //compile R.java into R.class
        Collection<File> files = FileUtils.listFiles(outputDir, new String[]{"java"}, true);
        StringBuilder javac = new StringBuilder();
        javac.append("javac -source ")
                .append(projectSourceVersion.isJava8Compatible() ? "1.8" : "1.7")
                .append(" -target ")
                .append(projectTargetVersion.isJava8Compatible() ? "1.8" : "1.7");

        for (File file : files) {
            javac.append(" ").append(file.getAbsolutePath());
        }
        javac.append(" ").append(buildDir).append("/generated/source/r/").append(variant.toLowerCase()).append("/").append(libPackageName.replace(".", "/")).append("/R.java");
        Utils.execCommand(javac.toString());
        //inject R$...class files into class.jar
        Collection<File> classes = FileUtils.listFiles(outputDir, new String[]{"class"}, true);
        StringBuilder injectCommandBuilder = new StringBuilder();
        String cdR = "cd " + outputDir.getAbsolutePath();
        injectCommandBuilder.append("jar -ufv ").append(androidLibrary.getClassesJarFile());
        for (File file : classes) {
            injectCommandBuilder.append(" ");
            String path = file.getAbsolutePath();
            path = path.replace("$", "\\$");
            injectCommandBuilder.append(path.substring(path.indexOf("/R") + 3));
        }
        Utils.execCommand("sh", cdR, injectCommandBuilder.toString());
    }

    private static class TextSymbolItem {
        private String type;
        private String clazz;
        private String name;
        private String value;

        TextSymbolItem(String type, String clazz, String name, String value) {
            this.type = type;
            this.clazz = clazz;
            this.name = name;
            this.value = value;
        }

        String getType() {
            return type;
        }

        String getClazz() {
            return clazz;
        }

        String getName() {
            return name;
        }

        String getValue() {
            return value;
        }
    }
}

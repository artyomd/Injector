package app.artyomd.injector.task;

import app.artyomd.injector.model.AndroidArchiveLibrary;
import app.artyomd.injector.model.TextSymbolItem;
import app.artyomd.injector.util.Utils;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class RSourceGenerator {
	public static void generate(@Nonnull AndroidArchiveLibrary androidLibrary, @Nonnull String libPackageName, @Nonnull String buildDir, @Nonnull String variant, JavaVersion projectSourceVersion, JavaVersion projectTargetVersion) throws IOException {
		File symbolFile = androidLibrary.getSymbolFile();
		File manifestFile = androidLibrary.getManifest();
		File rJarFile = new File(buildDir + "/intermediates/compile_only_not_namespaced_r_class_jar/" + variant.toLowerCase() + "/R.jar");
		if (!symbolFile.exists()) {
			System.out.println("R.txt does not exists");
			return;
		}
		if (!manifestFile.exists()) {
			throw new RuntimeException("Can not find " + manifestFile);
		}
		// read R.txt
		List<String> lines = FileUtils.readLines(symbolFile, Charsets.UTF_8);
		Map<String, List<TextSymbolItem>> symbolItemsMap = Maps.newHashMap();
		for (String line : lines) {
			String[] strings = line.split(" ", 4);
			TextSymbolItem symbolItem = new TextSymbolItem(strings[0], strings[1], strings[2], strings[3]);
			List<TextSymbolItem> symbolItems = symbolItemsMap.computeIfAbsent(symbolItem.getClazz(), k -> Lists.newArrayList());
			symbolItems.add(symbolItem);
		}
		if (symbolItemsMap.isEmpty()) {
			System.out.println("empty R.txt");
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
				FieldSpec.Builder fieldSpecBuilder = FieldSpec.builder(typeName, item.getName())
						.addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);
				if (checkField(rJarFile, libPackageName + ".R$" + item.getClazz(), item.getName())) {
					fieldSpecBuilder.initializer(libPackageName + ".R." + item.getClazz() + "." + item.getName());
				} else {
					fieldSpecBuilder.initializer(item.getValue());
				}
				icb.addField(fieldSpecBuilder.build());
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
		javac.append("javac")
				.append(" -cp ")
				.append(rJarFile.getAbsolutePath())
				.append(" -source ")
				.append(projectSourceVersion.isJava8Compatible() ? "1.8" : "1.7")
				.append(" -target ")
				.append(projectTargetVersion.isJava8Compatible() ? "1.8" : "1.7");

		for (File file : files) {
			javac.append(" ").append(file.getAbsolutePath());
		}
		//javac.append(" ").append(rFile.getAbsolutePath());
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

	private static boolean checkField(File jarFile, String clazzName, String fieldName) {
		//hacky solution
		//load jar file and try to get the filed if everything is ok field exists and compilation will not fail
		URL[] urls = new URL[1];
		try {
			urls[0] = jarFile.toURI().toURL();
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return false;
		}
		try (URLClassLoader cl = new URLClassLoader(urls)) {
			Class clazz = cl.loadClass(clazzName);
			clazz.getField(fieldName);
		} catch (IOException | ClassNotFoundException | NoSuchFieldException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
}

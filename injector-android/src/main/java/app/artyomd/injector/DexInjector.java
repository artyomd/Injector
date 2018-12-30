package app.artyomd.injector;

import android.os.Build;
import android.util.Log;

import dalvik.system.DexFile;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;

public final class DexInjector {
	private static final String TAG = DexInjector.class.getSimpleName();
	private static final String DEX_SUFFIX = "dex";

	private DexInjector() {
	}

	public static void installSecondaryDexes(ClassLoader loader, File dexDir,
	                                         List<? extends File> files)
			throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException,
			InvocationTargetException, NoSuchMethodException, IOException, SecurityException,
			ClassNotFoundException, InstantiationException {
		if (!files.isEmpty()) {
			if (Build.VERSION.SDK_INT >= 19) {
				V19.install(loader, files, dexDir);
			} else {
				V14.install(loader, files);
			}
		}
	}

	/**
	 * Locates a given field anywhere in the class inheritance hierarchy.
	 *
	 * @param instance an object to search the field into.
	 * @param name     field name
	 * @return a field object
	 * @throws NoSuchFieldException if the field cannot be located
	 */
	private static Field findField(Object instance, String name) throws NoSuchFieldException {
		for (Class<?> clazz = instance.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
			try {
				Field field = clazz.getDeclaredField(name);
				if (!field.isAccessible()) {
					field.setAccessible(true);
				}
				return field;
			} catch (NoSuchFieldException e) {
				// ignore and search next
			}
		}
		throw new NoSuchFieldException("Field " + name + " not found in " + instance.getClass());
	}

	/**
	 * Locates a given method anywhere in the class inheritance hierarchy.
	 *
	 * @param instance       an object to search the method into.
	 * @param name           method name
	 * @param parameterTypes method parameter types
	 * @return a method object
	 * @throws NoSuchMethodException if the method cannot be located
	 */
	private static Method findMethod(Object instance, String name, Class<?>... parameterTypes)
			throws NoSuchMethodException {
		for (Class<?> clazz = instance.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
			try {
				Method method = clazz.getDeclaredMethod(name, parameterTypes);
				if (!method.isAccessible()) {
					method.setAccessible(true);
				}
				return method;
			} catch (NoSuchMethodException e) {
				// ignore and search next
			}
		}
		throw new NoSuchMethodException("Method " + name + " with parameters " +
				Arrays.asList(parameterTypes) + " not found in " + instance.getClass());
	}

	/**
	 * Replace the value of a field containing a non null array, by a new array containing the
	 * elements of the original array plus the elements of extraElements.
	 *
	 * @param instance      the instance whose field is to be modified.
	 * @param fieldName     the field to modify.
	 * @param extraElements elements to append at the end of the array.
	 */
	private static void expandFieldArray(Object instance, String fieldName,
	                                     Object[] extraElements) throws NoSuchFieldException, IllegalArgumentException,
			IllegalAccessException {
		Field jlrField = findField(instance, fieldName);
		Object[] original = (Object[]) jlrField.get(instance);
		Object[] combined = (Object[]) Array.newInstance(
				original.getClass().getComponentType(), original.length + extraElements.length);
		System.arraycopy(original, 0, combined, 0, original.length);
		System.arraycopy(extraElements, 0, combined, original.length, extraElements.length);
		jlrField.set(instance, combined);
	}

	/**
	 * Installer for platform versions 19.
	 */
	private static final class V19 {
		static void install(ClassLoader loader,
		                    List<? extends File> additionalClassPathEntries,
		                    File optimizedDirectory)
				throws IllegalArgumentException, IllegalAccessException,
				NoSuchFieldException, InvocationTargetException, NoSuchMethodException,
				IOException {
			/* The patched class loader is expected to be a descendant of
			 * dalvik.system.BaseDexClassLoader. We modify its
			 * dalvik.system.DexPathList pathList field to append additional DEX
			 * file entries.
			 */
			Field pathListField = findField(loader, "pathList");
			Object dexPathList = pathListField.get(loader);
			ArrayList<IOException> suppressedExceptions = new ArrayList<>();
			expandFieldArray(dexPathList, "dexElements", makeDexElements(dexPathList,
					new ArrayList<>(additionalClassPathEntries), optimizedDirectory,
					suppressedExceptions));
			if (suppressedExceptions.size() > 0) {
				for (IOException e : suppressedExceptions) {
					Log.w(TAG, "Exception in makeDexElement", e);
				}
				Field suppressedExceptionsField =
						findField(dexPathList, "dexElementsSuppressedExceptions");
				IOException[] dexElementsSuppressedExceptions =
						(IOException[]) suppressedExceptionsField.get(dexPathList);
				if (dexElementsSuppressedExceptions == null) {
					dexElementsSuppressedExceptions =
							suppressedExceptions.toArray(
									new IOException[suppressedExceptions.size()]);
				} else {
					IOException[] combined =
							new IOException[suppressedExceptions.size() +
									dexElementsSuppressedExceptions.length];
					suppressedExceptions.toArray(combined);
					System.arraycopy(dexElementsSuppressedExceptions, 0, combined,
							suppressedExceptions.size(), dexElementsSuppressedExceptions.length);
					dexElementsSuppressedExceptions = combined;
				}
				suppressedExceptionsField.set(dexPathList, dexElementsSuppressedExceptions);
				throw new IOException("I/O exception during makeDexElement", suppressedExceptions.get(0));
			}
		}

		/**
		 * A wrapper around
		 * {@code private static final dalvik.system.DexPathList#makeDexElements}.
		 */
		private static Object[] makeDexElements(
				Object dexPathList, ArrayList<File> files, File optimizedDirectory,
				ArrayList<IOException> suppressedExceptions)
				throws IllegalAccessException, InvocationTargetException,
				NoSuchMethodException {
			Method makeDexElements = Build.VERSION.SDK_INT >= 23 ?
					findMethod(dexPathList, "makePathElements", List.class, File.class, List.class) :
					findMethod(dexPathList, "makeDexElements", ArrayList.class, File.class, ArrayList.class);
			return (Object[]) makeDexElements.invoke(dexPathList, files, optimizedDirectory,
					suppressedExceptions);
		}
	}

	/**
	 * Installer for platform versions 14, 15, 16, 17 and 18.
	 */
	private static final class V14 {

		private final ElementConstructor elementConstructor;

		private interface ElementConstructor {
			Object newInstance(File file, DexFile dex)
					throws IllegalArgumentException, InstantiationException,
					IllegalAccessException, InvocationTargetException, IOException;
		}

		/**
		 * Applies for ICS and early JB (initial release and MR1).
		 */
		private static class ICSElementConstructor implements ElementConstructor {
			private final Constructor<?> elementConstructor;

			ICSElementConstructor(Class<?> elementClass)
					throws SecurityException, NoSuchMethodException {
				elementConstructor =
						elementClass.getConstructor(File.class, ZipFile.class, DexFile.class);
				elementConstructor.setAccessible(true);
			}

			@Override
			public Object newInstance(File file, DexFile dex)
					throws IllegalArgumentException, InstantiationException,
					IllegalAccessException, InvocationTargetException, IOException {
				return elementConstructor.newInstance(file, new ZipFile(file), dex);
			}
		}

		/**
		 * Applies for some intermediate JB (MR1.1).
		 * <p>
		 * See Change-Id: I1a5b5d03572601707e1fb1fd4424c1ae2fd2217d
		 */
		private static class JBMR11ElementConstructor implements ElementConstructor {
			private final Constructor<?> elementConstructor;

			JBMR11ElementConstructor(Class<?> elementClass)
					throws SecurityException, NoSuchMethodException {
				elementConstructor = elementClass
						.getConstructor(File.class, File.class, DexFile.class);
				elementConstructor.setAccessible(true);
			}

			@Override
			public Object newInstance(File file, DexFile dex)
					throws IllegalArgumentException, InstantiationException,
					IllegalAccessException, InvocationTargetException {
				return elementConstructor.newInstance(file, file, dex);
			}
		}

		/**
		 * Applies for latest JB (MR2).
		 * <p>
		 * See Change-Id: Iec4dca2244db9c9c793ac157e258fd61557a7a5d
		 */
		private static class JBMR2ElementConstructor implements ElementConstructor {
			private final Constructor<?> elementConstructor;

			JBMR2ElementConstructor(Class<?> elementClass)
					throws SecurityException, NoSuchMethodException {
				elementConstructor = elementClass
						.getConstructor(File.class, Boolean.TYPE, File.class, DexFile.class);
				elementConstructor.setAccessible(true);
			}

			@Override
			public Object newInstance(File file, DexFile dex)
					throws IllegalArgumentException, InstantiationException,
					IllegalAccessException, InvocationTargetException {
				return elementConstructor.newInstance(file, Boolean.FALSE, file, dex);
			}
		}

		static void install(ClassLoader loader,
		                    List<? extends File> additionalClassPathEntries)
				throws IOException, SecurityException, IllegalArgumentException,
				ClassNotFoundException, NoSuchMethodException, InstantiationException,
				IllegalAccessException, InvocationTargetException, NoSuchFieldException {
			/* The patched class loader is expected to be a descendant of
			 * dalvik.system.BaseDexClassLoader. We modify its
			 * dalvik.system.DexPathList pathList field to append additional DEX
			 * file entries.
			 */
			Field pathListField = findField(loader, "pathList");
			Object dexPathList = pathListField.get(loader);
			Object[] elements = new V14().makeDexElements(additionalClassPathEntries);
			try {
				expandFieldArray(dexPathList, "dexElements", elements);
			} catch (NoSuchFieldException e) {
				// dexElements was renamed pathElements for a short period during JB development,
				// eventually it was renamed back shortly after.
				Log.w(TAG, "Failed find field 'dexElements' attempting 'pathElements'", e);
				expandFieldArray(dexPathList, "pathElements", elements);
			}
		}

		private V14() throws ClassNotFoundException, SecurityException, NoSuchMethodException {
			ElementConstructor constructor;
			Class<?> elementClass = Class.forName("dalvik.system.DexPathList$Element");
			try {
				constructor = new ICSElementConstructor(elementClass);
			} catch (NoSuchMethodException e) {
				try {
					constructor = new JBMR11ElementConstructor(elementClass);
				} catch (NoSuchMethodException exception) {
					constructor = new JBMR2ElementConstructor(elementClass);
				}
			}
			this.elementConstructor = constructor;
		}

		/**
		 * An emulation of {@code private static final dalvik.system.DexPathList#makeDexElements}
		 * accepting only extracted secondary dex files.
		 * OS version is catching IOException and just logging some of them, this version is letting
		 * them through.
		 */
		private Object[] makeDexElements(List<? extends File> files)
				throws IOException, SecurityException, IllegalArgumentException,
				InstantiationException, IllegalAccessException, InvocationTargetException {
			Object[] elements = new Object[files.size()];
			for (int i = 0; i < elements.length; i++) {
				File file = files.get(i);
				elements[i] = elementConstructor.newInstance(
						file,
						DexFile.loadDex(file.getPath(), optimizedPathFor(file), 0));
			}
			return elements;
		}

		/**
		 * Converts a zip file path of an extracted secondary dex to an output file path for an
		 * associated optimized dex file.
		 */
		private static String optimizedPathFor(File path) {
			// Any reproducible name ending with ".dex" should do but lets keep the same name
			// as DexPathList.optimizedPathFor
			File optimizedDirectory = path.getParentFile();
			String fileName = path.getName();
			String optimizedFileName =
					fileName.substring(0, fileName.length() - DEX_SUFFIX.length())
							+ DEX_SUFFIX;
			File result = new File(optimizedDirectory, optimizedFileName);
			return result.getPath();
		}
	}
}
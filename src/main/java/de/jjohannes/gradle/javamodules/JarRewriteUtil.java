package de.jjohannes.gradle.javamodules;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;

import java.io.*;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipException;

public class JarRewriteUtil {

    public static void addAutomaticModuleName(File originalJar, File moduleJar, AutomaticModuleName moduleName) {
        try (JarInputStream inputStream = new JarInputStream(new FileInputStream(originalJar))) {
            Manifest manifest = inputStream.getManifest();
            if (manifest == null) {
                manifest = new Manifest();
                manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
            }
            manifest.getMainAttributes().putValue("Automatic-Module-Name", moduleName.getModuleName());
            try (JarOutputStream outputStream = new JarOutputStream(new FileOutputStream(moduleJar), manifest)) {
                copyEntries(inputStream, outputStream);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void addModuleDescriptor(File originalJar, File moduleJar, ModuleInfo moduleInfo) {
        try (JarInputStream inputStream = new JarInputStream(new FileInputStream(originalJar))) {
            try (JarOutputStream outputStream = newJarOutputStream(new FileOutputStream(moduleJar), inputStream.getManifest())) {
                copyEntries(inputStream, outputStream);
                outputStream.putNextEntry(new JarEntry("module-info.class"));
                outputStream.write(addModuleInfo(moduleInfo));
                outputStream.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void mergeJars(File originalJar, List<File> jarsToMerge, File mergedJar) {
        mergedJar.getParentFile().mkdirs();
        try (JarInputStream inputStream = new JarInputStream(new FileInputStream(originalJar))) {
            try (JarOutputStream outputStream = newJarOutputStream(new FileOutputStream(mergedJar), inputStream.getManifest())) {
                copyEntries(inputStream, outputStream);
                for (File toMerge : jarsToMerge) {
                    try (JarInputStream toMergeInputStream = new JarInputStream(new FileInputStream(toMerge))) {
                        copyEntries(toMergeInputStream, outputStream);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static JarOutputStream newJarOutputStream(OutputStream out, Manifest manifest) throws IOException {
        return manifest == null ? new JarOutputStream(out) : new JarOutputStream(out, manifest);
    }

    private static void copyEntries(JarInputStream inputStream, JarOutputStream outputStream) throws IOException {
        JarEntry jarEntry = inputStream.getNextJarEntry();
        while (jarEntry != null) {
            try {
                outputStream.putNextEntry(jarEntry);
                outputStream.write(inputStream.readAllBytes());
                outputStream.closeEntry();
            } catch (ZipException e) {
                System.out.println("Ignoring: " + e.getMessage());
            }
            jarEntry = inputStream.getNextJarEntry();
        }
    }

    private static byte[] addModuleInfo(ModuleInfo moduleInfo) {
        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(Opcodes.V9, Opcodes.ACC_MODULE, "module-info", null, null, null);
        ModuleVisitor moduleVisitor = classWriter.visitModule(moduleInfo.getModuleName(), Opcodes.ACC_OPEN, moduleInfo.getModuleVersion());
        for (String packageName : moduleInfo.getExports()) {
            moduleVisitor.visitExport(packageName.replace('.', '/'), 0);
        }
        moduleVisitor.visitRequire("java.base", 0, null);
        for (String requireName : moduleInfo.getRequires()) {
            moduleVisitor.visitRequire(requireName, 0, null);
        }
        for (String requireName : moduleInfo.getRequiresTransitive()) {
            moduleVisitor.visitRequire(requireName, Opcodes.ACC_TRANSITIVE, null);
        }
        moduleVisitor.visitEnd();
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }
}

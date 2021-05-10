/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jjohannes.gradle.javamodules;

import org.gradle.api.artifacts.CacheableRule;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

/**
 * An artifact transform that applies additional information to Jars without module information.
 * The transformation fails the build if a Jar does not contain information and no extra information
 * was defined for it. This way we make sure that all Jars are turned into modules.
 */
@CacheableRule
abstract public class ExtraModuleInfoTransform implements TransformAction<ExtraModuleInfoTransform.Parameter> {

    private static final Pattern MODULE_INFO_CLASS_MRJAR_PATH = Pattern.compile("META-INF/versions/\\d+/module-info.class");
    private static final String SERVICES_PREFIX = "META-INF/services/";

    public interface Parameter extends TransformParameters {
        @Input
        MapProperty<String, ModuleInfo> getModuleInfo();
        @Input
        MapProperty<String, String> getAutomaticModules();
        @Input
        Property<Boolean> getFailOnMissingModuleInfo();
    }

    @InputArtifact
    protected abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(@Nonnull TransformOutputs outputs) {
        Map<String, ModuleInfo> moduleInfo = getParameters().getModuleInfo().get();
        Map<String, String> automaticModules = getParameters().getAutomaticModules().get();
        File originalJar = getInputArtifact().get().getAsFile();
        String originalJarName = originalJar.getName();

        if (isModule(originalJar)) {
            outputs.file(originalJar);
        } else if (moduleInfo.containsKey(originalJarName)) {
            addModuleDescriptor(originalJar, getModuleJar(outputs, originalJar), moduleInfo.get(originalJarName));
        } else if (isAutoModule(originalJar)) {
            outputs.file(originalJar);
        } else if (automaticModules.containsKey(originalJarName)) {
            addAutomaticModuleName(originalJar, getModuleJar(outputs, originalJar), automaticModules.get(originalJarName));
        } else {
            if (getParameters().getFailOnMissingModuleInfo().get()) {
                throw new RuntimeException("Not a module and no mapping defined: " + originalJarName);
            } else {
                outputs.file(originalJar);
            }
        }
    }

    private boolean isModule(File jar) {
        try (JarInputStream inputStream =  new JarInputStream(new FileInputStream(jar))) {
            boolean isMultiReleaseJar = containsMultiReleaseJarEntry(inputStream);
            ZipEntry next = inputStream.getNextEntry();
            while (next != null) {
                if ("module-info.class".equals(next.getName())) {
                    return true;
                }
                if (isMultiReleaseJar && MODULE_INFO_CLASS_MRJAR_PATH.matcher(next.getName()).matches()) {
                    return true;
                }
                next = inputStream.getNextEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    private boolean containsMultiReleaseJarEntry(JarInputStream jarStream) {
        Manifest manifest = jarStream.getManifest();
        return manifest != null && Boolean.parseBoolean(manifest.getMainAttributes().getValue("Multi-Release"));
    }

    private boolean isAutoModule(File jar) {
        try (JarInputStream inputStream = new JarInputStream(new FileInputStream(jar))) {
            Manifest manifest = inputStream.getManifest();
            return manifest != null && manifest.getMainAttributes().getValue("Automatic-Module-Name") != null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private File getModuleJar(TransformOutputs outputs, File originalJar) {
        return outputs.file(originalJar.getName().substring(0, originalJar.getName().lastIndexOf('.')) + "-module.jar");
    }

    private static void addAutomaticModuleName(File originalJar, File moduleJar, String moduleName) {
        try (JarInputStream inputStream = new JarInputStream(new FileInputStream(originalJar))) {
            Manifest manifest = inputStream.getManifest();
            if (manifest == null) {
                manifest = new Manifest();
                manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
            }
            manifest.getMainAttributes().putValue("Automatic-Module-Name", moduleName);
            try (JarOutputStream outputStream = new JarOutputStream(new FileOutputStream(moduleJar), manifest)) {
                copyAndExtractProviders(inputStream, outputStream);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void addModuleDescriptor(File originalJar, File moduleJar, ModuleInfo moduleInfo) {
        try (JarInputStream inputStream = new JarInputStream(new FileInputStream(originalJar))) {
            try (JarOutputStream outputStream = newJarOutputStream(new FileOutputStream(moduleJar), inputStream.getManifest())) {
                Map<String, String[]> providers = copyAndExtractProviders(inputStream, outputStream);
                outputStream.putNextEntry(new JarEntry("module-info.class"));
                outputStream.write(addModuleInfo(moduleInfo, providers));
                outputStream.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static JarOutputStream newJarOutputStream(OutputStream out, Manifest manifest) throws IOException {
        return manifest == null ? new JarOutputStream(out) : new JarOutputStream(out, manifest);
    }

    private static Map<String, String[]> copyAndExtractProviders(JarInputStream inputStream, JarOutputStream outputStream) throws IOException {
        JarEntry jarEntry = inputStream.getNextJarEntry();
        Map<String, String[]> providers = new LinkedHashMap<>();
        while (jarEntry != null) {
            byte[] content = inputStream.readAllBytes();
            String entryName = jarEntry.getName();
            if (entryName.startsWith(SERVICES_PREFIX) && !entryName.equals(SERVICES_PREFIX)) {
                providers.put(entryName.substring(SERVICES_PREFIX.length()), extractImplementations(content));
            }
            outputStream.putNextEntry(jarEntry);
            outputStream.write(content);
            outputStream.closeEntry();
            jarEntry = inputStream.getNextJarEntry();
        }
        return providers;
    }

    private static String[] extractImplementations(byte[] content) {
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8))
                .lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.startsWith("#"))
                .map(line -> line.replace('.','/'))
                .distinct()
                .toArray(String[]::new);
    }

    private static byte[] addModuleInfo(ModuleInfo moduleInfo, Map<String, String[]> providers) {
        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(Opcodes.V9, Opcodes.ACC_MODULE, "module-info", null, null, null);
        ModuleVisitor moduleVisitor = classWriter.visitModule(moduleInfo.moduleName, Opcodes.ACC_OPEN, moduleInfo.moduleVersion);
        for (String packageName : moduleInfo.exports) {
            moduleVisitor.visitExport(packageName.replace('.', '/'), 0);
        }
        moduleVisitor.visitRequire("java.base", 0, null);
        for (String requireName : moduleInfo.requires) {
            moduleVisitor.visitRequire(requireName, 0, null);
        }
        for (String requireName : moduleInfo.requiresTransitive) {
            moduleVisitor.visitRequire(requireName, Opcodes.ACC_TRANSITIVE, null);
        }
        for (String requireName : moduleInfo.requiresStatic) {
            moduleVisitor.visitRequire(requireName, Opcodes.ACC_STATIC_PHASE, null);
        }
        for (Map.Entry<String, String[]> entry : providers.entrySet()) {
            String name = entry.getKey();
            String[] implementations = entry.getValue();
            if (!moduleInfo.ignoreServiceProviders.contains(name)) {
                moduleVisitor.visitProvide(name.replace('.', '/'), implementations);
            }
        }
        moduleVisitor.visitEnd();
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }
}

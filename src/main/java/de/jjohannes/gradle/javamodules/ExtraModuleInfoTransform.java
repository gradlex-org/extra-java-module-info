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

import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;

import java.io.*;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.jar.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

/**
 * An artifact transform that applies additional information to Jars without module information.
 * The transformation fails the build if a Jar does not contain information and no extra information
 * was defined for it. This way we make sure that all Jars are turned into modules.
 */
abstract public class ExtraModuleInfoTransform implements TransformAction<ExtraModuleInfoTransform.Parameter> {

    private static final Pattern MODULE_INFO_CLASS_MRJAR_PATH = Pattern.compile("META-INF/versions/\\d+/module-info.class");

    public static class Parameter implements TransformParameters, Serializable {
        private Map<String, ModuleInfo> moduleInfo = Collections.emptyMap();
        private Map<String, String> automaticModules = Collections.emptyMap();

        @Input
        public Map<String, ModuleInfo> getModuleInfo() {
            return moduleInfo;
        }

        @Input
        public Map<String, String> getAutomaticModules() {
            return automaticModules;
        }

        public void setModuleInfo(Map<String, ModuleInfo> moduleInfo) {
            this.moduleInfo = moduleInfo;
        }

        public void setAutomaticModules(Map<String, String> automaticModules) {
            this.automaticModules = automaticModules;
        }
    }

    @InputArtifact
    protected abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(TransformOutputs outputs) {
        Map<String, ModuleInfo> moduleInfo = getParameters().moduleInfo;
        Map<String, String> automaticModules = getParameters().automaticModules;
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
            throw new RuntimeException("Not a module and no mapping defined: " + originalJarName);
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
                copyEntries(inputStream, outputStream);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void addModuleDescriptor(File originalJar, File moduleJar, ModuleInfo moduleInfo) {
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

    private static JarOutputStream newJarOutputStream(OutputStream out, Manifest manifest) throws IOException {
        return manifest == null ? new JarOutputStream(out) : new JarOutputStream(out, manifest);
    }

    private static void copyEntries(JarInputStream inputStream, JarOutputStream outputStream) throws IOException {
        JarEntry jarEntry = inputStream.getNextJarEntry();
        while (jarEntry != null) {
            outputStream.putNextEntry(jarEntry);
            outputStream.write(inputStream.readAllBytes());
            outputStream.closeEntry();
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

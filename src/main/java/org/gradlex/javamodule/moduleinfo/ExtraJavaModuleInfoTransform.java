/*
 * Copyright the GradleX team.
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

package org.gradlex.javamodule.moduleinfo;

import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.CacheableRule;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;

import javax.annotation.Nullable;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

import static org.gradlex.javamodule.moduleinfo.FilePathToModuleCoordinates.gaCoordinatesFromFilePathMatch;
import static org.gradlex.javamodule.moduleinfo.FilePathToModuleCoordinates.versionFromFilePath;
import static org.gradlex.javamodule.moduleinfo.ModuleNameUtil.automaticModulNameFromFileName;

/**
 * An artifact transform that applies additional information to Jars without module information.
 * The transformation fails the build if a Jar does not contain information and no extra information
 * was defined for it. This way we make sure that all Jars are turned into modules.
 */
@CacheableRule
@NonNullApi
public abstract class ExtraJavaModuleInfoTransform implements TransformAction<ExtraJavaModuleInfoTransform.Parameter> {

    private static final Pattern MODULE_INFO_CLASS_MRJAR_PATH = Pattern.compile("META-INF/versions/\\d+/module-info.class");
    private static final Pattern MRJAR_VERSIONS_PATH = Pattern.compile("META-INF/versions/\\d+/(.*)/.*");
    private static final Pattern JAR_SIGNATURE_PATH = Pattern.compile("^META-INF/[^/]+\\.(SF|RSA|DSA|sf|rsa|dsa)$");
    private static final String SERVICES_PREFIX = "META-INF/services/";

    // See: org.gradle.api.internal.file.archive.ZipCopyAction.CONSTANT_TIME_FOR_ZIP_ENTRIES
    private static final long CONSTANT_TIME_FOR_ZIP_ENTRIES = new GregorianCalendar(1980, Calendar.FEBRUARY, 1, 0, 0, 0).getTimeInMillis();

    public interface Parameter extends TransformParameters {
        @Input
        MapProperty<String, ModuleSpec> getModuleSpecs();

        @Input
        Property<Boolean> getFailOnMissingModuleInfo();

        @Input
        Property<Boolean> getFailOnAutomaticModules();

        @Input
        Property<Boolean> getDeriveAutomaticModuleNamesFromFileNames();

        @Input
        ListProperty<String> getMergeJarIds();

        @InputFiles
        ListProperty<RegularFile> getMergeJars();

        @Input
        MapProperty<String, Set<String>> getCompileClasspathDependencies();

        @Input
        MapProperty<String, Set<String>> getRuntimeClasspathDependencies();

        @Input
        MapProperty<String, Set<String>> getAnnotationProcessorClasspathDependencies();
    }

    @InputArtifact
    protected abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(TransformOutputs outputs) {
        Parameter parameters = getParameters();
        Map<String, ModuleSpec> moduleSpecs = parameters.getModuleSpecs().get();
        File originalJar = getInputArtifact().get().getAsFile();

        ModuleSpec moduleSpec = findModuleSpec(originalJar);

        if (willBeMerged(originalJar, moduleSpecs.values())) {  // No output if this Jar will be merged
            return;
        }
        boolean realModule = isModule(originalJar);
        if (moduleSpec instanceof ModuleInfo) {
            if (realModule && !((ModuleInfo) moduleSpec).patchRealModule) {
                throw new RuntimeException("Patching of real modules must be explicitly enabled with 'patchRealModule()'");
            }
            String definedName = moduleSpec.getModuleName();
            String expectedName = autoModuleName(originalJar);
            if (expectedName != null && !definedName.equals(expectedName) && !moduleSpec.overrideModuleName) {
                throw new RuntimeException("The name '" + definedName + "' is different than the Automatic-Module-Name '" + expectedName + "'; explicitly allow override via 'overrideName()'");
            }
            addModuleDescriptor(originalJar, getModuleJar(outputs, originalJar), (ModuleInfo) moduleSpec);
        } else if (moduleSpec instanceof AutomaticModuleName) {
            if (realModule) {
                throw new RuntimeException("Patching of real modules must be explicitly enabled with 'patchRealModule()' and can only be done with 'module()'");
            }
            String definedName = moduleSpec.getModuleName();
            String expectedName = autoModuleName(originalJar);
            if (expectedName != null && (moduleSpec.getMergedJars().isEmpty() || !definedName.equals(expectedName)) && !moduleSpec.overrideModuleName) {
                throw new RuntimeException("'" + definedName + "' already has the Automatic-Module-Name '" + expectedName + "'; explicitly allow override via 'overrideName()'");
            }
            if (parameters.getFailOnAutomaticModules().get()) {
                throw new RuntimeException("Use of 'automaticModule()' is prohibited. Use 'module()' instead: " + originalJar.getName());
            }
            addAutomaticModuleName(originalJar, getModuleJar(outputs, originalJar), (AutomaticModuleName) moduleSpec);
        } else if (realModule) {
            outputs.file(originalJar);
        } else if (autoModuleName(originalJar) != null) {
            if (parameters.getFailOnAutomaticModules().get()) {
                throw new RuntimeException("Found an automatic module: " + autoModuleName(originalJar) + " (" + originalJar.getName() + ")");
            }
            outputs.file(originalJar);
        } else if (parameters.getDeriveAutomaticModuleNamesFromFileNames().get()) {
            String automaticName = automaticModulNameFromFileName(originalJar);
            addAutomaticModuleName(originalJar, getModuleJar(outputs, originalJar), new AutomaticModuleName(originalJar.getName(), automaticName));
        } else if (parameters.getFailOnMissingModuleInfo().get()) {
            throw new RuntimeException("Not a module and no mapping defined: " + originalJar.getName());
        } else {
            outputs.file(originalJar);
        }
    }

    @Nullable
    private ModuleSpec findModuleSpec(File originalJar) {
        Map<String, ModuleSpec> moduleSpecs = getParameters().getModuleSpecs().get();

        Optional<String> gaCoordinates = moduleSpecs.keySet().stream().filter(ga -> gaCoordinatesFromFilePathMatch(originalJar.toPath(), ga)).findFirst();
        if (gaCoordinates.isPresent()) {
            return moduleSpecs.get(gaCoordinates.get());
        }

        String originalJarName = originalJar.getName();
        if (moduleSpecs.containsKey(originalJarName)) {
            return moduleSpecs.get(originalJarName);
        }

        return null;
    }

    private boolean willBeMerged(File originalJar, Collection<ModuleSpec> modules) {
        return modules.stream().anyMatch(module -> module.getMergedJars().stream().anyMatch(toMerge ->
                gaCoordinatesFromFilePathMatch(originalJar.toPath(), toMerge) || toMerge.equals(originalJar.getName())));
    }

    private boolean isModule(File jar) {
        if (!jar.isFile()) {
            // If the jar does not exist, we assume that the file, which is produced later is a local artifact and a module.
            // For local files this behavior is ok, because this transform is targeting published artifacts.
            // Still, this can cause an error: https://github.com/gradle/gradle/issues/27372
            // See also:
            // - https://github.com/jjohannes/extra-java-module-info/issues/15
            // - https://github.com/jjohannes/extra-java-module-info/issues/78
            return true;
        }
        try (JarInputStream inputStream = new JarInputStream(Files.newInputStream(jar.toPath()))) {
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

    @Nullable
    private String autoModuleName(File jar) {
        try (JarInputStream inputStream = new JarInputStream(Files.newInputStream(jar.toPath()))) {
            Manifest manifest = inputStream.getManifest();
            return manifest != null ? manifest.getMainAttributes().getValue("Automatic-Module-Name") : null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private File getModuleJar(TransformOutputs outputs, File originalJar) {
        return outputs.file(originalJar.getName().substring(0, originalJar.getName().lastIndexOf('.')) + "-module.jar");
    }

    private void addAutomaticModuleName(File originalJar, File moduleJar, AutomaticModuleName automaticModule) {
        try (JarInputStream inputStream = new JarInputStream(Files.newInputStream(originalJar.toPath()))) {
            Manifest manifest = inputStream.getManifest();
            if (manifest == null) {
                manifest = new Manifest();
                manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
            }
            manifest.getMainAttributes().putValue("Automatic-Module-Name", automaticModule.getModuleName());
            try (JarOutputStream outputStream = newJarOutputStream(Files.newOutputStream(moduleJar.toPath()), manifest)) {
                Map<String, List<String>> providers = new LinkedHashMap<>();
                Set<String> packages = new TreeSet<>();
                copyAndExtractProviders(inputStream, outputStream, !automaticModule.getMergedJars().isEmpty(), providers, packages);
                mergeJars(automaticModule, outputStream, providers, packages);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void addModuleDescriptor(File originalJar, File moduleJar, ModuleInfo moduleInfo) {
        try (JarInputStream inputStream = new JarInputStream(Files.newInputStream(originalJar.toPath()))) {
            try (JarOutputStream outputStream = newJarOutputStream(Files.newOutputStream(moduleJar.toPath()), inputStream.getManifest())) {
                Map<String, List<String>> providers = new LinkedHashMap<>();
                Set<String> packages = new TreeSet<>();
                copyAndExtractProviders(inputStream, outputStream, !moduleInfo.getMergedJars().isEmpty(), providers, packages);
                mergeJars(moduleInfo, outputStream, providers, packages);
                outputStream.putNextEntry(newReproducibleEntry("module-info.class"));
                outputStream.write(addModuleInfo(moduleInfo, providers, versionFromFilePath(originalJar.toPath()),
                        moduleInfo.exportAllPackages ? packages : Collections.emptySet()));
                outputStream.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private JarOutputStream newJarOutputStream(OutputStream out, @Nullable Manifest manifest) throws IOException {
        JarOutputStream jar = new JarOutputStream(out);
        if (manifest != null) {
            ZipEntry e = newReproducibleEntry(JarFile.MANIFEST_NAME);
            jar.putNextEntry(e);
            manifest.write(new BufferedOutputStream(jar));
            jar.closeEntry();
        }
        return jar;
    }

    private void copyAndExtractProviders(JarInputStream inputStream, JarOutputStream outputStream, boolean willMergeJars, Map<String, List<String>> providers, Set<String> packages) throws IOException {
        JarEntry jarEntry = inputStream.getNextJarEntry();
        while (jarEntry != null) {
            byte[] content = readAllBytes(inputStream);
            String entryName = jarEntry.getName();
            boolean isFileInServicesFolder = entryName.startsWith(SERVICES_PREFIX)
                    && !entryName.equals(SERVICES_PREFIX)
                    && !entryName.substring(SERVICES_PREFIX.length()).contains("/"); // ignore files in sub-folders
            if (isFileInServicesFolder) {
                String key = entryName.substring(SERVICES_PREFIX.length());
                if (!providers.containsKey(key)) {
                    providers.put(key, new ArrayList<>());
                }
                providers.get(key).addAll(extractImplementations(content));
            }

            if (!JAR_SIGNATURE_PATH.matcher(entryName).matches() && !"META-INF/MANIFEST.MF".equals(entryName) && !isModuleInfoClass(entryName)) {
                if (!willMergeJars || !isFileInServicesFolder) { // service provider files will be merged later
                    jarEntry.setCompressedSize(-1);
                    try {
                        outputStream.putNextEntry(jarEntry);
                        outputStream.write(content);
                        outputStream.closeEntry();
                    } catch (ZipException e) {
                        if (!e.getMessage().startsWith("duplicate entry:")) {
                            throw new RuntimeException(e);
                        }
                    }
                    if (entryName.endsWith(".class")) {
                        int i = entryName.lastIndexOf("/");
                        if (i > 0) {
                            Matcher mrJarMatcher = MRJAR_VERSIONS_PATH.matcher(entryName);
                            if (mrJarMatcher.matches()) {
                                // Strip the 'META-INF/versions/11' part
                                packages.add(mrJarMatcher.group(1));
                            } else {
                                packages.add(entryName.substring(0, i));
                            }
                        }
                    }
                }
            }
            jarEntry = inputStream.getNextJarEntry();
        }
    }

    private List<String> extractImplementations(byte[] content) {
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8))
                .lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.startsWith("#"))
                .distinct()
                .collect(Collectors.toList());
    }

    private byte[] addModuleInfo(ModuleInfo moduleInfo, Map<String, List<String>> providers, @Nullable String version, Set<String> autoExportedPackages) {
        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(Opcodes.V9, Opcodes.ACC_MODULE, "module-info", null, null, null);
        int openModule = moduleInfo.openModule ? Opcodes.ACC_OPEN : 0;
        String moduleVersion = moduleInfo.getModuleVersion() == null ? version : moduleInfo.getModuleVersion();
        ModuleVisitor moduleVisitor = classWriter.visitModule(moduleInfo.getModuleName(), openModule, moduleVersion);

        for (String packageName : autoExportedPackages) {
            moduleVisitor.visitExport(packageName, 0);
        }
        for (String packageName : moduleInfo.exports) {
            moduleVisitor.visitExport(packageName.replace('.', '/'), 0);
        }

        for (String packageName : moduleInfo.opens) {
            moduleVisitor.visitOpen(packageName.replace('.', '/'), 0);
        }

        moduleVisitor.visitRequire("java.base", 0, null);

        if (moduleInfo.requireAllDefinedDependencies) {
            Set<String> compileDependencies = getParameters().getCompileClasspathDependencies().get().get(moduleInfo.getIdentifier());
            Set<String> runtimeDependencies = getParameters().getRuntimeClasspathDependencies().get().get(moduleInfo.getIdentifier());
            Set<String> annotationProcessorDependencies = getParameters().getAnnotationProcessorClasspathDependencies().get().get(moduleInfo.getIdentifier());

            if (compileDependencies == null && runtimeDependencies == null && annotationProcessorDependencies == null) {
                throw new RuntimeException("[requires directives from metadata] " +
                        "Cannot find dependencies for '" + moduleInfo.getModuleName() + "'. " +
                        "Are '" + moduleInfo.getIdentifier() + "' the correct component coordinates?");
            }

            if (compileDependencies == null) {
                compileDependencies = Collections.emptySet();
            }
            if (runtimeDependencies == null) {
                runtimeDependencies = Collections.emptySet();
            }
            if (annotationProcessorDependencies == null) {
                annotationProcessorDependencies = Collections.emptySet();
            }
            Set<String> allDependencies = new TreeSet<>();
            allDependencies.addAll(compileDependencies);
            allDependencies.addAll(runtimeDependencies);
            allDependencies.addAll(annotationProcessorDependencies);
            for (String ga : allDependencies) {
                String depModuleName = gaToModuleName(ga);
                if (compileDependencies.contains(ga) && runtimeDependencies.contains(ga)) {
                    moduleVisitor.visitRequire(depModuleName, Opcodes.ACC_TRANSITIVE, null);
                } else if (runtimeDependencies.contains(ga) || annotationProcessorDependencies.contains(ga)) {
                    // We can currently not identify for sure if a 'requires' is NOT transitive.
                    // For that, we would need the 'compile classpath' of the module we are looking at right now.
                    // The 'compileDependencies' set is based only on the 'compile classpath' of the final consumer.
                    moduleVisitor.visitRequire(depModuleName, 0, null);
                } else if (compileDependencies.contains(ga)) {
                    moduleVisitor.visitRequire(depModuleName, Opcodes.ACC_STATIC_PHASE, null);
                }
            }
        }

        for (String requireName : moduleInfo.requires) {
            moduleVisitor.visitRequire(requireName, 0, null);
        }
        for (String requireName : moduleInfo.requiresTransitive) {
            moduleVisitor.visitRequire(requireName, Opcodes.ACC_TRANSITIVE, null);
        }
        for (String requireName : moduleInfo.requiresStatic) {
            moduleVisitor.visitRequire(requireName, Opcodes.ACC_STATIC_PHASE, null);
        }
        for (String usesName : moduleInfo.uses) {
            moduleVisitor.visitUse(usesName.replace('.', '/'));
        }
        for (Map.Entry<String, List<String>> entry : providers.entrySet()) {
            String name = entry.getKey();
            List<String> implementations = entry.getValue();
            if (!moduleInfo.ignoreServiceProviders.contains(name)) {
                moduleVisitor.visitProvide(name.replace('.', '/'),
                        implementations.stream().map(impl -> impl.replace('.', '/')).toArray(String[]::new));
            }
        }
        moduleVisitor.visitEnd();
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    private void mergeJars(ModuleSpec moduleSpec, JarOutputStream outputStream, Map<String, List<String>> providers, Set<String> packages) throws IOException {
        if (moduleSpec.getMergedJars().isEmpty()) {
            return;
        }

        RegularFile mergeJarFile = null;
        for (String identifier : moduleSpec.getMergedJars()) {
            List<String> ids = getParameters().getMergeJarIds().get();
            List<RegularFile> jarFiles = getParameters().getMergeJars().get();
            for (int i = 0; i < ids.size(); i++) {
                // referenced by 'group:version'
                if (ids.get(i).equals(identifier)) {
                    mergeJarFile = jarFiles.get(i);
                    break;
                }
                // referenced by 'jar file name'
                if (jarFiles.get(i).getAsFile().getName().equals(identifier)) {
                    mergeJarFile = jarFiles.get(i);
                    break;
                }
            }

            if (mergeJarFile != null) {
                try (JarInputStream toMergeInputStream = new JarInputStream(Files.newInputStream(mergeJarFile.getAsFile().toPath()))) {
                    copyAndExtractProviders(toMergeInputStream, outputStream, true, providers, packages);
                }
            } else {
                throw new RuntimeException("Jar not found: " + identifier);
            }
        }

        mergeServiceProviderFiles(outputStream, providers);
    }

    private void mergeServiceProviderFiles(JarOutputStream outputStream, Map<String, List<String>> providers) throws IOException {
        for (Map.Entry<String, List<String>> provider : providers.entrySet()) {
            outputStream.putNextEntry(newReproducibleEntry(SERVICES_PREFIX + provider.getKey()));
            for (String implementation : provider.getValue()) {
                outputStream.write(implementation.getBytes());
                outputStream.write("\n".getBytes());
            }
            outputStream.closeEntry();
        }
    }

    private JarEntry newReproducibleEntry(String name) {
        JarEntry jarEntry = new JarEntry(name);
        jarEntry.setTime(CONSTANT_TIME_FOR_ZIP_ENTRIES);
        return jarEntry;
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        final int bufLen = 4 * 0x400;
        byte[] buf = new byte[bufLen];
        int readLen;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            while ((readLen = inputStream.read(buf, 0, bufLen)) != -1) {
                outputStream.write(buf, 0, readLen);
            }
            return outputStream.toByteArray();
        }
    }

    private String gaToModuleName(String ga) {
        ModuleSpec moduleSpec = getParameters().getModuleSpecs().get().get(ga);
        if (moduleSpec != null) {
            return moduleSpec.getModuleName();
        }
        String moduleNameFromSharedMapping = moduleNameFromSharedMapping(ga);
        if (moduleNameFromSharedMapping != null) {
            return moduleNameFromSharedMapping;
        }

        throw new RuntimeException("[requires directives from metadata] " +
                "The module name of the following component is not known: " + ga +
                "\n - If it is already a module, make the module name known using 'knownModule(\"" + ga + "\", \"<module name>\")'" +
                "\n - If it is not a module, patch it using 'module()' or 'automaticModule()'");
    }

    @Nullable
    private String moduleNameFromSharedMapping(String ga) {
        try {
            Class<?> sharedMappings = Class.forName("org.gradlex.javamodule.dependencies.SharedMappings");
            @SuppressWarnings("unchecked")
            Map<String, String> mappings = (Map<String, String>) sharedMappings.getDeclaredField("mappings").get(null);
            Optional<String> found = mappings.entrySet().stream().filter(
                    e -> e.getValue().equals(ga)).map(Map.Entry::getKey).findFirst();
            return found.orElse(null);
        } catch (ReflectiveOperationException ignored) { }
        return null;
    }

    private static boolean isModuleInfoClass(String jarEntryName) {
        return "module-info.class".equals(jarEntryName) || MODULE_INFO_CLASS_MRJAR_PATH.matcher(jarEntryName).matches();
    }

}

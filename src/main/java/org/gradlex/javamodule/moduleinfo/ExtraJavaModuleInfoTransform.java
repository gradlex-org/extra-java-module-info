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

import org.gradle.api.artifacts.transform.CacheableTransform;
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
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
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
import java.util.LinkedHashSet;
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
import java.util.stream.Stream;
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
@CacheableTransform
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
        Property<Boolean> getFailOnModifiedDerivedModuleNames();

        @Input
        Property<Boolean> getDeriveAutomaticModuleNamesFromFileNames();

        @Input
        ListProperty<String> getMergeJarIds();

        @InputFiles
        @Classpath
        ListProperty<RegularFile> getMergeJars();

        @Input
        MapProperty<String, PublishedMetadata> getRequiresFromMetadata();

        @Input
        MapProperty<String, String> getAdditionalKnownModules();
    }

    @InputArtifact
    @Classpath
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

        checkInputExists(originalJar);

        // We return the original Jar without further analysis, if there is
        // (1) no spec (2) no auto-module check (3) no missing module-info check (4) no auto-name derivation
        if (moduleSpec == null
                && !getParameters().getFailOnAutomaticModules().get()
                && !getParameters().getFailOnMissingModuleInfo().get()
                && !getParameters().getDeriveAutomaticModuleNamesFromFileNames().get()) {
            outputs.file(originalJar);
            return;
        }

        boolean realModule = isModule(originalJar);
        if (moduleSpec instanceof ModuleInfo) {
            if (realModule && !((ModuleInfo) moduleSpec).patchRealModule) {
                throw new RuntimeException("Patching of real modules must be explicitly enabled with 'patchRealModule()' or 'preserveExisting()'");
            }
            String definedName = moduleSpec.getModuleName();
            String expectedName = autoModuleName(originalJar);
            if (expectedName != null && !definedName.equals(expectedName) && !moduleSpec.overrideModuleName) {
                throw new RuntimeException("The name '" + definedName + "' is different than the Automatic-Module-Name '" + expectedName + "'; explicitly allow override via 'overrideModuleName()'");
            }
            if (parameters.getFailOnModifiedDerivedModuleNames().get() && !realModule && expectedName == null && !moduleSpec.overrideModuleName) {
                String expectedAutomaticNameFromFileName = automaticModulNameFromFileName(originalJar);
                if (!definedName.equals(expectedAutomaticNameFromFileName)) {
                    throw new RuntimeException("The name '" + definedName + "' is different than the name derived from the Jar file name '" + expectedAutomaticNameFromFileName + "'; turn off 'failOnModifiedDerivedModuleNames' or explicitly allow override via 'overrideModuleName()'");
                }
            }
            addModuleDescriptor(originalJar, getModuleJar(outputs, originalJar), (ModuleInfo) moduleSpec);
        } else if (moduleSpec instanceof AutomaticModuleName) {
            if (realModule) {
                throw new RuntimeException("Patching of real modules must be explicitly enabled with 'patchRealModule()' and can only be done with 'module()'");
            }
            String definedName = moduleSpec.getModuleName();
            String expectedName = autoModuleName(originalJar);
            if (expectedName != null && (moduleSpec.getMergedJars().isEmpty() || !definedName.equals(expectedName)) && !moduleSpec.overrideModuleName) {
                throw new RuntimeException("'" + definedName + "' already has the Automatic-Module-Name '" + expectedName + "'; explicitly allow override via 'overrideModuleName()'");
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

    private void checkInputExists(File jar) {
        if (!jar.isFile()) {
            // If the jar does not exist, it is most likely a locally-built Jar that does not yet exist because the
            // transform was triggered at configuration time. See:
            // - https://github.com/gradle/gradle/issues/26155
            // - https://github.com/gradlex-org/extra-java-module-info/issues/15
            // - https://github.com/gradlex-org/extra-java-module-info/issues/78
            throw new RuntimeException("File does not exist: " + jar
                    + "\n  This is likely because a tool or another plugin performs early dependency resolution."
                    + "\n  You can prevent this error by setting 'skipLocalJars = true'.");
        }
    }

    @Nullable
    private ModuleSpec findModuleSpec(File originalJar) {
        Map<String, ModuleSpec> moduleSpecs = getParameters().getModuleSpecs().get();

        Optional<ModuleSpec> moduleSpec = moduleSpecs.values().stream().filter(spec -> gaCoordinatesFromFilePathMatch(originalJar.toPath(), spec.getIdentifier())).findFirst();
        if (moduleSpec.isPresent()) {
            String ga = moduleSpec.get().getIdentifier();
            if (moduleSpecs.containsKey(ga)) {
                return moduleSpecs.get(ga);
            } else {
                // maybe with classifier
                Stream<String> idsWithClassifier = moduleSpecs.keySet().stream().filter(id -> id.startsWith(ga + "|"));
                for (String idWithClassifier : idsWithClassifier.collect(Collectors.toList())) {
                    if (nameHasClassifier(originalJar, moduleSpecs.get(idWithClassifier))) {
                        return moduleSpecs.get(idWithClassifier);
                    }
                }
            }
            return null;
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
                copyAndExtractProviders(inputStream, outputStream, automaticModule.getRemovedPackages(), !automaticModule.getMergedJars().isEmpty(), providers, packages);
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
                byte[] existingModuleInfo = copyAndExtractProviders(inputStream, outputStream, moduleInfo.getRemovedPackages(), !moduleInfo.getMergedJars().isEmpty(), providers, packages);
                mergeJars(moduleInfo, outputStream, providers, packages);
                outputStream.putNextEntry(newReproducibleEntry("module-info.class"));

                if (moduleInfo.exportAllPackages) {
                    moduleInfo.exportAllPackagesExceptions.forEach(it -> packages.remove(packageToPath(it)));
                }
                outputStream.write(addModuleInfo(moduleInfo, providers, versionFromFilePath(originalJar.toPath()),
                        moduleInfo.exportAllPackages ? packages : Collections.emptySet(),
                        existingModuleInfo));
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

    @Nullable
    private byte[] copyAndExtractProviders(JarInputStream inputStream, JarOutputStream outputStream, List<String> removedPackages, boolean willMergeJars, Map<String, List<String>> providers, Set<String> packages) throws IOException {
        JarEntry jarEntry = inputStream.getNextJarEntry();
        byte[] existingModuleInfo = null;
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
            if (isModuleInfoClass(entryName)) {
                existingModuleInfo = content;
            } else if (!JAR_SIGNATURE_PATH.matcher(entryName).matches() && !"META-INF/MANIFEST.MF".equals(entryName)) {
                if (!willMergeJars || !isFileInServicesFolder) { // service provider files will be merged later
                    Matcher mrJarMatcher = MRJAR_VERSIONS_PATH.matcher(entryName);
                    int i = entryName.lastIndexOf("/");
                    String packagePath = i > 0 ? mrJarMatcher.matches()
                            ? mrJarMatcher.group(1)
                            : entryName.substring(0, i)
                            : "";

                    if (!removedPackages.contains(pathToPackage(packagePath))) {
                        if (entryName.endsWith(".class") && !packagePath.isEmpty()) {
                            packages.add(packagePath);
                        }

                        try {
                            jarEntry.setCompressedSize(-1);
                            outputStream.putNextEntry(jarEntry);
                            outputStream.write(content);
                            outputStream.closeEntry();
                        } catch (ZipException e) {
                            if (!e.getMessage().startsWith("duplicate entry:")) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            }
            jarEntry = inputStream.getNextJarEntry();
        }
        return existingModuleInfo;
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

    private byte[] addModuleInfo(ModuleInfo moduleInfo, Map<String, List<String>> providers, @Nullable String version, Set<String> autoExportedPackages,
                                 @Nullable byte[] existingModuleInfo) {
        ClassReader classReader = moduleInfo.preserveExisting && existingModuleInfo != null ? new ClassReader(existingModuleInfo) : null;
        ClassWriter classWriter = new ClassWriter(classReader, 0);
        int openModule = moduleInfo.openModule ? Opcodes.ACC_OPEN : 0;
        String moduleVersion = moduleInfo.getModuleVersion() == null ? version : moduleInfo.getModuleVersion();

        if (classReader == null) {
            classWriter.visit(Opcodes.V9, Opcodes.ACC_MODULE, "module-info", null, null, null);
            ModuleVisitor moduleVisitor = classWriter.visitModule(moduleInfo.getModuleName(), openModule, moduleVersion);
            moduleVisitor.visitRequire("java.base", 0, null);
            addModuleInfoEntires(moduleInfo, providers, autoExportedPackages, moduleVisitor);
            moduleVisitor.visitEnd();
            classWriter.visitEnd();
        } else {
            ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, classWriter) {
                @Override
                public ModuleVisitor visitModule(String name, int access, String version) {
                    ModuleVisitor moduleVisitor = super.visitModule(name, access, version);
                    return new ModuleVisitor(Opcodes.ASM9, moduleVisitor) {
                        @Override
                        public void visitEnd() {
                            addModuleInfoEntires(moduleInfo, Collections.emptyMap(), autoExportedPackages, this);
                            super.visitEnd();
                        }
                    };
                }
            };
            classReader.accept(classVisitor, 0);
        }
        return classWriter.toByteArray();
    }

    /**
     * Convert a Java package name to a path
     * @param packageName The package name
     * @return The package name converted to a path
     */
    private static String packageToPath(String packageName) {
        return packageName.replace('.', '/');
    }

    /**
     * Convert a path to a Java package name
     * @param path The path
     * @return The path converted to a package name
     */
    private static String pathToPackage(String path) {
        return path.replace('/', '.');
    }

    private void addModuleInfoEntires(ModuleInfo moduleInfo, Map<String, List<String>> providers, Set<String> autoExportedPackages, ModuleVisitor moduleVisitor) {
        for (String packageName : autoExportedPackages) {
            moduleVisitor.visitExport(packageName, 0);
        }
        for (Map.Entry<String, Set<String>> entry : moduleInfo.exports.entrySet()) {
            String packageName = entry.getKey();
            Set<String> modules = entry.getValue();
            moduleVisitor.visitExport(packageToPath(packageName), 0, modules.toArray(new String[0]));
        }

        for (Map.Entry<String, Set<String>> entry : moduleInfo.opens.entrySet()) {
            String packageName = entry.getKey();
            Set<String> modules = entry.getValue();
            moduleVisitor.visitOpen(packageToPath(packageName), 0, modules.toArray(new String[0]));
        }

        if (moduleInfo.requireAllDefinedDependencies) {
            String identifier = moduleInfo.getIdentifier();
            PublishedMetadata requires = getParameters().getRequiresFromMetadata().get().get(identifier);

            if (requires == null) {
                throw new RuntimeException("[requires directives from metadata] " +
                        "Cannot find dependencies for '" + moduleInfo.getModuleName() + "'. " +
                        "Are '" + moduleInfo.getIdentifier() + "' the correct component coordinates?");
            }
            if (requires.getErrorMessage() != null) {
                throw new RuntimeException("[requires directives from metadata] " +
                        "Cannot read metadata for '" + moduleInfo.getModuleName() + "': " +
                        requires.getErrorMessage());
            }

            for (String ga : requires.getRequires()) {
                String depModuleName = gaToModuleName(ga);
                moduleVisitor.visitRequire(depModuleName, 0, null);
            }
            for (String ga : requires.getRequiresTransitive()) {
                String depModuleName = gaToModuleName(ga);
                moduleVisitor.visitRequire(depModuleName, Opcodes.ACC_TRANSITIVE, null);
            }
            for (String ga : requires.getRequiresStaticTransitive()) {
                String depModuleName = gaToModuleName(ga);
                moduleVisitor.visitRequire(depModuleName, Opcodes.ACC_STATIC_PHASE | Opcodes.ACC_TRANSITIVE, null);
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
        for (String requireName : moduleInfo.requiresStaticTransitive) {
            moduleVisitor.visitRequire(requireName, Opcodes.ACC_STATIC_PHASE | Opcodes.ACC_TRANSITIVE, null);
        }
        for (String usesName : moduleInfo.uses) {
            moduleVisitor.visitUse(packageToPath(usesName));
        }
        for (Map.Entry<String, List<String>> entry : providers.entrySet()) {
            String name = entry.getKey();
            Set<String> skipSet = moduleInfo.ignoreServiceProviders.get(name);
            Set<String> implementations = new LinkedHashSet<>(entry.getValue());
            if (skipSet != null) {
                if (skipSet.isEmpty()) {
                    implementations.clear(); // Skip altogether
                } else {
                    implementations.removeAll(skipSet); // Skip some
                }
            }
            if (!implementations.isEmpty()) {
                moduleVisitor.visitProvide(
                        packageToPath(name),
                        implementations.stream().map(ExtraJavaModuleInfoTransform::packageToPath).toArray(String[]::new)
                );
            }
        }
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
                    copyAndExtractProviders(toMergeInputStream, outputStream, moduleSpec.getRemovedPackages(), true, providers, packages);
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
        Optional<String> foundInCustom = getParameters().getAdditionalKnownModules().get().entrySet().stream().filter(
                e -> e.getValue().equals(ga)).map(Map.Entry::getKey).findFirst();
        if (foundInCustom.isPresent()) {
            return foundInCustom.get();
        }

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

    private boolean nameHasClassifier(File jar, ModuleSpec spec) {
        return jar.getName().endsWith("-" + spec.getClassifier() + ".jar");
    }

    private static boolean isModuleInfoClass(String jarEntryName) {
        return "module-info.class".equals(jarEntryName) || MODULE_INFO_CLASS_MRJAR_PATH.matcher(jarEntryName).matches();
    }

}

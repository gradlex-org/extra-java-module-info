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
import org.gradle.internal.impldep.org.apache.maven.wagon.Streams;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.Map;
import java.util.jar.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import static de.jjohannes.gradle.javamodules.JarRewriteUtil.addAutomaticModuleName;
import static de.jjohannes.gradle.javamodules.JarRewriteUtil.addModuleDescriptor;

/**
 * An artifact transform that applies additional information to Jars without module information.
 * The transformation fails the build if a Jar does not contain information and no extra information
 * was defined for it. This way we make sure that all Jars are turned into modules.
 */
@CacheableRule
abstract public class ExtraModuleInfoTransform implements TransformAction<ExtraModuleInfoTransform.Parameter> {

    private static final Pattern MODULE_INFO_CLASS_MRJAR_PATH = Pattern.compile("META-INF/versions/\\d+/module-info.class");
    private static final Pattern JAR_SIGNATURE_PATH = Pattern.compile("^META-INF/[^/]+\\.(SF|RSA|DSA|sf|rsa|dsa)$");
    private static final String SERVICES_PREFIX = "META-INF/services/";

    public interface Parameter extends TransformParameters {
        @Input
        MapProperty<String, ModuleInfo> getModuleInfo();
        @Input
        MapProperty<String, AutomaticModuleName> getAutomaticModules();
        @Input
        Property<Boolean> getFailOnMissingModuleInfo();
    }

    @InputArtifact
    protected abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(@Nonnull TransformOutputs outputs) {
        Map<String, ModuleInfo> moduleInfo = getParameters().getModuleInfo().get();
        Map<String, AutomaticModuleName> automaticModules = getParameters().getAutomaticModules().get();
        File originalJar = getInputArtifact().get().getAsFile();
        String originalJarName = originalJar.getName();

        if (moduleInfo.containsKey(originalJarName)) {
            addModuleDescriptor(originalJar, getModuleJar(outputs, originalJar), moduleInfo.get(originalJarName));
        } else if (automaticModules.containsKey(originalJarName)) {
            addAutomaticModuleName(originalJar, getModuleJar(outputs, originalJar), automaticModules.get(originalJarName));
        } else if (isModule(originalJar)) {
            outputs.file(originalJar);
        } else if (isAutoModule(originalJar)) {
            outputs.file(originalJar);
        } else if (willBeMerged(originalJar, moduleInfo.values(), automaticModules.values())) {
            outputs.file(originalJar);
        } else {
            if (getParameters().getFailOnMissingModuleInfo().get()) {
                throw new RuntimeException("Not a module and no mapping defined: " + originalJarName);
            } else {
                outputs.file(originalJar);
            }
        }
    }

    private boolean willBeMerged(File originalJar, Collection<ModuleInfo> modules, Collection<AutomaticModuleName> automaticModules) {
        return Stream.concat(modules.stream(), automaticModules.stream()).anyMatch(module ->
                module.getMergedJars().stream().anyMatch(toMerge -> toMerge.equals(originalJar.getName())));
    }

    private boolean isModule(File jar) {
        if (!jar.isFile()) {
            // If the jar does not exist, we assume that the file, which is produced later is a local artifact and a module.
            // For local files this behavior is ok, because this transform is targeting published artifacts.
            // See also: https://github.com/jjohannes/extra-java-module-info/issues/15
            try {
                //noinspection ResultOfMethodCallIgnored
                jar.getParentFile().mkdirs();
                //noinspection ResultOfMethodCallIgnored
                jar.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return true;
        }
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

    private static void addAutomaticModuleName(File originalJar, File moduleJar, AutomaticModuleName automaticModule) {
        try (JarInputStream inputStream = new JarInputStream(new FileInputStream(originalJar))) {
            Manifest manifest = inputStream.getManifest();
            if (manifest == null) {
                manifest = new Manifest();
                manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
            }
            manifest.getMainAttributes().putValue("Automatic-Module-Name", automaticModule.getModuleName());
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
            if (!JAR_SIGNATURE_PATH.matcher(jarEntry.getName()).matches() && !"META-INF/MANIFEST.MF".equals(jarEntry.getName())) {
                outputStream.putNextEntry(jarEntry);
                outputStream.write(content);
                outputStream.closeEntry();
            }
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
        ModuleVisitor moduleVisitor = classWriter.visitModule(moduleInfo.getModuleName(), Opcodes.ACC_OPEN, moduleInfo.getModuleVersion());
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

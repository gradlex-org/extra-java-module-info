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
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.internal.impldep.org.apache.maven.wagon.Streams;

import javax.annotation.Nonnull;
import java.io.*;
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
abstract public class ExtraModuleInfoTransform implements TransformAction<ExtraModuleInfoTransform.Parameter> {

    private static final Pattern MODULE_INFO_CLASS_MRJAR_PATH = Pattern.compile("META-INF/versions/\\d+/module-info.class");

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
            }
        }
    }

    private boolean willBeMerged(File originalJar, Collection<ModuleInfo> modules, Collection<AutomaticModuleName> automaticModules) {
        return Stream.concat(modules.stream(), automaticModules.stream()).anyMatch(module ->
                module.getMergedJars().stream().anyMatch(toMerge -> toMerge.equals(originalJar.getName())));
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

}

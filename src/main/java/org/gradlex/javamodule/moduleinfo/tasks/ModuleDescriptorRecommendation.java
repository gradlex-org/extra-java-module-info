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

package org.gradlex.javamodule.moduleinfo.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;

public abstract class ModuleDescriptorRecommendation extends DefaultTask {

    private static final class Artifact {

        final ModuleIdentifier coordinates;

        final Set<ModuleIdentifier> runtimeDependencies = new HashSet<>();

        final Set<ModuleIdentifier> compileDependencies = new HashSet<>();

        final File jar;

        final SortedSet<String> requires = new TreeSet<>();
        final SortedSet<String> requiresTransitive = new TreeSet<>();
        final SortedSet<String> requiresStatic = new TreeSet<>();
        final SortedSet<String> exports = new TreeSet<>();

        final SortedSet<String> provides = new TreeSet<>();

        String moduleName;

        boolean automatic;

        Artifact(ModuleIdentifier coordinates, File jar) {
            this.coordinates = coordinates;
            this.jar = jar;
        }

        Set<ModuleIdentifier> allDependencies() {
            Set<ModuleIdentifier> out = new HashSet<>();
            out.addAll(compileDependencies);
            out.addAll(runtimeDependencies);
            return out;
        }

        boolean containsAnyRequires(String moduleName) {
            return requires.contains(moduleName) || requiresTransitive.contains(moduleName) || requiresStatic.contains(moduleName);
        }

        String dsl() {
            List<String> out = new ArrayList<>();
            String group = this.coordinates.getGroup();
            String name = this.coordinates.getName();
            String moduleName = this.moduleName;
            out.add("module('" + group + ":" + name + "', '" + moduleName + "') {");
            out.add("    closeModule()");
            for (String item : this.requiresTransitive) {
                out.add("    requiresTransitive('" + item + "')");
            }
            for (String item : this.requiresStatic) {
                out.add("    requiresStatic('" + item + "')");
            }
            for (String item : this.requires) {
                out.add("    requires('" + item + "')");
            }
            for (String item : this.exports) {
                out.add("    exports('" + item + "')");
            }
            for (String item : this.provides) {
                out.add("    // ignoreServiceProvider('" + item + "')");
            }
            out.add("}");
            return String.join("\n", out)
                    .replace('\'', '"');
        }

    }

    interface Java8SafeToolProvider {

        int run(PrintWriter out, PrintWriter err, String... args);

        @SuppressWarnings("Since15")
        static Java8SafeToolProvider findFirst(String name) {
            try {
                ToolProvider tool = ToolProvider.findFirst(name)
                        .orElseThrow(() -> new RuntimeException("The JDK does not bundle " + name));
                return tool::run;
            } catch (NoClassDefFoundError e) {
                throw new RuntimeException("This functionality requires Gradle to be powered by JDK 11+", e);
            }
        }

    }

    @InputFiles
    public abstract ListProperty<File> getRuntimeArtifacts();
    @Input
    public abstract ListProperty<ResolvedComponentResult> getRuntimeResolvedComponentResults();

    @InputFiles
    public abstract ListProperty<File> getCompileArtifacts();
    @Input
    public abstract ListProperty<ResolvedComponentResult> getCompileResolvedComponentResults();

    @Input
    public abstract Property<Integer> getRelease();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @TaskAction
    public void execute() throws IOException {
        Java8SafeToolProvider jdepsTool = Java8SafeToolProvider.findFirst("jdeps");
        Java8SafeToolProvider jarTool = Java8SafeToolProvider.findFirst("jar");

        Map<ModuleIdentifier, Artifact> artifacts = new HashMap<>();
        extractArtifactsAndTheirDependencies(artifacts, getRuntimeArtifacts().get(), getRuntimeResolvedComponentResults().get(), artifact -> artifact.runtimeDependencies);
        extractArtifactsAndTheirDependencies(artifacts, getCompileArtifacts().get(), getCompileResolvedComponentResults().get(), artifact -> artifact.compileDependencies);

        Path temporaryFolder = Files.createTempDirectory("jdeps-task");
        for (Map.Entry<ModuleIdentifier, Artifact> entry : artifacts.entrySet()) {
            Artifact artifact = entry.getValue();
            storeJarToolParsedMetadata(jarTool, artifact);
            if (artifact.automatic) {
                storeJdepsToolParsedMetadata(jdepsTool, temporaryFolder, artifact, artifacts.values());
            }
        }
        List<Artifact> modulesToRecommend = new ArrayList<>();
        for (Map.Entry<ModuleIdentifier, Artifact> entry : artifacts.entrySet()) {
            Artifact artifact = entry.getValue();
            if (artifact.automatic) {
                for (ModuleIdentifier dependency : artifact.allDependencies()) {
                    Artifact dependencyArtifact = artifacts.get(dependency);
                    // If the dependency modifier was not identified by jdeps, try to find it the "best" possible requires modifier
                    // using the same heuristic that is utilized by "requireAllDefinedDependencies()".
                    if (!artifact.containsAnyRequires(dependencyArtifact.moduleName)) {
                        boolean hasCompileDependency = artifact.compileDependencies.contains(dependencyArtifact.coordinates);
                        boolean hasRuntimeDependency = artifact.runtimeDependencies.contains(dependencyArtifact.coordinates);
                        if (hasCompileDependency && hasRuntimeDependency) {
                            artifact.requiresTransitive.add(dependencyArtifact.moduleName);
                        } else if (hasRuntimeDependency) {
                            artifact.requires.add(dependencyArtifact.moduleName);
                        } else if (hasCompileDependency) {
                            artifact.requiresStatic.add(dependencyArtifact.moduleName);
                        }
                    }
                }
                modulesToRecommend.add(artifact);
            }
        }

        modulesToRecommend.sort(Comparator.<Artifact, String>comparing(entry -> entry.coordinates.getGroup()).thenComparing(entry->entry.coordinates.getName()));

        for (Artifact artifact : modulesToRecommend) {
            System.out.println(artifact.dsl());
        }

        if (modulesToRecommend.isEmpty()) {
            System.out.println("All good. Looks like all the dependencies have the proper module-info.class defined");
        }

        getFileSystemOperations().delete(spec -> spec.delete(temporaryFolder));
    }

    private static void extractArtifactsAndTheirDependencies(Map<ModuleIdentifier, Artifact> jarsToAnalyze,
                                                             List<File> artifacts,
                                                             List<ResolvedComponentResult> resolvedComponentResults,
                                                             Function<Artifact, Set<ModuleIdentifier>> depsSink) {
        for (ResolvedComponentResult artifact : resolvedComponentResults) {
            ComponentIdentifier identifier = artifact.getId();
            if (identifier instanceof ModuleComponentIdentifier) {
                ModuleIdentifier moduleIdentifier = ((ModuleComponentIdentifier) identifier).getModuleIdentifier();
                int index = resolvedComponentResults.indexOf(artifact);
                jarsToAnalyze.computeIfAbsent(moduleIdentifier, (ignore) -> new Artifact(moduleIdentifier, artifacts.get(index)));
            }
        }
        for (ResolvedComponentResult resolvedComponent : resolvedComponentResults) {
            ModuleVersionIdentifier moduleVersion = resolvedComponent.getModuleVersion();
            if (moduleVersion == null) {
                continue;
            }
            Artifact artifact = jarsToAnalyze.get(moduleVersion.getModule());
            if (artifact == null) {
                continue;
            }
            for (DependencyResult dependency : resolvedComponent.getDependencies()) {
                if (dependency instanceof ResolvedDependencyResult) {
                    ModuleVersionIdentifier dependantModuleVersion = ((ResolvedDependencyResult) dependency).getSelected().getModuleVersion();
                    if (dependantModuleVersion != null) {
                        depsSink.apply(artifact).add(dependantModuleVersion.getModule());
                    }
                }
            }
        }
    }

    private static final Pattern REQUIRES_PATTERN = Pattern.compile("^ {4}requires (transitive )?(.*);$");
    private static final Pattern EXPORTS_PATTERN = Pattern.compile("^ {4}exports (.*);$");
    private static final Pattern PROVIDES_PATTERN = Pattern.compile("^ {4}provides (.*) with$");

    @SuppressWarnings("Since15")
    private void storeJdepsToolParsedMetadata(Java8SafeToolProvider jdeps, Path outputPath, Artifact targetArtifact, Collection<Artifact> jars) throws IOException {
        List<String> modulePath = new ArrayList<>();
        for (Artifact artifact : jars) {
            if (!artifact.equals(targetArtifact)) {
                modulePath.add(artifact.jar.getAbsolutePath());
            }
        }
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        List<String> args = new ArrayList<>();
        if (!modulePath.isEmpty()) {
            args.addAll(List.of( "--module-path", String.join(File.pathSeparator, modulePath)));
        }
        args.addAll(List.of("--generate-module-info", outputPath.toString()));
        args.addAll(List.of("--multi-release", String.valueOf(getRelease().get())));
        args.add("--ignore-missing-deps");
        args.add(targetArtifact.jar.getAbsolutePath());
        int retVal = jdeps.run(new PrintWriter(out, true), new PrintWriter(err, true), args.toArray(String[]::new));
        if (retVal != 0) {
            throw new RuntimeException(String.format("jdeps returned error %d\n%s\n%s", retVal, out, err));
        }
        String[] result = out.toString().split("\\R");
        String writingToMessage = result.length == 2
                ? result[1] // Skipping "Warning: --ignore-missing-deps specified. Missing dependencies from xyz are ignored"
                : result[0];
        String path = writingToMessage.replace("writing to ", "");
        String moduleInfoJava = Files.readString(Path.of(path));
        String[] parts = moduleInfoJava.split("\\R");
        for (String part : parts) {
            Matcher requiresMatcher = REQUIRES_PATTERN.matcher(part);
            if (requiresMatcher.matches()) {
                if (requiresMatcher.group(1) == null) {
                    targetArtifact.requires.add(requiresMatcher.group(2));
                } else {
                    targetArtifact.requiresTransitive.add(requiresMatcher.group(2));
                }
                continue;
            }
            Matcher exportsMatcher = EXPORTS_PATTERN.matcher(part);
            if (exportsMatcher.matches()) {
                targetArtifact.exports.add(exportsMatcher.group(1));
                continue;
            }
            Matcher providesMatcher = PROVIDES_PATTERN.matcher(part);
            if (providesMatcher.matches()) {
                targetArtifact.provides.add(providesMatcher.group(1));
            }
        }
    }

    private static final Pattern AUTOMATIC_MODULE_NAME_PATTERN = Pattern.compile("(.*?)(@.*)? automatic");
    private static final Pattern MODULE_INFO_CLASS_MODULE_NAME_PATTERN = Pattern.compile("(.*?)(@.*)? jar:(.*)");

    private void storeJarToolParsedMetadata(Java8SafeToolProvider jar, Artifact artifact) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        int retVal = jar.run(
                new PrintWriter(out, true),
                new PrintWriter(err, true),
                "--describe-module",
                "--file",
                artifact.jar.getAbsolutePath(),
                "--release",
                String.valueOf(getRelease().get())
        );
        if (retVal != 0) {
            throw new RuntimeException(String.format("jar returned error %d\n%s\n%s", retVal, out, err));
        }
        String[] result = out.toString().split("\\R");
        if (result[0].equals("No module descriptor found. Derived automatic module.")) {
            Matcher matcher = AUTOMATIC_MODULE_NAME_PATTERN.matcher(result[2]);
            if (!matcher.matches()) {
                throw new RuntimeException("Cannot extract module name from: " + out);
            }
            artifact.moduleName = matcher.group(1);
            artifact.automatic = true;
        } else {
            Matcher matcher = MODULE_INFO_CLASS_MODULE_NAME_PATTERN.matcher(result[0].startsWith("releases: ") ? result[2] : result[0]);
            if (!matcher.matches()) {
                throw new RuntimeException("Cannot extract module name from: " + out);
            }
            artifact.moduleName = matcher.group(1);
            artifact.automatic = false;
        }
    }

}
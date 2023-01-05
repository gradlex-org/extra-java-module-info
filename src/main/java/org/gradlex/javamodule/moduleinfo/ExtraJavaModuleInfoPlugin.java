/*
 * Copyright 2022 the GradleX team.
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
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.Directory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.util.GradleVersion;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Entry point of the plugin.
 */
@SuppressWarnings("unused")
@NonNullApi
public abstract class ExtraJavaModuleInfoPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        if (GradleVersion.current().compareTo(GradleVersion.version("6.4-rc-1")) < 0) {
            throw new RuntimeException("This plugin requires Gradle 6.4+");
        }

        // register the plugin extension as 'extraJavaModuleInfo {}' configuration block
        ExtraJavaModuleInfoPluginExtension extension = project.getExtensions().create("extraJavaModuleInfo", ExtraJavaModuleInfoPluginExtension.class);
        extension.getFailOnMissingModuleInfo().convention(true);

        // setup the transform for all projects in the build
        project.getPlugins().withType(JavaPlugin.class).configureEach(javaPlugin -> configureTransform(project, extension));
    }

    private void configureTransform(Project project, ExtraJavaModuleInfoPluginExtension extension) {
        Configuration javaModulesMergeJars = project.getConfigurations().create("javaModulesMergeJars", c -> {
            c.setVisible(false);
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
            c.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            c.getAttributes().attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));

            // Automatically add dependencies for Jars where we know the coordinates
            c.withDependencies(d -> extension.getModuleSpecs().get().values().stream().flatMap(m ->
                    m.getMergedJars().stream()).filter(s -> s.contains(":")).forEach(s ->
                    d.add(project.getDependencies().create(s))));

            // Automatically get versions from the runtime classpath
            if (GradleVersion.current().compareTo(GradleVersion.version("6.8")) >= 0) {
                //noinspection UnstableApiUsage
                c.shouldResolveConsistentlyWith(project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));
            }
        });

        Attribute<String> artifactType = Attribute.of("artifactType", String.class);
        Attribute<Boolean> javaModule = Attribute.of("javaModule", Boolean.class);

        project.getExtensions().getByType(SourceSetContainer.class).all(sourceSet -> {
            Configuration runtimeClasspath = project.getConfigurations().getByName(sourceSet.getRuntimeClasspathConfigurationName());
            Configuration compileClasspath = project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName());
            Configuration annotationProcessor = project.getConfigurations().getByName(sourceSet.getAnnotationProcessorConfigurationName());

            // compile, runtime and annotation processor classpaths express that they only accept modules by requesting the javaModule=true attribute
            runtimeClasspath.getAttributes().attribute(javaModule, true);
            compileClasspath.getAttributes().attribute(javaModule, true);
            annotationProcessor.getAttributes().attribute(javaModule, true);
        });

        // Jars may be transformed (or merged into) Module Jars
        registerTransform("jar", project, extension, javaModulesMergeJars, artifactType, javaModule);
        // Classpath entries may also be zip files that may be merged into Module Jars (from the docs: "Class paths to the .jar, .zip or .class files)"
        registerTransform("zip", project, extension, javaModulesMergeJars, artifactType, javaModule);
    }

    private void registerTransform(String fileExtension, Project project, ExtraJavaModuleInfoPluginExtension extension, Configuration javaModulesMergeJars, Attribute<String> artifactType, Attribute<Boolean> javaModule) {
        DependencyHandler dependencies = project.getDependencies();
        ConfigurationContainer configurations = project.getConfigurations();
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);

        // all Jars have a javaModule=false attribute by default; the transform also recognizes modules and returns them without modification
        dependencies.getArtifactTypes().maybeCreate(fileExtension).getAttributes().attribute(javaModule, false);

        // register the transform for Jars and "javaModule=false -> javaModule=true"; the plugin extension object fills the input parameter
        dependencies.registerTransform(ExtraJavaModuleInfoTransform.class, t -> {
            t.parameters(p -> {
                p.getModuleSpecs().set(extension.getModuleSpecs());
                p.getFailOnMissingModuleInfo().set(extension.getFailOnMissingModuleInfo());

                // See: https://github.com/adammurdoch/dependency-graph-as-task-inputs/blob/main/plugins/src/main/java/TestPlugin.java
                Provider<Set<ResolvedArtifactResult>> artifacts = project.provider(() ->
                        javaModulesMergeJars.getIncoming().artifactView(v -> v.lenient(true)).getArtifacts().getArtifacts());
                p.getMergeJarIds().set(artifacts.map(new IdExtractor()));
                p.getMergeJars().set(artifacts.map(new FileExtractor(project.getLayout())));

                p.getCompileClasspathDependencies().set(project.provider(() ->
                        toStringMap(sourceSets.stream().flatMap(s -> filteredResolutionResult(configurations.getByName(s.getCompileClasspathConfigurationName()), componentsOfInterest(extension))))));
                p.getRuntimeClasspathDependencies().set(project.provider(() ->
                        toStringMap(sourceSets.stream().flatMap(s -> filteredResolutionResult(configurations.getByName(s.getRuntimeClasspathConfigurationName()), componentsOfInterest(extension))))));
            });
            t.getFrom().attribute(artifactType, fileExtension).attribute(javaModule, false);
            t.getTo().attribute(artifactType, "jar").attribute(javaModule, true);
        });
    }

    private static Set<String> componentsOfInterest(ExtraJavaModuleInfoPluginExtension extension) {
        return extension.getModuleSpecs().get().values().stream().filter(ExtraJavaModuleInfoPlugin::needsDependencies).map(ModuleSpec::getIdentifier).collect(Collectors.toSet());
    }

    private Stream<ResolvedComponentResult> filteredResolutionResult(Configuration configuration, Set<String> componentsOfInterest) {
        if (componentsOfInterest.isEmpty()) {
            return Stream.empty();
        }
        return configuration.getIncoming().getResolutionResult().getAllComponents().stream().filter(c -> componentsOfInterest.contains(ga(c.getId())));
    }

    private Map<String, Set<String>> toStringMap(Stream<ResolvedComponentResult> result) {
        return result.collect(Collectors.toMap(
                c -> ga(c.getId()),
                c -> c.getDependencies().stream().map(ExtraJavaModuleInfoPlugin::ga).collect(Collectors.toSet()),
                (dependencies1, dependencies2) -> dependencies1));
    }

    private static boolean needsDependencies(ModuleSpec moduleSpec) {
        return moduleSpec instanceof ModuleInfo && ((ModuleInfo) moduleSpec).requireAllDefinedDependencies;
    }

    private static String ga(DependencyResult d) {
        if (d instanceof ResolvedDependencyResult) {
            return ga(((ResolvedDependencyResult) d).getSelected().getId());
        }
        return d.getRequested().getDisplayName();
    }

    private static String ga(ComponentIdentifier id) {
        if (id instanceof ModuleComponentIdentifier) {
            return ((ModuleComponentIdentifier) id).getGroup() + ":" + ((ModuleComponentIdentifier) id).getModule();
        }
        return id.getDisplayName();
    }

    private static class IdExtractor implements Transformer<List<String>, Collection<ResolvedArtifactResult>> {
        @Override
        public List<String> transform(Collection<ResolvedArtifactResult> artifacts) {
            return artifacts.stream().map(a -> {
                ComponentIdentifier componentIdentifier = a.getId().getComponentIdentifier();
                if (componentIdentifier instanceof ModuleComponentIdentifier) {
                    return ((ModuleComponentIdentifier) componentIdentifier).getModuleIdentifier().toString();
                } else {
                    return componentIdentifier.getDisplayName();
                }
            }).collect(Collectors.toList());
        }
    }

    private static class FileExtractor implements Transformer<List<RegularFile>, Collection<ResolvedArtifactResult>> {
        private final ProjectLayout projectLayout;

        public FileExtractor(ProjectLayout projectLayout) {
            this.projectLayout = projectLayout;
        }

        @Override
        public List<RegularFile> transform(Collection<ResolvedArtifactResult> artifacts) {
            Directory projectDirectory = projectLayout.getProjectDirectory();
            return artifacts.stream().map(a -> projectDirectory.file(a.getFile().getAbsolutePath())).collect(Collectors.toList());
        }
    }
}

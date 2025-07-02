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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.Directory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.HelpTasksPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.util.GradleVersion;
import org.gradlex.javamodule.moduleinfo.tasks.ModuleDescriptorRecommendation;

import java.io.CharArrayReader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE;
import static org.gradle.api.attributes.Category.LIBRARY;
import static org.gradle.api.attributes.Usage.JAVA_RUNTIME;
import static org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE;
import static org.gradle.api.plugins.JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME;
import static org.gradlex.javamodule.moduleinfo.ExtraJavaModuleInfoPluginExtension.JAVA_MODULE_ATTRIBUTE;

/**
 * Entry point of the plugin.
 */
public abstract class ExtraJavaModuleInfoPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        if (GradleVersion.current().compareTo(GradleVersion.version("6.8")) < 0) {
            throw new RuntimeException("This plugin requires Gradle 6.8+");
        }

        // register the plugin extension as 'extraJavaModuleInfo {}' configuration block
        ExtraJavaModuleInfoPluginExtension extension = project.getExtensions().create("extraJavaModuleInfo", ExtraJavaModuleInfoPluginExtension.class);
        extension.getFailOnMissingModuleInfo().convention(true);
        extension.getFailOnAutomaticModules().convention(false);
        extension.getSkipLocalJars().convention(false);
        extension.getDeriveAutomaticModuleNamesFromFileNames().convention(false);

        // setup the transform and the tasks for all projects in the build
        project.getPlugins().withType(JavaPlugin.class).configureEach(javaPlugin -> {
            configureTransform(project, extension);
            configureModuleDescriptorTasks(project);
        });
    }

    private void configureModuleDescriptorTasks(Project project) {
        project.getExtensions().getByType(SourceSetContainer.class).all(sourceSet -> {
            String name = sourceSet.getTaskName("", "moduleDescriptorRecommendations");
            project.getTasks().register(name, ModuleDescriptorRecommendation.class, task -> {
                Transformer<List<File>, Configuration> artifactsTransformer = configuration -> {
                    //noinspection CodeBlock2Expr
                    return configuration.getIncoming()
                            .getArtifacts()
                            .getArtifacts()
                            .stream()
                            .sorted(Comparator.comparing(artifact -> artifact.getId().getComponentIdentifier().toString()))
                            .map(ResolvedArtifactResult::getFile)
                            .collect(Collectors.toList());
                };

                Transformer<List<ResolvedComponentResult>, Configuration> componentsTransformer = configuration -> {
                    Set<ComponentIdentifier> artifacts = configuration.getIncoming()
                            .getArtifacts()
                            .getArtifacts()
                            .stream()
                            .map(artifact -> artifact.getId().getComponentIdentifier())
                            .collect(Collectors.toSet());
                    return configuration.getIncoming()
                            .getResolutionResult()
                            .getAllComponents()
                            .stream()
                            .filter(component -> artifacts.contains(component.getId()))
                            .sorted(Comparator.comparing(artifact -> artifact.getId().toString()))
                            .collect(Collectors.toList());
                };

                Provider<Configuration> compileClasspath = project.getConfigurations().named(sourceSet.getCompileClasspathConfigurationName());
                task.getCompileArtifacts().set(compileClasspath.map(artifactsTransformer));
                task.getCompileResolvedComponentResults().set(compileClasspath.map(componentsTransformer));

                Provider<Configuration> runtimeClasspath = project.getConfigurations().named(sourceSet.getRuntimeClasspathConfigurationName());
                task.getRuntimeArtifacts().set(runtimeClasspath.map(artifactsTransformer));
                task.getRuntimeResolvedComponentResults().set(runtimeClasspath.map(componentsTransformer));

                task.getRelease().convention(21);

                task.setGroup(HelpTasksPlugin.HELP_GROUP);
                task.setDescription("Generates module descriptors for 'org.gradlex.extra-java-module-info' plugin based on the dependency and class file analysis of automatic modules and non-modular dependencies");
            });
        });
    }

    private void configureTransform(Project project, ExtraJavaModuleInfoPluginExtension extension) {
        Configuration javaModulesMergeJars = project.getConfigurations().create("javaModulesMergeJars", c -> {
            c.setVisible(false);
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
            c.getAttributes().attribute(USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, JAVA_RUNTIME));
            c.getAttributes().attribute(CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, LIBRARY));

            // Automatically add dependencies for Jars where we know the coordinates
            c.withDependencies(d -> extension.getModuleSpecs().get().values().stream().flatMap(m ->
                    m.getMergedJars().stream()).filter(s -> s.contains(":")).forEach(s ->
                    d.add(project.getDependencies().create(s))));

            // Automatically get versions from the runtime classpath
            if (GradleVersion.current().compareTo(GradleVersion.version("6.8")) >= 0) {
                //noinspection UnstableApiUsage
                c.shouldResolveConsistentlyWith(project.getConfigurations().getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME));
            }
        });

        // If 'internal' is added by 'org.gradlex.jvm-dependency-conflict-resolution', extend from it to get access to versions
        project.getConfigurations().all(otherConfiguration -> {
            if ("internal".equals(otherConfiguration.getName())) {
                javaModulesMergeJars.extendsFrom(otherConfiguration);
            }
        });

        Attribute<String> artifactType = Attribute.of("artifactType", String.class);

        project.getExtensions().getByType(SourceSetContainer.class).all(sourceSet -> {
            // by default, activate plugin for all source sets
            extension.activate(sourceSet);

            // outgoing variants may express that they already provide a modular Jar and can hence skip the transform altogether
            Configuration runtimeElements = project.getConfigurations().findByName(sourceSet.getRuntimeElementsConfigurationName());
            Configuration apiElements = project.getConfigurations().findByName(sourceSet.getApiElementsConfigurationName());
            if (GradleVersion.current().compareTo(GradleVersion.version("7.4")) >= 0) {
                if (runtimeElements != null) {
                    runtimeElements.getOutgoing().getAttributes().attributeProvider(JAVA_MODULE_ATTRIBUTE, extension.getSkipLocalJars());
                }
                if (apiElements != null) {
                    apiElements.getOutgoing().getAttributes().attributeProvider(JAVA_MODULE_ATTRIBUTE, extension.getSkipLocalJars());
                }
            } else {
                project.afterEvaluate(p -> {
                    if (runtimeElements != null) {
                        runtimeElements.getOutgoing().getAttributes().attribute(JAVA_MODULE_ATTRIBUTE, extension.getSkipLocalJars().get());
                    }
                    if (apiElements != null) {
                        apiElements.getOutgoing().getAttributes().attribute(JAVA_MODULE_ATTRIBUTE, extension.getSkipLocalJars().get());
                    }
                });
            }
        });

        // Jars may be transformed (or merged into) Module Jars
        registerTransform("jar", project, extension, javaModulesMergeJars, artifactType, JAVA_MODULE_ATTRIBUTE);
        // Classpath entries may also be zip files that may be merged into Module Jars (from the docs: "Class paths to the .jar, .zip or .class files)"
        registerTransform("zip", project, extension, javaModulesMergeJars, artifactType, JAVA_MODULE_ATTRIBUTE);
    }

    private void registerTransform(String fileExtension, Project project, ExtraJavaModuleInfoPluginExtension extension, Configuration javaModulesMergeJars, Attribute<String> artifactType, Attribute<Boolean> javaModule) {
        DependencyHandler dependencies = project.getDependencies();

        // all Jars have a javaModule=false attribute by default; the transform also recognizes modules and returns them without modification
        dependencies.getArtifactTypes().maybeCreate(fileExtension).getAttributes().attribute(javaModule, false);

        // register the transform for Jars and "javaModule=false -> javaModule=true"; the plugin extension object fills the input parameter
        dependencies.registerTransform(ExtraJavaModuleInfoTransform.class, t -> {
            t.parameters(p -> {
                p.getModuleSpecs().set(extension.getModuleSpecs());
                p.getFailOnMissingModuleInfo().set(extension.getFailOnMissingModuleInfo());
                p.getFailOnAutomaticModules().set(extension.getFailOnAutomaticModules());
                p.getDeriveAutomaticModuleNamesFromFileNames().set(extension.getDeriveAutomaticModuleNamesFromFileNames());

                // See: https://github.com/adammurdoch/dependency-graph-as-task-inputs/blob/main/plugins/src/main/java/TestPlugin.java
                Provider<Set<ResolvedArtifactResult>> artifacts = project.provider(() ->
                        javaModulesMergeJars.getIncoming().artifactView(v -> v.lenient(true)).getArtifacts().getArtifacts());
                p.getMergeJarIds().set(artifacts.map(new IdExtractor()));
                p.getMergeJars().set(artifacts.map(new FileExtractor(project.getLayout())));

                Provider<Set<String>> componentsOfInterest = componentsOfInterest(extension);
                p.getRequiresFromMetadata().set(componentsOfInterest.map(gaSet -> gaSet.stream()
                        .collect(Collectors.toMap(ga -> ga, ga -> new PublishedMetadata(ga, project, extension)))));
                p.getAdditionalKnownModules().set(extractFromModuleDependenciesPlugin(project));
            });
            t.getFrom().attribute(artifactType, fileExtension).attribute(javaModule, false);
            t.getTo().attribute(artifactType, fileExtension).attribute(javaModule, true);
        });
    }

    private Provider<Map<String, String>> extractFromModuleDependenciesPlugin(Project project) {
        return project.provider(() -> {
            Object javaModuleDependencies = project.getExtensions().findByName("javaModuleDependencies");
            if (javaModuleDependencies == null) {
                return Collections.emptyMap();
            }
            try {
                Method getModulesProperties = javaModuleDependencies.getClass().getMethod("getModulesProperties");
                RegularFileProperty file = (RegularFileProperty) getModulesProperties.invoke(javaModuleDependencies);
                return project.getProviders().fileContents(file).getAsText().map(c -> {
                    Properties p = new Properties();
                    try {
                        p.load(new CharArrayReader(c.toCharArray()));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    Map<String, String> result = (Map) p;
                    return result;
                }).getOrElse(Collections.emptyMap());
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static Provider<Set<String>> componentsOfInterest(ExtraJavaModuleInfoPluginExtension extension) {
        return extension.getModuleSpecs().map(specs -> specs.values().stream()
                .filter(ExtraJavaModuleInfoPlugin::needsDependencies)
                .map(ModuleSpec::getIdentifier)
                .collect(Collectors.toSet()));
    }

    private static boolean needsDependencies(ModuleSpec moduleSpec) {
        return moduleSpec instanceof ModuleInfo
                && ((ModuleInfo) moduleSpec).requireAllDefinedDependencies
                && IdValidator.isCoordinates(moduleSpec.getIdentifier());
    }

    static String ga(ComponentIdentifier id) {
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

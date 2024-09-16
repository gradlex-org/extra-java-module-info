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

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmEnvironment;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.util.GradleVersion;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE;
import static org.gradle.api.attributes.Category.LIBRARY;
import static org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE;

public class PublishedMetadata implements Serializable {
    private static final Attribute<String> CATEGORY_ATTRIBUTE_UNTYPED = Attribute.of(CATEGORY_ATTRIBUTE.getName(), String.class);
    private static final String DEFAULT_VERSION_SOURCE_CONFIGURATION = "definedDependenciesVersions";

    private final String gav;
    private final List<String> requires = new ArrayList<>();
    private final List<String> requiresTransitive = new ArrayList<>();
    private final List<String> requiresStaticTransitive = new ArrayList<>();
    private String errorMessage = null;

    PublishedMetadata(String gav, Project project, ExtraJavaModuleInfoPluginExtension extension) {
        this.gav = gav;

        List<String> compileDependencies = componentVariant(extension.getVersionsProvidingConfiguration(), project, Usage.JAVA_API);
        List<String> runtimeDependencies = componentVariant(extension.getVersionsProvidingConfiguration(), project, Usage.JAVA_RUNTIME);

        Stream.concat(compileDependencies.stream(), runtimeDependencies.stream()).distinct().forEach(ga -> {
            if (compileDependencies.contains(ga) && runtimeDependencies.contains(ga)) {
                requiresTransitive.add(ga);
            } else if (runtimeDependencies.contains(ga)) {
                requires.add(ga);
            } else if (compileDependencies.contains(ga)) {
                requiresStaticTransitive.add(ga);
            }
        });
    }

    private List<String> componentVariant(Provider<String> versionsProvidingConfiguration, Project project, String usage) {
        Configuration versionsSource;
        if (versionsProvidingConfiguration.isPresent()) {
            versionsSource = project.getConfigurations().getByName(versionsProvidingConfiguration.get());
        } else {
            // version provider is not configured, create on adhoc based on ALL classpaths of the project
            versionsSource = maybeCreateDefaultVersionSourcConfiguration(project.getConfigurations(), project.getObjects(),
                    project.getExtensions().findByType(SourceSetContainer.class));
        }

        Configuration singleComponentVariantResolver = project.getConfigurations().detachedConfiguration(project.getDependencies().create(gav));
        singleComponentVariantResolver.setCanBeConsumed(false);
        singleComponentVariantResolver.shouldResolveConsistentlyWith(versionsSource);
        versionsSource.getAttributes().keySet().forEach(a -> {
            @SuppressWarnings("rawtypes") Attribute untypedAttributeKey = a;
            //noinspection unchecked
            singleComponentVariantResolver.getAttributes().attribute(untypedAttributeKey, requireNonNull(versionsSource.getAttributes().getAttribute(a)));
        });
        singleComponentVariantResolver.getAttributes().attribute(USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, usage));
        return firstAndOnlyComponentDependencies(singleComponentVariantResolver);
    }

    private Configuration maybeCreateDefaultVersionSourcConfiguration(ConfigurationContainer configurations, ObjectFactory objects, SourceSetContainer sourceSets) {
        String name = DEFAULT_VERSION_SOURCE_CONFIGURATION;
        Configuration existing = configurations.findByName(name);
        if (existing != null) {
            return existing;
        }

        return configurations.create(name, c -> {
            c.setCanBeResolved(true);
            c.setCanBeConsumed(false);
            c.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
            c.getAttributes().attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.LIBRARY));
            c.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, LibraryElements.JAR));
            c.getAttributes().attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.class, Bundling.EXTERNAL));
            if (GradleVersion.current().compareTo(GradleVersion.version("7.0")) >= 0) {
                c.getAttributes().attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                        objects.named(TargetJvmEnvironment.class, TargetJvmEnvironment.STANDARD_JVM));
            }

            if (sourceSets != null) {
                for (SourceSet sourceSet : sourceSets) {
                    Configuration implementation = configurations.getByName(sourceSet.getImplementationConfigurationName());
                    Configuration compileOnly = configurations.getByName(sourceSet.getCompileOnlyConfigurationName());
                    Configuration runtimeOnly = configurations.getByName(sourceSet.getRuntimeOnlyConfigurationName());
                    Configuration annotationProcessor = configurations.getByName(sourceSet.getAnnotationProcessorConfigurationName());
                    c.extendsFrom(implementation, compileOnly, runtimeOnly, annotationProcessor);
                }
            }
        });
    }

    private List<String> firstAndOnlyComponentDependencies(Configuration singleComponentVariantResolver) {
        DependencyResult result = singleComponentVariantResolver
                .getIncoming().getResolutionResult().getRoot()
                .getDependencies().iterator().next();

        if (result instanceof UnresolvedDependencyResult) {
            errorMessage = ((UnresolvedDependencyResult) result).getFailure().getMessage();
            return emptyList();
        } else {
            return ((ResolvedDependencyResult) result).getSelected().getDependencies().stream()
                    .filter(PublishedMetadata::filterComponentDependencies)
                    .map(PublishedMetadata::ga)
                    .collect(Collectors.toList());
        }
    }

    private static boolean filterComponentDependencies(DependencyResult d) {
        if (d instanceof ResolvedDependencyResult) {
            Category category = ((ResolvedDependencyResult) d).getResolvedVariant().getAttributes().getAttribute(CATEGORY_ATTRIBUTE);
            String categoryUntyped = ((ResolvedDependencyResult) d).getResolvedVariant().getAttributes().getAttribute(CATEGORY_ATTRIBUTE_UNTYPED);
            return LIBRARY.equals(categoryUntyped) || (category != null && LIBRARY.equals(category.getName()));
        }
        return false;
    }

    private static String ga(DependencyResult d) {
        if (d instanceof ResolvedDependencyResult) {
            return ExtraJavaModuleInfoPlugin.ga(((ResolvedDependencyResult) d).getSelected().getId());
        }
        return d.getRequested().getDisplayName();
    }

    public String getGav() {
        return gav;
    }

    public List<String> getRequires() {
        return requires;
    }

    public List<String> getRequiresTransitive() {
        return requiresTransitive;
    }

    public List<String> getRequiresStaticTransitive() {
        return requiresStaticTransitive;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}

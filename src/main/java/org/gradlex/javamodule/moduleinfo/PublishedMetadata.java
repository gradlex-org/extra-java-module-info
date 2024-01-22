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
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Usage;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE;
import static org.gradle.api.attributes.Category.LIBRARY;
import static org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE;

public class PublishedMetadata implements Serializable {
    private static final Attribute<String> CATEGORY_ATTRIBUTE_UNTYPED = Attribute.of(CATEGORY_ATTRIBUTE.getName(), String.class);

    private final String gav;
    private final List<String> requires = new ArrayList<>();
    private final List<String> requiresTransitive = new ArrayList<>();
    private final List<String> requiresStaticTransitive = new ArrayList<>();

    PublishedMetadata(String gav, Configuration origin, Project project) {
        this.gav = gav;
        List<String> compileDependencies = componentVariant(origin, project, Usage.JAVA_API);
        List<String> runtimeDependencies = componentVariant(origin, project, Usage.JAVA_RUNTIME);

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

    private List<String> componentVariant(Configuration origin, Project project, String usage) {
        Configuration singleComponentVariantResolver = project.getConfigurations().detachedConfiguration(project.getDependencies().create(gav));
        singleComponentVariantResolver.setCanBeConsumed(false);
        singleComponentVariantResolver.shouldResolveConsistentlyWith(origin);
        origin.getAttributes().keySet().forEach(a -> {
            @SuppressWarnings("rawtypes") Attribute untypedAttributeKey = a;
            //noinspection unchecked
            singleComponentVariantResolver.getAttributes().attribute(untypedAttributeKey, requireNonNull(origin.getAttributes().getAttribute(a)));
        });
        singleComponentVariantResolver.getAttributes().attribute(USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, usage));
        return firstAndOnlyComponent(singleComponentVariantResolver).getDependencies().stream()
                .filter(PublishedMetadata::filterComponentDependencies)
                .map(PublishedMetadata::ga)
                .collect(Collectors.toList());
    }

    private ResolvedComponentResult firstAndOnlyComponent(Configuration singleComponentVariantResolver) {
        ResolvedDependencyResult onlyResult = (ResolvedDependencyResult) singleComponentVariantResolver.getIncoming().getResolutionResult()
                .getRoot().getDependencies().iterator().next();
        return onlyResult.getSelected();
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
}

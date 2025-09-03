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

import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.provider.Provider;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static org.gradlex.javamodule.moduleinfo.IdValidator.validateIdentifier;
import static org.gradlex.javamodule.moduleinfo.ModuleNameUtil.validateModuleName;

/**
 * Details that real Modules and Automatic-Module-Names share.
 */
@SuppressWarnings("unused")
public abstract class ModuleSpec implements Serializable {

    private final String identifier;
    @Nullable
    private final String classifier; // optional
    private final String moduleName;
    private final List<String> removedPackages = new ArrayList<>();
    private final List<String> mergedJars = new ArrayList<>();

    boolean overrideModuleName;

    protected ModuleSpec(String identifier, String moduleName) {
        validateIdentifier(identifier);
        validateModuleName(moduleName);
        if (identifier.contains("|")) {
            this.identifier = identifier.split("\\|")[0];
            this.classifier = identifier.split("\\|")[1];
        } else {
            this.identifier = identifier;
            this.classifier = null;
        }
        this.moduleName = moduleName;
    }

    /**
     * @return group:name coordinates _or_ Jar file name
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * @return classifier, as an addition to group:name coordinates, if defined
     */
    @Nullable
    public String getClassifier() {
        return classifier;
    }

    /**
     * @return Module Name of the Module to construct
     */
    public String getModuleName() {
        return moduleName;
    }

    /**
     * @param packageName a package to remove from the Jar because it is a duplicate
     */
    public void removePackage(String packageName) {
        removedPackages.add(packageName);
    }

    /**
     * @return packages that are removed by the transform
     */
    public List<String> getRemovedPackages() {
        return removedPackages;
    }

    /**
     * @param identifier group:name coordinates _or_ Jar file name (of the Jar file to merge)
     */
    public void mergeJar(String identifier) {
        mergedJars.add(identifier);
    }

    /**
     * @param alias group:name coordinates alias from version catalog
     */
    public void mergeJar(Provider<MinimalExternalModuleDependency> alias) {
        mergeJar(alias.get().getModule().toString());
    }

    /**
     * @return all merged Jar identifiers
     */
    public List<String> getMergedJars() {
        return mergedJars;
    }

    /**
     * If the Module already has an Automatic-Module-Name, allow changing that name
     */
    public void overrideModuleName() {
        this.overrideModuleName = true;
    }
}

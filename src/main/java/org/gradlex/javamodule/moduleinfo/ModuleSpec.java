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
    private final String moduleName;
    private final List<String> mergedJars = new ArrayList<>();

    protected ModuleSpec(String identifier, String moduleName) {
        validateIdentifier(identifier);
        validateModuleName(moduleName);
        this.identifier = identifier;
        this.moduleName = moduleName;
    }

    /**
     * @return group:name coordinates _or_ Jar file name
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * @return Module Name of the Module to construct
     */
    public String getModuleName() {
        return moduleName;
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
}

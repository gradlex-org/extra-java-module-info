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

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Data class to hold the information that should be added as module-info.class to an existing Jar file.
 */
@SuppressWarnings("unused")
public class ModuleInfo extends ModuleSpec {

    private final String moduleVersion;

    final Set<String> exports = new LinkedHashSet<>();
    final Set<String> requires = new LinkedHashSet<>();
    final Set<String> requiresTransitive = new LinkedHashSet<>();
    final Set<String> requiresStatic = new LinkedHashSet<>();
    final Set<String> ignoreServiceProviders = new LinkedHashSet<>();

    boolean exportAllPackages;
    boolean requireAllDefinedDependencies;

    ModuleInfo(String identifier, String moduleName, String moduleVersion) {
        super(identifier, moduleName);
        this.moduleVersion = moduleVersion;
    }

    /**
     * @param exports corresponds to the directive in a 'module-info.java' file
     */
    public void exports(String exports) {
        addOrThrow(this.exports, exports);
    }

    /**
     * @param requires corresponds to the directive in a 'module-info.java' file
     */
    public void requires(String requires) {
        addOrThrow(this.requires, requires);
    }

    /**
     * @param requiresTransitive corresponds to the directive in a 'module-info.java' file
     */
    public void requiresTransitive(String requiresTransitive) {
        addOrThrow(this.requiresTransitive, requiresTransitive);
    }

    /**
     * @param requiresStatic corresponds to the directive in a 'module-info.java' file
     */
    public void requiresStatic(String requiresStatic) {
        addOrThrow(this.requiresStatic, requiresStatic);
    }

    /**
     * @param ignoreServiceProvider do not transfer service provider to the 'module-info.class'
     */
    public void ignoreServiceProvider(String ignoreServiceProvider) {
        addOrThrow(this.ignoreServiceProviders, ignoreServiceProvider);
    }

    /**
     * @return configured version of the Module
     */
    public String getModuleVersion() {
        return moduleVersion;
    }

    private static void addOrThrow(Set<String> target, String element) {
        if (!target.add(element)) {
            throw new IllegalArgumentException("The element '" + element + "' is already specified");
        }
    }

    /**
     * Automatically export all packages of the Jar. Can be used instead of individual 'exports()' statements.
     */
    public void exportAllPackages() {
        this.exportAllPackages = true;
    }

    /**
     * Automatically add 'requires' statements for all dependencies defined in the metadata of the component.
     */
    public void requireAllDefinedDependencies() {
        this.requireAllDefinedDependencies = true;
    }
}

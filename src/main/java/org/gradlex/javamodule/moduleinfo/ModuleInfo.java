// SPDX-License-Identifier: Apache-2.0
package org.gradlex.javamodule.moduleinfo;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.gradle.api.model.ObjectFactory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Data class to hold the information that should be added as module-info.class to an existing Jar file.
 */
@NullMarked
public class ModuleInfo extends ModuleSpec {

    @Nullable
    private final String moduleVersion;

    boolean openModule = true;
    final Map<String, Set<String>> exports = new LinkedHashMap<>();
    final Map<String, Set<String>> opens = new LinkedHashMap<>();
    final Set<String> requires = new LinkedHashSet<>();
    final Set<String> requiresTransitive = new LinkedHashSet<>();
    final Set<String> requiresStatic = new LinkedHashSet<>();
    final Set<String> requiresStaticTransitive = new LinkedHashSet<>();
    final Map<String, Set<String>> ignoreServiceProviders = new LinkedHashMap<>();
    final Set<String> uses = new LinkedHashSet<>();

    boolean exportAllPackages;
    boolean requireAllDefinedDependencies;
    boolean patchRealModule;
    boolean preserveExisting;

    ModuleInfo(String identifier, String moduleName, @Nullable String moduleVersion, ObjectFactory objectFactory) {
        super(identifier, moduleName);
        this.moduleVersion = moduleVersion;
    }

    /**
     * Should this be a 'module' instead of an 'open module'?
     */
    public void closeModule() {
        openModule = false;
    }

    /**
     * Calling this method at least once automatically makes this a "closed" module: 'module' instead of 'open module'.
     *
     * @param opens corresponds to the directive in a 'module-info.java' file
     * @param to modules this package should be opened to.
     */
    public void opens(String opens, String... to) {
        closeModule();
        addOrThrow(this.opens, opens, to);
    }

    /**
     * @param exports corresponds to the directive in a 'module-info.java' file
     * @param to modules this package should be exported to.
     */
    public void exports(String exports, String... to) {
        addOrThrow(this.exports, exports, to);
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
     * @param requiresStaticTransitive corresponds to the directive in a 'module-info.java' file
     */
    public void requiresStaticTransitive(String requiresStaticTransitive) {
        addOrThrow(this.requiresStaticTransitive, requiresStaticTransitive);
    }

    /**
     * @param uses corresponds to the directive in a 'module-info.java' file
     */
    public void uses(String uses) {
        addOrThrow(this.uses, uses);
    }

    /**
     * @param provider do not transfer service provider to the 'module-info.class'
     * @param implementations the array of specific implementations to skip
     */
    public void ignoreServiceProvider(String provider, String... implementations) {
        addOrThrow(this.ignoreServiceProviders, provider, implementations);
    }

    /**
     * @return configured version of the Module
     */
    @Nullable
    public String getModuleVersion() {
        return moduleVersion;
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

    /**
     * Allow patching real (JARs with module-info.class) modules by overriding the existing module-info.class.
     */
    public void patchRealModule() {
        this.patchRealModule = true;
    }

    /**
     * Allow patching real (JARs with module-info.class) by extending the existing module-info.class.
     */
    public void preserveExisting() {
        this.patchRealModule = true;
        this.preserveExisting = true;
    }

    private static void addOrThrow(Set<String> target, String element) {
        if (!target.add(element)) {
            throw new IllegalArgumentException("The element '" + element + "' is already specified");
        }
    }

    private static void addOrThrow(Map<String, Set<String>> target, String key, String... elements) {
        if (target.put(key, new LinkedHashSet<>(Arrays.asList(elements))) != null) {
            throw new IllegalArgumentException("The element '" + key + "' is already specified");
        }
    }
}

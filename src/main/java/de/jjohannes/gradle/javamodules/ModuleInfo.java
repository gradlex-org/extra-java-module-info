package de.jjohannes.gradle.javamodules;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Data class to hold the information that should be added as module-info.class to an existing Jar file.
 */
public class ModuleInfo extends ModuleSpec {

    private final String moduleVersion;

    final Set<String> exports = new LinkedHashSet<>();
    final Set<String> requires = new LinkedHashSet<>();
    final Set<String> requiresTransitive = new LinkedHashSet<>();
    final Set<String> requiresStatic = new LinkedHashSet<>();
    final Set<String> ignoreServiceProviders = new LinkedHashSet<>();

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

}

package de.jjohannes.gradle.javamodules;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

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

    ModuleInfo(String jarName, String moduleName, String moduleVersion) {
        super(jarName, moduleName);
        this.moduleVersion = moduleVersion;
    }

    public String getModuleVersion() {
        return moduleVersion;
    }

    protected Set<String> getExports() {
        return exports;
    }

    protected Set<String> getRequires() {
        return requires;
    }

    protected Set<String> getRequiresTransitive() {
        return requiresTransitive;
    }

    public void exports(String exports) {
        addOrThrow(this.exports, exports);
    }

    public void requires(String requires) {
        addOrThrow(this.requires, requires);
    }

    public void requiresTransitive(String requiresTransitive) {
        addOrThrow(this.requiresTransitive, requiresTransitive);
    }

    public void requiresStatic(String requiresStatic) {
        addOrThrow(this.requiresStatic, requiresStatic);
    }

    public void ignoreServiceProvider(String ignoreServiceProvider) {
        addOrThrow(this.ignoreServiceProviders, ignoreServiceProvider);
    }

    private static void addOrThrow(Set<String> target, String element) {
        if (!target.add(element)) {
            throw new IllegalArgumentException("The element '" + element + "' is already specified");
        }
    }

}

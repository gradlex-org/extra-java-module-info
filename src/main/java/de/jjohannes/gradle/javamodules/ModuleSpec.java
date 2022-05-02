package de.jjohannes.gradle.javamodules;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Details that real Modules and Automatic-Module-Names share.
 */
@SuppressWarnings("unused")
abstract public class ModuleSpec implements Serializable {

    private final String identifier;
    private final String moduleName;
    private final List<String> mergedJars = new ArrayList<>();

    ModuleSpec(String identifier, String moduleName) {
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
     * @return all merged Jar identifiers
     */
    public List<String> getMergedJars() {
        return mergedJars;
    }
}

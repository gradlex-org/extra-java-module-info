package de.jjohannes.gradle.javamodules;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

abstract public class ModuleSpec implements Serializable {

    private final String identifier;
    private final String moduleName;
    private final List<String> mergedJars = new ArrayList<>();

    public ModuleSpec(String identifier, String moduleName) {
        this.identifier = identifier;
        this.moduleName = moduleName;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void mergeJar(String identifier) {
        mergedJars.add(identifier);
    }

    public List<String> getMergedJars() {
        return mergedJars;
    }
}

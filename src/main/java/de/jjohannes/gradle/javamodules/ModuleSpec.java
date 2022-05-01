package de.jjohannes.gradle.javamodules;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

abstract public class ModuleSpec implements Serializable {

    private final String jarName;
    private final String moduleName;
    private final List<String> mergedJars = new ArrayList<>();

    public ModuleSpec(String jarName, String moduleName) {
        this.jarName = jarName;
        this.moduleName = moduleName;
    }

    public String getJarName() {
        return jarName;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void mergeJar(String jar) {
        mergedJars.add(jar);
    }

    public List<String> getMergedJars() {
        return mergedJars;
    }
}

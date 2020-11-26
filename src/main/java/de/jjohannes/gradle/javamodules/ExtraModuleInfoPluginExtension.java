package de.jjohannes.gradle.javamodules;

import org.gradle.api.Action;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

import javax.annotation.Nullable;

/**
 * A data class to collect all the module information we want to add.
 * Here the class is used as extension that can be configured in the build script
 * and as input to the ExtraModuleInfoTransform that add the information to Jars.
 */
public abstract class ExtraModuleInfoPluginExtension {

    abstract public MapProperty<String, ModuleInfo> getModuleInfo();
    abstract public MapProperty<String, AutomaticModuleName> getAutomaticModules();
    abstract public Property<Boolean> getFailOnMissingModuleInfo();

    /**
     * Add full module information for a given Jar file.
     */
    public void module(String jarName, String moduleName, String moduleVersion) {
        module(jarName, moduleName, moduleVersion, null);
    }

    /**
     * Add full module information, including exported packages and dependencies, for a given Jar file.
     */
    public void module(String jarName, String moduleName, String moduleVersion, @Nullable Action<? super ModuleInfo> conf) {
        ModuleInfo moduleInfo = new ModuleInfo(jarName, moduleName, moduleVersion);
        if (conf != null) {
            conf.execute(moduleInfo);
        }
        this.getModuleInfo().put(jarName, moduleInfo);
    }

    /**
     * Add only an automatic module name to a given jar file.
     */
    public void automaticModule(String jarName, String moduleName) {
        automaticModule(jarName, moduleName, null);
    }

    /**
     * Add only an automatic module name to a given jar file.
     */
    public void automaticModule(String jarName, String moduleName, @Nullable Action<? super ModuleSpec> conf) {
        AutomaticModuleName automaticModuleName = new AutomaticModuleName(jarName, moduleName);
        if (conf != null) {
            conf.execute(automaticModuleName);
        }
        getAutomaticModules().put(jarName, automaticModuleName);
    }
}

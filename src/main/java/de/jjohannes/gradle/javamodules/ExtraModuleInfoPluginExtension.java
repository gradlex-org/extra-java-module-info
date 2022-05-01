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

    abstract public MapProperty<String, ModuleSpec> getModuleSpecs();
    abstract public Property<Boolean> getFailOnMissingModuleInfo();

    /**
     * Add full module information for a given Jar file.
     */
    public void module(String identifier, String moduleName, String moduleVersion) {
        module(identifier, moduleName, moduleVersion, null);
    }

    /**
     * Add full module information, including exported packages and dependencies, for a given Jar file.
     */
    public void module(String identifier, String moduleName, String moduleVersion, @Nullable Action<? super ModuleInfo> conf) {
        ModuleInfo moduleInfo = new ModuleInfo(identifier, moduleName, moduleVersion);
        if (conf != null) {
            conf.execute(moduleInfo);
        }
        this.getModuleSpecs().put(identifier, moduleInfo);
    }

    /**
     * Add only an automatic module name to a given jar file.
     */
    public void automaticModule(String identifier, String moduleName) {
        automaticModule(identifier, moduleName, null);
    }

    /**
     * Add only an automatic module name to a given jar file.
     */
    public void automaticModule(String identifier, String moduleName, @Nullable Action<? super ModuleSpec> conf) {
        AutomaticModuleName automaticModuleName = new AutomaticModuleName(identifier, moduleName);
        if (conf != null) {
            conf.execute(automaticModuleName);
        }
        getModuleSpecs().put(identifier, automaticModuleName);
    }
}

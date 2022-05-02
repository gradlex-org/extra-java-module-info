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
@SuppressWarnings("unused")
public abstract class ExtraModuleInfoPluginExtension {

    abstract public MapProperty<String, ModuleSpec> getModuleSpecs();
    abstract public Property<Boolean> getFailOnMissingModuleInfo();

    /**
     * Add full module information for a given Jar file.
     *
     * @param identifier group:name coordinates _or_ Jar file name
     * @param moduleName the Module Name of the Module to construct
     */
    public void module(String identifier, String moduleName) {
        module(identifier, moduleName, null, null);
    }

    /**
     * Add full module information for a given Jar file.
     *
     * @param identifier group:name coordinates _or_ Jar file name
     * @param moduleName the Module Name of the Module to construct
    *  @param moduleVersion version to write into the module-info.class
     */
    public void module(String identifier, String moduleName, String moduleVersion) {
        module(identifier, moduleName, moduleVersion, null);
    }

    /**
     * Add full module information for a given Jar file.
     *
     * @param identifier group:name coordinates _or_ Jar file name
     * @param moduleName the Module Name of the Module to construct
     * @param conf configure exported packages and dependencies, see {@link ModuleInfo}
     */
    public void module(String identifier, String moduleName, @Nullable Action<? super ModuleInfo> conf) {
        module(identifier, moduleName, null, conf);
    }

    /**
     * Add full module information for a given Jar file.
     *
     * @param identifier group:name coordinates _or_ Jar file name
     * @param moduleName the Module Name of the Module to construct
     * @param moduleVersion version to write into the module-info.class
     * @param conf configure exported packages, dependencies and Jar merging, see {@link ModuleInfo}
     */
    public void module(String identifier, String moduleName, @Nullable String moduleVersion, @Nullable Action<? super ModuleInfo> conf) {
        ModuleInfo moduleInfo = new ModuleInfo(identifier, moduleName, moduleVersion);
        if (conf != null) {
            conf.execute(moduleInfo);
        }
        this.getModuleSpecs().put(identifier, moduleInfo);
    }

    /**
     * Add an Automatic-Module-Name to a given Jar file.
     *
     * @param identifier group:name coordinates _or_ Jar file name
     * @param moduleName the Module Name of the Module to construct
     */
    public void automaticModule(String identifier, String moduleName) {
        automaticModule(identifier, moduleName, null);
    }

    /**
     * Add an Automatic-Module-Name to a given Jar file.
     *
     * @param identifier group:name coordinates _or_ Jar file name
     * @param moduleName the Module Name of the Module to construct
     * @param conf configure Jar merging, see {@link AutomaticModuleName}
     */
    public void automaticModule(String identifier, String moduleName, @Nullable Action<? super AutomaticModuleName> conf) {
        AutomaticModuleName automaticModuleName = new AutomaticModuleName(identifier, moduleName);
        if (conf != null) {
            conf.execute(automaticModuleName);
        }
        getModuleSpecs().put(identifier, automaticModuleName);
    }
}

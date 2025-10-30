// SPDX-License-Identifier: Apache-2.0
package org.gradlex.javamodule.moduleinfo;

import javax.annotation.Nullable;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;

/**
 * A data class to collect all the module information we want to add.
 * Here the class is used as extension that can be configured in the build script
 * and as input to the ExtraModuleInfoTransform that add the information to Jars.
 */
@SuppressWarnings("unused")
public abstract class ExtraJavaModuleInfoPluginExtension {
    static Attribute<Boolean> JAVA_MODULE_ATTRIBUTE = Attribute.of("javaModule", Boolean.class);

    @Inject
    protected abstract ObjectFactory getObjects();

    @Inject
    protected abstract ConfigurationContainer getConfigurations();

    public abstract MapProperty<String, ModuleSpec> getModuleSpecs();

    public abstract Property<Boolean> getFailOnMissingModuleInfo();

    public abstract Property<Boolean> getFailOnAutomaticModules();

    public abstract Property<Boolean> getFailOnModifiedDerivedModuleNames();

    public abstract Property<Boolean> getSkipLocalJars();

    public abstract Property<Boolean> getDeriveAutomaticModuleNamesFromFileNames();

    public abstract Property<String> getVersionsProvidingConfiguration();

    /**
     * Add full module information for a given Jar file.
     *
     * @param identifier group:name coordinates _or_ Jar file name
     * @param moduleName the Module Name of the Module to construct
     */
    public void module(String identifier, String moduleName) {
        module(identifier, moduleName, (String) null);
    }

    /**
     * Add full module information for a given Jar file.
     *
     * @param alias group:name coordinates alias from version catalog
     * @param moduleName the Module Name of the Module to construct
     */
    public void module(Provider<MinimalExternalModuleDependency> alias, String moduleName) {
        module(alias.get().getModule().toString(), moduleName);
    }

    /**
     * Add full module information for a given Jar file.
     *
     * @param identifier group:name coordinates _or_ Jar file name
     * @param moduleName the Module Name of the Module to construct
     * @param moduleVersion version to write into the module-info.class
     */
    public void module(String identifier, String moduleName, @Nullable String moduleVersion) {
        module(identifier, moduleName, moduleVersion, m -> {
            m.exportAllPackages();
            if (identifier.contains(":")) { // only if the identifier is a coordinates (not a Jar)
                m.requireAllDefinedDependencies();
            }
        });
    }

    /**
     * Add full module information for a given Jar file.
     *
     * @param alias group:name coordinates alias from version catalog
     * @param moduleName the Module Name of the Module to construct
     * @param moduleVersion version to write into the module-info.class
     */
    public void module(
            Provider<MinimalExternalModuleDependency> alias, String moduleName, @Nullable String moduleVersion) {
        module(alias.get().getModule().toString(), moduleName, moduleVersion);
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
     * @param alias group:name coordinates alias from version catalog
     * @param moduleName the Module Name of the Module to construct
     * @param conf configure exported packages and dependencies, see {@link ModuleInfo}
     */
    public void module(
            Provider<MinimalExternalModuleDependency> alias,
            String moduleName,
            @Nullable Action<? super ModuleInfo> conf) {
        module(alias.get().getModule().toString(), moduleName, conf);
    }

    /**
     * Add full module information for a given Jar file.
     *
     * @param identifier group:name coordinates _or_ Jar file name
     * @param moduleName the Module Name of the Module to construct
     * @param moduleVersion version to write into the module-info.class
     * @param conf configure exported packages, dependencies and Jar merging, see {@link ModuleInfo}
     */
    public void module(
            String identifier,
            String moduleName,
            @Nullable String moduleVersion,
            @Nullable Action<? super ModuleInfo> conf) {
        ModuleInfo moduleInfo = new ModuleInfo(identifier, moduleName, moduleVersion, getObjects());
        if (conf != null) {
            conf.execute(moduleInfo);
        }
        this.getModuleSpecs().put(identifier, moduleInfo);
    }

    /**
     * Add full module information for a given Jar file.
     *
     * @param alias group:name coordinates alias from version catalog
     * @param moduleName the Module Name of the Module to construct
     * @param moduleVersion version to write into the module-info.class
     * @param conf configure exported packages, dependencies and Jar merging, see {@link ModuleInfo}
     */
    public void module(
            Provider<MinimalExternalModuleDependency> alias,
            String moduleName,
            @Nullable String moduleVersion,
            @Nullable Action<? super ModuleInfo> conf) {
        module(alias.get().getModule().toString(), moduleName, moduleVersion, conf);
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
     * @param alias group:name coordinates alias from version catalog
     * @param moduleName the Module Name of the Module to construct
     */
    public void automaticModule(Provider<MinimalExternalModuleDependency> alias, String moduleName) {
        automaticModule(alias.get().getModule().toString(), moduleName, null);
    }

    /**
     * Add an Automatic-Module-Name to a given Jar file.
     *
     * @param identifier group:name coordinates _or_ Jar file name
     * @param moduleName the Module Name of the Module to construct
     * @param conf configure Jar merging, see {@link AutomaticModuleName}
     */
    public void automaticModule(
            String identifier, String moduleName, @Nullable Action<? super AutomaticModuleName> conf) {
        AutomaticModuleName automaticModuleName = new AutomaticModuleName(identifier, moduleName);
        if (conf != null) {
            conf.execute(automaticModuleName);
        }
        getModuleSpecs().put(identifier, automaticModuleName);
    }

    /**
     * Add an Automatic-Module-Name to a given Jar file.
     *
     * @param alias group:name coordinates alias from version catalog
     * @param moduleName the Module Name of the Module to construct
     * @param conf configure Jar merging, see {@link AutomaticModuleName}
     */
    public void automaticModule(
            Provider<MinimalExternalModuleDependency> alias,
            String moduleName,
            @Nullable Action<? super AutomaticModuleName> conf) {
        automaticModule(alias.get().getModule().toString(), moduleName, conf);
    }

    /**
     * Let the plugin know about an existing module on the module path.
     * This may be needed when 'requiresDirectivesFromMetadata(true)' is used.
     *
     * @param coordinates group:name coordinates
     * @param moduleName the Module Name of the Module referred to by the coordinates
     */
    public void knownModule(String coordinates, String moduleName) {
        getModuleSpecs().put(coordinates, new KnownModule(coordinates, moduleName));
    }

    /**
     * Let the plugin know about an existing module on the module path.
     * This may be needed when 'requiresDirectivesFromMetadata(true)' is used.
     *
     * @param alias group:name coordinates alias from version catalog
     * @param moduleName the Module Name of the Module referred to by the coordinates
     */
    public void knownModule(Provider<MinimalExternalModuleDependency> alias, String moduleName) {
        knownModule(alias.get().getModule().toString(), moduleName);
    }

    /**
     * Activate the plugin's functionality for dependencies of all scopes of the given source set
     * (runtimeClasspath, compileClasspath, annotationProcessor).
     * Note that the plugin activates the functionality for all source sets by default.
     * Therefore, this method only has an effect for source sets for which a {@link #deactivate(SourceSet)}
     * has been performed.
     *
     * @param sourceSet the Source Set to activate (e.g. sourceSets.test)
     */
    public void activate(SourceSet sourceSet) {
        NamedDomainObjectProvider<Configuration> runtimeClasspath =
                getConfigurations().named(sourceSet.getRuntimeClasspathConfigurationName());
        NamedDomainObjectProvider<Configuration> compileClasspath =
                getConfigurations().named(sourceSet.getCompileClasspathConfigurationName());
        NamedDomainObjectProvider<Configuration> annotationProcessor =
                getConfigurations().named(sourceSet.getAnnotationProcessorConfigurationName());

        activate(runtimeClasspath);
        activate(compileClasspath);
        activate(annotationProcessor);
    }

    /**
     * Activate the plugin's functionality for a single resolvable Configuration.
     * This is useful to use the plugins for scopes that are not tied to a Source Set,
     * for which the plugin does not activate automatically.
     *
     * @param resolvable a resolvable Configuration (e.g. configurations["customClasspath"])
     */
    public void activate(Configuration resolvable) {
        resolvable.getAttributes().attribute(JAVA_MODULE_ATTRIBUTE, true);
    }

    /**
     * Variant of {@link #activate(SourceSet)} and {@link #activate(Configuration)} that accepts either a
     * Provider of {@link SourceSet} or a Provider of {@link Configuration}. This is a convenience to use
     * notations like 'activate(sourceSets.main)' in Kotlin DSL.
     *
     * @param sourceSetOrResolvable the Source Set or Configuration to activate
     */
    public void activate(NamedDomainObjectProvider<?> sourceSetOrResolvable) {
        Object realized = sourceSetOrResolvable.get();
        if (realized instanceof SourceSet) {
            activate((SourceSet) realized);
        } else if (realized instanceof Configuration) {
            activate((Configuration) realized);
        } else {
            throw new RuntimeException("Not SourceSet or Configuration: " + realized);
        }
    }

    /**
     * Deactivate the plugin's functionality for dependencies of all scopes of the given source set
     * (runtimeClasspath, compileClasspath, annotationProcessor).
     *
     * @param sourceSet the Source Set to deactivate (e.g. sourceSets.test)
     */
    public void deactivate(SourceSet sourceSet) {
        NamedDomainObjectProvider<Configuration> runtimeClasspath =
                getConfigurations().named(sourceSet.getRuntimeClasspathConfigurationName());
        NamedDomainObjectProvider<Configuration> compileClasspath =
                getConfigurations().named(sourceSet.getCompileClasspathConfigurationName());
        NamedDomainObjectProvider<Configuration> annotationProcessor =
                getConfigurations().named(sourceSet.getAnnotationProcessorConfigurationName());

        deactivate(runtimeClasspath);
        deactivate(compileClasspath);
        deactivate(annotationProcessor);
    }

    /**
     * Deactivate the plugin's functionality for a single resolvable Configuration.
     * This is useful if selected scopes do not use the Module Path and therefore
     * module information is not required.
     *
     * @param resolvable a resolvable Configuration (e.g. configurations.annotationProcessor)
     */
    public void deactivate(Configuration resolvable) {
        resolvable.getAttributes().attribute(JAVA_MODULE_ATTRIBUTE, false);
    }

    /**
     * Variant of {@link #deactivate(SourceSet)} and {@link #deactivate(Configuration)} that accepts either a
     * Provider of {@link SourceSet} or a Provider of {@link Configuration}. This is a convenience to use
     * notations like 'deactivate(sourceSets.test)' in Kotlin DSL.
     *
     * @param sourceSetOrResolvable the Source Set or Configuration to activate
     */
    public void deactivate(NamedDomainObjectProvider<?> sourceSetOrResolvable) {
        Object realized = sourceSetOrResolvable.get();
        if (realized instanceof SourceSet) {
            deactivate((SourceSet) realized);
        } else if (realized instanceof Configuration) {
            deactivate((Configuration) realized);
        } else {
            throw new RuntimeException("Not SourceSet or Configuration: " + realized);
        }
    }
}

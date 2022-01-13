package de.jjohannes.gradle.javamodules;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.util.GradleVersion;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.util.VersionNumber;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Entry point of the plugin.
 */
@SuppressWarnings("unused")
public class ExtraModuleInfoPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        if (GradleVersion.current().compareTo(GradleVersion.version("6.4-rc-1")) < 0) {
            throw new RuntimeException("This plugin requires Gradle 6.4+");
        }

        // register the plugin extension as 'extraJavaModuleInfo {}' configuration block
        ExtraModuleInfoPluginExtension extension = project.getExtensions().create("extraJavaModuleInfo", ExtraModuleInfoPluginExtension.class);
        extension.getFailOnMissingModuleInfo().convention(true);

        // setup the transform for all projects in the build
        project.getPlugins().withType(JavaPlugin.class).configureEach(javaPlugin -> configureTransformAndMergeTask(project, extension));
    }

    private void configureTransformAndMergeTask(Project project, ExtraModuleInfoPluginExtension extension) {
        configureTransform(project, extension);
        configureMergeTask(project, extension);
    }

    private void configureTransform(Project project, ExtraModuleInfoPluginExtension extension) {
        Attribute<String> artifactType = Attribute.of("artifactType", String.class);
        Attribute<Boolean> javaModule = Attribute.of("javaModule", Boolean.class);

        // compile and runtime classpath express that they only accept modules by requesting the javaModule=true attribute
        project.getConfigurations().matching(this::isResolvingJavaPluginConfiguration).all(
                c -> c.getAttributes().attribute(javaModule, true));

        // all Jars have a javaModule=false attribute by default; the transform also recognizes modules and returns them without modification
        project.getDependencies().getArtifactTypes().getByName("jar").getAttributes().attribute(javaModule, false);

        // register the transform for Jars and "javaModule=false -> javaModule=true"; the plugin extension object fills the input parameter
        project.getDependencies().registerTransform(ExtraModuleInfoTransform.class, t -> {
            t.parameters(p -> {
                p.getModuleInfo().set(extension.getModuleInfo());
                p.getAutomaticModules().set(extension.getAutomaticModules());
                p.getFailOnMissingModuleInfo().set(extension.getFailOnMissingModuleInfo());
            });
            t.getFrom().attribute(artifactType, "jar").attribute(javaModule, false);
            t.getTo().attribute(artifactType, "jar").attribute(javaModule, true);
        });
    }

    private boolean isResolvingJavaPluginConfiguration(Configuration configuration) {
        if (!configuration.isCanBeResolved()) {
            return false;
        }
        return configuration.getName().endsWith(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME.substring(1))
                || configuration.getName().endsWith(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME.substring(1))
                || configuration.getName().endsWith(JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME.substring(1));
    }

    private void configureMergeTask(Project project, ExtraModuleInfoPluginExtension extension) {
        project.getTasks().withType(JavaCompile.class).configureEach(javaCompile -> {
            Collection<ModuleInfo> modules = extension.getModuleInfo().get().values();
            Collection<AutomaticModuleName> automaticModules = extension.getAutomaticModules().get().values();
            if (modules.stream().anyMatch(m -> !m.getMergedJars().isEmpty()) ||
                    automaticModules.stream().anyMatch(m -> !m.getMergedJars().isEmpty())) {
                addMergeAction(project, javaCompile, modules, automaticModules);
            }
        });
    }

    private void addMergeAction(Project project, JavaCompile javaCompile, Collection<ModuleInfo> modules, Collection<AutomaticModuleName> automaticModules) {
        javaCompile.doFirst(new MergeModuleAction(
                Stream.concat(modules.stream(), automaticModules.stream()).filter(
                        v -> !v.getMergedJars().isEmpty()).collect(Collectors.toList()),
                project.getLayout().getBuildDirectory().dir("merged-jars/" + javaCompile.getName()).get().getAsFile(),
                project.getObjects()
        ));
    }
}

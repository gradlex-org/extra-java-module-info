package de.jjohannes.gradle.javamodules;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Usage;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.util.GradleVersion;

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
        project.getPlugins().withType(JavaPlugin.class).configureEach(javaPlugin -> configureTransform(project, extension));
    }

    private void configureTransform(Project project, ExtraModuleInfoPluginExtension extension) {
        Configuration javaModulesMergeJars = project.getConfigurations().create("javaModulesMergeJars", c -> {
            c.setVisible(false);
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
            c.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            c.getAttributes().attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
        });

        Attribute<String> artifactType = Attribute.of("artifactType", String.class);
        Attribute<Boolean> javaModule = Attribute.of("javaModule", Boolean.class);

        project.getExtensions().getByType(SourceSetContainer.class).all(sourceSet -> {
            Configuration runtimeClasspath = project.getConfigurations().getByName(sourceSet.getRuntimeClasspathConfigurationName());
            Configuration compileClasspath = project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName());
            Configuration annotationProcessor = project.getConfigurations().getByName(sourceSet.getAnnotationProcessorConfigurationName());

            // compile, runtime and annotation processor classpaths express that they only accept modules by requesting the javaModule=true attribute
            runtimeClasspath.getAttributes().attribute(javaModule, true);
            compileClasspath.getAttributes().attribute(javaModule, true);
            annotationProcessor.getAttributes().attribute(javaModule, true);
        });

        // all Jars have a javaModule=false attribute by default; the transform also recognizes modules and returns them without modification
        project.getDependencies().getArtifactTypes().getByName("jar").getAttributes().attribute(javaModule, false);

        // register the transform for Jars and "javaModule=false -> javaModule=true"; the plugin extension object fills the input parameter
        project.getDependencies().registerTransform(ExtraModuleInfoTransform.class, t -> {
            t.parameters(p -> {
                p.getModuleSpecs().set(extension.getModuleSpecs());
                p.getFailOnMissingModuleInfo().set(extension.getFailOnMissingModuleInfo());
                p.getMergeJars().from(javaModulesMergeJars);
            });
            t.getFrom().attribute(artifactType, "jar").attribute(javaModule, false);
            t.getTo().attribute(artifactType, "jar").attribute(javaModule, true);
        });
    }
}

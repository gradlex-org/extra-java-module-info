package de.jjohannes.gradle.javamodules.test

import de.jjohannes.gradle.javamodules.test.fixture.GradleBuild
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification

import java.util.jar.JarFile

class CombinationWithOtherPluginsFunctionalTest extends Specification {

    @Delegate
    GradleBuild build = new GradleBuild()

    def setup() {
        settingsFile << 'rootProject.name = "test-project"'
    }

    def "works in combination with shadow plugin"() {
        def shadowJar = file("app/build/libs/app-all.jar")

        given:
        settingsFile << """
            include("app", "utilities")
        """
        file("utilities/src/main/java/module-info.java") << """
            module utilities { }
        """
        file("utilities/src/main/java/Utility.java") << """
            public class Utility { }
        """
        file("utilities/build.gradle") << """
            plugins {
                id 'java-library'
            }
            java.modularity.inferModulePath = true
        """
        file("app/src/main/java/module-info.java") << """
            module app { }
        """
        file("app/src/main/java/App.java") << """
            public class App { }
        """
        file("app/build.gradle") << """
            plugins {
                id 'application'
                id 'de.jjohannes.extra-java-module-info'
                id 'com.github.johnrengelman.shadow' version '6.1.0'
            }
            dependencies {
                implementation project(':utilities')
            }
            java.modularity.inferModulePath = true
            application.mainClassName = 'App'
            configurations {
                runtimeClasspath {
                    attributes { attribute(Attribute.of("javaModule", Boolean), false) }
                }
            }
        """

        expect:
        runner(':app:shadowJar').build().task(':app:shadowJar').outcome == TaskOutcome.SUCCESS
        shadowJar.exists()
        // 1 * module-info, 2 * class file, 1 * META-INF folder, 1 * MANIFEST
        new JarFile(shadowJar).entries().asIterator().size() == 5
    }

}

package org.gradlex.javamodule.moduleinfo.test

import org.gradlex.javamodule.moduleinfo.test.fixture.GradleBuild
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.IgnoreIf
import spock.lang.Specification

import java.util.jar.JarFile

class CombinationWithOtherPluginsFunctionalTest extends Specification {

    @Delegate
    GradleBuild build = new GradleBuild()

    def setup() {
        settingsFile << 'rootProject.name = "test-project"'
    }

    def "mergeJar uses versions configured through jvm-dependency-conflict-resolution plugin"() {
        given:
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            public class Main { 
                public static void main(String[] args) {
                    Class<?> loggerFromApi = org.slf4j.Logger.class;
                    Class<?> ndcFromExt = org.slf4j.NDC.class;
                }
            }
        """
        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app {
                requires org.slf4j;
            }
        """
        settingsFile << """
            include("versions")
        """
        file('versions/build.gradle.kts') << """
            plugins { id("java-platform") }
            dependencies.constraints {
                api("org.slf4j:slf4j-api:1.7.32")
                api("org.slf4j:slf4j-ext:1.7.32")
            }
        """
        buildFile << """
            plugins {
                id("application")
                id("org.gradlex.extra-java-module-info")
                id("org.gradlex.jvm-dependency-conflict-resolution") version "2.1.2"
            }
            application.mainClass.set("org.gradle.sample.app.Main")
            dependencies {
                implementation("org.slf4j:slf4j-api")
            }
            jvmDependencyConflicts {            
                consistentResolution {
                    providesVersions(":")
                    platform(":versions")
                }
            }
            extraJavaModuleInfo {
                automaticModule("org.slf4j:slf4j-api", "org.slf4j") {
                    mergeJar("org.slf4j:slf4j-ext")
                }
            }
            tasks.named("run") {
                inputs.files(configurations.runtimeClasspath)
                doLast { println(inputs.files.map { it.name }) }
            }
        """

        when:
        def result = run()

        then:
        result.task(":run").outcome == TaskOutcome.SUCCESS
        result.output.contains('slf4j-api-1.7.32-module.jar')
        !result.output.contains('slf4j-api-1.7.32.jar')
        !result.output.contains('slf4j-ext-1.7.32.jar')
    }

    @IgnoreIf({ !GradleBuild.gradleVersionUnderTest?.startsWith('7.') })
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
                id 'org.gradlex.extra-java-module-info'
                id 'com.github.johnrengelman.shadow' version '7.1.2'
            }
            dependencies {
                implementation project(':utilities')
            }
            java.modularity.inferModulePath = true
            application.mainClass = 'App'
            configurations {
                runtimeClasspath {
                    attributes { attribute(Attribute.of("javaModule", Boolean), false) }
                }
            }
        """

        expect:
        runner(':app:shadowJar').build().task(':app:shadowJar').outcome == TaskOutcome.SUCCESS
        shadowJar.exists()
        // 4 Entries = 2 * class file, 1 * META-INF folder, 1 * MANIFEST
        // module-info.class is excluded (see: https://github.com/johnrengelman/shadow/issues/352)
        new JarFile(shadowJar).entries().asIterator().size() == 4
    }

}

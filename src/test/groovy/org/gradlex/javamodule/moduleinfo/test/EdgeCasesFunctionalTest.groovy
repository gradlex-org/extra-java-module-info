package org.gradlex.javamodule.moduleinfo.test

import org.gradle.testkit.runner.TaskOutcome
import org.gradlex.javamodule.moduleinfo.test.fixture.GradleBuild
import org.gradlex.javamodule.moduleinfo.test.fixture.LegacyLibraries
import spock.lang.Specification

import static org.gradlex.javamodule.moduleinfo.test.fixture.GradleBuild.gradleVersionUnderTest

class EdgeCasesFunctionalTest extends Specification {

    @Delegate
    GradleBuild build = new GradleBuild()

    LegacyLibraries libs = new LegacyLibraries(false)

    def setup() {
        settingsFile << 'rootProject.name = "test-project"'
        buildFile << '''
            plugins {
                id("application")
                id("org.gradlex.extra-java-module-info")
            }
            application {
                mainModule.set("org.gradle.sample.app")
                mainClass.set("org.gradle.sample.app.Main")
            }
        '''
    }

    def "does not fail if an unused Jar on the merge path cannot be resolved"() {
        given:
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            public class Main {
                public static void main(String[] args) {
                }
            }
        """
        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app {
                requires org.slf4j;
            }
        """
        buildFile << """                  
            dependencies {
                implementation("org.slf4j:slf4j-api:2.0.3")
            }
            
            extraJavaModuleInfo {
                failOnMissingModuleInfo.set(false)
                module(${libs.zookeeper}, "org.apache.zookeeper") {
                    mergeJar(${libs.zookeeperJute})

                    exports("org.apache.jute")
                    exports("org.apache.zookeeper")
                    exports("org.apache.zookeeper.server.persistence")
                }
            }
        """

        expect:
        run()
    }

    def "does fully merge zip files on the classpath"() {
        given:
        buildFile << """             
            ${gradleVersionUnderTest == "6.4.1"? 'configurations.javaModulesMergeJars.get().extendsFrom(configurations.implementation.get())' : '' }
            dependencies {
                implementation("org.apache.qpid:qpid-broker-core:9.0.0")
                implementation("org.apache.qpid:qpid-broker-plugins-management-http:9.0.0")
            }
            
            extraJavaModuleInfo {
                failOnMissingModuleInfo.set(false)
                automaticModule("org.apache.qpid:qpid-broker-core", "org.apache.qpid.broker") {
                    mergeJar("org.apache.qpid:qpid-broker-plugins-management-http")
                    mergeJar("org.dojotoolkit:dojo") // This is a Zip, selected by 'distribution' classifier in dependencies of 'qpid-broker-plugins-management-http'  
                    mergeJar("org.webjars.bower:dgrid")
                    mergeJar("org.webjars.bower:dstore")
                }
            }
            
            tasks.named("build") {
                doLast { println(configurations.runtimeClasspath.get().files.map { it.name }) }
            }
        """

        when:
        def result = build()

        then:
        result.output.contains('qpid-broker-core-9.0.0-module.jar')
        !result.output.contains('dgrid-1.3.3.jar')
        !result.output.contains('dojo-1.17.2-distribution.zip')
        !result.output.contains('dstore-1.1.2.jar')
        !result.output.contains('qpid-broker-plugins-management-http-9.0.0.jar')
    }

    def "can merge jars that are already modules"() {
        given:
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            public class Main {
                public static void main(String[] args) throws Exception {
                }
            }
        """
        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app {
                requires java.annotation;
            }
        """
        buildFile << """                  
            dependencies {
                implementation("com.google.code.findbugs:jsr305:3.0.2")
                implementation("javax.annotation:javax.annotation-api:1.3.2")
            }
            
            extraJavaModuleInfo {
                module("com.google.code.findbugs:jsr305", "java.annotation") {
                    mergeJar("javax.annotation:javax.annotation-api")
                    exports("javax.annotation")
                    exports("javax.annotation.concurrent")
                    exports("javax.annotation.meta")
                }
            }
            
            tasks.named("run") {
                doLast { println(configurations.runtimeClasspath.get().files.map { it.name }) }
            }
        """

        when:
        def result = run()

        then:
        result.output.contains('[jsr305-3.0.2-module.jar]')
    }

    def "can automatically export all packages of a multi-release legacy Jar"() {
        given:
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            import org.kohsuke.github.GHApp;
            
            public class Main {
                public static void main(String[] args) {
                    GHApp app = new GHApp();
                }
            }
        """
        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app {
                exports org.gradle.sample.app;
                
                requires org.kohsuke.github;
            }
        """
        buildFile << """          
            dependencies {
                implementation("org.kohsuke:github-api:1.317")
            }
            
            extraJavaModuleInfo {
                module("org.kohsuke:github-api", "org.kohsuke.github") {
                    exportAllPackages()
                    requires("org.apache.commons.lang3")
                }
            }
        """

        expect:
        run().task(':run').outcome == TaskOutcome.SUCCESS
    }
}

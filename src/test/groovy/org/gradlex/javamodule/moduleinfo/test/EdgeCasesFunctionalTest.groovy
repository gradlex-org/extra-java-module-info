package org.gradlex.javamodule.moduleinfo.test

import org.gradlex.javamodule.moduleinfo.test.fixture.GradleBuild
import org.gradlex.javamodule.moduleinfo.test.fixture.LegacyLibraries
import spock.lang.Specification

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

    def "doe not fail if an unused Jar on the merge path cannot be resolved"() {
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
                module("${libs.zookeeper}", "org.apache.zookeeper") {
                    mergeJar("${libs.zookeeperJute}")

                    exports("org.apache.jute")
                    exports("org.apache.zookeeper")
                    exports("org.apache.zookeeper.server.persistence")
                }
            }
        """

        expect:
        run()
    }
}

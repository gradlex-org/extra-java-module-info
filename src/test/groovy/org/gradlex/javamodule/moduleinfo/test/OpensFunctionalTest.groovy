package org.gradlex.javamodule.moduleinfo.test

import org.gradlex.javamodule.moduleinfo.test.fixture.GradleBuild
import org.gradlex.javamodule.moduleinfo.test.fixture.LegacyLibraries
import spock.lang.Specification

class OpensFunctionalTest extends Specification {

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
        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app {
                requires apache.commons.collections;
            }
        """
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            public class Main {
                public static void main(String[] args) throws ClassNotFoundException {
                    Class.forName("org.apache.commons.collections.buffer.BlockingBuffer").getDeclaredMethods()[0].setAccessible(true);
                    Class.forName("org.apache.commons.collections.bag.HashBag").getDeclaredMethods()[0].setAccessible(true);
                }
            }
        """
    }

    def "a module is open by default"() {
        given:
        buildFile << """     
            dependencies {
                implementation("commons-collections:commons-collections:3.2.2")
            }             
            extraJavaModuleInfo {
                module(${libs.commonsCollections}, "apache.commons.collections")
            }
        """

        expect:
        run()
    }

    def "a module can be closed"() {
        given:
        buildFile << """     
            dependencies {
                implementation("commons-collections:commons-collections:3.2.2")
            }             
            extraJavaModuleInfo {
                module(${libs.commonsCollections}, "apache.commons.collections") {
                    closeModule()
                }
            }
        """

        expect:
        def out = failRun()
        out.output.contains('module apache.commons.collections does not "exports org.apache.commons.collections.buffer" to module org.gradle.sample.app')
    }

    def "a module is closed once it has an open package"() {
        given:

        buildFile << """     
            dependencies {
                implementation("commons-collections:commons-collections:3.2.2")
            }             
            extraJavaModuleInfo {
                module(${libs.commonsCollections}, "apache.commons.collections") {
                    opens("org.apache.commons.collections.buffer")
                }
            }
        """

        expect:
        def out = failRun()
        out.output.contains('module apache.commons.collections does not "opens org.apache.commons.collections.bag" to module org.gradle.sample.app')
    }
}

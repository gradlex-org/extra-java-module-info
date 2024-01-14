package org.gradlex.javamodule.moduleinfo.test

import org.gradlex.javamodule.moduleinfo.test.fixture.GradleBuild
import org.gradlex.javamodule.moduleinfo.test.fixture.LegacyLibraries
import spock.lang.Specification

class ExportsFunctionalTest extends Specification {

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
                public static void main(String[] args) {                    
                    new org.apache.commons.collections.bag.HashBag();
                }
            }
        """
    }

    def "a package can be exported globally"() {
        given:
        buildFile << """     
            dependencies {
                implementation("commons-collections:commons-collections:3.2.2")
            }             
            extraJavaModuleInfo {
                module(${libs.commonsCollections}, "apache.commons.collections") {                    
                    exports("org.apache.commons.collections.bag")
                }
            }
        """

        expect:
        run()
    }

    def "a package can be exported to a specific module and only to this module"() {
        given:

        buildFile << """     
            dependencies {
                implementation("commons-collections:commons-collections:3.2.2")
            }             
            extraJavaModuleInfo {
                module(${libs.commonsCollections}, "apache.commons.collections") {
                    exports("org.apache.commons.collections.bag", "org.gradle.sample.lib")
                }
            }
        """

        expect:
        def out = failRun()
        out.output.contains('package org.apache.commons.collections.bag is declared in module apache.commons.collections, which does not export it to module org.gradle.sample.app')
    }

    def "a package can be exported to a specific module"() {
        given:

        buildFile << """     
            dependencies {
                implementation("commons-collections:commons-collections:3.2.2")
            }             
            extraJavaModuleInfo {
                module(${libs.commonsCollections}, "apache.commons.collections") {
                    exports("org.apache.commons.collections.bag", "org.gradle.sample.app")
                }
            }
        """

        expect:
        run()
    }

    def "a package can be exported to multiple modules"() {
        given:

        buildFile << """     
            dependencies {
                implementation("commons-collections:commons-collections:3.2.2")
            }             
            extraJavaModuleInfo {
                module(${libs.commonsCollections}, "apache.commons.collections") {
                    exports("org.apache.commons.collections.bag", "org.gradle.sample.lib", "org.gradle.sample.app")
                }
            }
        """

        expect:
        run()
    }

}

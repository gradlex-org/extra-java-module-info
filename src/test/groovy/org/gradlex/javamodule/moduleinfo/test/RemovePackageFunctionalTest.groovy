package org.gradlex.javamodule.moduleinfo.test

import org.gradlex.javamodule.moduleinfo.test.fixture.GradleBuild
import org.gradlex.javamodule.moduleinfo.test.fixture.LegacyLibraries
import spock.lang.Specification

class RemovePackageFunctionalTest extends Specification {

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
                mainModule.set("org.example.app")
                mainClass.set("org.example.app.Main")
            }
        '''
        file("src/main/java/module-info.java") << """
            module org.example.app {
                requires jdk.xml.dom;
                requires xerces;
            }
        """
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.example.app;
            public class Main {
                public static void main(String[] args) {                    
                    org.apache.xerces.util.DOMUtil util;
                }
            }
        """
    }

    def "can remove duplicated packages"() {
        given:
        buildFile << """     
            dependencies {
                implementation("xerces:xercesImpl:2.12.2") { isTransitive = false }
            }             
            extraJavaModuleInfo {
                module("xerces:xercesImpl", "xerces") {
                    removePackage("org.w3c.dom.html")
                    exportAllPackages()
                    requires("java.xml")
                }
            }
        """

        expect:
        run()
    }

}

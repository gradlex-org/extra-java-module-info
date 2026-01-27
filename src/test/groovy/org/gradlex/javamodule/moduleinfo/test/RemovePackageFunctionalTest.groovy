package org.gradlex.javamodule.moduleinfo.test

import org.gradlex.javamodule.moduleinfo.test.fixture.GradleBuild
import spock.lang.Specification

class RemovePackageFunctionalTest extends Specification {

    @Delegate
    GradleBuild build = new GradleBuild()

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
    }

    def "can remove duplicated packages"() {
        given:
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

    def "removes package from module-info if removePackage and preserveExisting are used together"() {
        given:
        file("src/main/java/module-info.java") << """
            module org.example.app {
                requires jakarta.el;
                requires org.apache.tomcat.embed.el;
            }
        """
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.example.app;
            public class Main {
                public static void main(String[] args) {          
                    jakarta.el.ELContext context; // from original jakarta.el-api module
                    org.apache.el.ValueExpressionLiteral exp; // from apache embedded module
                }
            }
        """

        buildFile << """     
            dependencies {
                implementation("jakarta.el:jakarta.el-api:5.0.1")
                implementation("org.apache.tomcat.embed:tomcat-embed-el:10.1.50")
            }       
            extraJavaModuleInfo {      
                module("org.apache.tomcat.embed:tomcat-embed-el", "org.apache.tomcat.embed.el") {
                    preserveExisting()
                    removePackage("jakarta.el")
                }
            }
        """

        expect:
        run()
    }

}

package org.gradlex.javamodule.moduleinfo.test

import org.gradlex.javamodule.moduleinfo.test.fixture.GradleBuild
import spock.lang.IgnoreIf
import spock.lang.Specification

class RealModuleJarPreservePatchingFunctionalTest extends Specification {

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
                mainModule.set("org.example")
                mainClass.set("org.example.Main")
            }
        '''
    }

    @IgnoreIf({ GradleBuild.gradleVersionUnderTest?.startsWith("6") }) // requires Gradle to support Java 17
    def "a real module cannot be extended via preserveExisting"() {
        given:
        buildFile << ''' 
            tasks.withType<JavaCompile>().configureEach {
                options.compilerArgs.add("-Xlint:all")
                options.compilerArgs.add("-Werror")
            }
            dependencies {
                implementation("org.apache.logging.log4j:log4j-api:2.24.3")
                
                // required because not declared in LOG4J metadata
                compileOnly("com.google.errorprone:error_prone_annotations:2.36.0")
                compileOnly("com.github.spotbugs:spotbugs-annotations:4.9.0")
                compileOnly("biz.aQute.bnd:biz.aQute.bnd.annotation:7.1.0")
                compileOnly("org.osgi:osgi.annotation:8.1.0") // this includes 'org.osgi.annotation.bundle' 
            }
            extraJavaModuleInfo {
                failOnMissingModuleInfo.set(false) // transitive dependencies of annotation libs

                module("org.apache.logging.log4j:log4j-api", "org.apache.logging.log4j") {
                    preserveExisting()
                    requiresStatic("com.google.errorprone.annotations")
                    requiresStatic("com.github.spotbugs.annotations")
                    requiresStatic("biz.aQute.bnd.annotation")
                    requiresStatic("org.osgi.annotation")
                }
                module("biz.aQute.bnd:biz.aQute.bnd.annotation", "biz.aQute.bnd.annotation") {
                    requiresStatic("org.osgi.annotation")
                    exportAllPackages()
                }
                module("org.osgi:osgi.annotation", "org.osgi.annotation") 
            }            
        '''
        file("src/main/java/module-info.java") << """
            module org.example {
                requires org.apache.logging.log4j;
            }
        """
        file("src/main/java/org/example/Main.java") << """
            package org.example;
            public class Main {
                org.apache.logging.log4j.message.ParameterizedMessage m; // needs errorprone
                org.apache.logging.log4j.status.StatusData d; // needs spotbugs
                org.apache.logging.log4j.util.SystemPropertiesPropertySource s; // needs aQute.bnd
                org.apache.logging.log4j.util.Activator a; // needs osgi
                
                public static void main(String[] args) {}
            }
        """

        expect:
        run()
    }

}

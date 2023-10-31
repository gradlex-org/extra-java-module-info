package org.gradlex.javamodule.moduleinfo.test

import org.gradlex.javamodule.moduleinfo.test.fixture.GradleBuild
import spock.lang.Specification

class AllowAutomaticModulesFunctionalTest extends Specification {

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
                mainModule.set("org.gradle.sample.app")
                mainClass.set("org.gradle.sample.app.Main")
            }
        '''
        file("src/main/java/module-info.java") << '''
            module org.gradle.sample.app {
                requires org.yaml.snakeyaml;
            }
        '''
        file("src/main/java/org/gradle/sample/app/Main.java") << '''
            package org.gradle.sample.app;

            import org.yaml.snakeyaml.Yaml;
            
            public class Main {
                public static void main(String[] args) {
                    System.out.println("Automatic: " + Yaml.class.getModule().getDescriptor().isAutomatic());
                }
            }
        '''
    }

    def "automatic modules are allowed by default"() {
        given:
        buildFile << ''' 
            dependencies {
                implementation("org.yaml:snakeyaml:1.33")
            }             
            extraJavaModuleInfo {
                
            }
        '''

        expect:
        def out = run()
        out.output.contains("Automatic: true")
    }

    def "automatic modules are not allowed when failOnAutomaticModules set to true"() {
        given:
        buildFile << ''' 
            dependencies {
                implementation("org.yaml:snakeyaml:1.33")
            }             
            extraJavaModuleInfo {
                failOnAutomaticModules.set(true)
            }
        '''

        expect:
        def out = failRun()
        out.output.contains("Found an automatic module: snakeyaml-1.33.jar")
    }

    def "automatic modules are allowed when failOnAutomaticModules set to true and there is a proper module override"() {
        given:
        buildFile << ''' 
            dependencies {
                implementation("org.yaml:snakeyaml:1.33")
            }             
            extraJavaModuleInfo {
                failOnAutomaticModules.set(true)
                module("org.yaml:snakeyaml", "org.yaml.snakeyaml") {
                    closeModule()
                    exports("org.yaml.snakeyaml")
                }
            }
        '''

        expect:
        def out = run()
        out.output.contains("Automatic: false")
    }

}

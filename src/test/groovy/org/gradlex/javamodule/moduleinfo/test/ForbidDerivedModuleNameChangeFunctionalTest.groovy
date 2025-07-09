package org.gradlex.javamodule.moduleinfo.test

import org.gradlex.javamodule.moduleinfo.test.fixture.GradleBuild
import spock.lang.Specification

class ForbidDerivedModuleNameChangeFunctionalTest extends Specification {

    @Delegate
    GradleBuild build = new GradleBuild()

    def setup() {
        settingsFile << 'rootProject.name = "test-project"'
        buildFile << '''
            plugins {                
                id("org.gradlex.extra-java-module-info")
                id("java-library")
            }
        '''
        file("src/main/java/module-info.java") << '''
            module org.gradle.sample.app {
                requires com.vladsch.flexmark.util.misc;
            }
        '''
    }

    def "fails for name change if failOnModifiedDerivedModuleNames=true"() {
        given:
        buildFile << ''' 
            dependencies {
                implementation("com.vladsch.flexmark:flexmark-util-misc:0.64.8")
            }             
            extraJavaModuleInfo {
                failOnModifiedDerivedModuleNames.set(true)
                module("com.vladsch.flexmark:flexmark-util-misc", "com.vladsch.flexmark.util.misc") {}
            }
        '''

        expect:
        def result = fail()
        result.output.contains("The name 'com.vladsch.flexmark.util.misc' is different than the name derived from the Jar file name 'flexmark.util.misc'; turn off 'failOnModifiedDerivedModuleNames' or explicitly allow override via 'overrideModuleName()'")
    }

    def "Allows name change if failOnModifiedDerivedModuleNames=fasle (default)"() {
        given:
        buildFile << ''' 
            dependencies {
                implementation("com.vladsch.flexmark:flexmark-util-misc:0.64.8")
            }             
            extraJavaModuleInfo {
                module("com.vladsch.flexmark:flexmark-util-misc", "com.vladsch.flexmark.util.misc") {}
            }
        '''

        expect:
        build()
    }

    def "Allows name change via overrideModuleName"() {
        given:
        buildFile << ''' 
            dependencies {
                implementation("com.vladsch.flexmark:flexmark-util-misc:0.64.8")
            }             
            extraJavaModuleInfo {
                failOnModifiedDerivedModuleNames.set(true)
                module("com.vladsch.flexmark:flexmark-util-misc", "com.vladsch.flexmark.util.misc") {
                    overrideModuleName()
                }
            }
        '''

        expect:
        build()
    }
}

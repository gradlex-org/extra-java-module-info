package org.gradlex.javamodule.moduleinfo.test

import org.gradlex.javamodule.moduleinfo.test.fixture.GradleBuild
import spock.lang.Specification

class IdValidationFunctionalTest extends Specification {

    @Delegate
    GradleBuild build = new GradleBuild()

    def setup() {
        settingsFile << 'rootProject.name = "test-project"'
        buildFile << '''
            plugins {
                id("application")
                id("org.gradlex.extra-java-module-info")
            }
        '''
    }

    def "fails for wrong coordinates"() {
        given:
        buildFile << """                  
            extraJavaModuleInfo {
                module("commons-logging:commons-logging:2.0", "apache.commons.logging") {
                    exportAllPackages()
                }
            }
        """

        expect:
        def out = fail()
        out.output.contains("'commons-logging:commons-logging:2.0' are not valid coordinates (group:name) / is not a valid file name (name-1.0.jar)")
    }

    def "fails for wrong file name"() {
        given:
        buildFile << """                  
            extraJavaModuleInfo {
                module("/dummy/some/my.jar", "apache.commons.logging")
            }
        """

        expect:
        def out = fail()
        out.output.contains("'/dummy/some/my.jar' are not valid coordinates (group:name) / is not a valid file name (name-1.0.jar)")
    }

    def "fails for wrong module name"() {
        given:
        buildFile << """            
            extraJavaModuleInfo {
                module("commons-logging:commons-logging", "apache.commons:logging")
            }
        """

        expect:
        def out = fail()
        out.output.contains("'apache.commons:logging' is not a valid Java Module name")
    }

}

package org.gradlex.javamodule.moduleinfo.test

import org.gradlex.javamodule.moduleinfo.test.fixture.GradleBuild
import spock.lang.Specification

class PluginActivationFunctionalTest extends Specification {

    @Delegate
    GradleBuild build = new GradleBuild()

    def setup() {
        settingsFile << 'rootProject.name = "test-project"'
        buildFile << '''
            plugins {
                id("java-library")
                id("org.gradlex.extra-java-module-info")
            }
            val customPath = configurations.create("customPath")
            dependencies {
                implementation("commons-cli:commons-cli:1.4")
                annotationProcessor("commons-cli:commons-cli:1.4")
                customPath("commons-cli:commons-cli:1.4")
            }
            extraJavaModuleInfo { module("commons-cli:commons-cli", "org.apache.commons.cli") }
            tasks.register("printRuntimeCP") {
                inputs.files(configurations.runtimeClasspath)
                doLast { print("RCP: "); println(inputs.files.files.map { it.name }) }
            }
            tasks.register("printCompileCP") {
                inputs.files(configurations.compileClasspath)
                doLast { print("CCP: "); println(inputs.files.files.map { it.name }) }
            }
            tasks.register("printAP") {
                inputs.files(configurations.annotationProcessor)
                doLast { print("AP:  "); println(inputs.files.files.map { it.name }) }
            }
            tasks.register("printCustom") {
                inputs.files(configurations["customPath"])
                doLast { print("CUS: "); println(inputs.files.files.map { it.name }) }
            }
        '''
    }

    def "plugin is a activated by default for all configurations of a source set"() {
        when:
        def result = task('printRuntimeCP', 'printCompileCP', 'printAP', '-q')

        then:
        result.output.contains('RCP: [commons-cli-1.4-module.jar]')
        result.output.contains('CCP: [commons-cli-1.4-module.jar]')
        result.output.contains('AP:  [commons-cli-1.4-module.jar]')
    }

    def "plugin can be deactivated for a source set"() {
        given:
        buildFile << 'extraJavaModuleInfo { deactivate(sourceSets.main) }'

        when:
        def result = task('printRuntimeCP', 'printCompileCP', 'printAP', '-q')

        then:
        result.output.contains('RCP: [commons-cli-1.4.jar]')
        result.output.contains('CCP: [commons-cli-1.4.jar]')
        result.output.contains('AP:  [commons-cli-1.4.jar]')
    }

    def "plugin can be deactivated and later re-activated for a source set"() {
        given:
        buildFile << 'extraJavaModuleInfo { deactivate(sourceSets.main.get()) }\n'
        buildFile << 'extraJavaModuleInfo { activate(sourceSets.main) }\n'

        when:
        def result = task('printRuntimeCP', 'printCompileCP', 'printAP', '-q')

        then:
        result.output.contains('RCP: [commons-cli-1.4-module.jar]')
        result.output.contains('CCP: [commons-cli-1.4-module.jar]')
        result.output.contains('AP:  [commons-cli-1.4-module.jar]')
    }

    def "plugin can be deactivated for a single configuration"() {
        given:
        buildFile << 'extraJavaModuleInfo { deactivate(configurations.annotationProcessor) }'

        when:
        def result = task('printRuntimeCP', 'printCompileCP', 'printAP', '-q')

        then:
        result.output.contains('RCP: [commons-cli-1.4-module.jar]')
        result.output.contains('CCP: [commons-cli-1.4-module.jar]')
        result.output.contains('AP:  [commons-cli-1.4.jar]')
    }

    def "plugin is not active for custom configurations by default"() {
        when:
        def result = task('printCustom', '-q')

        then:
        result.output.contains('CUS: [commons-cli-1.4.jar]')
    }

    def "plugin can be activated for a single custom configuration"() {
        given:
        buildFile << 'extraJavaModuleInfo { activate(customPath) }'

        when:
        def result = task('printCustom', '-q')

        then:
        result.output.contains('CUS: [commons-cli-1.4-module.jar]')
    }
}

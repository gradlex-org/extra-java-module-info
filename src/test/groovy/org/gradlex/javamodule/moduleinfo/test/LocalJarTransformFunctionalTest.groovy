package org.gradlex.javamodule.moduleinfo.test

import org.gradlex.javamodule.moduleinfo.test.fixture.GradleBuild
import spock.lang.Specification

class LocalJarTransformFunctionalTest extends Specification {

    @Delegate
    GradleBuild build = new GradleBuild()

    def setup() {
        settingsFile << '''
            rootProject.name = "test-project"
            include(":sub")
        '''
        file("sub/build.gradle.kts") << '''
            plugins {
                id("java-library")
                id("org.gradlex.extra-java-module-info")
                id("maven-publish")
            }
        '''
        buildFile << '''
            plugins {
                id("java-library")
                id("org.gradlex.extra-java-module-info")
            }
            dependencies {
                implementation(project(":sub"))
            }
        '''
    }

    def "a locally produced Jar is transformed"() {
        given:
        buildFile << '''
            extraJavaModuleInfo {
                // transform local Jar to assert that it has gone through transformation 
                module("sub.jar", "org.example.sub")
            }
            tasks.register("printCP") {
                inputs.files(configurations.runtimeClasspath)
                doLast { println(inputs.files.files.map { it.name }) }
            }
        '''

        when:
        def result = task('printCP', '-q')

        then:
        result.output.trim() == "[sub-module.jar]"
    }

    def "transformation of locally produced Jars can be deactivates"() {
        given:
        buildFile << '''
            tasks.register("printCP") {
                inputs.files(configurations.runtimeClasspath)
                doLast { println(inputs.files.files.map { it.name }) }
            }
        '''
        file("sub/build.gradle.kts") << """
            extraJavaModuleInfo { skipLocalJars.set(true) }
        """

        when:
        def result = task('printCP', '-q')

        then:
        result.output.trim() == "[sub.jar]"
    }


    def "deactivation of locally produced Jars does not cause additional attributes to be published"() {
        given:
        def repo = file("repo")
        file("sub/build.gradle.kts") << """
            group = "foo"
            version = "1"
            publishing {
                publications.create<MavenPublication>("lib").from(components["java"])
                repositories.maven("${repo.absolutePath}")
            }         
            extraJavaModuleInfo { skipLocalJars.set(true) }
        """

        when:
        task('publish')

        then:
        !new File(repo, 'foo/sub/1/sub-1.module').text.contains('"javaModule":')
    }

    def "if transform fails due to missing local Jar, an actionable error message is given"() {
        given:
        buildFile << '''
            tasks.register("printCP") {
                inputs.files(configurations.runtimeClasspath.get().files) // provoke error: access at configuration time 
                doLast { println(inputs.files.files.map { it.name }) }
            }      
        '''

        when:
        def result = failTask('printCP', '-q')

        then:
        result.output.contains("File does not exist:")
        result.output.contains("You can prevent this error by setting 'skipLocalJars = true'")
    }

    def "resolving early does not fail if transformation is disabled for locally produced Jars"() {
        given:
        buildFile << '''
            tasks.register("printCP") {
                inputs.files(configurations.runtimeClasspath.get().files) // provoke resolution at configuration time 
                doLast { println(inputs.files.files.map { it.name }) }
            }      
        '''
        file("sub/build.gradle.kts") << '''
            extraJavaModuleInfo { skipLocalJars.set(true) }
        '''

        when:
        def result = task('printCP', '-q')

        then:
        result.output.trim() == "[sub.jar]"
    }
}

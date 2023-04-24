package org.gradlex.javamodule.moduleinfo

import spock.lang.Specification

import java.nio.file.Path

import static org.gradlex.javamodule.moduleinfo.FilePathToModuleCoordinates.gaCoordinatesFromFilePathMatch
import static org.gradlex.javamodule.moduleinfo.FilePathToModuleCoordinates.versionFromFilePath

class FilePathToModuleCoordinatesTest extends Specification {

    def "version from gradle cache file path"() {
        given:
        def path = path('/Users/someone/.gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api/1.7.36/6c62681a2f655b49963a5983b8b0950a6120ae14/slf4j-api-1.7.36.jar')

        expect:
        versionFromFilePath(path)  == "1.7.36"
    }

    def "ga coordinates from gradle cache file path"() {
        given:
        def path = path('/Users/someone/.gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api/1.7.36/6c62681a2f655b49963a5983b8b0950a6120ae14/slf4j-api-1.7.36.jar')

        expect:
        gaCoordinatesFromFilePathMatch(path, "org.slf4j:slf4j-api")
    }

    def "version from m2 repo file path"() {
        given:
        def path = path('/Users/someone/.m2/repository/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar.')

        expect:
        versionFromFilePath(path)  == "3.0.2"
    }

    def "ga coordinates from m2 repo file path"() {
        Path jarPath

       when:
       jarPath = path('/Users/someone/.m2/repository/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar.')

       then:
       gaCoordinatesFromFilePathMatch(jarPath, "com.google.code.findbugs:jsr305")

        when:
        jarPath = path('/Users/someone/.m2/repository/de/odysseus/juel/juel-impl/2.2.7/juel-impl-2.2.7.jar.')

        then:
        gaCoordinatesFromFilePathMatch(jarPath, "de.odysseus.juel:juel-impl")
    }

    private Path path(String path) {
        new File(path).toPath()
    }
}

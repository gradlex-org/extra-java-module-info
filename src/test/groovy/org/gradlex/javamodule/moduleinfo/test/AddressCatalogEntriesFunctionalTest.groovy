package org.gradlex.javamodule.moduleinfo.test

import org.gradlex.javamodule.moduleinfo.test.fixture.GradleBuild
import org.gradlex.javamodule.moduleinfo.test.fixture.LegacyLibraries
import spock.lang.IgnoreIf

@IgnoreIf({ GradleBuild.gradleVersionUnderTest.startsWith("6.") })
class AddressCatalogEntriesFunctionalTest extends AbstractFunctionalTest {

    LegacyLibraries libs = new LegacyLibraries(false, true)

    def setup() {
        if (build.gradleVersionUnderTest?.startsWith("7.0")) {
            settingsFile << '''
                enableFeaturePreview("VERSION_CATALOGS")
            '''
        }
        file("gradle/libs.versions.toml") << LegacyLibraries.catalog()
    }
}

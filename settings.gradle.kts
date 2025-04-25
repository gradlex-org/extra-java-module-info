plugins {
    id("com.gradle.develocity") version "4.0.1"
}

rootProject.name = "extra-java-module-info"

dependencyResolutionManagement {
    repositories.mavenCentral()
}

develocity {
    buildScan {
        val isCi = providers.environmentVariable("CI").getOrElse("false").toBoolean()
        if (isCi) {
            termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
            termsOfUseAgree = "yes"
        } else {
            publishing.onlyIf { false }
        }
    }
}

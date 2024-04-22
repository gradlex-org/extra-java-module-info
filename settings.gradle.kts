plugins {
    id("com.gradle.develocity") version "3.17.2"
}

rootProject.name = "extra-java-module-info"

dependencyResolutionManagement {
    repositories.mavenCentral()
}

gradleEnterprise {
    val runsOnCI = providers.environmentVariable("CI").getOrElse("false").toBoolean()
    if (runsOnCI) {
        buildScan {
            publishAlways()
            termsOfServiceUrl = "https://gradle.com/terms-of-service"
            termsOfServiceAgree = "yes"
        }
    }
}

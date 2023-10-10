plugins {
    id("com.gradle.enterprise") version "3.15.1"
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

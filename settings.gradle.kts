plugins {
    id("com.gradle.enterprise") version "3.16"
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

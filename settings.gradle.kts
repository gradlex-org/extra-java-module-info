plugins {
    id("com.gradle.enterprise") version "3.10"
}

rootProject.name = "extra-java-module-info"

dependencyResolutionManagement {
    repositories.mavenCentral()
}

gradleEnterprise {
    buildScan {
        publishAlways()
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}

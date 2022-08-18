plugins {
    id("groovy")
    id("org.gradlex.internal.plugin-publish-conventions") version "0.4"
}

group = "de.jjohannes.gradle"
version = "0.16"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

signing {
    isRequired = false
}

dependencies {
    implementation("org.gradlex:${project.name}:1.0")

    testImplementation("org.spockframework:spock-core:2.1-groovy-3.0")
}

pluginPublishConventions {
    id("de.jjohannes.${project.name}")
    implementationClass("de.jjohannes.gradle.javamodules.ExtraModuleInfoPlugin")
    displayName("Extra Java Module Info Gradle Plugin")
    description("!!! Plugin ID changed to 'org.gradlex.${project.name}' !!!")
    tags("gradlex", "java", "modularity", "jigsaw", "jpms")
    gitHub("https://github.com/gradlex-org/extra-java-module-info")
    developer {
        id.set("jjohannes")
        name.set("Jendrik Johannes")
        email.set("jendrik@gradlex.org")
    }
}

tasks.test {
    description = "Runs tests against the Gradle version the plugin is built with"
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    maxParallelForks = 4
}

listOf("6.4.1", "6.9.2", "7.0.2").forEach { gradleVersionUnderTest ->
    val testGradle = tasks.register<Test>("testGradle$gradleVersionUnderTest") {
        group = "verification"
        description = "Runs tests against Gradle $gradleVersionUnderTest"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform()
        maxParallelForks = 4
        systemProperty("gradleVersionUnderTest", gradleVersionUnderTest)
    }
    tasks.check {
        dependsOn(testGradle)
    }
}

plugins {
    id("com.gradle.plugin-publish") version "1.0.0-rc-2"
    id("groovy")
}

group = "de.jjohannes.gradle"
version = "0.12"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation("org.ow2.asm:asm:8.0.1")

    testImplementation("org.spockframework:spock-core:2.1-groovy-3.0")
}

gradlePlugin {
    plugins {
        create("extra-java-module-info") {
            id = "de.jjohannes.extra-java-module-info"
            implementationClass = "de.jjohannes.gradle.javamodules.ExtraModuleInfoPlugin"
            displayName = "Add module information to legacy Java libraries"
            description = "Add module information to Java libraries that do not have any."
        }
    }
}

pluginBundle {
    website = "https://github.com/jjohannes/extra-java-module-info"
    vcsUrl = "https://github.com/jjohannes/extra-java-module-info.git"
    tags = listOf("java", "modularity", "jigsaw", "jpms")
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

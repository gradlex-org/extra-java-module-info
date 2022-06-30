plugins {
    id("com.gradle.plugin-publish") version "1.0.0-rc-2"
    id("groovy")
}

group = "de.jjohannes.gradle"
version = "0.15"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation("org.ow2.asm:asm:8.0.1")

    testImplementation("org.spockframework:spock-core:2.1-groovy-3.0")
}

val pluginId = "de.jjohannes.extra-java-module-info"
val pluginClass = "de.jjohannes.gradle.javamodules.ExtraModuleInfoPlugin"
val pluginName = "Extra Java Module Info Gradle Plugin"
val pluginDescription = "Add module information to legacy Java libraries."
val pluginBundleTags = listOf("java", "modularity", "jigsaw", "jpms")
val pluginGitHub = "https://github.com/jjohannes/extra-java-module-info"

gradlePlugin {
    plugins {
        create(project.name) {
            id = pluginId
            implementationClass = pluginClass
            displayName = pluginName
            description = pluginDescription
        }
    }
}

pluginBundle {
    website = pluginGitHub
    vcsUrl = pluginGitHub
    tags = pluginBundleTags
}

publishing {
    publications.withType<MavenPublication>().all {
        pom.name.set(pluginName)
        pom.description.set(pluginDescription)
        pom.url.set(pluginGitHub)
        pom.licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        pom.developers {
            developer {
                id.set("jjohannes")
                name.set("Jendrik Johannes")
                email.set("jendrik@onepiece.software")
            }
        }
        pom.scm {
            url.set(pluginGitHub)
        }
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

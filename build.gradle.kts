plugins {
    id("groovy")
    id("org.gradlex.internal.plugin-publish-conventions") version "0.6"
}

group = "org.gradlex"
version = "1.12"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation("org.ow2.asm:asm:9.8")

    testImplementation("org.spockframework:spock-core:2.3-groovy-3.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

pluginPublishConventions {
    id("${project.group}.${project.name}")
    implementationClass("org.gradlex.javamodule.moduleinfo.ExtraJavaModuleInfoPlugin")
    displayName("Extra Java Module Info Gradle Plugin")
    description("Add module information to legacy Java libraries.")
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

listOf("6.8.3", "6.9.2", "7.0.2", "7.6.1").forEach { gradleVersionUnderTest ->
    val testGradle = tasks.register<Test>("testGradle$gradleVersionUnderTest") {
        group = "verification"
        description = "Runs tests against Gradle $gradleVersionUnderTest"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform()
        maxParallelForks = 4
        systemProperty("gradleVersionUnderTest", gradleVersionUnderTest)
        javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(11) }
    }
    tasks.check {
        dependsOn(testGradle)
    }
}

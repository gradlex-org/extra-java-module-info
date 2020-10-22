/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    `java-gradle-plugin`
    `maven-publish`
    groovy
    id("com.gradle.plugin-publish") version "0.11.0"
    id("com.github.hierynomus.license") version "0.15.0"
}

repositories {
    jcenter()
}

group = "de.jjohannes.gradle"
version = "0.3"

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("--release", "9"))
}

val functionalTest: SourceSet by sourceSets.creating
gradlePlugin.testSourceSets(functionalTest)
val functionalTestTask = tasks.register<Test>("functionalTest") {
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
}
tasks.check {
    dependsOn(functionalTestTask)
}

dependencies {
    implementation(gradleApi())
    implementation("org.ow2.asm:asm:8.0.1")
    "functionalTestImplementation"("org.spockframework:spock-core:1.2-groovy-2.5")
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

license {
    header = rootProject.file("config/HEADER.txt")
    strictCheck = true
    ignoreFailures = false
    mapping(mapOf(
        "java"   to "SLASHSTAR_STYLE",
        "kt"     to "SLASHSTAR_STYLE",
        "groovy" to "SLASHSTAR_STYLE",
        "kts"    to "SLASHSTAR_STYLE"
    ))
    ext.set("year", "2020")
    exclude("**/build/*")
    exclude("**/.gradle/*")
}

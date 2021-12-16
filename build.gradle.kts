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
    id("java-gradle-plugin")
    id("maven-publish")
    id("groovy")
    id("com.gradle.plugin-publish") version "0.15.0"
}

repositories {
    mavenCentral()
}

group = "de.jjohannes.gradle"
version = "0.10"

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("--release", "9"))
}
tasks.javadoc {
    (options as CoreJavadocOptions).addStringOption("Xdoclint:none", "-quiet")
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

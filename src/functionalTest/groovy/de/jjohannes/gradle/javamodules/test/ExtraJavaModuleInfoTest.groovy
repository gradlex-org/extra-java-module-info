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
package de.jjohannes.gradle.javamodules.test

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.util.jar.JarFile

class ExtraJavaModuleInfoTest extends Specification {

    @Rule
    TemporaryFolder testFolder = new TemporaryFolder()

    def setup() {
        testFolder.newFile('settings.gradle.kts') << 'rootProject.name = "test-project"'
    }

    def "can add module information to legacy library"() {
        given:
        new File(testFolder.root, "src/main/java/org/gradle/sample/app/data").mkdirs()

        testFolder.newFile("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            import com.google.gson.Gson;
            import org.apache.commons.beanutils.BeanUtils;
            import org.apache.commons.cli.CommandLine;
            import org.apache.commons.cli.CommandLineParser;
            import org.apache.commons.cli.DefaultParser;
            import org.apache.commons.cli.Options;
            import org.apache.commons.lang3.StringUtils;
            import org.gradle.sample.app.data.Message;
            
            public class Main {
            
                public static void main(String[] args) throws Exception {
                    Options options = new Options();
                    options.addOption("json", true, "data to parse");
                    options.addOption("debug", false, "prints module infos");
                    CommandLineParser parser = new DefaultParser();
                    CommandLine cmd = parser.parse(options, args);
            
                    if (cmd.hasOption("debug")) {
                        printModuleDebug(Main.class);
                        printModuleDebug(Gson.class);
                        printModuleDebug(StringUtils.class);
                        printModuleDebug(CommandLine.class);
                        printModuleDebug(BeanUtils.class);
                    }
            
                    String json = cmd.getOptionValue("json");
                    Message message = new Gson().fromJson(json == null ? "{}" : json, Message.class);
            
                    Object copy = BeanUtils.cloneBean(message);
                    System.out.println();
                    System.out.println("Original: " + copy.toString());
                    System.out.println("Copy:     " + copy.toString());
            
                }
            
                private static void printModuleDebug(Class<?> clazz) {
                    System.out.println(clazz.getModule().getName() + " - " + clazz.getModule().getDescriptor().version().get());
                }
            
            }
        """
        testFolder.newFile("src/main/java/org/gradle/sample/app/data/Message.java") << """
            package org.gradle.sample.app.data;
            
            import java.util.List;
            import java.util.ArrayList;
            
            public class Message {
                private String message;
                private List<String> receivers = new ArrayList<>();
            
                public String getMessage() {
                    return message;
                }
            
                public void setMessage(String message) {
                    this.message = message;
                }
            
                public List<String> getReceivers() {
                    return receivers;
                }
            
                public void setReceivers(List<String> receivers) {
                    this.receivers = receivers;
                }
            
                @Override
                public String toString() {
                    return "Message{message='" + message + '\\'' +
                        ", receivers=" + receivers + '}';
                }
            }
        """
        testFolder.newFile("src/main/java/module-info.java") << """
            module org.gradle.sample.app {
                exports org.gradle.sample.app;
                opens org.gradle.sample.app.data; // allow Gson to access via reflection
            
                requires com.google.gson;
                requires org.apache.commons.lang3;
                requires org.apache.commons.cli;
                requires org.apache.commons.beanutils;
            }
        """
        testFolder.newFile("build.gradle.kts") << """
            plugins {
                application
                id("de.jjohannes.extra-java-module-info")
            }
            
            repositories {
                mavenCentral()
            }
            
            java {
                modularity.inferModulePath.set(true)
            }
            
            dependencies {
                implementation("com.google.code.gson:gson:2.8.6")           // real module
                implementation("net.bytebuddy:byte-buddy:1.10.9")           // real module with multi-release jar
                implementation("org.apache.commons:commons-lang3:3.10")     // automatic module
                implementation("commons-beanutils:commons-beanutils:1.9.4") // plain library (also brings in other libraries transitively)
                implementation("commons-cli:commons-cli:1.4")               // plain library        
            }
            
            extraJavaModuleInfo {
                module("commons-beanutils-1.9.4.jar", "org.apache.commons.beanutils", "1.9.4") {
                    exports("org.apache.commons.beanutils")
                    
                    requires("org.apache.commons.logging")
                    requires("java.sql")
                    requires("java.desktop")
                }
                module("commons-cli-1.4.jar", "org.apache.commons.cli", "3.2.2") {
                    exports("org.apache.commons.cli")
                }
                module("commons-collections-3.2.2.jar", "org.apache.commons.collections", "3.2.2")
                automaticModule("commons-logging-1.2.jar", "org.apache.commons.logging")
            }
            
            application {
                mainModule.set("org.gradle.sample.app")
                mainClass.set("org.gradle.sample.app.Main")
            }
        """

        expect:
        build().task(':compileJava').outcome == TaskOutcome.SUCCESS
    }

    def "can add Automatic-Module-Name to libraries without MANIFEST.MF"() {
        given:
        new File(testFolder.root, "src/main/java/org/gradle/sample/app/data").mkdirs()

        testFolder.newFile("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            import javax.inject.Singleton;
            
            public class Main {
            
                public static void main(String[] args)  {
                    printModuleDebug(Singleton.class);
                }
            
                private static void printModuleDebug(Class<?> clazz) {
                    System.out.println(clazz.getModule().getName() + " - " + clazz.getModule().getDescriptor().version().get());
                }
            
            }
        """
        testFolder.newFile("src/main/java/module-info.java") << """
            module org.gradle.sample.app {               
                requires javax.inject;               
            }
        """
        testFolder.newFile("build.gradle.kts") << """
            plugins {
                application
                id("de.jjohannes.extra-java-module-info")
            }
            
            repositories {
                mavenCentral()
            }
            
            java {
                modularity.inferModulePath.set(true)
            }
            
            dependencies {
                implementation("javax.inject:javax.inject:1")                     
            }
            
            extraJavaModuleInfo {               
                automaticModule("javax.inject-1.jar", "javax.inject")
            }
            
            application {
                mainModule.set("org.gradle.sample.app")
                mainClass.set("org.gradle.sample.app.Main")
            }
        """

        expect:
        build().task(':compileJava').outcome == TaskOutcome.SUCCESS
    }

    def "can add module-info.class to libraries without MANIFEST.MF"() {
        given:
        new File(testFolder.root, "src/main/java/org/gradle/sample/app/data").mkdirs()

        testFolder.newFile("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            import javax.inject.Singleton;
            
            public class Main {
            
                public static void main(String[] args)  {
                    printModuleDebug(Singleton.class);
                }
            
                private static void printModuleDebug(Class<?> clazz) {
                    System.out.println(clazz.getModule().getName() + " - " + clazz.getModule().getDescriptor().version().get());
                }
            
            }
        """
        testFolder.newFile("src/main/java/module-info.java") << """
            module org.gradle.sample.app {               
                requires javax.inject;               
            }
        """
        testFolder.newFile("build.gradle.kts") << """
            plugins {
                application
                id("de.jjohannes.extra-java-module-info")
            }
            
            repositories {
                mavenCentral()
            }
            
            java {
                modularity.inferModulePath.set(true)
            }
            
            dependencies {
                implementation("javax.inject:javax.inject:1")                     
            }
            
            extraJavaModuleInfo {               
                module("javax.inject-1.jar", "javax.inject", "") {
                    exports("javax.inject")
                }
            }
                                 
            application {
                mainModule.set("org.gradle.sample.app")
                mainClass.set("org.gradle.sample.app.Main")
            }
        """

        expect:
        build().task(':compileJava').outcome == TaskOutcome.SUCCESS
    }

    def "can retrofit META-INF/services/* metadata to module-info.class"() {
        given:
        new File(testFolder.root, "src/main/java/org/gradle/sample/app/data").mkdirs()

        testFolder.newFile("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            import java.util.ServiceLoader;
            import java.util.stream.Collectors;
            import org.apache.logging.log4j.spi.Provider;
            
            public class Main {
            
                public static void main(String[] args)  {
                    Provider provider = ServiceLoader.load(Provider.class).findFirst()
                                        .orElseThrow(() -> new AssertionError("No providers loaded"));
                    System.out.println(provider.getClass());
                }
                          
            }
        """
        testFolder.newFile("src/main/java/module-info.java") << """
            module org.gradle.sample.app {               
                requires org.apache.logging.log4j;
                requires org.apache.logging.log4j.core;
                
                uses org.apache.logging.log4j.spi.Provider;                              
            }
        """
        testFolder.newFile("build.gradle.kts") << """
            plugins {
                application
                id("de.jjohannes.extra-java-module-info")
            }
            
            repositories {
                mavenCentral()
            }
            
            java {
                modularity.inferModulePath.set(true)
            }
            
            dependencies {
                implementation("org.apache.logging.log4j:log4j-core:2.14.0")                     
            }
            
            extraJavaModuleInfo {               
                module("log4j-core-2.14.0.jar", "org.apache.logging.log4j.core", "2.14.0") {
                    requires("java.compiler")
                    requires("java.desktop")
                    requires("org.apache.logging.log4j")
                    exports("org.apache.logging.log4j.core")
                }
            }
                                 
            application {
                mainModule.set("org.gradle.sample.app")
                mainClass.set("org.gradle.sample.app.Main")
            }
        """

        expect:
        run().task(':run').outcome == TaskOutcome.SUCCESS
    }

    def "can omit unwanted META-INF/services from automatic migration"() {
        given:
        new File(testFolder.root, "src/main/java/org/gradle/sample/app/data").mkdirs()

        testFolder.newFile("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            import java.util.ServiceLoader;
            import java.util.stream.Collectors;
            import javax.script.ScriptEngineFactory;
            import javax.script.ScriptException;
            
            public class Main {
            
                public static void main(String[] args) throws ScriptException {
                    ScriptEngineFactory scriptEngineFactory = ServiceLoader.load(ScriptEngineFactory.class)
                                        .findFirst()
                                        .orElseThrow(() -> new AssertionError("No providers loaded"));
                    String engineName = scriptEngineFactory.getEngineName();
                    if (!engineName.equals("Groovy Scripting Engine")) {
                        throw new AssertionError("Incorrect Script Engine Loaded: " + engineName);
                    }
                    int revVal = (int) scriptEngineFactory.getScriptEngine().eval("2+2");
                    if (revVal != 4) {
                        throw new AssertionError("Invalid evaluation result: " + revVal);
                    }
                }
                          
            }
        """
        testFolder.newFile("src/main/java/module-info.java") << """
            module org.gradle.sample.app {               
                requires groovy.all;
                uses javax.script.ScriptEngineFactory;                                                           
            }
        """
        testFolder.newFile("build.gradle.kts") << """
            plugins {
                application
                id("de.jjohannes.extra-java-module-info")
            }
            
            repositories {
                mavenCentral()
            }
            
            java {
                modularity.inferModulePath.set(true)
            }
            
            dependencies {
                implementation("org.codehaus.groovy:groovy-all:2.4.15")                     
            }
            
            extraJavaModuleInfo {               
                module("groovy-all-2.4.15.jar", "groovy.all", "2.4.15") {
                   requiresTransitive("java.scripting")
                   requires("java.logging")
                   requires("java.desktop")
                   ignoreServiceProvider("org.codehaus.groovy.runtime.ExtensionModule")
                   ignoreServiceProvider("org.codehaus.groovy.plugins.Runners")
                   ignoreServiceProvider("org.codehaus.groovy.source.Extensions")
                }
            }
             
            application {
                mainModule.set("org.gradle.sample.app")
                mainClass.set("org.gradle.sample.app.Main")
            }
        """

        expect:
        run().task(':run').outcome == TaskOutcome.SUCCESS
    }

    def "can add static/transitive 'requires' modifiers to legacy libraries"() {
        given:
        new File(testFolder.root, "src/main/java/org/gradle/sample/app/data").mkdirs()

        testFolder.newFile("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            import static java.lang.module.ModuleDescriptor.Requires;
            import static java.util.function.Function.identity;
            
            import java.lang.module.ModuleDescriptor;
            import java.util.Map;
            import java.util.stream.Collectors;
            
            public class Main {
            
                public static void main(String[] args) {
                    Map<String, Requires> index = ModuleLayer.boot()
                            .findModule("spring.boot.autoconfigure")
                            .map(Module::getDescriptor)
                            .map(ModuleDescriptor::requires)
                            .orElseThrow(() -> new AssertionError("Cannot find spring.boot.autoconfigure"))
                            .stream()
                            .collect(Collectors.toMap(Requires::name, identity()));
                    if (!index.get("spring.boot").modifiers().contains(Requires.Modifier.TRANSITIVE)) {
                        throw new AssertionError("spring.boot must be declared as transitive");
                    }
                    if (!index.get("com.google.gson").modifiers().contains(Requires.Modifier.STATIC)) {
                        throw new AssertionError("com.google.gson must be declared as static");
                    }
                    if (!index.get("spring.context").modifiers().isEmpty()) {
                        throw new AssertionError("spring.boot must not have any modifiers");
                    }
                }
            
            }
        """
        testFolder.newFile("src/main/java/module-info.java") << """
            module org.gradle.sample.app {               
                requires spring.boot.autoconfigure;
            }
        """
        testFolder.newFile("build.gradle.kts") << """
            plugins {
                application
                id("de.jjohannes.extra-java-module-info")
            }
            
            repositories {
                mavenCentral()
            }
            
            java {
                modularity.inferModulePath.set(true)
            }
            
            dependencies {
                implementation("org.springframework.boot:spring-boot-autoconfigure:2.4.2") 
            }
            
            extraJavaModuleInfo {               
                module("spring-boot-autoconfigure-2.4.2.jar", "spring.boot.autoconfigure", "2.4.2") {
                    requires("spring.context")
                    requiresTransitive("spring.boot")
                    requiresStatic("com.google.gson")
                }
            }
                                 
            application {
                mainModule.set("org.gradle.sample.app")
                mainClass.set("org.gradle.sample.app.Main")
            }
        """

        expect:
        run().task(':run').outcome == TaskOutcome.SUCCESS
    }

    def "fails by default if no module info is defined for a legacy library"() {
        given:
        new File(testFolder.root, "src/main/java").mkdirs()
        testFolder.newFile("src/main/java/module-info.java") << """
            module org.gradle.sample.app { }
        """
        testFolder.newFile("build.gradle.kts") << """
            plugins {
                application
                id("de.jjohannes.extra-java-module-info")
            }
            repositories { mavenCentral() } 
            java { modularity.inferModulePath.set(true) }
            dependencies { implementation("commons-cli:commons-cli:1.4")   }
        """

        expect:
        fail().task(':compileJava').outcome == TaskOutcome.FAILED
    }

    def "can opt-out of strict module requirement"() {
        given:
        new File(testFolder.root, "src/main/java").mkdirs()
        testFolder.newFile("src/main/java/module-info.java") << """
            module org.gradle.sample.app { }
        """
        testFolder.newFile("build.gradle.kts") << """
            plugins {
                application
                id("de.jjohannes.extra-java-module-info")
            }
            repositories { mavenCentral() } 
            java { modularity.inferModulePath.set(true) }
            dependencies { implementation("commons-cli:commons-cli:1.4")   }
            extraJavaModuleInfo {
                failOnMissingModuleInfo.set(false)
            }
            tasks.compileJava {
                doLast { println(classpath.map { it.name }) }
            }
        """

        when:
        def result = build()

        then:
        result.task(':compileJava').outcome == TaskOutcome.SUCCESS
        result.output.contains('[commons-cli-1.4.jar]')
    }

    def "can opt-out for selected configurations by modifying the javaModule attribute"() {
        given:
        new File(testFolder.root, "src/test/java").mkdirs()
        testFolder.newFile("src/test/java/Test.java") << ""
        testFolder.newFile("build.gradle.kts") << """
            plugins {
                application
                id("de.jjohannes.extra-java-module-info")
            }
            configurations {
                testRuntimeClasspath {
                    attributes { attribute(Attribute.of("javaModule", Boolean::class.javaObjectType), false) }
                }
                testCompileClasspath {
                    attributes { attribute(Attribute.of("javaModule", Boolean::class.javaObjectType), false) }
                }
            }
            repositories { mavenCentral() } 
            java { modularity.inferModulePath.set(true) }
            dependencies { testImplementation("commons-cli:commons-cli:1.4") }
        """

        expect:
        build().task(':compileTestJava').outcome == TaskOutcome.SUCCESS
    }

    def "works in combination with shadow plugin"() {
        def shadowJar = new File(testFolder.root, "app/build/libs/app-all.jar")

        given:
        new File(testFolder.root, "utilities/src/main/java").mkdirs()
        new File(testFolder.root, "app/src/main/java").mkdirs()

        testFolder.newFile("settings.gradle") << """
            include('app', 'utilities')
        """
        testFolder.newFile("utilities/src/main/java/module-info.java") << """
            module utilities { }
        """
        testFolder.newFile("utilities/src/main/java/Utility.java") << """
            public class Utility { }
        """
        testFolder.newFile("utilities/build.gradle") << """
            plugins {
                id 'java-library'
            }
            java.modularity.inferModulePath = true
        """
        testFolder.newFile("app/src/main/java/module-info.java") << """
            module app { }
        """
        testFolder.newFile("app/src/main/java/App.java") << """
            public class App { }
        """
        testFolder.newFile("app/build.gradle") << """
            plugins {
                id 'application'
                id 'de.jjohannes.extra-java-module-info'
                id 'com.github.johnrengelman.shadow' version '6.1.0'
            }
            dependencies {
                implementation project(':utilities')
            }
            java.modularity.inferModulePath = true
            application.mainClassName = 'App'
            configurations {
                runtimeClasspath {
                    attributes { attribute(Attribute.of("javaModule", Boolean), false) }
                }
            }
        """

        expect:
        gradleRunnerFor([':app:shadowJar']).build().task(':app:shadowJar').outcome == TaskOutcome.SUCCESS
        shadowJar.exists()
        // 1 * module-info, 2 * class file, 1 * META-INF folder, 1 * MANIFEST
        new JarFile(shadowJar).entries().asIterator().size() == 5
    }

    BuildResult build() {
        gradleRunnerFor(['build']).build()
    }

    BuildResult run() {
        gradleRunnerFor(['run']).build()
    }

    BuildResult fail() {
        gradleRunnerFor(['build']).buildAndFail()
    }

    GradleRunner gradleRunnerFor(List<String> args) {
        GradleRunner.create()
                .forwardOutput()
                .withPluginClasspath()
                .withProjectDir(testFolder.root)
                .withArguments(args + '-s').withDebug(true)
    }
}

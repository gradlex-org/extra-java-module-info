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
            repositories {  mavenCentral() } 
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
            repositories {  mavenCentral() } 
            java { modularity.inferModulePath.set(true) }
            dependencies { implementation("commons-cli:commons-cli:1.4")   }
            extraJavaModuleInfo {
                failOnMissingModuleInfo.set(false)
            }
        """

        expect:
        build().task(':compileJava').outcome == TaskOutcome.SUCCESS
    }

    BuildResult build() {
        gradleRunnerFor(['build']).build()
    }

    BuildResult fail() {
        gradleRunnerFor(['build']).buildAndFail()
    }

    GradleRunner gradleRunnerFor(List<String> args) {
        GradleRunner.create()
                .forwardOutput()
                .withPluginClasspath()
                .withProjectDir(testFolder.root)
                .withArguments(args)
    }
}

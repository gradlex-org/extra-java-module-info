A Gradle 6.4+ plugin to use legacy Java libraries as _Java Modules_ in a modular Java project.

# Java Modules with Gradle

- Documentation on Building Java Modules with Gradle 6.4+: 
  https://docs.gradle.org/6.4-rc-1/samples/index.html#java_modules
- Samples for Building Java Modules with Gradle 6.4+: 
  https://docs.gradle.org/6.4-rc-1/userguide/java_library_plugin.html#sec:java_library_modular
- Give feedback on Java Module support in Gradle: 
  https://github.com/gradle/gradle/issues/890

# How to use this plugin

This plugin allows you to add module information to a Java library that does not have any.
If you do that, you can give it a proper _module name_ and Gradle can pick it up to put it on the _module path_ during compilation, testing and execution.

```
plugins {
    id("java-library")
    id("de.jjohannes.extra-java-module-info") version "0.4"
}

// add module information for all direct and transitive depencies that are not modules
extraJavaModuleInfo {
    // failOnMissingModuleInfo.set(false)
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
```

Sample uses Gradle's Kotlin DSL (`build.gradle.kts` file). The Groovy DSL syntax is similar.

# FAQ

## How do I deactivate the plugin functionality for a certain classpath?

This is can be useful for the test classpath if it should be used for unit testing on the classpath (rather than the module path).
If you use the [shadow plugin](https://plugins.gradle.org/plugin/com.github.johnrengelman.shadow) and [encounter this issue](https://github.com/jjohannes/extra-java-module-info/issues/7),
you can deactivate it for the runtime classpath as the module information is irrelevant for a fat Jar in any case.

**Kotlin DSL**
```
configurations {
    runtimeClasspath { // testRuntimeClasspath, testCompileClasspath, ... 
        attributes { attribute(Attribute.of("javaModule", Boolean::class.javaObjectType), false) }
    }
}
```

**Groovy DSL**
```
configurations {
    runtimeClasspath { // testRuntimeClasspath, testCompileClasspath, ... 
        attributes { attribute(Attribute.of("javaModule", Boolean), false) }
    }
}
```

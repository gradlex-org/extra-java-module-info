A Gradle 6.4+ plugin to use legacy Java libraries as _Java Modules_ in a modular Java project.

This plugin is maintained by me, [Jendrik Johannes](https://github.com/jjohannes).
I offer consulting and training for Gradle and/or the Java Module System - please [reach out](mailto:jendrik.johannes@gmail.com) if you are interested.
There is also my [YouTube channel](https://www.youtube.com/playlist?list=PLWQK2ZdV4Yl2k2OmC_gsjDpdIBTN0qqkE) on Gradle topics.

Special thanks goes to [Ihor Herasymenko](https://github.com/iherasymenko) who has been contributing many features and fixes to this plugin!

If you have a suggestion or a question, please [open an issue](https://github.com/jjohannes/extra-java-module-info/issues/new).

There is a [CHANGELOG.md](CHANGELOG.md).

# Java Modules with Gradle

If you plan to build Java Modules with Gradle, you should consider using these plugins on top of Gradle core:

- [`id("de.jjohannes.java-module-dependencies")`](https://github.com/jjohannes/java-module-dependencies)  
  Avoid duplicated dependency definitions and get your Module Path under control
- [`id("de.jjohannes.java-module-testing")`](https://github.com/jjohannes/java-module-testing)  
  Proper test setup for Java Modules
- [`id("de.jjohannes.extra-java-module-info")`](https://github.com/jjohannes/extra-java-module-info)  
  Only if your (existing) project cannot avoid using non-module legacy Jars

[Here is a sample](https://github.com/jjohannes/java-module-testing/tree/main/samples/use-all-java-module-plugins)
that shows all plugins in combination.

[Full Java Module System Project Setup](https://github.com/jjohannes/gradle-project-setup-howto/tree/java_module_system) is a full-fledged Java Module System project setup using these plugins.  
[<img src="https://onepiecesoftware.github.io/img/videos/15-3.png" width="260">](https://www.youtube.com/watch?v=uRieSnovlVc&list=PLWQK2ZdV4Yl2k2OmC_gsjDpdIBTN0qqkE)

# How to use this plugin

This plugin allows you to add module information to a Java library that does not have any.
If you do that, you can give it a proper _module name_ and Gradle can pick it up to put it on the _module path_ during compilation, testing and execution.

The plugin should be applied to **all subprojects** of your multi-project build.
It is recommended to use a [convention plugin](https://docs.gradle.org/current/samples/sample_convention_plugins.html#organizing_build_logic) for that.

## Plugin dependency

Add this to the build file of your convention plugin's build
(e.g. `build-logic/build.gradle(.kts)` or `buildSrc/build.gradle(.kts)`).

```
dependencies {
    implementation("de.jjohannes.gradle:extra-java-module-info:0.12")
}
```

## Defining extra module information
In your convention plugin, apply the plugin and define the additional module info:

```
plugins {
    ...
    id("de.jjohannes.extra-java-module-info")
}

// add module information for all direct and transitive dependencies that are not modules
extraJavaModuleInfo {
    // failOnMissingModuleInfo.set(false)
    module("commons-beanutils:commons-beanutils", "org.apache.commons.beanutils") {
        exports("org.apache.commons.beanutils")
        
        requires("org.apache.commons.logging")
        requires("java.sql")
        requires("java.desktop")
        
        // requiresTransitive(...)
        // requiresStatic(...)
    }
    module("commons-cli:commons-cli", "org.apache.commons.cli") {
        exports("org.apache.commons.cli")
    }
    module("commons-collections:commons-collections", "org.apache.commons.collections")
    automaticModule("commons-logging:commons-logging", "org.apache.commons.logging")
}
```

## Dependencies in build files

Now dependencies defined in your build files are all treated as modules if enough extra information was provided.
For example:

```
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

This can be useful for the test classpath if it should be used for unit testing on the classpath (rather than the module path).
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

## How do I add `provides ... with ...` declarations to the `module-info.class` descriptor?

The plugin will automatically retrofit all the available `META-INF/services/*` descriptors into `module-info.class` for you. The `META-INF/services/*` descriptors will be preserved so that a transformed JAR will continue to work if it is placed on the classpath.

The plugin also allows you to ignore some unwanted services from being automatically converted into `provides .. with ...` declarations. 

```
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
```

## What do I do in a 'split package' situation?

The Java Module System does not allow the same package to be used in more than one _module_.
This is an issue with legacy libraries, where it was common practice to use the same package in multiple Jars.
This plugin offers the option to merge multiple Jars into one in such situations:

```
 extraJavaModuleInfo {
    module("org.apache.zookeeper:zookeeper", "org.apache.zookeeper") {
        mergeJar("org.apache.zookeeper:zookeeper-jute")
        
        // ...
    }
    automaticModule("org.slf4j:slf4j-api", "org.slf4j") {
        mergeJar("org.slf4j:slf4j-ext")
    }
}
```

Note: The merged Jar will include the *first* appearance of duplicated files (like the `MANIFEST.MF`).
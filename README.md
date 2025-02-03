# Extra Java Module Info Gradle plugin

[![Build Status](https://img.shields.io/endpoint.svg?url=https%3A%2F%2Factions-badge.atrox.dev%2Fgradlex-org%2Fextra-java-module-info%2Fbadge%3Fref%3Dmain&style=flat)](https://actions-badge.atrox.dev/gradlex-org/extra-java-module-info/goto?ref=main)
[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v?label=Plugin%20Portal&metadataUrl=https%3A%2F%2Fplugins.gradle.org%2Fm2%2Forg%2Fgradlex%2Fextra-java-module-info%2Forg.gradlex.extra-java-module-info.gradle.plugin%2Fmaven-metadata.xml)](https://plugins.gradle.org/plugin/org.gradlex.extra-java-module-info)

A Gradle 6.8+ plugin to use legacy Java libraries as _Java Modules_ in a modular Java project.

This [GradleX](https://gradlex.org) plugin is maintained by me, [Jendrik Johannes](https://github.com/jjohannes).
I offer consulting and training for Gradle and/or the Java Module System - please [reach out](mailto:jendrik.johannes@gmail.com) if you are interested.
There is also my [YouTube channel](https://www.youtube.com/playlist?list=PLWQK2ZdV4Yl2k2OmC_gsjDpdIBTN0qqkE) on Gradle topics.

Special thanks goes to [Ihor Herasymenko](https://github.com/iherasymenko) who has been contributing many features and fixes to this plugin!

If you have a suggestion or a question, please [open an issue](https://github.com/gradlex-org/extra-java-module-info/issues/new).

There is a [CHANGELOG.md](CHANGELOG.md).

# Java Modules with Gradle

If you plan to build Java Modules with Gradle, you should consider using these plugins on top of Gradle core:

- [`id("org.gradlex.java-module-dependencies")`](https://github.com/gradlex-org/java-module-dependencies)  
  Avoid duplicated dependency definitions and get your Module Path under control
- [`id("org.gradlex.java-module-testing")`](https://github.com/gradlex-org/java-module-testing)  
  Proper test setup for Java Modules
- [`id("org.gradlex.extra-java-module-info")`](https://github.com/gradlex-org/extra-java-module-info)  
  Only if your (existing) project cannot avoid using non-module legacy Jars

[Here is a sample](https://github.com/gradlex-org/java-module-testing/tree/main/samples/use-all-java-module-plugins)
that shows all plugins in combination.

[In episodes 31, 32, 33 of Understanding Gradle](https://github.com/jjohannes/understanding-gradle) I explain what these plugins do and why they are needed.
[<img src="https://onepiecesoftware.github.io/img/videos/31.png" width="260">](https://www.youtube.com/watch?v=X9u1taDwLSA&list=PLWQK2ZdV4Yl2k2OmC_gsjDpdIBTN0qqkE)
[<img src="https://onepiecesoftware.github.io/img/videos/32.png" width="260">](https://www.youtube.com/watch?v=T9U0BOlVc-c&list=PLWQK2ZdV4Yl2k2OmC_gsjDpdIBTN0qqkE)
[<img src="https://onepiecesoftware.github.io/img/videos/33.png" width="260">](https://www.youtube.com/watch?v=6rFEDcP8Noc&list=PLWQK2ZdV4Yl2k2OmC_gsjDpdIBTN0qqkE)

[Full Java Module System Project Setup](https://github.com/jjohannes/gradle-project-setup-howto/tree/java_module_system) is a full-fledged Java Module System project setup using these plugins.  
[<img src="https://onepiecesoftware.github.io/img/videos/15-3.png" width="260">]([https://www.youtube.com/watch?v=uRieSnovlVc&list=PLWQK2ZdV4Yl2k2OmC_gsjDpdIBTN0qqkE](https://www.youtube.com/watch?v=T9U0BOlVc-c&list=PLWQK2ZdV4Yl2k2OmC_gsjDpdIBTN0qqkE))

# How to use this plugin

This plugin allows you to add module information to a Java library that does not have any.
If you do that, you can give it a proper _module name_ and Gradle can pick it up to put it on the _module path_ during compilation, testing and execution.

The plugin should be applied to **all subprojects** of your multi-project build.
It is recommended to use a [convention plugin](https://docs.gradle.org/current/samples/sample_convention_plugins.html#organizing_build_logic) for that.

## Plugin dependency

Add this to the build file of your convention plugin's build
(e.g. `gradle/plugins/build.gradle(.kts)` or `buildSrc/build.gradle(.kts)`).

```
dependencies {
    implementation("org.gradlex:extra-java-module-info:1.10")
}
```

## Defining extra module information
In your convention plugin, apply the plugin and define the additional module info:

```
plugins {
    ...
    id("org.gradlex.extra-java-module-info")
}

// add module information for all direct and transitive dependencies that are not modules
extraJavaModuleInfo {
    // failOnMissingModuleInfo.set(false)
    module("commons-beanutils:commons-beanutils", "org.apache.commons.beanutils") {
        exports("org.apache.commons.beanutils")
        // or granuarly allowing access to a package by specific modules
        // exports("org.apache.commons.beanutils",
        //         "org.mycompany.server", "org.mycompany.client")
        // or simply export all packages
        // exportAllPackages()
        
        requiresTransitive("org.apache.commons.logging")
        requires("java.sql")
        requires("java.desktop")
        
        // closeModule()
        // opens("org.apache.commons.beanutils")
        // or granuarly allowing runtime-only access to a package by specific modules
        // opens("org.apache.commons.beanutils",
        //       "org.mycompany.server", "org.mycompany.client")
        
        // requiresTransitive(...)
        // requiresStatic(...)
        
        // requireAllDefinedDependencies()
    }
    module("commons-cli:commons-cli", "org.apache.commons.cli") {
        exports("org.apache.commons.cli")
    }
    module("commons-collections:commons-collections", "org.apache.commons.collections")
    automaticModule("commons-logging:commons-logging", "org.apache.commons.logging")
    
    // when the Jar has a classifier - 'linux-x86_64' in this example:
    module("io.netty:netty-transport-native-epoll|linux-x86_64",
           "io.netty.transport.epoll.linux.x86_64") 
    // when you somehow cannot address a Jar via coordinates, you may use the Jar name:
    module("commons-logging-1.2.jar", "org.apache.commons.loggin")
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
If you use the [shadow plugin](https://plugins.gradle.org/plugin/com.github.johnrengelman.shadow) and [encounter this issue](https://github.com/gradlex-org/extra-java-module-info/issues/7),
you can deactivate it for the runtime classpath as the module information is irrelevant for a fat Jar in any case.

**Kotlin DSL**
```
// Disable for a single Classpath (Configuration)
configurations {
    runtimeClasspath { // testRuntimeClasspath, testCompileClasspath, ... 
        attributes { attribute(Attribute.of("javaModule", Boolean::class.javaObjectType), false) }
    }
}

// Disable for all 'annotationProcessor' paths
sourceSets.all {
    configurations.getByName(annotationProcessorConfigurationName) {
        attributes { attribute(Attribute.of("javaModule", Boolean::class.javaObjectType), false) }
    }
}
```

**Groovy DSL**
```
// Disable for a single Classpath (Configuration)
configurations {
    runtimeClasspath { // testRuntimeClasspath, testCompileClasspath, ... 
        attributes { attribute(Attribute.of("javaModule", Boolean), false) }
    }
}

// Disable for all 'annotationProcessor' paths
sourceSets.all {
    configurations.getByName(annotationProcessorConfigurationName) {
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

## Should I use real modules or automatic modules?

Only if you use _real_ modules (Jars with `module-info.class`) everywhere you can use all features of the Java Module System
(see e.g. [#38](https://github.com/gradlex-org/extra-java-module-info/issues/38) for why it may be problematic to depend on an automatic module).
Still, using automatic modules is more convenient if you need to work with a lot of legacy libraries, because you do not need to define `exports` and `requires` directives.
Alternatively though, this plugin offers a way to define a _real_ module, without defining all of those directives explicitly:

```
extraJavaModuleInfo {
    module("org.apache.httpcomponents:httpclient", "org.apache.httpcomponents.httpclient") {
        exportAllPackages() // Adds an `exports` for each package found in the Jar
        requireAllDefinedDependencies() // Adds `requires (transitive|static)` directives based on dependencies defined in the component's metadata
    }
}
```

## How can I avoid that the same Jar is transformed multiple times when using requireAllDefinedDependencies?

When using the `requireAllDefinedDependencies` option, all metadata of the dependencies on your classpath is input to the Jar transformation.
In a multi-project however, each subproject typically has different classpaths and not all metadata is available everywhere.
This leads to a situation, where Gradle's transformation system does not know if transforming the same Jar will lead to the same result.
Then, the same Jar is transformed many times. This is not necessary a problem, as the results of the transforms are cached
and do not run on every build invocation. However, the effect of this is still visible:
for example when you import the project in IntelliJ IDEA.
You see the same dependency many times in the _External Libraries_ list and IntelliJ is doing additional indexing work.

To circumvent this, you need to construct a common classpath – as a _resolvable configuration_ – that the transform can use.
This needs to be done in all subprojects. You use the `versionsProvidingConfiguration` to tell the plugin about the commons classpath.

```
extraJavaModuleInfo {
    versionsProvidingConfiguration = "mainRuntimeClasspath"
}
```

To create such a common classpath, some setup work is needed.
And it depends on your overall project structure if and how to do that.
Here is an example setup you may use:

```
val consistentResolutionAttribute = Attribute.of("consistent-resolution", String::class.java)

// Define an Outgoing Variant (aka Consumable Configuration) that knows about all dependencies
configurations.create("allDependencies") {
    isCanBeConsumed = true
    isCanBeResolved = false
    sourceSets.all {
        extendsFrom(
            configurations[this.implementationConfigurationName],
            configurations[this.compileOnlyConfigurationName],
            configurations[this.runtimeOnlyConfigurationName],
            configurations[this.annotationProcessorConfigurationName]
        )
    }
    attributes { attribute(consistentResolutionAttribute, "global") }
}

// Define a "global claspath" (as Resolvable Configuration)
val mainRuntimeClasspath = configurations.create("mainRuntimeClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes.attribute(consistentResolutionAttribute, "global")
}

// Add a dependency to the 'main' project(s) (:app ins this example) that transitively 
// depend on all subprojects to create a depenedency graph wih "everything"
dependencies { mainRuntimeClasspath(project(":app")) }

// Use the global classpath for consisten resolution (optional)
configurations.runtimeClasspath {
    shouldResolveConsistentlyWith(mainRuntimeClasspath)
}
```

## I have many automatic modules in my project. How can I convert them into proper modules and control what they export or require?

The plugin provides a set of `<sourceSet>moduleDescriptorRecommendations` tasks that generate the real module declarations utilizing [jdeps](https://docs.oracle.com/en/java/javase/11/tools/jdeps.html) and dependency metadata.

This task generates module info spec for the JARs that do not contain the proper `module-info.class` descriptors.

NOTE: This functionality requires Gradle to be run with Java 11+ and failing on missing module information should be disabled via `failOnMissingModuleInfo.set(false)`.

## How can I ensure there are no automatic modules in my dependency graph?

If your goal is to fully modularize your application, you should enable the following configuration setting, which is disabled by default.

```
extraJavaModuleInfo {
    failOnAutomaticModules.set(true)
}
```

With this setting enabled, the build will fail unless you define a module override for every automatic module that appears in your dependency tree, as shown below.

```
dependencies {
    implementation("org.yaml:snakeyaml:1.33")
}             
extraJavaModuleInfo {
    failOnAutomaticModules.set(true)
    module("org.yaml:snakeyaml", "org.yaml.snakeyaml") {
        closeModule()
        exports("org.yaml.snakeyaml")
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

In some cases, it may also be sufficient to remove appearances of the problematic package completely from some of the Jars.
This can be the case if classes are in fact duplicated, or if classes are not used.
For this, you can utilise the `removePackage` functionality:

```
extraJavaModuleInfo {
    module("xerces:xercesImpl", "xerces") {
        removePackage("org.w3c.dom.html")
        // ...
    }
}
```

## How can I fix a library with a broken `module-info.class`?

To fix a library with a broken `module-info.class`, you can override the modular descriptor in the same way it is done with non-modular JARs.
However, you need to specify `patchRealModule()` to overwrite the existing `module-info.class`.
You can also use `preserveExisting()`, if the exiting `module-info.class` is working in general, but misses entries.

```
extraJavaModuleInfo {                
    module("org.apache.tomcat.embed:tomcat-embed-core", "org.apache.tomcat.embed.core") {
        patchRealModule()   // overwrite existing module-info.class
        preserveExisting()  // extend existing module-info.class 
        requires("java.desktop")
        requires("java.instrument")
        ...
    }
}    
```

This opt-in behavior is designed to prevent over-patching real modules, especially during version upgrades. For example, when a newer version of a library already contains the proper `module-info.class`, the extra module info overrides should be removed.

## Can't things just work™ without all that configuration?

If you use legacy libraries and want to use the Java Module System with all its features, you should patch all Jars to include a `module-info`.
However, if you get started and just want things to be put on the Module Path, you can set the following option:

```
extraJavaModuleInfo {
    deriveAutomaticModuleNamesFromFileNames.set(true)
}
```

Now, also Jars that do not have a `module-info.class` and no `Automatic-Module-Name` entry will automatically be processed to get an `Automatic-Module-Name` based on the Jar file name.
This feature is helpful if you start to migrate an existing project to the Module Path.
The pivotal feature of this plugin though, is to add a complete `module-info.class` to all Jars using the `module(...)` patch option for each legacy Jar individually.

# Disclaimer

Gradle and the Gradle logo are trademarks of Gradle, Inc.
The GradleX project is not endorsed by, affiliated with, or associated with Gradle or Gradle, Inc. in any way.

version = "1.13.1"

dependencies { implementation("org.ow2.asm:asm:9.9") }

publishingConventions {
    pluginPortal("${project.group}.${project.name}") {
        implementationClass("org.gradlex.javamodule.moduleinfo.ExtraJavaModuleInfoPlugin")
        displayName("Extra Java Module Info Gradle Plugin")
        description("Add module information to legacy Java libraries.")
        tags("gradlex", "java", "modularity", "jigsaw", "jpms")
    }
    gitHub("https://github.com/gradlex-org/extra-java-module-info")
    developer {
        id.set("jjohannes")
        name.set("Jendrik Johannes")
        email.set("jendrik@gradlex.org")
    }
}

testingConventions { testGradleVersions("6.8.3", "6.9.4", "7.6.5", "8.14.2") }

// === the following custom configuration should be removed once tests are migrated to Java
apply(plugin = "groovy")

tasks.named<GroovyCompile>("compileTestGroovy") { targetCompatibility = "11" } // allow tests to run against 6.x

dependencies { testImplementation("org.spockframework:spock-core:2.3-groovy-4.0") } //
// ====================================================================================

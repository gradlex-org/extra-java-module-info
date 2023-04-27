package org.gradlex.javamodule.moduleinfo.test

import org.gradlex.javamodule.moduleinfo.test.fixture.GradleBuild
import org.gradlex.javamodule.moduleinfo.test.fixture.LegacyLibraries
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification

class ConfigurationDetailsFunctionalTest extends Specification {

    @Delegate
    GradleBuild build = new GradleBuild()

    def setup() {
        settingsFile << 'rootProject.name = "test-project"'
        buildFile << '''
            plugins {
                id("application")
                id("org.gradlex.extra-java-module-info")
            }
            application {
                mainModule.set("org.gradle.sample.app")
                mainClass.set("org.gradle.sample.app.Main")
            }
        '''
    }

    def "fails by default if no module info is defined for a legacy library"() {
        given:
        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app { }
        """
        buildFile << """
            dependencies { implementation("commons-cli:commons-cli:1.4")   }
        """

        expect:
        fail().task(':compileJava').outcome == TaskOutcome.FAILED
    }

    def "can opt-out of strict module requirement"() {
        given:
        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app { }
        """
        buildFile << """
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
        file("src/test/java/Test.java") << ""
        buildFile << """
            configurations {
                testRuntimeClasspath {
                    attributes { attribute(Attribute.of("javaModule", Boolean::class.javaObjectType), false) }
                }
                testCompileClasspath {
                    attributes { attribute(Attribute.of("javaModule", Boolean::class.javaObjectType), false) }
                }
            }
            dependencies { testImplementation("commons-cli:commons-cli:1.4") }
        """

        expect:
        build().task(':compileTestJava').outcome == TaskOutcome.SUCCESS
    }

    // See: https://github.com/jjohannes/extra-java-module-info/issues/23
    def "ignores MANIFEST.MF files that are not correctly positioned in Jar"() {
        given:
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            public class Main {
                public static void main(String[] args) throws Exception {                
                }
            }
        """

        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app {
                requires com.google.javascript.closure.compiler;
            }
        """
        buildFile << """
            dependencies {
                implementation("com.google.javascript:closure-compiler:v20211201")    
            }
            
            extraJavaModuleInfo {
                automaticModule(${new LegacyLibraries().closureCompiler}, "com.google.javascript.closure.compiler")
            }
            
            application {
                mainModule.set("org.gradle.sample.app")
                mainClass.set("org.gradle.sample.app.Main")
            }
        """

        expect:
        build().task(':compileJava').outcome == TaskOutcome.SUCCESS
    }

    // See: https://github.com/jjohannes/extra-java-module-info/issues/27
    def "correctly handles copying JAR contents on JDKs < 16"() {
        given:
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            public class Main {
                public static void main(String[] args) {     
                    System.out.println(org.w3c.css.sac.Parser.class);           
                }
            }
        """
        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app {
                requires sac;
            }
        """
        buildFile << """
            dependencies {
                implementation("org.w3c.css:sac:1.3")    
            }
            
            extraJavaModuleInfo {
                automaticModule(${new LegacyLibraries().sac}, "sac")
            }
            
            application {
                mainModule.set("org.gradle.sample.app")
                mainClass.set("org.gradle.sample.app.Main")
            }
        """

        expect:
        build().task(':compileJava').outcome == TaskOutcome.SUCCESS
    }

    def "can define version of module explicitly"() {
        given:
        def libs = new LegacyLibraries()
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            import org.apache.commons.cli.CommandLineParser;
            
            public class Main {
                public static void main(String[] args) throws Exception {
                    System.out.println(CommandLineParser.class.getModule().getName() + "=" + CommandLineParser.class.getModule().getDescriptor().version().get());
                    System.out.println(ModuleLayer.boot().findModule("org.apache.commons.collections").get().getName() + "=" + ModuleLayer.boot().findModule("org.apache.commons.collections").get().getDescriptor().version().get());
                }
            }
        """
        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app {
                requires org.apache.commons.cli;
                requires org.apache.commons.collections;
            }
        """
        buildFile << """
            dependencies {
                implementation("commons-cli:commons-cli:1.4")  
                implementation("commons-collections:commons-collections:3.2.2")
            }
            
            extraJavaModuleInfo {
                module(${libs.commonsCli}, "org.apache.commons.cli", "8.1") {
                    exports("org.apache.commons.cli")
                }
                module(${libs.commonsCollections}, "org.apache.commons.collections", "9.2")
            }
        """

        when:
        def result = run()

        then:
        result.task(':run').outcome == TaskOutcome.SUCCESS
        result.output.contains('org.apache.commons.cli=8.1')
        result.output.contains('org.apache.commons.collections=9.2')
    }

    def "automatically uses resolved version for module version"() {
        given:
        def libs = new LegacyLibraries()
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            import org.apache.commons.cli.CommandLineParser;
            
            public class Main {
                public static void main(String[] args) throws Exception {
                    System.out.println(CommandLineParser.class.getModule().getName() + "=" + CommandLineParser.class.getModule().getDescriptor().version().get());
                    System.out.println(ModuleLayer.boot().findModule("org.apache.commons.collections").get().getName() + "=" + ModuleLayer.boot().findModule("org.apache.commons.collections").get().getDescriptor().version().get());
                }
            }
        """
        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app {
                requires org.apache.commons.cli;
                requires org.apache.commons.collections;
                
                requires static jsr305;
            }
        """
        buildFile << """
            dependencies {
                implementation("commons-cli:commons-cli:1.4")  
                implementation("commons-collections:commons-collections:3.2.2")
                
                compileOnly("com.google.code.findbugs:jsr305:3.0.2")
            }
            
            extraJavaModuleInfo {
                module(${libs.commonsCli}, "org.apache.commons.cli") {
                    exports("org.apache.commons.cli")
                }
                module(${libs.commonsCollections}, "org.apache.commons.collections")

                automaticModule(${libs.jsr305}, "jsr305")
            }
        """

        when:
        def result = run()

        then:
        result.task(':run').outcome == TaskOutcome.SUCCESS
        result.output.contains('org.apache.commons.cli=1.4')
        result.output.contains('org.apache.commons.collections=3.2.2')
    }
}

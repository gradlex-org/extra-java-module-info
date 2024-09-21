package org.gradlex.javamodule.moduleinfo.test

import org.gradle.testkit.runner.TaskOutcome
import org.gradlex.javamodule.moduleinfo.test.fixture.GradleBuild
import spock.lang.Specification

class ClassifiedJarsFunctionalTest extends Specification {

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

    def "can address classified Jars via coordinates"() {
        given:
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            public class Main {
                public static void main(String[] args) { }
            }
        """
        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app {               
                requires io.netty.transport.epoll.linux.x86_64;
                requires io.netty.transport.epoll.linux.aarch_64;
            }
        """
        buildFile << """
            dependencies {
                implementation(platform("io.netty:netty-bom:4.1.110.Final"))
                implementation("io.netty:netty-transport-native-epoll:0:linux-x86_64")
                implementation("io.netty:netty-transport-native-epoll:0:linux-aarch_64")              
            }
            extraJavaModuleInfo {               
                module("io.netty:netty-transport-native-epoll|linux-x86_64", "io.netty.transport.epoll.linux.x86_64")
                module("io.netty:netty-transport-native-epoll|linux-aarch_64", "io.netty.transport.epoll.linux.aarch_64")
            }
        """

        expect:
        build().task(':compileJava').outcome == TaskOutcome.SUCCESS
    }

}

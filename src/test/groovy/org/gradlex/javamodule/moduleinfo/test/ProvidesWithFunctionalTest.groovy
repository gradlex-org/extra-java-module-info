package org.gradlex.javamodule.moduleinfo.test

import org.gradlex.javamodule.moduleinfo.test.fixture.GradleBuild
import spock.lang.Specification

class ProvidesWithFunctionalTest extends Specification {

    @Delegate
    GradleBuild build = new GradleBuild()

    def setup() {
        settingsFile << 'rootProject.name = "test-project"'
        buildFile << """
            plugins {
                id("application")
                id("org.gradlex.extra-java-module-info")
            }
            application {
                mainModule.set("org.gradle.sample.app")
                mainClass.set("org.gradle.sample.app.Main")
            }
        """
    }

    def "can define missing service providers"() {
        setup:
        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app {
                requires org.hibernate.sqlite;
                requires org.hibernate.orm.core;
                uses org.hibernate.dialect.Dialect;
            }
        """
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            public class Main {
                public static void main(String[] args) { 
                    java.util.ServiceLoader.load(org.hibernate.dialect.Dialect.class).forEach(
                        d -> System.out.println(d.getClass()));
                }
            }
        """
        buildFile << """     
            dependencies {
                implementation("com.github.gwenn:sqlite-dialect:0.2.0")
            }             
            extraJavaModuleInfo {
                module("com.github.gwenn:sqlite-dialect", "org.hibernate.sqlite") {
                    requires("java.sql")
                    requires("org.hibernate.orm.core")
                    provides("org.hibernate.dialect.Dialect", "org.sqlite.hibernate.dialect.SQLiteDialect")
                }
                module("org.hibernate:hibernate-core", "org.hibernate.orm.core") {
                    requires("java.persistence")
                    requires("java.sql")
                    exportAllPackages()
                }
                module("antlr:antlr", "org.antlr") { }
            }
        """

        expect:
        def result = run()
        result.output.contains("INFO: HHH000400: Using dialect: org.sqlite.hibernate.dialect.SQLiteDialect")
    }
}

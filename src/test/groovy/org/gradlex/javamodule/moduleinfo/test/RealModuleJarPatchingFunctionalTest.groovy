package org.gradlex.javamodule.moduleinfo.test

import org.gradlex.javamodule.moduleinfo.test.fixture.GradleBuild
import spock.lang.Specification

class RealModuleJarPatchingFunctionalTest extends Specification {

    @Delegate
    GradleBuild build = new GradleBuild()

    def setup() {
        settingsFile << 'rootProject.name = "test-project"'
        buildFile << '''
            plugins {
                id("java")
                id("org.gradlex.extra-java-module-info")
            }
                       
            tasks.register<JavaExec>("run") {
                mainClass.set("jdk.tools.jlink.internal.Main")
                mainModule.set("jdk.jlink")
                args = listOf(
                        "--module-path",
                        configurations.runtimeClasspath.get().asPath,
                        "--output",
                        "jlink-image-test",
                        "--add-modules",
                        "org.apache.tomcat.embed.core"
                )
            }
                                                
        '''
    }

    def "jlink fails because of a broken module descriptor"() {
        given:
        buildFile << ''' 
            dependencies {
                implementation("org.apache.tomcat.embed:tomcat-embed-core:10.1.13")
            }
            
            extraJavaModuleInfo {
            
            }                       
        '''

        expect:
        def out = failRun()
        out.output.contains("Packages that are exported or open in org.apache.tomcat.embed.core are not present: [org.apache.catalina.ssi]")
    }

    def "jlink succeeds with the patched module descriptor"() {
        given:
        buildFile << ''' 
            dependencies {
                implementation("org.apache.tomcat.embed:tomcat-embed-core:10.1.13")
            }
            
            extraJavaModuleInfo {                
                module("org.apache.tomcat.embed:tomcat-embed-core", "org.apache.tomcat.embed.core") {
                    patchRealModule()
                    requires("java.desktop")
                    requires("java.instrument")
                    requires("java.logging")
                    requires("java.rmi")
            
                    requiresStatic("jakarta.ejb")
                    requiresStatic("jakarta.mail")
                    requiresStatic("jakarta.persistence")
                    requiresStatic("jakarta.xml.ws")
                    requiresStatic("java.xml.ws")
            
                    requiresTransitive("jakarta.annotation")
                    requiresTransitive("java.management")
                    requiresTransitive("java.naming")
                    requiresTransitive("java.security.jgss")
                    requiresTransitive("java.sql")
                    requiresTransitive("java.xml")
            
                    exports("jakarta.security.auth.message")
                    exports("jakarta.security.auth.message.callback")
                    exports("jakarta.security.auth.message.config")
                    exports("jakarta.security.auth.message.module")
                    exports("jakarta.servlet")
                    exports("jakarta.servlet.annotation")
                    exports("jakarta.servlet.descriptor")
                    exports("jakarta.servlet.http")
                    exports("jakarta.servlet.resources")
                    exports("org.apache.catalina")
                    exports("org.apache.catalina.authenticator")
                    exports("org.apache.catalina.authenticator.jaspic")
                    exports("org.apache.catalina.connector")
                    exports("org.apache.catalina.core")
                    exports("org.apache.catalina.deploy")
                    exports("org.apache.catalina.filters")
                    exports("org.apache.catalina.loader")
                    exports("org.apache.catalina.manager")
                    exports("org.apache.catalina.manager.host")
                    exports("org.apache.catalina.manager.util")
                    exports("org.apache.catalina.mapper")
                    exports("org.apache.catalina.mbeans")
                    exports("org.apache.catalina.realm")
                    exports("org.apache.catalina.security")
                    exports("org.apache.catalina.servlets")
                    exports("org.apache.catalina.session")
                    // The only difference with the bundled module-info.class
                    // exports("org.apache.catalina.ssi")
                    exports("org.apache.catalina.startup")
                    exports("org.apache.catalina.users")
                    exports("org.apache.catalina.util")
                    exports("org.apache.catalina.valves")
                    exports("org.apache.catalina.valves.rewrite")
                    exports("org.apache.catalina.webresources")
                    exports("org.apache.catalina.webresources.war")
                    exports("org.apache.coyote")
                    exports("org.apache.coyote.ajp")
                    exports("org.apache.coyote.http11")
                    exports("org.apache.coyote.http11.filters")
                    exports("org.apache.coyote.http11.upgrade")
                    exports("org.apache.coyote.http2")
                    exports("org.apache.juli")
                    exports("org.apache.juli.logging")
                    exports("org.apache.naming")
                    exports("org.apache.naming.factory")
                    exports("org.apache.naming.java")
                    exports("org.apache.tomcat")
                    exports("org.apache.tomcat.jni")
                    exports("org.apache.tomcat.util")
                    exports("org.apache.tomcat.util.bcel.classfile")
                    exports("org.apache.tomcat.util.buf")
                    exports("org.apache.tomcat.util.codec.binary")
                    exports("org.apache.tomcat.util.collections")
                    exports("org.apache.tomcat.util.compat")
                    exports("org.apache.tomcat.util.descriptor")
                    exports("org.apache.tomcat.util.descriptor.tagplugin")
                    exports("org.apache.tomcat.util.descriptor.web")
                    exports("org.apache.tomcat.util.digester")
                    exports("org.apache.tomcat.util.file")
                    exports("org.apache.tomcat.util.http")
                    exports("org.apache.tomcat.util.http.fileupload")
                    exports("org.apache.tomcat.util.http.fileupload.disk")
                    exports("org.apache.tomcat.util.http.fileupload.impl")
                    exports("org.apache.tomcat.util.http.fileupload.servlet")
                    exports("org.apache.tomcat.util.http.fileupload.util")
                    exports("org.apache.tomcat.util.http.parser")
                    exports("org.apache.tomcat.util.log")
                    exports("org.apache.tomcat.util.modeler")
                    exports("org.apache.tomcat.util.modeler.modules")
                    exports("org.apache.tomcat.util.net")
                    exports("org.apache.tomcat.util.net.openssl")
                    exports("org.apache.tomcat.util.net.openssl.ciphers")
                    exports("org.apache.tomcat.util.res")
                    exports("org.apache.tomcat.util.scan")
                    exports("org.apache.tomcat.util.security")
                    exports("org.apache.tomcat.util.threads")
            
                    uses("org.apache.juli.logging.Log")                    
                }
            }               
        '''

        expect:
        run()
    }

    def "patching of real modules must be explicitly enabled"() {
        given:
        buildFile << ''' 
            dependencies {
                implementation("org.apache.tomcat.embed:tomcat-embed-core:10.1.13")
            }
                        
            extraJavaModuleInfo {                
                module("org.apache.tomcat.embed:tomcat-embed-core", "org.apache.tomcat.embed.core") {                    
                    requires("java.desktop")
                }
            }                       
        '''

        expect:
        def out = failRun()
        out.output.contains("Patching of real modules must be explicitly enabled with patchRealModule()")
    }

    def "a real module cannot be demoted to an automatic module"() {
        given:
        buildFile << ''' 
            dependencies {
                implementation("org.apache.tomcat.embed:tomcat-embed-core:10.1.13")
            }
                        
            extraJavaModuleInfo {                
                automaticModule("org.apache.tomcat.embed:tomcat-embed-core", "org.apache.tomcat.embed.core")
            }                       
        '''

        expect:
        def out = failRun()
        out.output.contains("Patching of real modules must be explicitly enabled with patchRealModule() and can only be done with `module` spec")
    }

}

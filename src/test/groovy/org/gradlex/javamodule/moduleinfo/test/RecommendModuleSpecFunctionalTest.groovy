package org.gradlex.javamodule.moduleinfo.test

import org.gradlex.javamodule.moduleinfo.test.fixture.GradleBuild
import spock.lang.Specification

class RecommendModuleSpecFunctionalTest extends Specification {

    @Delegate
    GradleBuild build = new GradleBuild()

    def setup() {
        settingsFile << 'rootProject.name = "test-project"'
    }

    def "generates a recommendation for an automatic module defined via Manifest"() {
        given:
        buildFile << ''' 
            plugins {
                id("java")
                id("org.gradlex.extra-java-module-info")
            }
            dependencies {
                implementation("org.yaml:snakeyaml:1.33")
            }            
        '''

        expect:
        def out = task("moduleDescriptorRecommendations")
        out.output.contains('''
            |module("org.yaml:snakeyaml", "org.yaml.snakeyaml") {
            |    closeModule()
            |    requiresTransitive("java.desktop")
            |    requires("java.logging")
            |    exports("org.yaml.snakeyaml")
            |    exports("org.yaml.snakeyaml.comments")
            |    exports("org.yaml.snakeyaml.composer")
            |    exports("org.yaml.snakeyaml.constructor")
            |    exports("org.yaml.snakeyaml.emitter")
            |    exports("org.yaml.snakeyaml.env")
            |    exports("org.yaml.snakeyaml.error")
            |    exports("org.yaml.snakeyaml.events")
            |    exports("org.yaml.snakeyaml.extensions.compactnotation")
            |    exports("org.yaml.snakeyaml.external.biz.base64Coder")
            |    exports("org.yaml.snakeyaml.external.com.google.gdata.util.common.base")
            |    exports("org.yaml.snakeyaml.introspector")
            |    exports("org.yaml.snakeyaml.nodes")
            |    exports("org.yaml.snakeyaml.parser")
            |    exports("org.yaml.snakeyaml.reader")
            |    exports("org.yaml.snakeyaml.representer")
            |    exports("org.yaml.snakeyaml.resolver")
            |    exports("org.yaml.snakeyaml.scanner")
            |    exports("org.yaml.snakeyaml.serializer")
            |    exports("org.yaml.snakeyaml.tokens")
            |    exports("org.yaml.snakeyaml.util")
            |}'''.stripMargin())
    }

    def "does not provide recommendations for already modular jars"() {
        given:
        buildFile << ''' 
            plugins {
                id("java")
                id("org.gradlex.extra-java-module-info")
            }
            
            extraJavaModuleInfo {
                failOnMissingModuleInfo.set(false)
            }
            
            dependencies {                
                implementation("com.fasterxml.jackson.core:jackson-databind:2.15.3")
            }            
        '''

        expect:
        def out = task("moduleDescriptorRecommendations")
        out.output.contains('All good. Looks like all the dependencies have the proper module-info.class defined')
        !out.output.contains('module(')
    }

    def "supports different source sets"() {
        given:
        buildFile << ''' 
            plugins {
                id("java")
                id("org.gradlex.extra-java-module-info")
            }
            
            extraJavaModuleInfo {
                failOnMissingModuleInfo.set(false)
            }
            
            dependencies {                
                implementation("jakarta.servlet:jakarta.servlet-api:4.0.2")
                testImplementation("junit:junit:4.13.2")
            }            
        '''

        expect:
        def out = task("testModuleDescriptorRecommendations")
        out.output.contains('''
        |module("jakarta.servlet:jakarta.servlet-api", "jakarta.servlet.api") {
        |    closeModule()
        |    exports("javax.servlet")
        |    exports("javax.servlet.annotation")
        |    exports("javax.servlet.descriptor")
        |    exports("javax.servlet.http")
        |}
        |module("junit:junit", "junit") {
        |    closeModule()
        |    requiresTransitive("hamcrest.core")
        |    exports("junit.extensions")
        |    exports("junit.framework")
        |    exports("junit.runner")
        |    exports("junit.textui")
        |    exports("org.junit")
        |    exports("org.junit.experimental")
        |    exports("org.junit.experimental.categories")
        |    exports("org.junit.experimental.max")
        |    exports("org.junit.experimental.results")
        |    exports("org.junit.experimental.runners")
        |    exports("org.junit.experimental.theories")
        |    exports("org.junit.experimental.theories.internal")
        |    exports("org.junit.experimental.theories.suppliers")
        |    exports("org.junit.function")
        |    exports("org.junit.internal")
        |    exports("org.junit.internal.builders")
        |    exports("org.junit.internal.management")
        |    exports("org.junit.internal.matchers")
        |    exports("org.junit.internal.requests")
        |    exports("org.junit.internal.runners")
        |    exports("org.junit.internal.runners.model")
        |    exports("org.junit.internal.runners.rules")
        |    exports("org.junit.internal.runners.statements")
        |    exports("org.junit.matchers")
        |    exports("org.junit.rules")
        |    exports("org.junit.runner")
        |    exports("org.junit.runner.manipulation")
        |    exports("org.junit.runner.notification")
        |    exports("org.junit.runners")
        |    exports("org.junit.runners.model")
        |    exports("org.junit.runners.parameterized")
        |    exports("org.junit.validator")
        |}
        |module("org.hamcrest:hamcrest-core", "hamcrest.core") {
        |    closeModule()
        |    exports("org.hamcrest")
        |    exports("org.hamcrest.core")
        |    exports("org.hamcrest.internal")
        |}'''.stripMargin())
    }

    def "generates a recommendation for a module that is missing `Automatic-Module-Name` in manifest"() {
        given:
        buildFile << ''' 
            plugins {
                id("java")
                id("org.gradlex.extra-java-module-info")
            }
            
            extraJavaModuleInfo {
                failOnMissingModuleInfo.set(false)
            }
            
            dependencies {
                implementation("javax.inject:javax.inject:1")
            }            
        '''

        expect:
        def out = task("moduleDescriptorRecommendations")
        out.output.contains('''
            |module("javax.inject:javax.inject", "javax.inject") {
            |    closeModule()
            |    exports("javax.inject")
            |}'''.stripMargin())
    }

    def "generates multiple recommendations based on a large classpath configuration"() {
        given:
        buildFile << ''' 
            plugins {
                id("java")
                id("org.gradlex.extra-java-module-info")
            }
            
            extraJavaModuleInfo {
                failOnMissingModuleInfo.set(false)
            }
            
            dependencies {
                implementation("org.springframework:spring-webmvc:5.3.30")
                implementation("com.fasterxml.jackson.core:jackson-databind:2.15.3")
            }            
        '''

        expect:
        def out = task("moduleDescriptorRecommendations")
        out.output.contains('''
            |module("org.springframework:spring-aop", "spring.aop") {
            |    closeModule()
            |    requiresTransitive("java.xml")
            |    requiresTransitive("spring.beans")
            |    requiresTransitive("spring.core")
            |    requiresTransitive("spring.jcl")
            |    exports("org.aopalliance.aop")
            |    exports("org.aopalliance.intercept")
            |    exports("org.springframework.aop")
            |    exports("org.springframework.aop.aspectj")
            |    exports("org.springframework.aop.aspectj.annotation")
            |    exports("org.springframework.aop.aspectj.autoproxy")
            |    exports("org.springframework.aop.config")
            |    exports("org.springframework.aop.framework")
            |    exports("org.springframework.aop.framework.adapter")
            |    exports("org.springframework.aop.framework.autoproxy")
            |    exports("org.springframework.aop.framework.autoproxy.target")
            |    exports("org.springframework.aop.interceptor")
            |    exports("org.springframework.aop.scope")
            |    exports("org.springframework.aop.support")
            |    exports("org.springframework.aop.support.annotation")
            |    exports("org.springframework.aop.target")
            |    exports("org.springframework.aop.target.dynamic")
            |}
            |module("org.springframework:spring-beans", "spring.beans") {
            |    closeModule()
            |    requiresTransitive("java.desktop")
            |    requiresTransitive("java.prefs")
            |    requiresTransitive("java.xml")
            |    requiresTransitive("spring.core")
            |    requiresTransitive("spring.jcl")
            |    exports("org.springframework.beans")
            |    exports("org.springframework.beans.annotation")
            |    exports("org.springframework.beans.factory")
            |    exports("org.springframework.beans.factory.annotation")
            |    exports("org.springframework.beans.factory.config")
            |    exports("org.springframework.beans.factory.groovy")
            |    exports("org.springframework.beans.factory.parsing")
            |    exports("org.springframework.beans.factory.serviceloader")
            |    exports("org.springframework.beans.factory.support")
            |    exports("org.springframework.beans.factory.wiring")
            |    exports("org.springframework.beans.factory.xml")
            |    exports("org.springframework.beans.propertyeditors")
            |    exports("org.springframework.beans.support")
            |}
            |module("org.springframework:spring-context", "spring.context") {
            |    closeModule()
            |    requiresTransitive("java.desktop")
            |    requiresTransitive("java.instrument")
            |    requiresTransitive("java.management")
            |    requiresTransitive("java.naming")
            |    requiresTransitive("java.rmi")
            |    requiresTransitive("java.scripting")
            |    requiresTransitive("java.xml")
            |    requiresTransitive("jdk.httpserver")
            |    requiresTransitive("spring.aop")
            |    requiresTransitive("spring.beans")
            |    requiresTransitive("spring.core")
            |    requiresTransitive("spring.expression")
            |    requiresTransitive("spring.jcl")
            |    exports("org.springframework.cache")
            |    exports("org.springframework.cache.annotation")
            |    exports("org.springframework.cache.concurrent")
            |    exports("org.springframework.cache.config")
            |    exports("org.springframework.cache.interceptor")
            |    exports("org.springframework.cache.support")
            |    exports("org.springframework.context")
            |    exports("org.springframework.context.annotation")
            |    exports("org.springframework.context.config")
            |    exports("org.springframework.context.event")
            |    exports("org.springframework.context.expression")
            |    exports("org.springframework.context.i18n")
            |    exports("org.springframework.context.index")
            |    exports("org.springframework.context.support")
            |    exports("org.springframework.context.weaving")
            |    exports("org.springframework.ejb.access")
            |    exports("org.springframework.ejb.config")
            |    exports("org.springframework.format")
            |    exports("org.springframework.format.annotation")
            |    exports("org.springframework.format.datetime")
            |    exports("org.springframework.format.datetime.joda")
            |    exports("org.springframework.format.datetime.standard")
            |    exports("org.springframework.format.number")
            |    exports("org.springframework.format.number.money")
            |    exports("org.springframework.format.support")
            |    exports("org.springframework.instrument.classloading")
            |    exports("org.springframework.instrument.classloading.glassfish")
            |    exports("org.springframework.instrument.classloading.jboss")
            |    exports("org.springframework.instrument.classloading.tomcat")
            |    exports("org.springframework.instrument.classloading.weblogic")
            |    exports("org.springframework.instrument.classloading.websphere")
            |    exports("org.springframework.jmx")
            |    exports("org.springframework.jmx.access")
            |    exports("org.springframework.jmx.export")
            |    exports("org.springframework.jmx.export.annotation")
            |    exports("org.springframework.jmx.export.assembler")
            |    exports("org.springframework.jmx.export.metadata")
            |    exports("org.springframework.jmx.export.naming")
            |    exports("org.springframework.jmx.export.notification")
            |    exports("org.springframework.jmx.support")
            |    exports("org.springframework.jndi")
            |    exports("org.springframework.jndi.support")
            |    exports("org.springframework.remoting")
            |    exports("org.springframework.remoting.rmi")
            |    exports("org.springframework.remoting.soap")
            |    exports("org.springframework.remoting.support")
            |    exports("org.springframework.scheduling")
            |    exports("org.springframework.scheduling.annotation")
            |    exports("org.springframework.scheduling.concurrent")
            |    exports("org.springframework.scheduling.config")
            |    exports("org.springframework.scheduling.support")
            |    exports("org.springframework.scripting")
            |    exports("org.springframework.scripting.bsh")
            |    exports("org.springframework.scripting.config")
            |    exports("org.springframework.scripting.groovy")
            |    exports("org.springframework.scripting.support")
            |    exports("org.springframework.stereotype")
            |    exports("org.springframework.ui")
            |    exports("org.springframework.ui.context")
            |    exports("org.springframework.ui.context.support")
            |    exports("org.springframework.validation")
            |    exports("org.springframework.validation.annotation")
            |    exports("org.springframework.validation.beanvalidation")
            |    exports("org.springframework.validation.support")
            |}
            |module("org.springframework:spring-core", "spring.core") {
            |    closeModule()
            |    requiresTransitive("java.desktop")
            |    requiresTransitive("java.xml")
            |    requiresTransitive("jdk.unsupported")
            |    requiresTransitive("spring.jcl")
            |    requires("jdk.jfr")
            |    exports("org.springframework.asm")
            |    exports("org.springframework.cglib")
            |    exports("org.springframework.cglib.beans")
            |    exports("org.springframework.cglib.core")
            |    exports("org.springframework.cglib.core.internal")
            |    exports("org.springframework.cglib.proxy")
            |    exports("org.springframework.cglib.reflect")
            |    exports("org.springframework.cglib.transform")
            |    exports("org.springframework.cglib.transform.impl")
            |    exports("org.springframework.cglib.util")
            |    exports("org.springframework.core")
            |    exports("org.springframework.core.annotation")
            |    exports("org.springframework.core.codec")
            |    exports("org.springframework.core.convert")
            |    exports("org.springframework.core.convert.converter")
            |    exports("org.springframework.core.convert.support")
            |    exports("org.springframework.core.env")
            |    exports("org.springframework.core.io")
            |    exports("org.springframework.core.io.buffer")
            |    exports("org.springframework.core.io.support")
            |    exports("org.springframework.core.log")
            |    exports("org.springframework.core.metrics")
            |    exports("org.springframework.core.metrics.jfr")
            |    exports("org.springframework.core.serializer")
            |    exports("org.springframework.core.serializer.support")
            |    exports("org.springframework.core.style")
            |    exports("org.springframework.core.task")
            |    exports("org.springframework.core.task.support")
            |    exports("org.springframework.core.type")
            |    exports("org.springframework.core.type.classreading")
            |    exports("org.springframework.core.type.filter")
            |    exports("org.springframework.lang")
            |    exports("org.springframework.objenesis")
            |    exports("org.springframework.objenesis.instantiator")
            |    exports("org.springframework.objenesis.instantiator.android")
            |    exports("org.springframework.objenesis.instantiator.annotations")
            |    exports("org.springframework.objenesis.instantiator.basic")
            |    exports("org.springframework.objenesis.instantiator.gcj")
            |    exports("org.springframework.objenesis.instantiator.perc")
            |    exports("org.springframework.objenesis.instantiator.sun")
            |    exports("org.springframework.objenesis.instantiator.util")
            |    exports("org.springframework.objenesis.strategy")
            |    exports("org.springframework.util")
            |    exports("org.springframework.util.backoff")
            |    exports("org.springframework.util.comparator")
            |    exports("org.springframework.util.concurrent")
            |    exports("org.springframework.util.function")
            |    exports("org.springframework.util.unit")
            |    exports("org.springframework.util.xml")
            |    // ignoreServiceProvider("reactor.blockhound.integration.BlockHoundIntegration")
            |}
            |module("org.springframework:spring-expression", "spring.expression") {
            |    closeModule()
            |    requiresTransitive("spring.core")
            |    requires("spring.jcl")
            |    exports("org.springframework.expression")
            |    exports("org.springframework.expression.common")
            |    exports("org.springframework.expression.spel")
            |    exports("org.springframework.expression.spel.ast")
            |    exports("org.springframework.expression.spel.standard")
            |    exports("org.springframework.expression.spel.support")
            |}
            |module("org.springframework:spring-jcl", "spring.jcl") {
            |    closeModule()
            |    requires("java.logging")
            |    exports("org.apache.commons.logging")
            |    exports("org.apache.commons.logging.impl")
            |    // ignoreServiceProvider("org.apache.commons.logging.LogFactory")
            |}
            |module("org.springframework:spring-web", "spring.web") {
            |    closeModule()
            |    requiresTransitive("com.fasterxml.jackson.annotation")
            |    requiresTransitive("com.fasterxml.jackson.core")
            |    requiresTransitive("com.fasterxml.jackson.databind")
            |    requiresTransitive("java.desktop")
            |    requiresTransitive("java.xml")
            |    requiresTransitive("jdk.httpserver")
            |    requiresTransitive("spring.aop")
            |    requiresTransitive("spring.beans")
            |    requiresTransitive("spring.context")
            |    requiresTransitive("spring.core")
            |    requiresTransitive("spring.jcl")
            |    requires("java.rmi")
            |    exports("org.springframework.http")
            |    exports("org.springframework.http.client")
            |    exports("org.springframework.http.client.reactive")
            |    exports("org.springframework.http.client.support")
            |    exports("org.springframework.http.codec")
            |    exports("org.springframework.http.codec.cbor")
            |    exports("org.springframework.http.codec.json")
            |    exports("org.springframework.http.codec.multipart")
            |    exports("org.springframework.http.codec.protobuf")
            |    exports("org.springframework.http.codec.support")
            |    exports("org.springframework.http.codec.xml")
            |    exports("org.springframework.http.converter")
            |    exports("org.springframework.http.converter.cbor")
            |    exports("org.springframework.http.converter.feed")
            |    exports("org.springframework.http.converter.json")
            |    exports("org.springframework.http.converter.protobuf")
            |    exports("org.springframework.http.converter.smile")
            |    exports("org.springframework.http.converter.support")
            |    exports("org.springframework.http.converter.xml")
            |    exports("org.springframework.http.server")
            |    exports("org.springframework.http.server.reactive")
            |    exports("org.springframework.remoting.caucho")
            |    exports("org.springframework.remoting.httpinvoker")
            |    exports("org.springframework.remoting.jaxws")
            |    exports("org.springframework.web")
            |    exports("org.springframework.web.accept")
            |    exports("org.springframework.web.bind")
            |    exports("org.springframework.web.bind.annotation")
            |    exports("org.springframework.web.bind.support")
            |    exports("org.springframework.web.client")
            |    exports("org.springframework.web.client.support")
            |    exports("org.springframework.web.context")
            |    exports("org.springframework.web.context.annotation")
            |    exports("org.springframework.web.context.request")
            |    exports("org.springframework.web.context.request.async")
            |    exports("org.springframework.web.context.support")
            |    exports("org.springframework.web.cors")
            |    exports("org.springframework.web.cors.reactive")
            |    exports("org.springframework.web.filter")
            |    exports("org.springframework.web.filter.reactive")
            |    exports("org.springframework.web.jsf")
            |    exports("org.springframework.web.jsf.el")
            |    exports("org.springframework.web.method")
            |    exports("org.springframework.web.method.annotation")
            |    exports("org.springframework.web.method.support")
            |    exports("org.springframework.web.multipart")
            |    exports("org.springframework.web.multipart.commons")
            |    exports("org.springframework.web.multipart.support")
            |    exports("org.springframework.web.server")
            |    exports("org.springframework.web.server.adapter")
            |    exports("org.springframework.web.server.handler")
            |    exports("org.springframework.web.server.i18n")
            |    exports("org.springframework.web.server.session")
            |    exports("org.springframework.web.util")
            |    exports("org.springframework.web.util.pattern")
            |    // ignoreServiceProvider("javax.servlet.ServletContainerInitializer")
            |    // ignoreServiceProvider("reactor.blockhound.integration.BlockHoundIntegration")
            |}
            |module("org.springframework:spring-webmvc", "spring.webmvc") {
            |    closeModule()
            |    requiresTransitive("com.fasterxml.jackson.core")
            |    requiresTransitive("com.fasterxml.jackson.databind")
            |    requiresTransitive("java.desktop")
            |    requiresTransitive("java.scripting")
            |    requiresTransitive("java.xml")
            |    requiresTransitive("spring.beans")
            |    requiresTransitive("spring.context")
            |    requiresTransitive("spring.core")
            |    requiresTransitive("spring.jcl")
            |    requiresTransitive("spring.web")
            |    requires("com.fasterxml.jackson.annotation")
            |    requires("spring.aop")
            |    requires("spring.expression")
            |    exports("org.springframework.web.servlet")
            |    exports("org.springframework.web.servlet.config")
            |    exports("org.springframework.web.servlet.config.annotation")
            |    exports("org.springframework.web.servlet.function")
            |    exports("org.springframework.web.servlet.function.support")
            |    exports("org.springframework.web.servlet.handler")
            |    exports("org.springframework.web.servlet.i18n")
            |    exports("org.springframework.web.servlet.mvc")
            |    exports("org.springframework.web.servlet.mvc.annotation")
            |    exports("org.springframework.web.servlet.mvc.condition")
            |    exports("org.springframework.web.servlet.mvc.method")
            |    exports("org.springframework.web.servlet.mvc.method.annotation")
            |    exports("org.springframework.web.servlet.mvc.support")
            |    exports("org.springframework.web.servlet.resource")
            |    exports("org.springframework.web.servlet.support")
            |    exports("org.springframework.web.servlet.tags")
            |    exports("org.springframework.web.servlet.tags.form")
            |    exports("org.springframework.web.servlet.theme")
            |    exports("org.springframework.web.servlet.view")
            |    exports("org.springframework.web.servlet.view.document")
            |    exports("org.springframework.web.servlet.view.feed")
            |    exports("org.springframework.web.servlet.view.freemarker")
            |    exports("org.springframework.web.servlet.view.groovy")
            |    exports("org.springframework.web.servlet.view.json")
            |    exports("org.springframework.web.servlet.view.script")
            |    exports("org.springframework.web.servlet.view.tiles3")
            |    exports("org.springframework.web.servlet.view.xml")
            |    exports("org.springframework.web.servlet.view.xslt")
            |}'''.stripMargin())
    }

}

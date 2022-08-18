package org.gradlex.javamodule.moduleinfo.test.fixture

class LegacyLibraries {

    final boolean jarNameOnly

    LegacyLibraries(boolean jarNameOnly = false) {
        this.jarNameOnly = jarNameOnly
    }

    def closureCompiler = jarNameOnly ? "closure-compiler-v20211201.jar" : "com.google.javascript:closure-compiler"
    def commonsBeanutils = jarNameOnly ? "commons-beanutils-1.9.4.jar" : "commons-beanutils:commons-beanutils"
    def commonsCli = jarNameOnly ? "commons-cli-1.4.jar" : "commons-cli:commons-cli"
    def commonsCollections = jarNameOnly ? "commons-collections-3.2.2.jar" : "commons-collections:commons-collections"
    def commonsLogging = jarNameOnly ? "commons-logging-1.2.jar" : "commons-logging:commons-logging"
    def groovyAll = jarNameOnly ? "groovy-all-2.4.15.jar" : "org.codehaus.groovy:groovy-all"
    def javaxInject = jarNameOnly ? "javax.inject-1.jar" : "javax.inject:javax.inject"
    def jsr305 = jarNameOnly ? "jsr305-3.0.2.jar" : "com.google.code.findbugs:jsr305"
    def log4jCore = jarNameOnly ? "log4j-core-2.14.0.jar" : "org.apache.logging.log4j:log4j-core"
    def sac = jarNameOnly ? "sac-1.3.jar" : "org.w3c.css:sac"
    def slf4jApi = jarNameOnly ? "slf4j-api-1.7.32.jar" : "org.slf4j:slf4j-api"
    def slf4jExt = jarNameOnly ? "slf4j-ext-1.7.32.jar" : "org.slf4j:slf4j-ext"
    def springBootAutoconfigure = jarNameOnly ? "spring-boot-autoconfigure-2.4.2.jar" : "org.springframework.boot:spring-boot-autoconfigure"
    def zookeeper = jarNameOnly ? "zookeeper-3.8.0.jar" : "org.apache.zookeeper:zookeeper"
    def zookeeperJute = jarNameOnly ? "zookeeper-jute-3.8.0.jar" : "org.apache.zookeeper:zookeeper-jute"
}
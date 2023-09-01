package org.gradlex.javamodule.moduleinfo.test.fixture

class LegacyLibraries {

    final boolean jarNameOnly
    final boolean aliases

    LegacyLibraries(boolean jarNameOnly = false, boolean aliases = false) {
        this.jarNameOnly = jarNameOnly
        this.aliases = aliases
    }

    def closureCompiler = jarNameOnly ? '"closure-compiler-v20211201.jar"' : aliases ? 'libs.closure.compiler' : '"com.google.javascript:closure-compiler"'
    def commonsBeanutils = jarNameOnly ? '"commons-beanutils-1.9.4.jar"' : aliases ? 'libs.commons.beanutils' : '"commons-beanutils:commons-beanutils"'
    def commonsCli = jarNameOnly ? '"commons-cli-1.4.jar"' : aliases ? 'libs.commons.cli' : '"commons-cli:commons-cli"'
    def commonsCollections = jarNameOnly ? '"commons-collections-3.2.2.jar"' : aliases ? 'libs.commons.collections' : '"commons-collections:commons-collections"'
    def commonsHttpClient = jarNameOnly ? '"httpclient-4.5.14.jar"' : aliases ? 'libs.httpclient' : '"org.apache.httpcomponents:httpclient"'
    def commonsLogging = jarNameOnly ? '"commons-logging-1.2.jar"' : aliases ? 'libs.commons.logging' : '"commons-logging:commons-logging"'
    def groovyAll = jarNameOnly ? '"groovy-all-2.4.15.jar"' : aliases ? 'libs.groovy.all' : '"org.codehaus.groovy:groovy-all"'
    def javaxInject = jarNameOnly ? '"javax.inject-1.jar"' : aliases ? 'libs.javax.inject' : '"javax.inject:javax.inject"'
    def jsr305 = jarNameOnly ? '"jsr305-3.0.2.jar"' : aliases ? 'libs.jsr305' : '"com.google.code.findbugs:jsr305"'
    def log4jCore = jarNameOnly ? '"log4j-core-2.14.0.jar"' : aliases ? 'libs.log4j.core' : '"org.apache.logging.log4j:log4j-core"'
    def qpidJmsClient = jarNameOnly ? '"qpid-jms-client-2.2.0.jar"' : aliases ? 'libs.qpid.jms.client' : '"org.apache.qpid:qpid-jms-client"'
    def qpidJmsDiscovery = jarNameOnly ? '"qpid-jms-discovery-2.2.0.jar"' : aliases ? 'libs.qpid.jms.discovery' : '"org.apache.qpid:qpid-jms-discovery"'
    def sac = jarNameOnly ? '"sac-1.3.jar"' : aliases ? 'libs.sac' : '"org.w3c.css:sac"'
    def slf4jApi = jarNameOnly ? '"slf4j-api-1.7.32.jar"' : aliases ? 'libs.slf4j.api' : '"org.slf4j:slf4j-api"'
    def slf4jExt = jarNameOnly ? '"slf4j-ext-1.7.32.jar"' : aliases ? 'libs.slf4j.ext' : '"org.slf4j:slf4j-ext"'
    def springBootAutoconfigure = jarNameOnly ? '"spring-boot-autoconfigure-2.4.2.jar"' : aliases ? 'libs.spring.boot.autoconfigure' : '"org.springframework.boot:spring-boot-autoconfigure"'
    def zookeeper = jarNameOnly ? '"zookeeper-3.8.0.jar"' : aliases ? 'libs.zookeeper.core' : '"org.apache.zookeeper:zookeeper"'
    def zookeeperJute = jarNameOnly ? '"zookeeper-jute-3.8.0.jar"' : aliases ? 'libs.zookeeper.jute' : '"org.apache.zookeeper:zookeeper-jute"'

    static String catalog() {
        LegacyLibraries aliases = new LegacyLibraries(false, true)
        LegacyLibraries coordinates = new LegacyLibraries(false, false)

        """
            [libraries]
            ${alias(aliases.closureCompiler)} = '${unquote(coordinates.closureCompiler)}:1'
            ${alias(aliases.commonsBeanutils)} = '${unquote(coordinates.commonsBeanutils)}:1'
            ${alias(aliases.commonsCli)} = '${unquote(coordinates.commonsCli)}:1'
            ${alias(aliases.commonsCollections)} = '${unquote(coordinates.commonsCollections)}:1'
            ${alias(aliases.commonsHttpClient)} = '${unquote(coordinates.commonsHttpClient)}:1'
            ${alias(aliases.commonsLogging)} = '${unquote(coordinates.commonsLogging)}:1'
            ${alias(aliases.groovyAll)} = '${unquote(coordinates.groovyAll)}:1'
            ${alias(aliases.javaxInject)} = '${unquote(coordinates.javaxInject)}:1'
            ${alias(aliases.jsr305)} = '${unquote(coordinates.jsr305)}:1'
            ${alias(aliases.log4jCore)} = '${unquote(coordinates.log4jCore)}:1'
            ${alias(aliases.qpidJmsClient)} = '${unquote(coordinates.qpidJmsClient)}:1'
            ${alias(aliases.qpidJmsDiscovery)} = '${unquote(coordinates.qpidJmsDiscovery)}:1'
            ${alias(aliases.sac)} = '${unquote(coordinates.sac)}:1'
            ${alias(aliases.slf4jApi)} = '${unquote(coordinates.slf4jApi)}:1'
            ${alias(aliases.slf4jExt)} = '${unquote(coordinates.slf4jExt)}:1'
            ${alias(aliases.springBootAutoconfigure)} = '${unquote(coordinates.springBootAutoconfigure)}:1'
            ${alias(aliases.zookeeper)} = '${unquote(coordinates.zookeeper)}:1'
            ${alias(aliases.zookeeperJute)} = '${unquote(coordinates.zookeeperJute)}:1'
        """.stripIndent()
    }

    private static String alias(String alias) {
        alias.substring('libs.'.length()).replace('.', '-')
    }

    private static String unquote(String coordinates) {
        coordinates.replace('"', '')
    }
}
package org.gradlex.javamodule.moduleinfo.test

import org.gradlex.javamodule.moduleinfo.test.fixture.GradleBuild
import org.gradlex.javamodule.moduleinfo.test.fixture.LegacyLibraries
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification

abstract class AbstractFunctionalTest extends Specification {

    abstract LegacyLibraries getLibs()

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

    def "can add module information to legacy library"() {
        given:
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            import com.google.gson.Gson;
            import org.apache.commons.beanutils.BeanUtils;
            import org.apache.commons.cli.CommandLine;
            import org.apache.commons.cli.CommandLineParser;
            import org.apache.commons.cli.DefaultParser;
            import org.apache.commons.cli.Options;
            import org.apache.commons.lang3.StringUtils;
            import org.gradle.sample.app.data.Message;
            
            public class Main {
            
                public static void main(String[] args) throws Exception {
                    Options options = new Options();
                    options.addOption("json", true, "data to parse");
                    options.addOption("debug", false, "prints module infos");
                    CommandLineParser parser = new DefaultParser();
                    CommandLine cmd = parser.parse(options, args);
            
                    if (cmd.hasOption("debug")) {
                        printModuleDebug(Main.class);
                        printModuleDebug(Gson.class);
                        printModuleDebug(StringUtils.class);
                        printModuleDebug(CommandLine.class);
                        printModuleDebug(BeanUtils.class);
                    }
            
                    String json = cmd.getOptionValue("json");
                    Message message = new Gson().fromJson(json == null ? "{}" : json, Message.class);
            
                    Object copy = BeanUtils.cloneBean(message);
                    System.out.println();
                    System.out.println("Original: " + copy.toString());
                    System.out.println("Copy:     " + copy.toString());
            
                }
            
                private static void printModuleDebug(Class<?> clazz) {
                    System.out.println(clazz.getModule().getName() + " - " + clazz.getModule().getDescriptor().version().get());
                }
            
            }
        """
        file("src/main/java/org/gradle/sample/app/data/Message.java") << """
            package org.gradle.sample.app.data;
            
            import java.util.List;
            import java.util.ArrayList;
            
            public class Message {
                private String message;
                private List<String> receivers = new ArrayList<>();
            
                public String getMessage() {
                    return message;
                }
            
                public void setMessage(String message) {
                    this.message = message;
                }
            
                public List<String> getReceivers() {
                    return receivers;
                }
            
                public void setReceivers(List<String> receivers) {
                    this.receivers = receivers;
                }
            
                @Override
                public String toString() {
                    return "Message{message='" + message + '\\'' +
                        ", receivers=" + receivers + '}';
                }
            }
        """
        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app {
                exports org.gradle.sample.app;
                opens org.gradle.sample.app.data; // allow Gson to access via reflection
            
                requires com.google.gson;
                requires org.apache.commons.lang3;
                requires org.apache.commons.cli;
                requires org.apache.commons.beanutils;
            }
        """
        buildFile << """          
            dependencies {
                implementation("com.google.code.gson:gson:2.8.6")           // real module
                implementation("net.bytebuddy:byte-buddy:1.10.9")           // real module with multi-release jar
                implementation("org.apache.commons:commons-lang3:3.10")     // automatic module
                implementation("commons-beanutils:commons-beanutils:1.9.4") // plain library (also brings in other libraries transitively)
                implementation("commons-cli:commons-cli:1.4")               // plain library        
            }
            
            extraJavaModuleInfo {
                module(${libs.commonsBeanutils}, "org.apache.commons.beanutils") {
                    exports("org.apache.commons.beanutils")
                    
                    requires("org.apache.commons.logging")
                    requires("java.sql")
                    requires("java.desktop")
                }
                module(${libs.commonsCli}, "org.apache.commons.cli") {
                    exports("org.apache.commons.cli")
                }
                module(${libs.commonsCollections}, "org.apache.commons.collections")
                automaticModule(${libs.commonsLogging}, "org.apache.commons.logging")
            }
        """

        expect:
        build().task(':compileJava').outcome == TaskOutcome.SUCCESS
    }

    def "can add Automatic-Module-Name to libraries without MANIFEST.MF"() {
        given:
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            import javax.inject.Singleton;
            
            public class Main {
            
                public static void main(String[] args)  {
                    printModuleDebug(Singleton.class);
                }
            
                private static void printModuleDebug(Class<?> clazz) {
                    System.out.println(clazz.getModule().getName() + " - " + clazz.getModule().getDescriptor().version().get());
                }
            
            }
        """
        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app {               
                requires javax.inject;               
            }
        """
        buildFile << """
            dependencies {
                implementation("javax.inject:javax.inject:1")                     
            }
            
            extraJavaModuleInfo {               
                automaticModule(${libs.javaxInject}, "javax.inject")
            }
        """

        expect:
        build().task(':compileJava').outcome == TaskOutcome.SUCCESS
    }

    def "can add module-info.class to libraries without MANIFEST.MF"() {
        given:
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            import javax.inject.Singleton;
            
            public class Main {
            
                public static void main(String[] args)  {
                    printModuleDebug(Singleton.class);
                }
            
                private static void printModuleDebug(Class<?> clazz) {
                    System.out.println(clazz.getModule().getName() + " - " + clazz.getModule().getDescriptor().version().get());
                }
            
            }
        """
        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app {               
                requires javax.inject;               
            }
        """
        buildFile << """
            dependencies {
                implementation("javax.inject:javax.inject:1")                     
            }
            
            extraJavaModuleInfo {               
                module(${libs.javaxInject}, "javax.inject") {
                    exports("javax.inject")
                }
            }
        """

        expect:
        build().task(':compileJava').outcome == TaskOutcome.SUCCESS
    }

    def "can retrofit META-INF/services/* metadata to module-info.class"() {
        given:
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            import java.util.ServiceLoader;
            import java.util.stream.Collectors;
            import org.apache.logging.log4j.spi.Provider;
            
            public class Main {
            
                public static void main(String[] args)  {
                    Provider provider = ServiceLoader.load(Provider.class).findFirst()
                                        .orElseThrow(() -> new AssertionError("No providers loaded"));
                    System.out.println(provider.getClass());
                }
                          
            }
        """
        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app {               
                requires org.apache.logging.log4j;
                requires org.apache.logging.log4j.core;
                
                uses org.apache.logging.log4j.spi.Provider;                              
            }
        """
        buildFile << """
            dependencies {
                implementation("org.apache.logging.log4j:log4j-core:2.14.0")                     
            }
            
            extraJavaModuleInfo {               
                module(${libs.log4jCore}, "org.apache.logging.log4j.core", "2.14.0") {
                    requires("java.compiler")
                    requires("java.desktop")
                    requires("org.apache.logging.log4j")
                    exports("org.apache.logging.log4j.core")
                }
            }
        """

        expect:
        run().task(':run').outcome == TaskOutcome.SUCCESS
    }

    def "can omit unwanted META-INF/services from automatic migration"() {
        given:
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            import java.util.ServiceLoader;
            import java.util.stream.Collectors;
            import javax.script.ScriptEngineFactory;
            import javax.script.ScriptException;
            
            public class Main {
            
                public static void main(String[] args) throws ScriptException {
                    ScriptEngineFactory scriptEngineFactory = ServiceLoader.load(ScriptEngineFactory.class)
                                        .findFirst()
                                        .orElseThrow(() -> new AssertionError("No providers loaded"));
                    String engineName = scriptEngineFactory.getEngineName();
                    if (!engineName.equals("Groovy Scripting Engine")) {
                        throw new AssertionError("Incorrect Script Engine Loaded: " + engineName);
                    }
                    int revVal = (int) scriptEngineFactory.getScriptEngine().eval("2+2");
                    if (revVal != 4) {
                        throw new AssertionError("Invalid evaluation result: " + revVal);
                    }
                }
                          
            }
        """
        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app {               
                requires groovy.all;
                uses javax.script.ScriptEngineFactory;                                                           
            }
        """
        buildFile << """
            dependencies {
                implementation("org.codehaus.groovy:groovy-all:2.4.15")                     
            }
            
            extraJavaModuleInfo {               
                module(${libs.groovyAll}, "groovy.all", "2.4.15") {
                   requiresTransitive("java.scripting")
                   requires("java.logging")
                   requires("java.desktop")
                   ignoreServiceProvider("org.codehaus.groovy.runtime.ExtensionModule")
                   ignoreServiceProvider("org.codehaus.groovy.plugins.Runners")
                   ignoreServiceProvider("org.codehaus.groovy.source.Extensions")
                }
            }
        """

        expect:
        run().task(':run').outcome == TaskOutcome.SUCCESS
    }

    def "only takes well-defined services from META-INF/services"() {
        given:
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            import org.apache.qpid.jms.JmsConnection;

            public class Main {
                public static void main(String[] args) { 
                    // Make sure files in '/META-INF/services/' that are not service provider entries are preserved
                    JmsConnection.class.getResource("/META-INF/services/org/apache/qpid/jms/provider/amqp").toString();
                }
            }
        """
        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app {               
                requires qpid.jms.client;                                                        
            }
        """
        buildFile << """
            dependencies {
                implementation("org.apache.qpid:qpid-jms-client:2.2.0")                 
            }
            
            extraJavaModuleInfo {
                module(${libs.qpidJmsClient}, "qpid.jms.client") {
                    exports("org.apache.qpid.jms")
                    requires("jakarta.messaging")
                }
            }
        """

        expect:
        run().task(':run').outcome == TaskOutcome.SUCCESS
    }

    def "only takes well-defined services from META-INF/services (merge Jar)"() {
        given:
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            import org.apache.qpid.jms.JmsConnection;

            public class Main {
                public static void main(String[] args) { 
                    // Make sure files in '/META-INF/services/' that are not service provider entries are preserved
                    JmsConnection.class.getResource("/META-INF/services/org/apache/qpid/jms/provider/amqp").toString();
                }
            }
        """
        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app {               
                requires qpid.jms.discovery;                                                        
            }
        """
        buildFile << """
            dependencies {
                implementation("org.apache.qpid:qpid-jms-discovery:2.2.0")
            }
            
            extraJavaModuleInfo {
                module(${libs.qpidJmsDiscovery}, "qpid.jms.discovery") {
                    exports("org.apache.qpid.jms")
                    requires("jakarta.messaging")
                    mergeJar("org.apache.qpid:qpid-jms-client")
                }
            }
        """

        expect:
        run().task(':run').outcome == TaskOutcome.SUCCESS
    }

    def "can add static/transitive 'requires' modifiers to legacy libraries"() {
        given:
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            import static java.lang.module.ModuleDescriptor.Requires;
            import static java.util.function.Function.identity;
            
            import java.lang.module.ModuleDescriptor;
            import java.util.Map;
            import java.util.stream.Collectors;
            
            public class Main {
            
                public static void main(String[] args) {
                    Map<String, Requires> index = ModuleLayer.boot()
                            .findModule("spring.boot.autoconfigure")
                            .map(Module::getDescriptor)
                            .map(ModuleDescriptor::requires)
                            .orElseThrow(() -> new AssertionError("Cannot find spring.boot.autoconfigure"))
                            .stream()
                            .collect(Collectors.toMap(Requires::name, identity()));
                    if (!index.get("spring.boot").modifiers().contains(Requires.Modifier.TRANSITIVE)) {
                        throw new AssertionError("spring.boot must be declared as transitive");
                    }
                    if (!index.get("com.google.gson").modifiers().contains(Requires.Modifier.STATIC)) {
                        throw new AssertionError("com.google.gson must be declared as static");
                    }
                    if (!index.get("spring.context").modifiers().isEmpty()) {
                        throw new AssertionError("spring.boot must not have any modifiers");
                    }
                }
            
            }
        """
        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app {               
                requires spring.boot.autoconfigure;
            }
        """
        buildFile << """
            dependencies {
                implementation("org.springframework.boot:spring-boot-autoconfigure:2.4.2") 
            }
            
            extraJavaModuleInfo {               
                module(${libs.springBootAutoconfigure}, "spring.boot.autoconfigure") {
                    requires("spring.context")
                    requiresTransitive("spring.boot")
                    requiresStatic("com.google.gson")
                }
            }
        """

        expect:
        run().task(':run').outcome == TaskOutcome.SUCCESS
    }

    def "can add 'uses' directives to legacy libraries"() {
        given:
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            import javax.cache.Caching;
            import javax.cache.CacheManager;
            
            public class Main {
            
                public static void main(String[] args)  {
                    CacheManager manager = Caching.getCachingProvider().getCacheManager();
                    System.out.println(manager.getClass());
                }
            
            }
        """
        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app {               
                requires cache.api;
            }
        """
        buildFile << """
            dependencies {
                implementation("javax.cache:cache-api:1.1.1") 
                runtimeOnly("com.github.ben-manes.caffeine:caffeine:3.1.2")
                runtimeOnly("com.github.ben-manes.caffeine:jcache:3.1.2")
            }
            
            extraJavaModuleInfo {               
                module("javax.cache:cache-api", "cache.api") {
                    failOnMissingModuleInfo.set(false)
                    exportAllPackages()
                    requireAllDefinedDependencies()
                    uses("javax.cache.spi.CachingProvider")
                }
            }
        """

        expect:
        run().task(':run').outcome == TaskOutcome.SUCCESS
    }

    def "can merge several jars into one module"() {
        given:
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            import org.apache.zookeeper.server.persistence.Util;
            import org.apache.zookeeper.server.persistence.FileHeader;
            
            import org.slf4j.Logger;
            import org.slf4j.NDC;
            
            public class Main {
                public static void main(String[] args) throws Exception {
                    Class<?> utilFromMain = Util.class;
                    Class<?> fileHeaderFromJute = FileHeader.class;
                    
                    Class<?> loggerFromApi = Logger.class;
                    Class<?> ndcFromExt = NDC.class;
                }
            }
        """
        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app {
                requires org.apache.zookeeper;
                requires org.slf4j;
            }
        """
        buildFile << """                  
            dependencies {
                implementation("org.apache.zookeeper:zookeeper:3.8.0")
                implementation("org.slf4j:slf4j-ext:1.7.32")
                
                ${libs.jarNameOnly? 'javaModulesMergeJars("org.apache.zookeeper:zookeeper")' : '' }
                ${libs.jarNameOnly? 'javaModulesMergeJars("org.slf4j:slf4j-ext")' : '' }
            }
            
            extraJavaModuleInfo {
                failOnMissingModuleInfo.set(false)
                module(${libs.zookeeper}, "org.apache.zookeeper") {
                    mergeJar(${libs.zookeeperJute})

                    exports("org.apache.jute")
                    exports("org.apache.zookeeper")
                    exports("org.apache.zookeeper.server.persistence")
                }
                automaticModule(${libs.slf4jApi}, "org.slf4j") {
                    mergeJar(${libs.slf4jExt})
                }
            }
            
            tasks.named("run") {
                inputs.files(configurations.runtimeClasspath)
                doLast { println(inputs.files.map { it.name }) }
            }
        """

        when:
        def result = run()

        then:
        result.output.contains('zookeeper-3.8.0-module.jar')
        !result.output.contains('zookeeper-3.8.0.jar')
        !result.output.contains('zookeeper-jute-3.8.0.jar')
        result.output.contains('slf4j-api-1.7.32-module.jar')
        !result.output.contains('slf4j-api-1.7.32.jar')
        !result.output.contains('slf4j-ext-1.7.32.jar')
    }

    def "merges service provider files of several jars"() {
        given:
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;

            public class Main {
                public static void main(String[] args) {
                }
            }
        """
        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app {
                requires org.apache.qpid.broker;
            }
        """
        buildFile << """   
            dependencies {
                implementation(platform("org.apache.qpid:qpid-broker-parent:8.0.6"))
                javaModulesMergeJars(platform("org.apache.qpid:qpid-broker-parent:8.0.6"))
                
                implementation("org.apache.qpid:qpid-broker-core")
                implementation("org.apache.qpid:qpid-broker-plugins-amqp-1-0-protocol")
                implementation("org.apache.qpid:qpid-broker-plugins-memory-store")
                implementation("org.apache.qpid:qpid-jms-client")
                implementation("org.apache.qpid:qpid-broker-plugins-management-http")
                implementation("org.apache.qpid:qpid-broker-plugins-websocket")
            }               
            
            extraJavaModuleInfo {
                failOnMissingModuleInfo.set(false)
                automaticModule("org.apache.qpid:qpid-broker-core", "org.apache.qpid.broker") {
                    mergeJar("org.apache.qpid:qpid-broker-plugins-amqp-1-0-protocol")
                    mergeJar("org.apache.qpid:qpid-broker-plugins-memory-store")
                    mergeJar("org.apache.qpid:qpid-jms-client")
                    mergeJar("org.apache.qpid:qpid-broker-plugins-management-http")
                    mergeJar("org.apache.qpid:qpid-broker-plugins-websocket")
                }
            }
            
            tasks.named("run") {
                val archives = objects.newInstance(ServiceInjection::class.java).archiveOperations
                inputs.files(configurations.runtimeClasspath.get().filter { 
                    it.name == "qpid-broker-core-8.0.6-module.jar"
                }.elements.map { archives.zipTree(it.single()) })
                doLast {
                     println(
                        inputs.files.find {
                            it.path.endsWith("/META-INF/services/org.apache.qpid.server.plugin.ConfiguredObjectRegistration")
                        }!!.readText()
                    )
                }
            }
            interface ServiceInjection {
                @get:Inject
                val archiveOperations: ArchiveOperations
            }
        """

        when:
        def result = run()

        then:
        result.output.contains('''
org.apache.qpid.server.security.auth.manager.ConfiguredObjectRegistrationImpl
org.apache.qpid.server.model.adapter.ConfiguredObjectRegistrationImpl
org.apache.qpid.server.model.ConfiguredObjectRegistrationImpl
org.apache.qpid.server.virtualhost.ConfiguredObjectRegistrationImpl
org.apache.qpid.server.security.group.ConfiguredObjectRegistrationImpl
org.apache.qpid.server.security.group.cloudfoundry.ConfiguredObjectRegistrationImpl
org.apache.qpid.server.virtualhostnode.ConfiguredObjectRegistrationImpl
org.apache.qpid.server.security.auth.manager.oauth2.ConfiguredObjectRegistrationImpl
org.apache.qpid.server.exchange.ConfiguredObjectRegistrationImpl
org.apache.qpid.server.model.port.ConfiguredObjectRegistrationImpl
org.apache.qpid.server.queue.ConfiguredObjectRegistrationImpl
org.apache.qpid.server.security.ConfiguredObjectRegistrationImpl
org.apache.qpid.server.protocol.v1_0.ConfiguredObjectRegistrationImpl
org.apache.qpid.server.virtualhostnode.memory.ConfiguredObjectRegistrationImpl
org.apache.qpid.server.store.ConfiguredObjectRegistrationImpl
org.apache.qpid.server.virtualhost.memory.ConfiguredObjectRegistrationImpl
org.apache.qpid.server.management.plugin.ConfiguredObjectRegistrationImpl''')
    }

    def "can automatically export all packages of a legacy library"() {
        given:
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            public class Main {
                public static void main(String[] args) {
                    org.apache.commons.collections.Bag a;
                    org.apache.commons.collections.bag.HashBag b;
                    org.apache.commons.collections.bidimap.DualHashBidiMap c;
                    org.apache.commons.collections.buffer.BlockingBuffer e;
                    org.apache.commons.collections.collection.CompositeCollection f;
                    org.apache.commons.collections.comparators.BooleanComparator g;
                    org.apache.commons.collections.functors.AllPredicate h;
                    org.apache.commons.collections.iterators.ArrayIterator i;
                    org.apache.commons.collections.keyvalue.MultiKey j;
                    org.apache.commons.collections.list.LazyList k;
                    org.apache.commons.collections.map.FixedSizeMap l;
                    org.apache.commons.collections.set.TypedSet m;
                }            
            }
        """
        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app {
                exports org.gradle.sample.app;
                
                requires org.apache.commons.collections;
            }
        """
        buildFile << """          
            dependencies {
                implementation("commons-collections:commons-collections:3.2.2")
            }
            
            extraJavaModuleInfo {
                module(${libs.commonsCollections}, "org.apache.commons.collections") {
                    exportAllPackages()
                }
            }
        """

        expect:
        run().task(':run').outcome == TaskOutcome.SUCCESS
    }

    def "can add module information to legacy library"() {
        given:
        file("src/main/java/org/gradle/sample/app/Main.java") << """
            package org.gradle.sample.app;
            
            import com.google.gson.Gson;
            import org.apache.commons.beanutils.BeanUtils;
            import org.apache.commons.cli.CommandLine;
            import org.apache.commons.cli.CommandLineParser;
            import org.apache.commons.cli.DefaultParser;
            import org.apache.commons.cli.Options;
            import org.apache.commons.lang3.StringUtils;
            import org.gradle.sample.app.data.Message;
            
            public class Main {
            
                public static void main(String[] args) throws Exception {
                    Options options = new Options();
                    options.addOption("json", true, "data to parse");
                    options.addOption("debug", false, "prints module infos");
                    CommandLineParser parser = new DefaultParser();
                    CommandLine cmd = parser.parse(options, args);
            
                    if (cmd.hasOption("debug")) {
                        printModuleDebug(Main.class);
                        printModuleDebug(Gson.class);
                        printModuleDebug(StringUtils.class);
                        printModuleDebug(CommandLine.class);
                        printModuleDebug(BeanUtils.class);
                    }
            
                    String json = cmd.getOptionValue("json");
                    Message message = new Gson().fromJson(json == null ? "{}" : json, Message.class);
            
                    Object copy = BeanUtils.cloneBean(message);
                    System.out.println();
                    System.out.println("Original: " + copy.toString());
                    System.out.println("Copy:     " + copy.toString());
            
                }
            
                private static void printModuleDebug(Class<?> clazz) {
                    System.out.println(clazz.getModule().getName() + " - " + clazz.getModule().getDescriptor().version().get());
                }
            
            }
        """
        file("src/main/java/org/gradle/sample/app/data/Message.java") << """
            package org.gradle.sample.app.data;
            
            import java.util.List;
            import java.util.ArrayList;
            
            public class Message {
                private String message;
                private List<String> receivers = new ArrayList<>();
            
                public String getMessage() {
                    return message;
                }
            
                public void setMessage(String message) {
                    this.message = message;
                }
            
                public List<String> getReceivers() {
                    return receivers;
                }
            
                public void setReceivers(List<String> receivers) {
                    this.receivers = receivers;
                }
            
                @Override
                public String toString() {
                    return "Message{message='" + message + '\\'' +
                        ", receivers=" + receivers + '}';
                }
            }
        """
        file("src/main/java/module-info.java") << """
            module org.gradle.sample.app {
                exports org.gradle.sample.app;
                opens org.gradle.sample.app.data; // allow Gson to access via reflection
            
                requires com.google.gson;
                requires org.apache.commons.lang3;
                requires commons.cli;
                requires commons.beanutils;
            }
        """
        buildFile << """          
            dependencies {
                implementation("com.google.code.gson:gson:2.8.6")           // real module
                implementation("net.bytebuddy:byte-buddy:1.10.9")           // real module with multi-release jar
                implementation("org.apache.commons:commons-lang3:3.10")     // automatic module
                implementation("commons-beanutils:commons-beanutils:1.9.4") // plain library (also brings in other libraries transitively)
                implementation("commons-cli:commons-cli:1.4")               // plain library        
            }
            
            extraJavaModuleInfo {
                deriveAutomaticModuleNamesFromFileNames.set(true)
            }
        """

        expect:
        build().task(':compileJava').outcome == TaskOutcome.SUCCESS
    }

}

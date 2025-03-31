package org.gradlex.javamodule.moduleinfo.test

import org.gradlex.javamodule.moduleinfo.test.fixture.GradleBuild
import spock.lang.Specification

class IgnoreServiceProviderFunctionalTest extends Specification {

    @Delegate
    GradleBuild build = new GradleBuild()

    def "specific implementations can be ignored"() {
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
            
            repositories {
                mavenCentral()
            }
                       
            dependencies {
                implementation("org.hsqldb:hsqldb:2.7.4")
                implementation("org.liquibase:liquibase-core:4.31.1")
                implementation("com.mattbertolini:liquibase-slf4j:5.1.0")
                implementation("org.slf4j:slf4j-simple:2.0.16")
                components {
                    withModule("org.liquibase:liquibase-core") {
                        allVariants {
                            withDependencies {
                                removeIf { it.name in setOf("opencsv", "jaxb-api", "commons-collections4", "commons-text") }
                            }
                        }
                    }
                }
            }
            
            extraJavaModuleInfo {
                failOnAutomaticModules.set(true)
                module("org.liquibase:liquibase-core", "liquibase.core") {
                    closeModule()
                    requiresTransitive("java.sql")
                    requires("java.desktop")
                    requires("java.logging")
                    requires("java.naming")
                    requires("java.xml")
                    requires("org.apache.commons.io")
                    requires("org.apache.commons.lang3")
                    requires("org.yaml.snakeyaml")
                    exports("liquibase")
                    exports("liquibase.analytics")
                    exports("liquibase.analytics.configuration")
                    exports("liquibase.configuration")
                    exports("liquibase.database.jvm")
                    exports("liquibase.exception")
                    exports("liquibase.logging")
                    exports("liquibase.logging.core")
                    exports("liquibase.resource")
                    exports("liquibase.ui")
            
                    opens("www.liquibase.org.xml.ns.dbchangelog")
            
                    uses("liquibase.change.Change")
                    uses("liquibase.changelog.ChangeLogHistoryService")
                    uses("liquibase.changelog.visitor.ValidatingVisitorGenerator")
                    uses("liquibase.changeset.ChangeSetService")
                    uses("liquibase.command.CommandStep")
                    uses("liquibase.command.LiquibaseCommand")
                    uses("liquibase.configuration.AutoloadedConfigurations")
                    uses("liquibase.configuration.ConfigurationValueProvider")
                    uses("liquibase.configuration.ConfiguredValueModifier")
                    uses("liquibase.database.Database")
                    uses("liquibase.database.DatabaseConnection")
                    uses("liquibase.database.LiquibaseTableNames")
                    uses("liquibase.database.jvm.ConnectionPatterns")
                    uses("liquibase.datatype.LiquibaseDataType")
                    uses("liquibase.diff.DiffGenerator")
                    uses("liquibase.diff.compare.DatabaseObjectComparator")
                    uses("liquibase.diff.output.changelog.ChangeGenerator")
                    uses("liquibase.executor.Executor")
                    uses("liquibase.io.OutputFileHandler")
                    uses("liquibase.lockservice.LockService")
                    uses("liquibase.logging.LogService")
                    uses("liquibase.logging.mdc.CustomMdcObject")
                    uses("liquibase.logging.mdc.MdcManager")
                    uses("liquibase.parser.ChangeLogParser")
                    uses("liquibase.parser.LiquibaseSqlParser")
                    uses("liquibase.parser.NamespaceDetails")
                    uses("liquibase.parser.SnapshotParser")
                    uses("liquibase.precondition.Precondition")
                    uses("liquibase.report.ShowSummaryGenerator")
                    uses("liquibase.resource.PathHandler")
                    uses("liquibase.serializer.ChangeLogSerializer")
                    uses("liquibase.serializer.SnapshotSerializer")
                    uses("liquibase.servicelocator.ServiceLocator")
                    uses("liquibase.snapshot.SnapshotGenerator")
                    uses("liquibase.sqlgenerator.SqlGenerator")
                    uses("liquibase.structure.DatabaseObject")
                    ignoreServiceProvider("liquibase.change.Change", "liquibase.change.core.LoadDataChange", "liquibase.change.core.LoadUpdateDataChange")
                }
            }
        '''
        file("src/main/java/module-info.java") << '''
            @SuppressWarnings("opens") // the db package contains a resource file
            module org.gradle.sample.app {
                requires liquibase.core;
                requires org.hsqldb;
                //opens org.example.db to liquibase.core; -- this is too strict, the package needs to be "opened" globally so that liquibase's resource scan mechanism can detect resource files there
                opens org.gradle.sample.db;
            }
        '''
        file("src/main/resources/simplelogger.properties") << '''
            org.slf4j.simpleLogger.logFile=System.out
            org.slf4j.simpleLogger.cacheOutputStream=false
        '''.stripIndent()
        file("src/main/resources/org/gradle/sample/db/db.changelog-master.xml") << '''
            <databaseChangeLog
                    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.31.xsd">
            
                <changeSet id="1743298524674-1" author="Ihor Herasymenko">
                    <createTable tableName="person">
                        <column name="person_id" type="int"/>
                        <column name="person_name" type="varchar(32)"/>
                    </createTable>
                </changeSet>
            
            </databaseChangeLog>
        '''.stripIndent()
        file("src/main/java/org/gradle/sample/app/Main.java") << '''
            package org.gradle.sample.app;

            import liquibase.Liquibase;
            import liquibase.Scope;
            import liquibase.UpdateSummaryEnum;
            import liquibase.analytics.configuration.AnalyticsArgs;
            import liquibase.database.jvm.JdbcConnection;
            import liquibase.resource.ClassLoaderResourceAccessor;
            import liquibase.ui.UIServiceEnum;
            import org.hsqldb.jdbc.JDBCDataSource;
            
            import java.sql.Connection;
            import java.util.Map;
            
            public class Main {
                public static void main(String[] args) throws Exception {
                    JDBCDataSource ds = new JDBCDataSource();
                    ds.setURL("jdbc:hsqldb:mem:test");
                    try (Connection connection = ds.getConnection()) {
                        Map<String, Object> attrs = Map.of(
                                // use logging instead of printing directly to stdout
                                Scope.Attr.ui.name(), UIServiceEnum.LOGGER.getUiServiceClass().getConstructor().newInstance(),
                                // do not send analytics
                                AnalyticsArgs.ENABLED.getKey(), false
                        );
                        Scope.child(attrs, () -> {
                            Liquibase liquibase = new Liquibase(
                                    "org/gradle/sample/db/db.changelog-master.xml",
                                    new ClassLoaderResourceAccessor(),
                                    new JdbcConnection(connection)
                            );
            
                            liquibase.setShowSummary(UpdateSummaryEnum.OFF);
                            liquibase.update();
                        });
                    }
            
                }
            }
        '''
        expect:
        def out = run()
        out.output.contains('[main] INFO liquibase.lockservice.StandardLockService - Successfully acquired change log lock')
        out.output.contains('[main] INFO liquibase.ui.LoggerUIService - Liquibase: Update has been successful. Rows affected: 1')
        out.output.contains('[main] INFO liquibase.lockservice.StandardLockService - Successfully released change log lock')
        !out.output.contains("Caused by: java.lang.NoClassDefFoundError: com/opencsv/exceptions/CsvMalformedLineException")
    }

}

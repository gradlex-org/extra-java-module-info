package org.gradlex.javamodule.moduleinfo.test.fixture

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

import java.lang.management.ManagementFactory
import java.nio.file.Files

class GradleBuild {

    final File projectDir
    final File buildFile
    final File settingsFile

    final static String gradleVersionUnderTest = System.getProperty("gradleVersionUnderTest")

    GradleBuild(File projectDir = Files.createTempDirectory("gradle-build").toFile()) {
        this.projectDir = projectDir
        this.buildFile = new File(projectDir, "build.gradle.kts")
        this.settingsFile = new File(projectDir, "settings.gradle.kts")
    }

    File file(String path) {
        new File(projectDir, path).tap {
            it.getParentFile().mkdirs()
        }
    }

    BuildResult build() {
        runner('build').build()
    }

    BuildResult run() {
        runner('run').build()
    }

    BuildResult failRun() {
        runner('run').buildAndFail()
    }

    BuildResult test() {
        runner('test').build()
    }

    BuildResult fail() {
        runner('build').buildAndFail()
    }

    BuildResult task(String... taskNames) {
        runner(taskNames).build()
    }

    GradleRunner runner(String... args) {
        if (buildFile.exists()) {
            buildFile << '\nrepositories.mavenCentral()'
            if (gradleVersionUnderTest && gradleVersionUnderTest.startsWith("6.")) {
                buildFile << '\njava.modularity.inferModulePath.set(true)'
            }
        }
        List<String> latestFeaturesArgs = gradleVersionUnderTest ? [] : [
                '--configuration-cache',
                '-Dorg.gradle.unsafe.isolated-projects=true',
        ]
        GradleRunner.create()
                .forwardOutput()
                .withPluginClasspath()
                .withProjectDir(projectDir)
                .withArguments(Arrays.asList(args) + latestFeaturesArgs + '-s')
                .withDebug(ManagementFactory.getRuntimeMXBean().getInputArguments().toString().contains("-agentlib:jdwp")).with {
            gradleVersionUnderTest ? it.withGradleVersion(gradleVersionUnderTest) : it
        }
    }
}

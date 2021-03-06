/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.integtests

import org.gradle.api.DefaultTask
import org.gradle.api.internal.ConventionTask
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CodeNarc
import org.gradle.api.plugins.quality.FindBugs
import org.gradle.api.plugins.quality.JDepend
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.War
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.scala.ScalaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.plugins.ear.Ear
import org.gradle.plugins.ide.eclipse.GenerateEclipseClasspath
import org.gradle.plugins.ide.eclipse.GenerateEclipseJdt
import org.gradle.plugins.ide.eclipse.GenerateEclipseProject
import org.gradle.plugins.ide.eclipse.GenerateEclipseWtpComponent
import org.gradle.plugins.ide.eclipse.GenerateEclipseWtpFacet
import org.gradle.plugins.ide.idea.GenerateIdeaModule
import org.gradle.plugins.ide.idea.GenerateIdeaProject
import org.gradle.plugins.ide.idea.GenerateIdeaWorkspace
import org.gradle.plugins.signing.Sign
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.GradleVersion
import org.junit.Assume

/**
 * Tests that task classes compiled against earlier versions of Gradle are still compatible.
 */
@LeaksFileHandles
class TaskSubclassingBinaryCompatibilityCrossVersionSpec extends CrossVersionIntegrationSpec {
    def "can use task subclass compiled using previous Gradle version"() {
        given:
        def taskClasses = [
            DefaultTask,
            SourceTask,
            ConventionTask,
            Copy,
            Sync,
            Zip,
            org.gradle.api.tasks.bundling.Jar,
            Tar,
            War,
            JavaCompile,
            GroovyCompile,
            ScalaCompile,
            Test,
            CodeNarc,
            Checkstyle,
            Ear,
            FindBugs,
            Pmd,
            JDepend,
            Sign,
            org.gradle.api.tasks.application.CreateStartScripts,
            GenerateEclipseJdt,
            GenerateEclipseClasspath,
            GenerateEclipseProject,
            GenerateEclipseWtpComponent,
            GenerateEclipseWtpFacet,
            GenerateIdeaModule,
            GenerateIdeaWorkspace,
            GenerateIdeaProject
        ]

        // Task types added after 1.0

        if (previous.version >= GradleVersion.version("2.4")) {
            taskClasses << org.gradle.jvm.application.tasks.CreateStartScripts
        }
        if (previous.version >= GradleVersion.version("2.3")) {
            taskClasses << org.gradle.jvm.tasks.Jar
        }

        // Some breakages that were not detected prior to release. Please do not add any more exceptions

        if (previous.version < GradleVersion.version("1.1")) {
            // Breaking changes were made to Test between 1.0 and 1.1
            taskClasses.remove(Test)
        }
        if (previous.version < GradleVersion.version("2.0")) {
            // Breaking changes were made to JavaCompile prior to 2.0
            taskClasses.remove(JavaCompile)
        }

        Map<String, String> subclasses = taskClasses.collectEntries { ["custom" + it.name.replace(".", "_"), it.name] }

        file("producer/build.gradle") << """
            apply plugin: 'groovy'
            dependencies {
                ${previous.version < GradleVersion.version("1.4-rc-1") ? "groovy" : "compile"} localGroovy()
                compile gradleApi()
            }
        """

        file("producer/src/main/groovy/SomePlugin.groovy") << """
            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class SomePlugin implements Plugin<Project> {
                void apply(Project p) { """ <<
            subclasses.collect { "p.tasks.create('${it.key}', ${it.key})" }.join("\n") << """
                }
            }
            """ <<

            subclasses.collect {
                def className = it.key
                """class ${className} extends ${it.value} {
    ${className}() {
        // GRADLE-3185
        project.logger.lifecycle('task created')
        // GRADLE-3207
        super.getServices()
    }
}"""
            }.join("\n")

        buildFile << """
buildscript {
    dependencies { classpath fileTree(dir: "producer/build/libs", include: '*.jar') }
}

apply plugin: SomePlugin
"""

        expect:
        version previous withTasks 'assemble' inDirectory(file("producer")) run()
        version current withTasks 'tasks' requireGradleDistribution() run()
    }

    def "task can use all methods declared by Task interface that AbstractTask specialises"() {
        // Don't run these for RC 3, as stuff did change during the RCs
        Assume.assumeFalse(previous.version == GradleVersion.version("2.14-rc-3"))

        when:
        file("producer/build.gradle") << """
            apply plugin: 'groovy'
            dependencies {
                ${previous.version < GradleVersion.version("1.4-rc-1") ? "groovy" : "compile"} localGroovy()
                compile gradleApi()
            }
        """

        file("producer/src/main/java/SubclassTask.java") << """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.*;
            import org.gradle.api.logging.LogLevel;

            public class SubclassTask extends DefaultTask {
                @TaskAction
                public void doGet() {
                    // Note: not all of these specialise at time of writing, but may do in the future
                    getTaskDependencies();
                    getState();
                    getLogging();
                    getLogging().captureStandardOutput(LogLevel.INFO);
                    getStandardOutputCapture();
                    getInputs();
                    getOutputs();
                }
            }
        """

        buildFile << """
            buildscript {
                dependencies { classpath fileTree(dir: "producer/build/libs", include: '*.jar') }
            }

            task t(type: SubclassTask)
        """

        then:
        version previous requireGradleDistribution() withTasks 'assemble' inDirectory(file("producer")) run()
        version current requireGradleDistribution() withTasks 't' run()
    }
}

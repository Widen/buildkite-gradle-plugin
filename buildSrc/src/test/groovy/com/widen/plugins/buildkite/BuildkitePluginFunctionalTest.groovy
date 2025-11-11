package com.widen.plugins.buildkite

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

/**
 * Functional tests for BuildkitePlugin using Gradle TestKit.
 */
class BuildkitePluginFunctionalTest extends Specification {
    @TempDir
    File testProjectDir

    File buildFile
    File settingsFile

    def setup() {
        buildFile = new File(testProjectDir, 'build.gradle')
        settingsFile = new File(testProjectDir, 'settings.gradle')
    }

    def "can apply plugin"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments('tasks', '--all')
            .withPluginClasspath()
            .build()

        then:
        result.task(':tasks').outcome == TaskOutcome.SUCCESS
        result.output.contains('Buildkite tasks')
    }

    def "pipelines task lists configured pipelines"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                pipeline('test') {
                    env 'FOO', 'bar'
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments('pipelines')
            .withPluginClasspath()
            .build()

        then:
        result.task(':pipelines').outcome == TaskOutcome.SUCCESS
        result.output.contains('test')
    }

    def "creates upload task for pipeline"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline('myPipeline') {
                    env 'TEST', 'value'
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments('tasks', '--all')
            .withPluginClasspath()
            .build()

        then:
        result.task(':tasks').outcome == TaskOutcome.SUCCESS
        result.output.contains('uploadMyPipelinePipeline')
    }

    def "default pipeline creates uploadPipeline task"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                
                pipeline('default') {
                    env 'TEST', 'value'
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments('tasks', '--all')
            .withPluginClasspath()
            .build()

        then:
        result.task(':tasks').outcome == TaskOutcome.SUCCESS
        result.output.contains('uploadPipeline')
    }

    def "can configure default agent queue"() {
        given:
        buildFile << """
            plugins {
                id 'com.widen.buildkite'
            }
            
            buildkite {
                includeScripts = false
                defaultAgentQueue = 'custom-queue'
                
                pipeline('test') {
                    env 'FOO', 'bar'
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments('pipelines')
            .withPluginClasspath()
            .build()

        then:
        result.task(':pipelines').outcome == TaskOutcome.SUCCESS
    }
}


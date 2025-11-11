package com.widen.plugins.buildkite

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for BuildkitePlugin.
 */
class BuildkitePluginTest extends Specification {

    Project project

    def setup() {
        project = ProjectBuilder.builder().build()
    }

    def "plugin can be applied to project"() {
        when:
        project.pluginManager.apply('com.widen.buildkite')

        then:
        project.plugins.hasPlugin(BuildkitePlugin)
    }

    def "plugin creates buildkite extension"() {
        when:
        project.pluginManager.apply('com.widen.buildkite')

        then:
        project.extensions.findByName('buildkite') != null
        project.extensions.findByName('buildkite') instanceof BuildkiteExtension
    }

    def "plugin creates pipelines task"() {
        when:
        project.pluginManager.apply('com.widen.buildkite')

        then:
        project.tasks.findByName('pipelines') != null
        project.tasks.findByName('pipelines').group == 'Buildkite'
    }

    def "plugin creates upload tasks after evaluation"() {
        given:
        project.pluginManager.apply('com.widen.buildkite')

        when:
        project.buildkite {
            includeScripts = false
            pipeline('test') {
                env 'FOO', 'bar'
            }
        }
        project.evaluate()

        then:
        project.tasks.findByName('uploadTestPipeline') != null
        project.tasks.findByName('uploadTestPipeline') instanceof UploadPipelineTask
    }

    def "default pipeline creates uploadPipeline task"() {
        given:
        project.pluginManager.apply('com.widen.buildkite')

        when:
        project.buildkite {
            includeScripts = false
            pipeline('default') {
                env 'FOO', 'bar'
            }
        }
        project.evaluate()

        then:
        project.tasks.findByName('uploadPipeline') != null
    }

    def "extension has default values"() {
        when:
        project.pluginManager.apply('com.widen.buildkite')
        def extension = project.extensions.getByType(BuildkiteExtension)

        then:
        extension.defaultAgentQueue == 'builder'
        extension.includeScripts == true
    }

    def "can configure extension properties"() {
        when:
        project.pluginManager.apply('com.widen.buildkite')
        project.buildkite {
            defaultAgentQueue = 'custom-queue'
            includeScripts = false
        }
        def extension = project.extensions.getByType(BuildkiteExtension)

        then:
        extension.defaultAgentQueue == 'custom-queue'
        extension.includeScripts == false
    }
}


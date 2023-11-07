package com.widen.plugins.buildkite

import groovy.transform.PackageScope
import org.gradle.api.Project

class BuildkiteExtension {
    @PackageScope
    Project project

    @PackageScope
    final Map<String, Closure<BuildkitePipeline>> pipelines = [:]

    @PackageScope
    final Map<String, String> pluginVersions = [
        docker: 'v5.9.0',
        'docker-compose': 'v4.15.0',
    ]

    /**
     * The default agent queue name to use for steps that do not specify one.
     */
    String defaultAgentQueue = 'builder'

    /**
     * Whether detected pipeline script files should be included automatically.
     */
    boolean includeScripts = true

    /**
     * Set the default agent queue name.
     */
    void defaultAgentQueue(String queueName) {
        defaultAgentQueue = queueName
    }

    /**
     * Specify the version of a Buildkite plugin that should be used inside pipelines if no version is specified.
     */
    void pluginVersion(String name, String version) {
        pluginVersions[name] = version
    }

    /**
     * Defines the default pipeline.
     */
    void pipeline(@DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = BuildkitePipeline) Closure closure) {
        pipeline('default', closure)
    }

    /**
     * Defines a named pipeline.
     */
    void pipeline(
        String name,
        @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = BuildkitePipeline) Closure closure
    ) {
        pipelines[name] = {
            def pipeline = new BuildkitePipeline(project, this)
            pipeline.with(closure)
            return pipeline
        }
    }
}

package com.widen.plugins.buildkite

import org.gradle.api.Project

/**
 * Base class for scripts
 */
abstract class PipelineScript extends Script {
    BuildkitePipeline pipeline
    BuildkitePlugin.Extension buildkite
    Project project

    Object getProperty(String property) {
        if ('buildkite' == property) {
            return buildkite
        }
        if ('project' == property) {
            return project
        }
        return pipeline.getProperty(property)
    }

    void setProperty(String property, Object newValue) {
        pipeline.setProperty(property, newValue)
    }

    Object invokeMethod(String name, Object args) {
        return pipeline.invokeMethod(name, args)
    }
}

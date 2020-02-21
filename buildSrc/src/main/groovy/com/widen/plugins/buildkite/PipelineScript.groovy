package com.widen.plugins.buildkite

import org.gradle.api.Project

/**
 * Base class for scripts that define a Buildkite pipeline.
 */
abstract class PipelineScript extends Script {
    Project project
    BuildkiteExtension buildkite
    BuildkitePipeline pipeline

    Object getProperty(String property) {
        try {
            return getMetaClass().getProperty(this, property)
        } catch (MissingPropertyException e) {
            return pipeline.getProperty(property)
        }
    }

    void setProperty(String property, Object newValue) {
        pipeline.setProperty(property, newValue)
    }

    Object invokeMethod(String name, Object args) {
        try {
            return getMetaClass().invokeMethod(this, name, args)
        } catch (MissingMethodException e) {
            return pipeline.invokeMethod(name, args)
        }
    }
}

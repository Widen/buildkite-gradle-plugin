package com.widen.plugins.buildkite

import groovy.json.JsonOutput
import groovy.transform.PackageScope
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * A Gradle task that uploads a pipeline during a build.
 */
class UploadPipelineTask extends DefaultTask {
    @PackageScope
    Closure<BuildkitePipeline> pipelineConfig

    /**
     * Upload the pipeline to Buildkite to be executed.
     */
    @TaskAction
    void upload() {
        def pipeline = pipelineConfig.call()

        if (System.getenv('BUILDKITE') && !System.getenv('PIPELINE_TO_STDOUT')) {
            def cmd = ['buildkite-agent', 'pipeline', 'upload']

            if (!pipeline.interpolate) {
                cmd << '--no-interpolation'
            }

            if (pipeline.replace) {
                cmd << '--replace'
            }

            Process process = cmd.execute()
            process.consumeProcessOutput((Appendable) System.out, (Appendable) System.err)
            process.withWriter {
                it << pipeline.toJson()
            }
            if (process.waitFor() != 0) {
                throw new RuntimeException("buildkite-agent returned exit code ${process.exitValue()}")
            }
        } else {
            print(JsonOutput.prettyPrint(pipeline.toJson()))
        }
    }
}

package com.widen.plugins

import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * A Gradle task that uploads a pipeline during a build.
 */
class UploadPipelineTask extends DefaultTask {
    Closure<BuildkitePipeline> pipelineConfigure

    /**
     * Upload the pipeline to Buildkite to be executed.
     */
    @TaskAction
    void upload() {
        def pipeline = pipelineConfigure.call()

        if (System.env.BUILDKITE || System.env.CI) {
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
                throw new RuntimeException()
            }
        } else {
            print(JsonOutput.prettyPrint(pipeline.toJson()))
        }
    }
}

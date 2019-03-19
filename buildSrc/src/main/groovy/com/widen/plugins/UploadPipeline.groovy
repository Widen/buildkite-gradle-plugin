package com.widen.plugins

import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files

/**
 * A Gradle task that allows you to define a Buildkite pipeline dynamically and then upload it during a build.
 */
class UploadPipeline extends DefaultTask {
    private final List steps = []

    /**
     * Enable or disable variable interpolation on upload.
     */
    boolean interpolate = true

    /**
     * Replace the rest of the existing pipeline with the steps uploaded. Jobs that are already running are not removed.
     */
    boolean replace = false

    /**
     * Add a <a href="https://buildkite.com/docs/pipelines/command-step">command step</a> to the pipeline.
     *
     * @param closure
     */
    void commandStep(@DelegatesTo(CommandStep) Closure closure) {
        def step = new CommandStep()
        closure = closure.rehydrate(step, this, this)
        closure.resolveStrategy = Closure.OWNER_FIRST
        closure()
        steps << step.model
    }

    /**
     * Add a <a href="https://buildkite.com/docs/pipelines/wait-step">wait step</a> to the pipeline.
     */
    void waitStep() {
        steps << 'wait'
    }

    /**
     * Add a <a href="https://buildkite.com/docs/pipelines/wait-step">wait step</a> to the pipeline that continues on
     * failure.
     */
    void waitStepContinueOnFailure() {
        steps << [
            wait: [
                continue_on_failure: true
            ]
        ]
    }

    /**
     * Helper method to return the correct Widen Kubernetes cluster endpoint for a given environment and region.
     *
     * @param environment
     * @param region
     * @return
     */
    String k8s(String environment, String region) {
        if (environment == 'stage' && region == 'us-east-1') {
            return '***REMOVED***'
        }
        return "***REMOVED***"
    }

    /**
     * Get the JSON representation of the pipeline.
     *
     * @return A JSON string.
     */
    String toJson() {
        return JsonOutput.toJson([
            steps: steps
        ])
    }

    /**
     * Upload the pipeline to Buildkite to be executed.
     */
    @TaskAction
    void upload() {
        if (System.env.BUILDKITE || System.env.CI) {
            def cmd = ['buildkite-agent', 'pipeline', 'upload']

            if (!interpolate) {
                cmd << '--no-interpolation'
            }

            if (replace) {
                cmd << '--replace'
            }

            Process process = cmd.execute()
            process.consumeProcessOutput((Appendable) System.out, (Appendable) System.err)
            process.withWriter {
                it << toJson()
            }
            if (process.waitFor() != 0) {
                throw new RuntimeException()
            }
        } else {
            print(JsonOutput.prettyPrint(toJson()))
        }
    }

    /**
     * Base class for Buildkite step types.
     */
    abstract class Step {
        protected final Map model = [:]

        /**
         * Sets the label that will be displayed in the pipeline visualisation in Buildkite.
         *
         * @param label
         */
        void label(String label) {
            model.label = label
        }

        /**
         * The branch pattern defining which branches will include this step in their builds.
         *
         * @param branches The branches to match.
         */
        void branches(String... branches) {
            model.branches = branches.join(' ')
        }
    }

    /**
     * Configuration for a Buildkite command step.
     */
    class CommandStep extends Step {
        void command(String command) {
            model.command = command
        }

        void commands(String[] commands) {
            model.command = commands
        }

        void artifactPath(String path) {
            model.get('artifact_paths', []) << path
        }

        void agentQueue(String name) {
            model.get('agents', [:]).queue = name
        }

        void agentQueue(String name, String region) {
            agentQueue(region == 'us-east-1' ? name : "$name-$region")
        }

        void environment(String name, String value) {
            model.get('env', [:]).put(name, value)
        }

        void environment(Map<String, String> variables) {
            variables.each { name, value ->
                environment(name, value)
            }
        }

        void skip() {
            model.skip = true
        }

        void skip(String reason) {
            model.skip = reason
        }

        void plugin(String name, Object config) {
            model.get('plugins', []) << [
                (name): config
            ]
        }

        void dockerComposeContainer(String name) {
            def composeFiles = [
                'docker-compose.yml',
                'docker-compose.buildkite.yml',
            ].findAll {
                Files.exists(project.rootDir.toPath().resolve(it))
            }

            plugin 'docker-compose#v2.3.0', [
                run: name,
                config: composeFiles
            ]
        }
    }
}

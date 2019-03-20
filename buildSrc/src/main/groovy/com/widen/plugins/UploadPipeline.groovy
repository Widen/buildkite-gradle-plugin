package com.widen.plugins

import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files

/**
 * A Gradle task that allows you to define a Buildkite pipeline dynamically and then upload it during a build.
 */
class UploadPipeline extends DefaultTask {
    private static final String DOCKER_PLUGIN_VERSION = 'v1.1.1'
    private static final String DOCKER_COMPOSE_PLUGIN_VERSION = 'v2.3.0'

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
        closure.resolveStrategy = Closure.DELEGATE_FIRST
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
            return 'k8s1.us-east1.stage.yden.us'
        }
        return "k8s1.${region}.${environment}.yden.io"
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

        void automaticRetry(@DelegatesTo(AutomaticRetry) Closure closure) {
            def config = new AutomaticRetry()
            closure = closure.rehydrate(config, this, this)
            closure.resolveStrategy = Closure.DELEGATE_FIRST
            closure()
            model.get('retry', [:]).automatic = config.model
        }

        void plugin(String name, Object config) {
            model.get('plugins', []) << [
                (name): config
            ]
        }

        void plugin(String name, String version, Object config) {
            plugin("$name#$version", config)
        }

        void docker(@DelegatesTo(Docker) Closure closure) {
            def config = new Docker()
            closure = closure.rehydrate(config, this, this)
            closure.resolveStrategy = Closure.DELEGATE_FIRST
            closure()
            plugin('docker', DOCKER_PLUGIN_VERSION, config.model)
        }

        void dockerCompose(@DelegatesTo(DockerCompose) Closure closure) {
            def config = new DockerCompose()

            // Pre-populate some config files.
            ['docker-compose.yml', 'docker-compose.buildkite.yml'].each {
                if (Files.exists(project.rootDir.toPath().resolve(it))) {
                    config.composeFile(it)
                }
            }

            closure = closure.rehydrate(config, this, this)
            closure.resolveStrategy = Closure.DELEGATE_FIRST
            closure()

            plugin('docker-compose', DOCKER_COMPOSE_PLUGIN_VERSION, config.model)
        }

        class AutomaticRetry {
            protected Object model = true

            void exitStatus(Integer exitStatus) {
                if (!(model instanceof Map)) {
                    model = [:]
                }
                model['exit_status'] = exitStatus
            }

            void limit(Integer limit) {
                if (!(model instanceof Map)) {
                    model = [:]
                }
                model['limit'] = limit
            }
        }
    }

    class Docker {
        protected final Map model = [:]

        void image(String image) {
            model.image = image
        }

        void alwaysPull() {
            alwaysPull(true)
        }

        void alwaysPull(boolean pull) {
            model['always-pull'] = pull
        }

        void environment(String name) {
            model.get('environment', []) << name
        }

        void environment(String name, String value) {
            model.get('environment', []) << "$name=$value"
        }

        void environment(Map<String, String> variables) {
            variables.each { name, value ->
                environment(name, value)
            }
        }
    }

    class DockerCompose {
        protected final Map model = [:]

        void run(String service) {
            model.run = service
        }

        void build(String... services) {
            model.build = services
        }

        void environment(String name) {
            model.get('env', []) << name
        }

        void environment(String name, String value) {
            model.get('env', []) << "$name=$value"
        }

        void environment(Map<String, String> variables) {
            variables.each { name, value ->
                environment(name, value)
            }
        }

        void composeFile(String composeFile) {
            model.get('config', []) << composeFile
        }
    }
}

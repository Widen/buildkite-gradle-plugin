package com.widen.plugins


import groovy.json.JsonOutput

import java.nio.file.Files
import java.time.Duration

class BuildkitePipeline implements ConfigurableEnvironment {
    private final BuildkitePlugin.Config pluginConfig
    private final Map<String, String> env = [:]
    private final List steps = []

    BuildkitePipeline(BuildkitePlugin.Config pluginConfig) {
        this.pluginConfig = pluginConfig
    }

    /**
     * Enable or disable variable interpolation on upload.
     */
    boolean interpolate = true

    /**
     * Replace the rest of the existing pipeline with the steps uploaded. Jobs that are already running are not removed.
     */
    boolean replace = false

    /**
     * Add an environment variable to apply to all steps.
     */
    void environment(String name, Object value) {
        env[name] = value.toString()
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
     * Add a <a href="https://buildkite.com/docs/pipelines/command-step">command step</a> to the pipeline.
     *
     * @param closure
     */
    void commandStep(@DelegatesTo(CommandStep) Closure closure) {
        def step = new CommandStep()
        step.with(closure)
        steps << step.model
    }

    /**
     * Configuration for a Buildkite command step.
     */
    class CommandStep extends Step implements ConfigurableEnvironment {
        // Set defaults.
        {
            agentQueue 'builder'
        }

        /**
         * The shell command to run during this step.
         */
        void command(String command) {
            model.command = command
        }

        /**
         * The shell commands to run during this step.
         */
        void commands(String... commands) {
            model.command = commands
        }

        /**
         * The agent queue that should run this step.
         *
         * @param name The agent queue name.
         */
        void agentQueue(String name) {
            model.get('agents', [:]).queue = name
        }

        /**
         * The agent queue that should run this step.
         *
         * @param name The agent queue base name.
         * @param region A specific AWS region to run in.
         */
        void agentQueue(String name, String region) {
            agentQueue(region == 'us-east-1' ? name : "$name-$region")
        }

        /**
         * Add a glob path of artifacts to upload from this step.
         */
        void artifactPath(String path) {
            model.get('artifact_paths', []) << path
        }

        /**
         * The maximum number of jobs created from this step that are allowed to run at the same time. If you use this attribute, you must also
         * define a label for it with the concurrency_group attribute.
         *
         * @param group A unique name for this concurrency group.
         * @param concurrency The number of max concurrent jobs.
         */
        void concurrency(String group, Integer concurrency) {
            model.concurrency = concurrency
            model.concurrency_group = group
        }

        /**
         * Add an environment variable to this step.
         *
         * @param name The variable name.
         * @param value The variable value.
         */
        void environment(String name, Object value) {
            model.get('env', [:]).put(name, value.toString())
        }

        /**
         * The number of parallel jobs that will be created based on this step.
         */
        void parallelism(Integer parallelism) {
            model.parallelism = parallelism
        }

        /**
         * Retry this step automatically on failure.
         */
        void automaticRetry(@DelegatesTo(AutomaticRetry) Closure closure) {
            def config = new AutomaticRetry()
            config.with(closure)
            model.get('retry', [:]).automatic = config.model
        }

        /**
         * Configuration for automatic retries.
         */
        class AutomaticRetry {
            protected Object model = true

            /**
             * The exit status number that will cause this job to retry.
             */
            void exitStatus(Integer exitStatus) {
                if (!(model instanceof Map)) {
                    model = [:]
                }
                model['exit_status'] = exitStatus
            }

            /**
             * The number of times this job can be retried. The maximum value this can be set to is 10.
             */
            void limit(Integer limit) {
                if (!(model instanceof Map)) {
                    model = [:]
                }
                model['limit'] = limit
            }
        }

        /**
         * Skip this step.
         */
        void skip() {
            model.skip = true
        }

        /**
         * Skip this step with a given reason string.
         */
        void skip(String reason) {
            model.skip = reason
        }

        /**
         * The amount of time a job created from this step is allowed to run. If the job does not finish within this limit, it will be automatically cancelled
         * and the build will fail.
         */
        void timeout(Duration timeout) {
            model.timeout_in_minutes = Math.max(timeout.toMinutes(), 1)
        }

        /**
         * Add a Buildkite plugin to this step.
         *
         * @param name The plugin name.
         * @param config An object that should be passed to the plugin as configuration.
         */
        void plugin(String name, Object config) {
            plugin(name, pluginConfig.pluginVersions[name], config)
        }

        /**
         * Add a Buildkite plugin to this step.
         *
         * @param name The plugin name.
         * @param name The plugin version.
         * @param config An object that should be passed to the plugin as configuration.
         */
        void plugin(String name, String version, Object config) {
            def key = version ? "$name#$version" : name
            model.get('plugins', []) << [
                (key): config
            ]
        }

        /**
         * Add the <a href="https://github.com/buildkite-plugins/docker-buildkite-plugin">Docker plugin</a> to this step.
         */
        void docker(@DelegatesTo(Docker) Closure closure) {
            def config = new Docker()
            config.with(closure)
            plugin('docker', config.model)
        }

        /**
         * Configuration for the Docker plugin.
         */
        class Docker implements ConfigurableEnvironment {
            protected final Map model = [:]

            /**
             * The name of the Docker image to use.
             */
            void image(String image) {
                model.image = image
            }

            /**
             * Always pull the latest image before running the command.
             */
            void alwaysPull() {
                model['always-pull'] = true
            }

            /**
             * Set an environment variable to pass into the Docker container.
             *
             * @param name The variable name.
             */
            void environment(String name) {
                model.get('environment', []) << name
            }

            /**
             * Set an environment variable to pass into the Docker container.
             *
             * @param name The variable name.
             * @param value The value to set.
             */
            void environment(String name, Object value) {
                model.get('environment', []) << "$name=$value"
            }

            /**
             * Automatically propagate all pipeline environment variables into the container.
             */
            void propagateEnvironment() {
                model['propagate-environment'] = true
            }

            /**
             * Add a volume mount to pass to the container.
             */
            void volume(String source, String target) {
                model.get('volumes', []) << "$source:$target"
            }

            /**
             * Add extra volume mounts to pass to the container.
             */
            void volumes(Map<String, String> volumes) {
                volumes.each { source, target ->
                    volume(source, target)
                }
            }
        }

        /**
         * Add the <a href="https://github.com/buildkite-plugins/docker-compose-buildkite-plugin">Docker Compose plugin</a> to this step.
         */
        void dockerCompose(@DelegatesTo(DockerCompose) Closure closure) {
            def config = new DockerCompose()

            // Pre-populate some config files.
            ['docker-compose.yml', 'docker-compose.buildkite.yml'].each {
                if (Files.exists(pluginConfig.rootDir.toPath().resolve(it))) {
                    config.composeFile(it)
                }
            }

            config.with(closure)

            plugin('docker-compose', config.model)
        }

        /**
         * Configuration for the Docker Compose plugin.
         */
        class DockerCompose implements ConfigurableEnvironment {
            protected final Map model = [:]

            /**
             * The names of services to build.
             */
            void build(String... services) {
                model.get('build', []).addAll(services)
            }

            /**
             * The name of the service the command should be run within.
             */
            void run(String service) {
                model.run = service
            }

            /**
             * The repository for pushing and pulling pre-built images.
             */
            void imageRepository(String repository) {
                model['image-repository'] = repository
            }

            /**
             * The name to use when tagging pre-built images.
             */
            void imageName(String name) {
                model['image-name'] = name
            }

            /**
             * Set an environment variable to pass into the container.
             */
            void environment(String name) {
                model.get('env', []) << name
            }

            /**
             * Set an environment variable to pass into the container.
             */
            void environment(String name, Object value) {
                model.get('env', []) << "$name=$value"
            }

            /**
             * Add a Docker Compose configuration file to use.
             */
            void composeFile(String composeFile) {
                model.get('config', []) << composeFile
            }
        }
    }

    /**
     * Add a command step that executes a Gradle task.
     */
    void gradleStep(@DelegatesTo(GradleStep) Closure closure) {
        def step = new GradleStep()

        step.with {
            with(closure)

            def systemPropertyArgs = systemProperties.collect {
                "-D$it.key=$it.value"
            }

            command "./gradlew $task ${systemPropertyArgs.join(' ')} \${GRADLE_SWITCHES}"

            docker {
                image 'quay.io/widen/builder-gradle:2.0.0'
                propagateEnvironment()
                environment 'WIDEN_DOCKER_TAG'
                volumes([
                    "./": "/work",
                    "~/.aws": "/root/.aws",
                    "~/.docker": "/root/.docker",
                    "~/.gradle": "/root/.gradle",
                    "/usr/bin/buildkite-agent": "/usr/bin/buildkite-agent",
                    "/usr/bin/docker": "/usr/bin/docker",
                    "/var/run/docker.sock": "/var/run/docker.sock",
                ])
            }
        }

        steps << step.model
    }

    /**
     * Configuration for a Gradle command step.
     */
    class GradleStep extends CommandStep {
        protected String task
        protected String[] args = []
        protected Map<String, Object> systemProperties = [:]

        /**
         * The name of the Gradle task to execute.
         */
        void task(String name) {
            this.task = name
        }

        /**
         * Arguments to pass to Gradle.
         */
        void args(String... args) {
            this.args = args
        }

        /**
         * Set a Java system property for the Gradle task.
         */
        void systemProperty(String name, Object value) {
            systemProperties[name] = value
        }
    }

    /**
     * Add a command step runs the <a href="https://docs.yden.us/docs-publisher/">Docs Publisher</a>.
     */
    void publishDocsStep(@DelegatesTo(PublishDocsStep) Closure closure) {
        def step = new PublishDocsStep()
        step.with(closure)

        Objects.requireNonNull(step.appName, 'appName')

        commandStep {
            model.putAll(step.model)

            command 'publish-docs'
            docker {
                image 'quay.io/widen/docs-publisher'
                alwaysPull()
                environment 'APP_NAME', step.appName
                environment 'BUILDKITE_BRANCH'
                environment 'BUILDKITE_REPO'
            }
        }
    }

    class PublishDocsStep extends Step {
        String appName

        {
            label ':md: Publish Docs'
        }
    }

    /**
     * Get the JSON representation of the pipeline.
     *
     * @return A JSON string.
     */
    String toJson() {
        return JsonOutput.toJson([
            env: env,
            steps: steps
        ])
    }

    /**
     * Base class for Buildkite step types.
     */
    abstract class Step {
        protected final Map model = [:]

        /**
         * Sets the label that will be displayed in the pipeline visualisation in Buildkite.
         */
        void label(String label) {
            model.label = label
        }

        /**
         * The branch patterns defining which branches will include this step in their builds.
         */
        void branches(String... branches) {
            model.branches = branches.join(' ')
        }
    }
}

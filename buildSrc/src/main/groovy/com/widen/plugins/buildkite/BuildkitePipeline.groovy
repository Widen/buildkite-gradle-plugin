package com.widen.plugins.buildkite

import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import org.gradle.api.Project

import java.nio.file.Files
import java.time.Duration

class BuildkitePipeline implements ConfigurableEnvironment {
    private final Project project
    private final BuildkiteExtension buildkite
    private final Map<String, String> env = [:]
    private final List<Object> steps = []

    BuildkitePipeline(Project project, BuildkiteExtension buildkite) {
        this.project = project
        this.buildkite = buildkite
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
    void commandStep(@DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = CommandStep) Closure closure) {
        def step = new CommandStep()
        step.with(closure)
        steps << step.model
    }

    /**
     * A command step runs one or more shell commands on an agent.
     */
    class CommandStep extends Step implements ConfigurableEnvironment {
        // Set defaults.
        {
            agentQueue buildkite.defaultAgentQueue
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
         * The maximum number of jobs created from this step that are allowed to run at the same time. If you use
         * this attribute, you must also define a label for it with the concurrency_group attribute.
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
        void automaticRetry(@DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = AutomaticRetry) Closure closure) {
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
         * The amount of time a job created from this step is allowed to run. If the job does not finish within this
         * limit, it will be automatically cancelled and the build will fail.
         */
        void timeout(Duration timeout) {
            model.timeout_in_minutes = Math.max(timeout.toMinutes(), 1)
        }

        /**
         * Add a Buildkite plugin to this step.
         *
         * @param name The plugin name or URL and version.
         * @param config An optional object that should be passed to the plugin as configuration, or a closure that
         * constructs the configuration.
         *
         * See <a href="https://buildkite.com/docs/pipelines/plugins#plugin-sources">plugin sources</a> for accepted
         * syntax for the plugin name.
         */
        void plugin(String name, Object config = null) {
            if (config instanceof Closure) {
                def builder = new JsonBuilder()
                builder.call(config)
                config = builder.content
            }

            // If no version is given and a default version is defined, set it.
            if (!name.contains("#") && buildkite.pluginVersions.containsKey(name)) {
                name += "#${buildkite.pluginVersions[name]}"
            }

            model.get('plugins', []) << [
                (name): config
            ]
        }

        /**
         * Add the <a href="https://github.com/buildkite-plugins/docker-buildkite-plugin">Docker plugin</a> to this
         * step.
         */
        void docker(@DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = Docker) Closure closure) {
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

            /**
             * Override the Docker container entrypoint.
             */
            void entrypoint(String entrypoint) {
                model.entrypoint = entrypoint
            }

            /**
             * Set the shell to use for the command.
             */
            void shell(String... args) {
                model.shell = args
            }
        }

        /**
         * Add the <a href="https://github.com/buildkite-plugins/docker-compose-buildkite-plugin">Docker Compose
         * plugin</a> to this step.
         */
        void dockerCompose(@DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = DockerCompose) Closure closure) {
            def config = new DockerCompose()

            // Pre-populate some config files.
            ['docker-compose.yml', 'docker-compose.buildkite.yml'].each {
                if (Files.exists(project.rootDir.toPath().resolve(it))) {
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
             * Push a built service to a repository. Multiple pushes are allowed in one step.
             */
            void push(String service, String image) {
                model.get('push', []) << "$service:$image"
            }

            /**
             * Push a built service to a repository. Multiple pushes are allowed in one step.
             */
            void push(String service, String image, String tag) {
                push(service, "$image:$tag")
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
             * Specify a Docker image to pull down to use as a layer cache for building the given service.
             */
            void cacheFrom(String service, String image) {
                model.get('cache-from', []) << "$service:$image"
            }

            /**
             * Specify a Docker image to pull down to use as a layer cache for building the given service.
             */
            void cacheFrom(String service, String image, String tag) {
                cacheFrom(service, "$image:$tag")
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
     * Add a <a href="https://buildkite.com/docs/pipelines/block-step">block step</a> to the pipeline.
     *
     * @param closure
     */
    void blockStep(String label, @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = BlockStep) Closure closure =
        null) {
        def step = new BlockStep()
        step.label(label)
        if (closure) {
            step.with(closure)
        }
        steps << step.model
    }

    /**
     * A block step is used to pause the execution of a build and wait on a team member to unblock it via the web or
     * the API.
     */
    class BlockStep extends Step {
        @Override
        void label(String label) {
            model.block = label
        }

        /**
         * Sets an instructional message displayed in the dialog box when the unblock step is activated.
         */
        void prompt(String prompt) {
            model.prompt = prompt
        }

        /**
         * Add a text field to be filled out before unblocking the step.
         *
         * @param label The label of the field.
         * @param key The meta-data key that stores the field's input (e.g. via the buildkite-agent meta-data
         * command) The key may only contain alphanumeric characters, slashes or dashes.
         */
        void textField(String label, String key, @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = TextField)
            Closure closure = null) {
            addField(new TextField(label, key), closure)
        }

        class TextField extends Field {
            TextField(String label, String key) {
                model.text = label
                model.key = key
            }
        }

        /**
         * Add a select field to be filled out before unblocking the step.
         *
         * @param label The label of the field.
         * @param key The meta-data key that stores the field's input (e.g. via the buildkite-agent meta-data
         * command) The key may only contain alphanumeric characters, slashes or dashes.
         */
        void selectField(String label, String key, @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value =
            SelectField) Closure closure = null) {
            addField(new SelectField(label, key), closure)
        }

        class SelectField extends Field {
            SelectField(String label, String key) {
                model.select = label
                model.key = key
            }

            /**
             * A boolean value that defines whether multiple options may be selected. When multiple options are
             * selected, they are delimited in the meta-data field by a line break (\n).
             */
            void multiple(boolean multiple = true) {
                model.multiple = multiple
            }

            /**
             * When multiple is enabled, set an array of values to select by default.
             */
            void defaultValues(String... values) {
                model.default = values.toList()
            }

            /**
             * Add a field option.
             *
             * @param label The text displayed for the option.
             * @param value The value to be stored as meta-data (e.g. to be later retrieved via the buildkite-agent
             * meta-data command).
             */
            void option(String label, String value) {
                model.get('options', []) << [
                    label: label,
                    value: value,
                ]
            }
        }

        private void addField(Field field, Closure closure) {
            if (closure) {
                field.with(closure)
            }
            model.get('fields', []) << field.model
        }

        abstract class Field {
            protected final Map model = [:]

            /**
             * Set the explanatory text that is shown after the label.
             */
            void hint(String hint) {
                model.hint = hint
            }

            /**
             * A boolean value that defines whether the field is required for form submission.
             */
            void required(boolean required = true) {
                model.required = required
            }

            /**
             * The value that is pre-filled or pre-selected.
             */
            void defaultValue(String value) {
                model.default = value
            }
        }
    }

    /**
     * Add a <a href="https://buildkite.com/docs/pipelines/trigger-step">trigger step</a> to the pipeline.
     *
     * @param trigger The slug of the pipeline to create a build. The pipeline slug must be lowercase.
     * @param closure
     */
    void triggerStep(String trigger, @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = TriggerStep) Closure
        closure = null) {
        def step = new TriggerStep(trigger)
        if (closure) {
            step.with(closure)
        }
        steps << step.model
    }

    /**
     * A trigger step creates a build on another pipeline. You can use trigger steps to separate your test and deploy
     * pipelines, or to create build dependencies between pipelines.
     */
    class TriggerStep extends Step {
        TriggerStep(String trigger) {
            model.trigger = trigger
        }

        /**
         * If set to true the step will immediately continue, regardless of the success of the triggered build. If
         * set to false the step will wait for the triggered build to complete and continue only if the triggered
         * build passed.
         */
        void async(boolean async = true) {
            model.async = async
        }

        /**
         * Configure optional attributes for the triggered build.
         */
        void build(@DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = Build) Closure closure) {
            def build = new Build()
            build.with(closure)
        }

        class Build implements ConfigurableEnvironment {
            /**
             * The message for the build. Supports emoji. Default: the label of the trigger step.
             */
            void message(String message) {
                model.get('build', [:]).message = message
            }

            /**
             * The commit hash for the build.
             */
            void commit(String commit) {
                model.get('build', [:]).commit = commit
            }

            /**
             * The branch for the build.
             */
            void branch(String branch) {
                model.get('build', [:]).branch = branch
            }

            /**
             * Set meta-data for the build.
             */
            void metadata(@DelegatesTo(strategy = Closure.OWNER_FIRST, value = Map) Closure closure) {
                def map = [:]
                closure = (Closure) closure.clone()
                closure.delegate = map
                closure.resolveStrategy = Closure.OWNER_FIRST
                closure.call()

                model.get('build', [:])
                    .get('meta_data', [:])
                    .putAll(map)
            }

            @Override
            void environment(String name, Object value) {
                model.get('build', [:])
                    .get('env', [:])
                    .put(name, value)
            }
        }
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
}

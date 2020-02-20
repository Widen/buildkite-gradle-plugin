package com.widen.plugins.buildkite


import org.gradle.api.Plugin
import org.gradle.api.Project

class BuildkitePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        def extension = project.extensions.create('buildkite', Extension)

        extension.config = new Config().with {
            rootDir = project.rootDir
            it
        }

        project.task('pipelines') {
            doLast {
                extension.pipelines.each {
                    println it.key
                }
            }
        }

        // Run anything that needs to be done after plugin configuration has been evaluated.
        project.afterEvaluate {
            if (extension.includeScripts) {
                project.fileTree(project.rootDir) {
                    include '.buildkite/pipeline*.gradle'
                }.each {
                    project.apply from: it
                }
            }

            extension.pipelines.each { name, config ->
                def taskName = name == 'default' ? 'uploadPipeline' : "upload${name}Pipeline"

                project.tasks.create(taskName, UploadPipelineTask) {
                    pipelineConfigure = config
                }
            }
        }
    }

    static class Config {
        String defaultAgentQueue = 'builder'

        final Map<String, String> pluginVersions = [
            docker: 'v3.2.0',
            'docker-compose': 'v3.0.3',
        ]

        File rootDir
    }

    static class Extension {
        protected final Map<String, Closure<BuildkitePipeline>> pipelines = [:]
        protected Config config

        /**
         * Whether detected pipeline script files should be included automatically.
         */
        boolean includeScripts = true

        /**
         * Set the default agent queue name to use for steps that do not specify one.
         */
        void defaultAgentQueue(String queueName) {
            config.defaultAgentQueue = queueName
        }

        /**
         * Specify the version of a Buildkite plugin that should be used inside pipelines if no version is specified.
         */
        void pluginVersion(String name, String version) {
            config.pluginVersions[name] = version
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
                def pipeline = new BuildkitePipeline(config)
                pipeline.with(closure)
                return pipeline
            }
        }
    }
}

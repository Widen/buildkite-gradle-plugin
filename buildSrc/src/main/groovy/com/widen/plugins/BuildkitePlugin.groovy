package com.widen.plugins


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

        project.afterEvaluate {
            extension.pipelines.each { name, config ->
                def taskName = name == 'default' ? 'uploadPipeline' : "upload${name}Pipeline"

                project.tasks.create(taskName, UploadPipelineTask) {
                    pipelineConfigure = config
                }
            }
        }
    }

    static class Config {
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
         * Specify the version of a Buildkite plugin that should be used inside pipelines if no version is specified.
         */
        void pluginVersion(String name, String version) {
            config.pluginVersions[name] = version
        }

        /**
         * Defines the default pipeline.
         */
        void pipeline(@DelegatesTo(BuildkitePipeline) Closure closure) {
            pipeline('default', closure)
        }

        /**
         * Defines a named pipeline.
         */
        void pipeline(String name, @DelegatesTo(BuildkitePipeline) Closure closure) {
            pipelines[name] = {
                def pipeline = new BuildkitePipeline(config)
                pipeline.with(closure)
                return pipeline
            }
        }
    }
}

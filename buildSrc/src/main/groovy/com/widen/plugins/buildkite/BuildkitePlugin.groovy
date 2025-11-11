package com.widen.plugins.buildkite

import org.codehaus.groovy.control.CompilerConfiguration
import org.gradle.api.Plugin
import org.gradle.api.Project

class BuildkitePlugin implements Plugin<Project> {
    private static final String GROUP = 'Buildkite'

    @Override
    void apply(Project project) {
        def extension = project.extensions.create('buildkite', BuildkiteExtension)
        extension.project = project

        project.tasks.register('pipelines') { task ->
            task.group = GROUP
            task.description = "List all Buildkite pipelines."

            task.doLast {
                extension.pipelines.each {
                    println it.key
                }
            }
        }

        // Run anything that needs to be done after plugin configuration has been evaluated.
        project.afterEvaluate {
            if (extension.includeScripts) {
                loadPipelineScripts(project, extension, project.fileTree(project.rootDir) {
                    include '.buildkite/pipeline*.gradle'
                })
            }

            extension.pipelines.each { name, config ->
                def taskName = name == 'default' ? 'uploadPipeline' : "upload${name.capitalize()}Pipeline"

                project.tasks.register(taskName, UploadPipelineTask) { task ->
                    task.group = GROUP
                    task.description = "Upload the $name pipeline to the current job."
                    task.pipelineConfig = config
                }
            }
        }
    }

    private static loadPipelineScripts(Project project, BuildkiteExtension extension, Iterable<File> files) {
        def shell = new GroovyShell(project.buildscript.classLoader, new CompilerConfiguration(
            scriptBaseClass: PipelineScript.class.name
        ))

        files.each { file ->
            def pipelineName = pipelineNameFromFile(file)

            extension.pipeline(pipelineName) { BuildkitePipeline pipeline ->
                // Avoid loading the file until the pipeline spec is actually requested.
                def script = (PipelineScript) shell.parse(new GroovyCodeSource(
                    file.text,
                    "${pipelineName}Pipeline", // override class name to handle files with dashes
                    file.path
                ))
                script.setProject(project)
                script.setBuildkite(extension)
                script.setPipeline(pipeline)
                script.run()
            }
        }
    }

    private static String pipelineNameFromFile(File file) {
        return file.name.find(/pipeline\.([^.]+)\.gradle/) { x, name ->
            name.replaceAll(/[^a-zA-Z0-9]+([a-zA-Z0-9]+)/) { y, word ->
                word.capitalize()
            }
        } ?: 'default'
    }
}

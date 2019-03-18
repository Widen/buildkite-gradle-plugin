package com.widen.plugins

import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files

class UploadPipeline extends DefaultTask {
    private boolean replace = false
    private steps = []

    void replace(boolean replace) {
        this.replace = replace
    }

    String k8s(String environment, String region) {
        if (environment == 'stage' && region == 'us-east-1') {
            return 'k8s1.us-east1.stage.yden.us'
        }
        return "k8s1.${region}.${environment}.yden.io"
    }

    void commandStep(@DelegatesTo(CommandStep) Closure closure) {
        def step = new CommandStep()
        closure = closure.rehydrate(step, this, this)
        closure.resolveStrategy = Closure.OWNER_FIRST
        closure()

        steps << step.model
    }

    void waitStep() {
        steps << 'wait'
    }

    void waitStepContinueOnFailure() {
        steps << [
            wait: [
                continue_on_failure: true
            ]
        ]
    }

    String toJson() {
        return JsonOutput.toJson([
            steps: steps
        ])
    }

    @TaskAction
    void upload() {
        if (System.env.BUILDKITE || System.env.CI) {
            def cmd = ['buildkite-agent', 'pipeline', 'upload', '--no-interpolation']

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

    class Step {
        protected Map model = [:]

        void label(String label) {
            model.label = label
        }

        void branch(String branch) {
            model.branches = branch
        }

        void agentQueue(String name) {
            model.get('agents', [:]).queue = name
        }

        void agentQueue(String name, String region) {
            agentQueue(region == 'us-east-1' ? name : "$name-$region")
        }
    }

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

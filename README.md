# Gradle Buildkite Plugin

[![Build Status](https://badge.buildkite.com/9a1d9c36585e925d7b531e3f456a33de3bddda2a6db9ffee91.svg)](https://buildkite.com/widen/gradle-buildkite-plugin)

Provides a Gradle DSL for dynamically generating Buildkite pipelines.

## Usage

First add the plugin to your project:

```groovy
plugins {
    id 'widen.buildkite' version '0.1.7'
}
```

Now you can start defining Buildkite pipelines using Groovy inside your `build.gradle`. Below is an example of defining a Gradle task `deployStagePipeline`:

```groovy
tasks.create('deployStagePipeline', com.widen.plugins.UploadPipeline) {
    def regions = ['us-east-1', 'eu-west-1']

    regions.each { region ->
        commandStep {
            label ":rocket: Deploy app to stage $region"
            command "./bksh deploy-helm-2 -r app-stage -f app-stage-${region}.yaml -g \${DOCKER_TAG} -k ${k8s('stage', region)} -v 4.2.3"
            agentQueue 'deploy-stage', region
        }
        commandStep {
            label ":sleeping: Wait for stage $region deploy to finish"
            command "./bksh wait-for-deploy http://app.${region}.widen-stage.com/health"
            agentQueue 'deploy-stage', region
        }
    }

    waitStep()

    regions.each { region ->
        commandStep {
            label ":smoking: Integration test the stage $region deployment"
            command "./gradlew app-app:integrationTest -Dapp.endpoint=http://app.${region}.widen-stage.com --continue \${GRADLE_SWITCHES}"
            branch 'master'
            agentQueue 'integ-stage', region
            dockerCompose {
                run 'gradle'
            }
        }
    }
}
```

This example demonstrates the power of using a language like Groovy to dynamically generate a pipeline based on lists or other dynamic code. You could even parallelize your unit tests by generating a separate step for each subproject reported by Gradle!

Running this Gradle task locally spits out the JSON representation, so you can see if your pipeline looks correct. Inside Buildkite the pipeline will be added to the current build.

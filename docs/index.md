# Gradle Buildkite Plugin

Provides a Gradle DSL for dynamically generating Buildkite pipelines.

## Installation

First add the plugin to your project:

```groovy
plugins {
    id 'widen.buildkite' version '0.2.0'
}
```

Check out the [releases page](https://github.com/Widen/gradle-buildkite-plugin/releases) for a list of versions and the changelog for each. Now you are ready to start defining Buildkite pipelines using Groovy inside your `build.gradle`.

## Usage
 
Below is an example of defining Buildkite pipeline:

```groovy
buildkite {
    pipeline('deployStage') {
        def regions = ['us-east-1', 'eu-west-1']
    
        regions.each { region ->
            commandStep {
                label ":rocket: Deploy app to stage $region"
                command "./bksh deploy-helm-2 -r app-stage -f app-stage-${region}.yaml -g \${DOCKER_TAG} -k k8s2-stage-$region -v 4.2.3"
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
}
```

A Gradle task named `uploadDeployStagePipeline` will be created automatically. Running this Gradle task locally spits out the JSON representation, so you can see if your pipeline looks correct. Inside Buildkite the pipeline will be added to the current build.

This example demonstrates the power of using a language like Groovy to dynamically generate a pipeline based on lists or other dynamic code. You could even parallelize your unit tests by generating a separate step for each subproject reported by Gradle! Check out the [plugin's own pipeline](https://github.com/Widen/gradle-buildkite-plugin/blob/master/build.gradle) for more examples.

# Buildkite Gradle Plugin

[![Build Status](https://badge.buildkite.com/9a1d9c36585e925d7b531e3f456a33de3bddda2a6db9ffee91.svg)](https://buildkite.com/widen/buildkite-gradle-plugin)

A [Gradle] plugin that provides a DSL for dynamically generating [Buildkite] pipelines.

Made with :heart: by Widen.

## Installation

First add the plugin to your project:

```groovy
plugins {
    id 'com.widen.buildkite' version '0.4.0'
}
```

Check out the [releases] page for a list of versions and the changelog for each. Now you are ready to start defining Buildkite pipelines using Groovy inside your `build.gradle`.

## Configuration

Below is an example of defining a Buildkite pipeline:

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

You can also define your pipelines inside Gradle files in a `.buildkite` directory matching the pattern `pipeline*.gradle`. These files will be loaded and evaluated inside the plugin context automatically (unless `buildkite.includeScripts` is set to false).

This example demonstrates the power of using a language like Groovy to dynamically generate a pipeline based on lists or other dynamic code. You could even parallelize your unit tests by generating a separate step for each subproject reported by Gradle! Check out the [plugin's own pipeline](https://github.com/Widen/buildkite-gradle-plugin/blob/master/build.gradle) for more examples.

## License

Available under the Apache-2.0 license. See [the license file](LICENSE) for details.


[Buildkite]: https://buildkite.com
[Gradle]: https://gradle.org
[releases]: https://github.com/Widen/buildkite-gradle-plugin/releases

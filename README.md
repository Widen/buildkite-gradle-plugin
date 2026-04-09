# Buildkite Gradle Plugin

[![Build Status](https://badge.buildkite.com/9a1d9c36585e925d7b531e3f456a33de3bddda2a6db9ffee91.svg)](https://buildkite.com/widen/buildkite-gradle-plugin)
[![GitHub tag (latest SemVer)](https://img.shields.io/github/v/tag/Widen/buildkite-gradle-plugin?color=%2302303A&label=plugin&logo=gradle)][plugin page]

A [Gradle] plugin that provides a DSL for dynamically generating [Buildkite] pipelines.

Made with :heart: by Widen.

## Installation

First add the plugin to your project:

```groovy
plugins {
    id 'com.widen.buildkite' version '1.0.0'
}
```

Check out the [releases] page for a list of versions and the changelog for each. Now you are ready to start defining Buildkite pipelines using Groovy inside your `build.gradle`!

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

You can also define your pipelines inside Gradle files in a `.buildkite` directory matching the pattern `pipeline*.gradle`. These files will be loaded and evaluated inside the pipeline context automatically (unless `buildkite.includeScripts` is set to false). The name of the pipeline is determined from the file name automatically; `pipeline.{name}.gradle` becomes the camelCase version of `{name}`, while `pipeline.gradle` is named `default`. See [`pipeline.extra-steps.gradle`](.buildkite/pipeline.extra-steps.gradle) for an example of this.

This example demonstrates the power of using a language like Groovy to dynamically generate a pipeline based on lists or other dynamic code. You could even parallelize your unit tests by generating a separate step for each subproject reported by Gradle! Check out the [plugin's own pipeline](https://github.com/Widen/buildkite-gradle-plugin/blob/master/build.gradle) for more examples.

## YAML to Groovy DSL Reference

Each example below shows a Buildkite YAML pipeline snippet and the equivalent Groovy DSL.

### String values

```yaml
steps:
  - label: "Run tests"
    command: "gradle test"
    key: "tests"
```

```groovy
commandStep {
    label 'Run tests'
    command 'gradle test'
    key 'tests'
}
```

### String arrays

```yaml
steps:
  - command:
      - "npm install"
      - "npm test"
    artifact_paths:
      - "build/reports/**/*"
      - "build/test-results/**/*"
    branches: "main release/*"
```

```groovy
commandStep {
    commands 'npm install', 'npm test'
    artifactPath 'build/reports/**/*'
    artifactPath 'build/test-results/**/*'
    branches 'main', 'release/*'
}
```

`commands` takes varargs. `artifactPath` is called once per path. `branches` takes varargs and joins with spaces.

### Maps (key-value pairs)

Environment variables support three equivalent syntaxes at both pipeline-level and inside `commandStep`:

```yaml
env:
  CI: "true"
  JAVA_VERSION: "11"
```

```groovy
// Key-value pairs
environment 'CI', 'true'
environment 'JAVA_VERSION', '11'

// Map literal
environment CI: 'true', JAVA_VERSION: '11'

// Closure
environment {
    CI = 'true'
    JAVA_VERSION = '11'
}
```

### Agent targeting

```yaml
steps:
  - agents:
      queue: "deploy-prod"
```

```groovy
commandStep {
    agentQueue 'deploy-prod'
}
```

With region (concatenates as `queue-region`):

```groovy
commandStep {
    agentQueue 'deploy', 'us-east-1'
}
```

### Nested plugin config (built-in helpers)

Docker and Docker Compose have dedicated DSL blocks.

```yaml
steps:
  - plugins:
      - docker#v3.2.0:
          image: "openjdk:11"
          always-pull: true
          propagate-environment: true
          volumes:
            - "/tmp/cache:/cache"
```

```groovy
commandStep {
    docker {
        image 'openjdk:11'
        alwaysPull()
        propagateEnvironment()
        volume '/tmp/cache', '/cache'
    }
}
```

```yaml
steps:
  - plugins:
      - docker-compose#v3.0.3:
          run: "app"
          build:
            - "app"
            - "db"
          config:
            - "docker-compose.yml"
            - "docker-compose.ci.yml"
```

```groovy
commandStep {
    dockerCompose {
        run 'app'
        build 'app', 'db'
        composeFile 'docker-compose.yml'
        composeFile 'docker-compose.ci.yml'
    }
}
```

### Arbitrary plugin config (map or closure)

For plugins without a built-in helper:

```yaml
steps:
  - plugins:
      - artifacts#v1.3.0:
          download: "build/libs/*.jar"
          upload: "build/reports/**/*"
```

```groovy
// Map form
commandStep {
    plugin 'artifacts#v1.3.0', [download: 'build/libs/*.jar', upload: 'build/reports/**/*']
}

// Closure form
commandStep {
    plugin 'artifacts#v1.3.0', {
        download 'build/libs/*.jar'
        upload 'build/reports/**/*'
    }
}
```

### Forcing a YAML array inside plugin config

The DSL unwraps single method-call arguments to scalars, so `targets 'lint'` produces `targets: lint` (a string), not `targets: [lint]` (an array). Use assignment syntax or an explicit list to force array output.

Single-item array:

```yaml
steps:
  - plugins:
      - my-plugin#v1.0.0:
          targets:
            - lint
```

```groovy
// Assignment syntax (recommended)
commandStep {
    plugin 'my-plugin#v1.0.0', {
        targets = ['lint']
    }
}

// Explicit list argument
commandStep {
    plugin 'my-plugin#v1.0.0', {
        targets(['lint'])
    }
}
```

Three-item array (multi-argument method call works naturally):

```yaml
steps:
  - plugins:
      - my-plugin#v1.0.0:
          targets:
            - lint
            - test
            - build
```

```groovy
commandStep {
    plugin 'my-plugin#v1.0.0', {
        targets 'lint', 'test', 'build'
    }
}
```

### Retry with nested map

```yaml
steps:
  - retry:
      automatic:
        - exit_status: -1
          limit: 3
```

```groovy
commandStep {
    automaticRetry {
        exitStatus(-1)
        limit 3
    }
}
```

### Block step with fields

```yaml
steps:
  - block: "Deploy"
    prompt: "Ready to deploy?"
    fields:
      - text: "Release Notes"
        key: "release-notes"
        hint: "Describe what changed"
        required: true
      - select: "Environment"
        key: "deploy-env"
        options:
          - label: "Staging"
            value: "staging"
          - label: "Production"
            value: "prod"
```

```groovy
blockStep('Deploy') {
    prompt 'Ready to deploy?'
    textField('Release Notes', 'release-notes') {
        hint 'Describe what changed'
        required()
    }
    selectField('Environment', 'deploy-env') {
        option 'Staging', 'staging'
        option 'Production', 'prod'
    }
}
```

### Trigger step with build config

```yaml
steps:
  - trigger: "deploy-pipeline"
    label: "Trigger Deploy"
    async: false
    build:
      message: "Deploy from main"
      branch: "main"
      commit: "HEAD"
      env:
        DEPLOY_ENV: "production"
      meta_data:
        release: "v2.1.0"
```

```groovy
triggerStep('deploy-pipeline') {
    label 'Trigger Deploy'
    async false
    build {
        message 'Deploy from main'
        branch 'main'
        commit 'HEAD'
        environment 'DEPLOY_ENV', 'production'
        metadata {
            release = 'v2.1.0'
        }
    }
}
```

### Conditional logic and dynamic generation

The main advantage over static YAML -- use Groovy control flow for dynamic pipelines:

```yaml
steps:
  - label: "Test us-east-1"
    command: "gradle test -Dregion=us-east-1"
    agents:
      queue: "test-us-east-1"
  - label: "Test eu-west-1"
    command: "gradle test -Dregion=eu-west-1"
    agents:
      queue: "test-eu-west-1"
```

```groovy
def regions = ['us-east-1', 'eu-west-1']

regions.each { region ->
    commandStep {
        label "Test $region"
        command "gradle test -Dregion=$region"
        agentQueue 'test', region
    }
}
```

Conditional step execution:

```groovy
commandStep {
    label 'Deploy'
    command './deploy.sh'
    onDefaultBranch()  // Only on default branch
}

commandStep {
    label 'Preview'
    command './preview.sh'
    notOnDefaultBranch()  // Only on non-default branches
}

commandStep {
    label 'Nightly'
    command './nightly.sh'
    ifCondition 'build.source == "schedule"'
}
```

## Tasks

Aside from the `upload{name}Pipeline` tasks created, a `pipelines` task is also provided that lists the names of all pipelines found in the project.

## Publishing

To release a new version, simply update the version line in `build.gradle`:

```groovy
version = '0.6.0'  // Update to your new version
```

Then commit and publish:

```bash
git add build.gradle
git commit -m "Bump version to 1.0.0"
git tag 1.0.0
git push origin master --tags
mise publish-plugin
```

## License

Available under the Apache-2.0 license. See [the license file](LICENSE) for details.


[Buildkite]: https://buildkite.com
[Gradle]: https://gradle.org
[plugin page]: https://plugins.gradle.org/plugin/com.widen.buildkite
[releases]: https://github.com/Widen/buildkite-gradle-plugin/releases

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

You can also define pipelines in standalone Gradle script files inside a `.buildkite/` directory. Any file matching `pipeline*.gradle` is read and a Gradle task created automatically (unless `buildkite.includeScripts = false` is set).

**File naming → pipeline name → Gradle task name:**

| File | Pipeline name | Gradle task |
|---|---|---|
| `.buildkite/pipeline.gradle` | `default` | `uploadPipeline` |
| `.buildkite/pipeline.extra-steps.gradle` | `extraSteps` | `uploadExtraStepsPipeline` |
| `.buildkite/pipeline.deploy-prod.gradle` | `deployProd` | `uploadDeployProdPipeline` |

The segment between `pipeline.` and `.gradle` is converted to camelCase (hyphens and other separators become word boundaries).

**Script format:** Write DSL calls directly at the top level — no `buildkite { pipeline { } }` wrapper needed, since the file is already evaluated inside the pipeline context:

```groovy
// .buildkite/pipeline.extra-steps.gradle

environment {
    DEPLOY_ENV = 'staging'
}

commandStep {
    label 'Run tests'
    command './gradlew test'
}

waitStep()

commandStep {
    label 'Deploy'
    command './deploy.sh'
    // Access the Gradle project object directly
    environment {
        VERSION = project.version
    }
}
```

The `project` object is available in script files, so you can read Gradle project properties (e.g. `project.version`, `project.name`) to drive pipeline logic. See [`.buildkite/pipeline.extra-steps.gradle`](.buildkite/pipeline.extra-steps.gradle) for a working example.

This example demonstrates the power of using a language like Groovy to dynamically generate a pipeline based on lists or other dynamic code. You could even parallelize your unit tests by generating a separate step for each subproject reported by Gradle! Check out the [plugin's own pipeline](https://github.com/Widen/buildkite-gradle-plugin/blob/master/build.gradle) for more examples.

## Tasks

Aside from the `upload{name}Pipeline` tasks created, a `pipelines` task is also provided that lists the names of all pipelines found in the project.

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

### Special keyword reference

These DSL methods have non-obvious output — the YAML key name, structure, or value differs from what the method name suggests.

| DSL Method | YAML Output | Notes |
|---|---|---|
| `agentQueue 'name'` | `agents: {queue: name}` | Writes into a nested map under `agents`, not a top-level key |
| `agentQueue 'name', 'region'` | `agents: {queue: name-region}` | Concatenates name and region with `-`; `us-east-1` is stripped to bare name |
| `artifactPath 'glob'` | `artifact_paths: [glob]` | Accumulates — call multiple times to build the list; key is snake_case plural |
| `concurrency 'group', N` | `concurrency: N`<br>`concurrency_group: group` | Single call writes two separate top-level keys simultaneously |
| `timeout Duration.ofMinutes(N)` | `timeout_in_minutes: N` | Accepts `java.time.Duration`; converts to minutes with a minimum of 1 |
| `softFail true` | `soft_fail: true` | Boolean form — sets a plain boolean |
| `softFail 1, 127` | `soft_fail: [{exit_status: 1}, {exit_status: 127}]` | Vararg int form produces a list of maps — structurally different from the boolean form |
| `branches 'main', 'v*'` | `branches: "main v*"` | Varargs joined into a single space-delimited string, not a YAML list |
| `dependsOn 'a'` | `depends_on: a` | Single string → scalar value |
| `dependsOn 'a', 'b'` | `depends_on: [a, b]` | Multiple strings → list; the two forms produce structurally different YAML |
| `onDefaultBranch()` | `if: "build.branch == pipeline.default_branch"` | Zero-arg shortcut; hard-codes the Buildkite expression string |
| `notOnDefaultBranch()` | `if: "build.branch != pipeline.default_branch"` | Inverse of `onDefaultBranch()` |
| `ifCondition 'expr'` | `if: expr` | Named `ifCondition` to avoid collision with Groovy's reserved `if` keyword |
| `composeFile 'path'` | `config: [path]` | DSL name differs from the YAML key (`config`); accumulates into a list |
| `plugin 'name', config` | `plugins: [{name#version: config}]` | Auto-appends the default version from `buildkite.pluginVersion` when no `#version` is in the name |
| `waitStep()` | `wait` (bare string) | Emits the plain string `"wait"`, not a map |
| `waitStepContinueOnFailure()` | `wait: {continue_on_failure: true}` | Emits a map; contrast with `waitStep()` which emits the bare string |

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

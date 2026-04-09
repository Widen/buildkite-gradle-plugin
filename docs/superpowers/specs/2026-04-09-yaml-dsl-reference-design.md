# Design: YAML to Groovy DSL Quick Reference for README

**Date:** 2026-04-09
**Status:** Approved

## Goal

Add a quick-reference section to the README that shows common Buildkite YAML pipeline constructs alongside their Groovy DSL equivalents. Targeted at experienced Buildkite users who already know YAML and want to learn the DSL syntax.

## Location

New `## YAML to Groovy DSL Reference` section in `README.md`, inserted between the existing "Configuration" and "Tasks" sections.

## Format

Each pattern is a sub-section (`###` heading) containing:

1. A brief label (the heading itself)
2. A YAML fenced code block showing the Buildkite pipeline YAML
3. A Groovy fenced code block showing the equivalent DSL

Examples are minimal -- just enough to show the pattern. Values should be realistic (plausible CI commands, not placeholder `foo`/`bar`).

## Patterns (in order)

### 1. String values

Scalar configuration: `label`, `command`, `key`.

YAML:
```yaml
steps:
  - label: "Run tests"
    command: "gradle test"
    key: "tests"
```

Groovy:
```groovy
commandStep {
    label 'Run tests'
    command 'gradle test'
    key 'tests'
}
```

### 2. String arrays

List values: multiple commands, artifact paths, branches.

YAML:
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

Groovy:
```groovy
commandStep {
    commands 'npm install', 'npm test'
    artifactPath 'build/reports/**/*'
    artifactPath 'build/test-results/**/*'
    branches 'main', 'release/*'
}
```

Note: `commands` takes varargs. `artifactPath` is called once per path. `branches` takes varargs and joins with spaces.

### 3. Maps (key-value pairs)

Environment variables support three syntaxes. Show all three.

YAML (pipeline-level):
```yaml
env:
  CI: "true"
  JAVA_VERSION: "11"
```

Groovy -- three equivalent forms:
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

Note: All three forms work at both pipeline-level and inside `commandStep`.

### 4. Agent targeting

Simple map with `agents.queue`.

YAML:
```yaml
steps:
  - agents:
      queue: "deploy-prod"
```

Groovy:
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

### 5. Nested plugin config (built-in helpers)

Docker and Docker Compose have dedicated DSL blocks.

YAML:
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

Groovy:
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

YAML (docker-compose):
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

Groovy:
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

### 6. Arbitrary plugin config (map or closure)

For plugins without a built-in helper.

YAML:
```yaml
steps:
  - plugins:
      - artifacts#v1.3.0:
          download: "build/libs/*.jar"
          upload: "build/reports/**/*"
```

Groovy (map form):
```groovy
commandStep {
    plugin 'artifacts#v1.3.0', [download: 'build/libs/*.jar', upload: 'build/reports/**/*']
}
```

Groovy (closure form -- the closure is passed as the second argument via Groovy's trailing closure syntax):
```groovy
commandStep {
    plugin 'artifacts#v1.3.0', {
        download 'build/libs/*.jar'
        upload 'build/reports/**/*'
    }
}
```

### 7. Retry with nested map

YAML:
```yaml
steps:
  - retry:
      automatic:
        - exit_status: -1
          limit: 3
```

Groovy:
```groovy
commandStep {
    automaticRetry {
        exitStatus(-1)
        limit 3
    }
}
```

### 8. Block step with fields

Nested list of maps with typed sub-objects.

YAML:
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
        multiple: false
        options:
          - label: "Staging"
            value: "staging"
          - label: "Production"
            value: "prod"
```

Groovy:
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

### 9. Trigger step with build config

Nested map containing sub-maps (`env`, `meta_data`).

YAML:
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

Groovy:
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

### 10. Conditional logic and dynamic generation

Groovy control flow for dynamic pipelines -- the main advantage over static YAML.

YAML (static, must be duplicated manually):
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

Groovy (dynamic):
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

Also show `if` conditions:
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

## What Does Not Change

- Existing "Configuration" example (the full pipeline) stays as-is
- "Installation", "Tasks", "Publishing", "License" sections are untouched
- No changes to plugin source code

## Constraints

- Examples must match actual plugin behavior (verified against DSL source and test fixtures)
- No placeholder values -- use realistic CI examples
- Match existing README tone (concise, no tutorial prose)

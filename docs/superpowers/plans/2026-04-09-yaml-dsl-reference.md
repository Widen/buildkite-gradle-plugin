# YAML to Groovy DSL Reference — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a quick-reference section to README.md showing 10 common Buildkite YAML patterns alongside their Groovy DSL equivalents.

**Architecture:** Single edit to `README.md` — insert a new `## YAML to Groovy DSL Reference` section between lines 65 ("Configuration" section end) and 67 ("## Tasks"). Content comes directly from the approved spec.

**Tech Stack:** Markdown

---

### Task 1: Insert the YAML to Groovy DSL Reference section into README.md

**Files:**
- Modify: `README.md:65-67` (insert new section between "Configuration" and "Tasks")

**Reference:** All example content is defined in `docs/superpowers/specs/2026-04-09-yaml-dsl-reference-design.md`, sections 1-10.

- [ ] **Step 1: Insert the new section**

In `README.md`, insert the following content between the line ending "Check out the [plugin's own pipeline]..." (line 65) and the "## Tasks" heading (line 67). The new section starts after one blank line following line 65.

```markdown
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
```

- [ ] **Step 2: Verify the edit**

Visually confirm:
1. The new section appears between "Configuration" and "Tasks"
2. All 10 patterns are present with correct headings
3. No existing content was modified
4. Fenced code blocks are properly closed (count opening/closing triple-backticks)

Run: `grep -c '```' README.md`
Expected: Even number (every opening block has a closing block). Original README has 6 (3 blocks). New section adds 40 (20 blocks). Total: 46.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: add YAML to Groovy DSL quick reference to README"
```

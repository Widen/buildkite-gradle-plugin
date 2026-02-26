# Expected Pipeline Output

This directory contains baseline YAML files that represent the expected output from the Buildkite Gradle Plugin DSL.

## Purpose

These YAML files are used by the `PipelineDslFunctionalTest` class to:
1. Validate that the DSL generates the correct pipeline JSON/YAML output
2. Detect unintended changes to pipeline generation
3. Serve as examples of what each DSL configuration produces

## How It Works

Each test in `PipelineDslFunctionalTest`:
1. Configures a pipeline using the DSL
2. Runs the `uploadPipeline` task (which outputs JSON when not in CI)
3. Converts the JSON output to YAML
4. Compares it with the corresponding baseline file in this directory

### First Run Behavior

When a test runs and its corresponding YAML file doesn't exist:
- The test will **create** the baseline file with the actual output
- This file becomes the "expected" output for future test runs
- You should review the generated file to ensure it's correct

### Subsequent Runs

When the baseline file exists:
- The test compares the actual output against the baseline
- If they match, the test passes
- If they differ, the test fails and shows the difference

## File Naming Convention

Files are named after their test names, converted to kebab-case:
- Test: `test command step with docker plugin`
- File: `command-step-docker-plugin.yaml`

## Updating Baselines

If you intentionally change the DSL output:
1. Delete the affected baseline YAML files
2. Run the tests again to regenerate them
3. Review the new files to ensure correctness
4. Commit the updated baseline files

## Coverage

The tests cover all major DSL features:
- **Pipeline-level configuration**: environment variables, interpolation, replace
- **Wait steps**: basic and continue-on-failure variants
- **Command steps**: with all available options including:
  - Agent queues and regional queues
  - Artifacts
  - Concurrency
  - Environment variables
  - Parallelism
  - Automatic retry
  - Skip conditions
  - Timeouts
  - Dependencies (key/depends_on)
  - Branch conditionals
  - Soft fail
  - Plugins (Docker, Docker Compose, custom)
- **Block steps**: with prompts, text fields, and select fields
- **Trigger steps**: basic and with build configuration
- **Complex pipelines**: combining multiple step types

## Example

A test for a basic command step:

```groovy
def "test command step with basic properties"() {
    given:
    buildFile << """
        buildkite {
            pipeline {
                commandStep {
                    label 'Build'
                    command 'echo "Hello World"'
                }
            }
        }
    """
    
    when:
    def output = runUploadPipeline()
    def json = extractJsonFromOutput(output)
    
    then:
    assertMatchesExpectedYaml('command-step-basic', json)
}
```

Generates `command-step-basic.yaml`:
```yaml
env: {}
steps:
- agents: {queue: builder}
  command: echo "Hello World"
  label: Build
```


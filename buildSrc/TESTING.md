# Testing with Gradle TestKit

This project uses [Gradle TestKit](https://docs.gradle.org/current/userguide/test_kit.html) for testing the Buildkite Gradle plugin.

## Overview

TestKit allows you to test Gradle plugins by running a Gradle build with your plugin applied in an isolated environment. This provides:

- **Functional Testing**: Execute real Gradle builds with your plugin
- **Isolated Environment**: Each test runs in a temporary directory
- **Full Build Lifecycle**: Test plugin behavior across all build phases
- **GradleRunner API**: Programmatic control over test builds

## Test Types

### Functional Tests
Located in `src/test/groovy/com/widen/plugins/buildkite/BuildkitePluginFunctionalTest.groovy`

These tests use `GradleRunner` to execute actual Gradle builds with the plugin applied. They verify:
- Plugin can be applied successfully
- Tasks are created correctly
- Plugin configuration works as expected
- Build output contains expected content

Example:
```groovy
def result = GradleRunner.create()
    .withProjectDir(testProjectDir.root)
    .withArguments('pipelines')
    .withPluginClasspath()
    .build()
```

### Unit Tests
Located in `src/test/groovy/com/widen/plugins/buildkite/BuildkitePluginTest.groovy`

These tests use `ProjectBuilder` to create a Gradle project instance for faster unit testing of plugin logic without running a full build.

## Running Tests

Run all tests:
```bash
./gradlew test
```

Run tests from buildSrc:
```bash
cd buildSrc
../gradlew test
```

Run with test output:
```bash
./gradlew test --info
```

Run specific test:
```bash
./gradlew test --tests BuildkitePluginFunctionalTest
```

## Test Framework

The tests use [Spock Framework](http://spockframework.org/) which provides:
- BDD-style test structure (given/when/then)
- Groovy-based DSL for readable tests
- Powerful assertion and mocking capabilities
- Data-driven testing support

## Directory Structure

```
buildSrc/
├── build.gradle                    # Build config with TestKit dependencies
└── src/
    ├── main/
    │   └── groovy/                # Plugin source code
    └── test/
        ├── groovy/                # Test source code
        │   └── com/widen/plugins/buildkite/
        │       ├── BuildkitePluginTest.groovy              # Unit tests
        │       └── BuildkitePluginFunctionalTest.groovy    # Functional tests
        └── resources/             # Test resources (if needed)
```

## Writing New Tests

### Functional Test Template
```groovy
def "test description"() {
    given:
    buildFile << """
        plugins {
            id 'com.widen.buildkite'
        }
        // plugin configuration
    """

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withArguments('taskName')
        .withPluginClasspath()
        .build()  // or .buildAndFail() for negative tests

    then:
    result.task(':taskName').outcome == TaskOutcome.SUCCESS
    result.output.contains('expected text')
}
```

### Unit Test Template
```groovy
def "test description"() {
    given:
    project.pluginManager.apply('com.widen.buildkite')

    when:
    // perform action

    then:
    // assertions
}
```

## Key TestKit Features

### GradleRunner
- `.withProjectDir()` - Set test project directory
- `.withArguments()` - Specify Gradle arguments/tasks
- `.withPluginClasspath()` - Include plugin under test
- `.build()` - Execute build expecting success
- `.buildAndFail()` - Execute build expecting failure
- `.forwardOutput()` - Show output in console

### TaskOutcome
Verify task execution results:
- `TaskOutcome.SUCCESS` - Task executed successfully
- `TaskOutcome.FAILED` - Task failed
- `TaskOutcome.UP_TO_DATE` - Task skipped (up-to-date)
- `TaskOutcome.SKIPPED` - Task skipped
- `TaskOutcome.FROM_CACHE` - Task output from cache

## Resources

- [Gradle TestKit User Guide](https://docs.gradle.org/current/userguide/test_kit.html)
- [Spock Framework](http://spockframework.org/)
- [Testing Gradle Plugins](https://docs.gradle.org/current/userguide/testing_gradle_plugins.html)


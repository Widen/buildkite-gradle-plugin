# Design: Forcing a YAML Array Inside Plugin Config

**Date:** 2026-04-09
**Status:** Approved

## Goal

Document how to force a YAML array (sequence) inside a custom plugin configuration closure when the plugin expects a list but only one item is needed. Add a functional test to cover both single-item and multi-item array cases.

## Background

`PluginBuilder.invokeMethod` (line 38–39 of `PluginBuilder.groovy`) unconditionally unwraps single method-call arguments:

```groovy
} else if (expandedArgs.size() == 1) {
    model[name] = expandedArgs[0]   // scalar, not a list
}
```

So `tasks ':check'` inside a plugin closure produces `tasks: ":check"` (scalar) in the output, not `tasks: [":check"]` (array). This surprises users whose plugin requires a YAML sequence even for one item.

Two existing syntaxes **do** produce a single-item array:
- Assignment: `tasks = [':check']` → calls `setProperty`, which calls `expand(Iterable)` → list preserved
- Explicit list argument: `tasks([':check'])` → list lands as `expandedArgs[0]` (a List), preserved intact

Multi-item method calls (`tasks ':check', ':test', ':integrationTest'`) naturally produce a list via line 41.

## Changes

### 1. README.md

Add a new `### Forcing a YAML array inside plugin config` sub-section to the `## YAML to Groovy DSL Reference` section.

**Placement:** After `### Arbitrary plugin config (map or closure)`, before `### Retry with nested map`.

**Content — single-item array:**

YAML:
```yaml
steps:
  - plugins:
      - my-plugin#v1.0.0:
          targets:
            - lint
```

Groovy (two equivalent forms):
```groovy
// Assignment syntax (recommended for single-item arrays)
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

Note to include: Using bare `targets 'lint'` produces a scalar string, not an array — the DSL unwraps single method-call arguments. Use assignment (`=`) or wrap in a list to force array output.

**Content — three-item array:**

YAML:
```yaml
steps:
  - plugins:
      - my-plugin#v1.0.0:
          targets:
            - lint
            - test
            - build
```

Groovy:
```groovy
commandStep {
    plugin 'my-plugin#v1.0.0', {
        targets 'lint', 'test', 'build'
    }
}
```

Note: Multiple arguments naturally produce an array — no special syntax needed.

### 2. New test in PipelineDslFunctionalTest.groovy

**Test name:** `'plugin with forced array config'`

**Location:** After the existing `'command step with arbitrary plugin config'` test (or nearest logical position).

**Test build.gradle content:**
```groovy
buildkite {
    pipeline {
        commandStep {
            label 'Single-item array'
            plugin 'my-plugin#v1.0.0', {
                targets = ['lint']
            }
        }
        commandStep {
            label 'Multi-item array'
            plugin 'my-plugin#v1.0.0', {
                targets 'lint', 'test', 'build'
            }
        }
    }
}
```

**Expected output fixture:** `buildSrc/src/test/resources/expected-pipeline-output/command-step-plugin-array-config.yaml`

```yaml
env: {}
steps:
- agents: {queue: builder}
  label: Single-item array
  plugins:
  - my-plugin#v1.0.0:
      targets: [lint]
- agents: {queue: builder}
  label: Multi-item array
  plugins:
  - my-plugin#v1.0.0:
      targets: [lint, test, build]
```

## Constraints

- No changes to `PluginBuilder.groovy` or any other source file — docs and test only
- The test must follow the exact pattern of existing tests in `PipelineDslFunctionalTest.groovy`:
  - Uses `buildFile << """..."""` to write the build script
  - Runs `gradleRunner.withArguments('uploadDefaultPipeline').build()`
  - Compares `result.output` to the expected fixture via `assertPipelineOutput`
- Match README style: concise, no tutorial prose

## Files Changed

| File | Change |
|------|--------|
| `README.md` | Add new sub-section between "Arbitrary plugin config" and "Retry with nested map" |
| `buildSrc/src/test/groovy/com/widen/plugins/buildkite/PipelineDslFunctionalTest.groovy` | Add one new test |
| `buildSrc/src/test/resources/expected-pipeline-output/command-step-plugin-array-config.yaml` | New fixture file |

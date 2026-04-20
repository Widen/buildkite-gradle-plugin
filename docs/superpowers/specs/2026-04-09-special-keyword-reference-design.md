# Design: Special Keyword Reference Table

**Date:** 2026-04-09
**Status:** Approved

## Goal

Add a `### Special keyword reference` table at the end of the `## YAML to Groovy DSL Reference` section in `README.md`, covering DSL methods whose YAML output is non-obvious — the key name, structure, or value differs from what the method name suggests.

## Location

New `### Special keyword reference` sub-section inserted immediately before `## Tasks` (i.e., at the end of the `## YAML to Groovy DSL Reference` section), after `### Conditional logic and dynamic generation`.

## Format

One introductory sentence, then a single Markdown table with three columns:

- **DSL Method** — the Groovy call, inline code-formatted, showing representative arguments
- **YAML Output** — the key(s) actually written to the pipeline, inline code-formatted
- **Notes** — one concise sentence explaining what's non-obvious

## Table Content (15 rows)

| DSL Method | YAML Output | Notes |
|---|---|---|
| `agentQueue 'name'` | `agents: {queue: name}` | Writes into a nested map under `agents`, not a top-level `agentQueue` key |
| `agentQueue 'name', 'region'` | `agents: {queue: name-region}` | Concatenates name and region with `-`; `us-east-1` is stripped to bare name |
| `artifactPath 'glob'` | `artifact_paths: [glob]` | Accumulates — call multiple times to add paths; produces snake_case plural key |
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
| `plugin 'name', config` | `plugins: [{name#version: config}]` | Auto-appends the default version from `buildkite.pluginVersion` when no `#version` is present in the name |
| `waitStep()` | `wait` (bare string) | Emits the plain string `"wait"`, not a map |
| `waitStepContinueOnFailure()` | `wait: {continue_on_failure: true}` | Emits a map; contrast with `waitStep()` which emits the bare string |

*(17 rows total — the two `dependsOn` and two `softFail` overloads are each split into separate rows to show the structural difference.)*

## Intro Sentence

> These DSL methods have non-obvious output — the YAML key name, structure, or value differs from what the method name suggests.

## What Does Not Change

- No source code changes
- All other README sections unchanged
- No new tests required (table is documentation of existing behavior, all covered by existing tests)

## Files Changed

| File | Change |
|------|--------|
| `README.md` | Add `### Special keyword reference` sub-section before `## Tasks` |

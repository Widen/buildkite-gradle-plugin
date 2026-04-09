# Special Keyword Reference Table — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `### Special keyword reference` table at the end of the `## YAML to Groovy DSL Reference` section in `README.md`, documenting 17 DSL methods whose YAML output is non-obvious.

**Architecture:** Single edit to `README.md` — insert the table sub-section between line 421 (blank line after the last code block of "Conditional logic and dynamic generation") and line 422 (`## Tasks`). Documentation only, no code or test changes.

**Tech Stack:** Markdown

---

### Task 1: Insert the special keyword reference table

**Files:**
- Modify: `README.md:421-422` (insert new sub-section between "Conditional logic" and "## Tasks")

- [ ] **Step 1: Insert the new sub-section**

In `README.md`, replace the blank line + `## Tasks` boundary with the new section followed by `## Tasks`. Specifically, insert after line 421 (the blank line after the last ` ``` ` of the "Conditional logic" section) and before line 422 (`## Tasks`).

The new content to insert:

````markdown
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

````

- [ ] **Step 2: Verify the edit**

Run:
```bash
grep -n "^### \|^## " README.md
```

Expected — confirm the new section appears as the last `###` before `## Tasks`:
```
...
372:### Conditional logic and dynamic generation
???:### Special keyword reference
???:## Tasks
...
```

Also verify balanced code fences:
```bash
grep -c '```' README.md
```

Expected: even number (same as before — this section adds no code blocks, only a table).

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: add special keyword reference table to DSL reference section"
```

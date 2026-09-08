---
id: aix-01m21fkah2mw
title: Move harness-owned update policy onto :runtime-env descriptors
status: open
type: task
priority: 3
mode: afk
created: '2026-09-08T21:43:15.234763621Z'
updated: '2026-09-08T21:43:19.628263500Z'
tags:
- harness
- registry
acceptance:
- title: Claude's DISABLE_AUTOUPDATER=1 comes from the :claude descriptor's :runtime-env and is only set when Claude is enabled
  done: false
- title: the hard-coded DISABLE_AUTOUPDATER line in docker/run.clj is gone
  done: false
- title: pi's PI_SKIP_VERSION_CHECK=true comes from :runtime-env and is dropped from :env-passthrough
  done: false
- title: runtime-env tests in run_test.clj are table-driven over every descriptor that declares :runtime-env
  done: false
- title: Gemini CLI and OpenCode update-check knobs are checked against current upstream docs and either added or recorded as not applicable
  done: false
- title: 'an ADR records the precedence rule: config env < harness :runtime-env < docker_args'
  done: false
- title: docs/HARNESSES.md shows a Set-by-aishell entry next to the forwarded variables for each affected harness
  done: false
- title: the full test suite passes and clj-kondo --lint src test is clean
  done: false
links:
- aix-01m219j5rjsq
---

## Description

PR #2 introduced `:runtime-env` on the Harness descriptor: fixed `{"VAR" "value"}` entries that aishell sets whenever the Harness is enabled, applied after config `env` so aishell's update ownership wins, and losing only to the `docker_args` escape hatch. Copilot uses it for `COPILOT_AUTO_UPDATE=false`.

Three other Harnesses carry the same "aishell owns updates" policy, each expressed differently:

- **Claude Code**: `run.clj` hard-codes `-e DISABLE_AUTOUPDATER=1` for every container, whether or not Claude is enabled. That is Claude's variable and belongs on its descriptor.
- **pi**: `PI_SKIP_VERSION_CHECK` sits in `:env-passthrough`, so the version check is only skipped when the user happens to export the variable on the host. The Harness volume is read-only and pinned, so the check is noise in every Sandbox; it should be unconditional.
- **Codex**: `:fixed-args ["-c" "check_for_update_on_startup=false"]` is the same policy through argv. It stays as is; `:fixed-args` and `:runtime-env` are complementary (argv policy vs env policy).

### Scope

- Move the Claude flag and the pi variable into their descriptors' `:runtime-env`; delete the hard-coded line and the pi passthrough entry.
- Check Gemini CLI and OpenCode for update-check or self-update knobs against their current documentation. Variable names change between releases, so verify before adding. Telemetry opt-outs are a separate policy decision: do not add them here, note them in the ticket if found.
- Extend `run_test.clj` so runtime-env coverage iterates over every descriptor declaring `:runtime-env`, instead of a Copilot-only assertion.
- Write the ADR the PR #2 review asked for, since the precedence rule now governs several Harnesses.
- Update `docs/HARNESSES.md`, `docs/CONFIGURATION.md` and `llm.txt` where they describe these variables.

### Behaviour change to call out in the changelog

`DISABLE_AUTOUPDATER=1` stops being set in Sandboxes where Claude is disabled. Only Claude Code reads it, so no user-visible effect is expected, but it must be stated.

### Out of scope

- Telemetry opt-outs.
- Changing `:fixed-args` for Codex.
- Any change to the `env` or `docker_args` escape hatches.

# Harness-owned runtime environment, and where it sits in the precedence chain

A Harness descriptor may declare `:runtime-env` — fixed `{"VAR" "value"}`
entries aishell sets in every Sandbox where that Harness is enabled. Inside a
Sandbox the last `-e` flag wins, and aishell emits them in one fixed order:

```
config env  <  harness :runtime-env  <  docker_args
```

## Context

Several Harnesses update themselves. Claude Code runs an autoupdater, GitHub
Copilot CLI refreshes its own npm package, pi checks its version on startup,
and Codex CLI checks for updates on startup. None of that is wanted inside a
Sandbox: the Harness volume is mounted read-only, so a self-update either
fails outright or, where it can write elsewhere, defeats the version pin that
`aishell setup --with-<id>=VERSION` and `aishell update` exist to hold. aishell
owns which version of a Harness runs; the Harness does not.

Before this decision the policy was expressed a different way per Harness:

- Claude Code: `-e DISABLE_AUTOUPDATER=1`, hard-coded in `build-docker-args`
  and set in every Sandbox whether or not Claude was enabled.
- pi: `PI_SKIP_VERSION_CHECK` listed in `:env-passthrough`, so the check was
  skipped only when the user happened to export the variable on the host.
- Codex CLI: `:fixed-args ["-c" "check_for_update_on_startup=false"]` — the
  same policy expressed through argv.
- GitHub Copilot CLI: `:runtime-env {"COPILOT_AUTO_UPDATE" "false"}`, which is
  the shape this ADR generalises.

Three spellings for one policy meant the policy could not be read off the
registry, and two of them were wrong in a way a reader would not notice: one
leaked into Sandboxes that had no Claude in them, the other did nothing unless
the user had already solved the problem by hand.

## Decision

- **`:runtime-env` is the descriptor key for env-shaped policy aishell owns.**
  It is a map of literal strings; no host lookup, no interpolation. A
  descriptor declares only variables its own Harness reads.
- **It applies exactly when the Harness is enabled**, keyed off `:state-key`,
  like every other per-Harness derivation. Nothing a disabled Harness declares
  reaches the Sandbox.
- **It is emitted after config `env` and before `docker_args`.** A user's
  ordinary `env:` entry cannot silently re-enable a self-updater, because the
  point of the policy is that the pin holds. `docker_args` is the low-level
  escape hatch, documented as such, and it stays the last word — someone who
  writes `docker_args: ["-e", "COPILOT_AUTO_UPDATE=true"]` has said what they
  mean.
- **`:fixed-args` stays the argv counterpart.** Codex CLI keeps expressing its
  update policy through `-c check_for_update_on_startup=false`, because that is
  the knob Codex actually offers. The two keys are complementary: argv policy
  and env policy, chosen by what the Harness supports rather than by taste.
- **A knob is added only when its upstream documents it.** Variable names churn
  between releases, and an undocumented one is a no-op that reads like a
  guarantee. As of this ADR, Gemini CLI (`general.enableAutoUpdate` in
  `settings.json`) and OpenCode (`autoupdate` in `opencode.json`) expose the
  setting through config files only, so neither declares `:runtime-env`.
- **Telemetry opt-outs are out of scope.** They are a separate policy decision
  about what leaves a Sandbox, not about who owns the installed version.

## Consequences

- `DISABLE_AUTOUPDATER=1` is no longer set in Sandboxes without Claude Code.
  Only Claude Code reads it, so no behavior changes, but the variable stops
  appearing in `docker run` argv where it never belonged.
- pi's version check is now skipped in every Sandbox rather than only where the
  host exported `PI_SKIP_VERSION_CHECK`; the variable leaves `:env-passthrough`,
  so setting it on the host no longer has an effect of its own.
- The policy is readable off the registry: `(filter :runtime-env harness/registry)`
  answers "what does aishell force, and for which Harness". The behavioral
  runtime-env tests iterate that same filter, so a Harness that gains the key is
  exercised without new tests; the literal inventory beside them still has to be
  extended, as every registry table does.
- Config-file knobs stay out of reach. aishell mounts host Harness config
  directories rather than writing into them, so Gemini CLI and OpenCode
  self-update unless the user disables it in their own settings. Making aishell
  author those files is a larger decision about ownership of user config, and
  this ADR does not take it.

---
id: aix-01m219j5rjsq
title: Support GitHub Copilot CLI as a first-class Harness
status: closed
type: feature
priority: 2
mode: afk
created: '2026-09-08T19:57:46.129924992Z'
updated: '2026-09-08T21:43:19.628263500Z'
closed: '2026-09-08T20:07:08.607879786Z'
tags:
- copilot
- harness
acceptance:
- title: setup supports --with-copilot and --with-copilot=VERSION
  done: true
- title: the @github/copilot package installs into the shared Harness volume
  done: true
- title: aishell copilot and the in-Sandbox copilot alias share launch argument semantics
  done: true
- title: bare Copilot launches do not inject --allow-all or --yolo
  done: true
- title: host ~/.copilot persists only for Copilot-enabled Sandboxes
  done: true
- title: only COPILOT_GITHUB_TOKEN and COPILOT_GH_HOST pass through automatically
  done: true
- title: COPILOT_AUTO_UPDATE=false is effective for Copilot-enabled Sandboxes
  done: true
- title: setup reuse, update, help, check, info, and summaries use the GitHub Copilot CLI descriptor
  done: true
- title: existing Copilot-disabled Harness-volume hashes remain unchanged
  done: true
- title: README, Harness, configuration, llm, context, and changelog documentation covers Copilot
  done: true
- title: the full test suite passes
  done: true
- title: clj-kondo --lint src test completes with no lint errors
  done: true
links:
- aix-01m21fkah2mw
---

## Problem Statement

aishell cannot currently install or launch GitHub Copilot CLI. Users who want Copilot must install it ad hoc inside an ephemeral Sandbox, losing the installation when the container exits and bypassing aishell's saved setup intent, shared Harness volume, configuration mounts, credential scoping, update command, and status reporting.

## Solution

Add GitHub Copilot CLI as a first-class Harness described by the existing closed Harness registry. `aishell setup --with-copilot[=VERSION]` installs the `@github/copilot` npm package into the shared Harness volume, `aishell copilot` launches the `copilot` executable, and host `~/.copilot` state is mounted into enabled Sandboxes. aishell forwards only Copilot-specific authentication and host-selection variables and disables Copilot self-updates so `aishell update` and version pins remain authoritative.

## User Stories

1. As an aishell user, I want to select GitHub Copilot CLI during setup, so that it is installed reproducibly with my other Harnesses.
2. As an aishell user, I want to pin a Copilot npm version, so that I can choose reproducibility or latest stable behavior.
3. As an aishell user, I want my Copilot selection and version retained by `--reuse-config`, so that later setup changes do not silently remove it.
4. As an aishell user, I want `aishell copilot` to launch Copilot in my project's Sandbox, so that it receives the same project isolation as other Harnesses.
5. As an aishell user, I want arguments after `aishell copilot` passed through unchanged, so that all Copilot CLI modes remain available.
6. As an aishell user, I want `harness_args.copilot` applied on both direct launches and the in-Sandbox alias, so that persistent defaults behave consistently.
7. As an aishell user, I want Copilot's own permission prompts preserved by default, so that enabling the Harness does not silently enable `--allow-all`.
8. As an aishell user, I want my Copilot login, settings, plugins, saved permissions, and sessions preserved across Sandboxes, so that ephemeral containers do not erase user-owned state.
9. As a token-authenticated user, I want `COPILOT_GITHUB_TOKEN` forwarded only when Copilot is enabled, so that the credential is not exposed to unrelated Harnesses.
10. As a GitHub Enterprise Cloud user, I want `COPILOT_GH_HOST` forwarded only when Copilot is enabled, so that Copilot connects to my selected data-residency hostname.
11. As a security-conscious user, I want general `GH_TOKEN`, `GITHUB_TOKEN`, and `GH_HOST` values excluded from automatic passthrough, so that broad GitHub credentials remain explicit opt-ins.
12. As an aishell user, I want Copilot self-updates disabled in the Sandbox, so that read-only Harness volumes, `aishell update`, and explicit version pins do not conflict.
13. As an aishell user, I want setup summaries, help, `info`, `check`, and update output to call the Harness “GitHub Copilot CLI,” so that its status is discoverable and consistently named.
14. As an existing aishell user who does not enable Copilot, I want my saved setup and Harness-volume hash to retain their current behavior, so that upgrading aishell does not trigger an unnecessary volume rebuild.
15. As an aishell maintainer, I want Copilot represented through the Harness descriptor rather than new scattered lists, so that the registry remains the single source of Harness facts.

## Implementation Decisions

- Add a `:copilot` descriptor to `aishell.harness`, ordered after Codex CLI, with canonical label `GitHub Copilot CLI`, subcommand and executable `copilot`, state key `:with-copilot`, version key `:copilot-version`, interactive and pre-start capabilities enabled, config defaults enabled, and Harness-volume participation enabled.
- Install through the existing npm path with package `@github/copilot`. Unpinned setup resolves to the npm tag `latest`; `--with-copilot=VALUE` is passed through as the npm version using the same validation and installation rules as the other npm-backed Harnesses. aishell's Node.js 24 foundation already exceeds Copilot's Node.js 22 minimum.
- Emit an always-present shell alias for enabled Copilot so direct `aishell copilot` launches and interactive Sandbox launches both use the registry's shared argv interpreter. Do not declare a skip-permissions flag or fixed `--allow-all` argument.
- Add host-relative directory `~/.copilot` to the descriptor's config paths. Reuse the existing path-creation and cross-platform mount pipeline: create the host directory when absent, mount it wholesale at the corresponding container home path, and share it across projects. Do not add a Copilot isolation mode.
- Do not mount Copilot's external cache locations and do not forward `COPILOT_HOME` or `COPILOT_CACHE_HOME`. The supported persistence boundary is the conventional `~/.copilot` directory.
- Declare `COPILOT_GITHUB_TOKEN` and `COPILOT_GH_HOST` as Copilot-specific host environment passthrough. Do not automatically pass `GH_TOKEN`, `GITHUB_TOKEN`, or `GH_HOST`; users may opt into those through the existing `env` configuration.
- Extend the pure-data Harness descriptor schema with a fixed runtime-environment capability, named consistently with the registry's existing fields. Copilot declares `COPILOT_AUTO_UPDATE=false`. Docker argument derivation emits this value whenever Copilot is enabled, after ordinary config environment entries so config cannot accidentally defeat aishell's update ownership; the existing low-level `docker_args` escape hatch remains unchanged.
- All setup behavior derives from the descriptor: flag parsing, optional version validation, empty and saved setup state, `--reuse-config`, summaries, enabled-Harness labels, Harness-volume configuration, and update population. No Copilot-specific setup branch is added.
- Existing saved states require no migration. Missing `:with-copilot` and `:copilot-version` values resolve to disabled and nil through the registry-derived empty state. A disabled Copilot descriptor must not alter existing Harness-volume hashes.
- All command and reporting behavior derives from the descriptor: pass-through dispatch, installed-command discoverability, typo candidates, launch validation, canonical labels, `check`, `info`, update output, and help flag ordering. Avoid adding literal Copilot membership lists outside tests that intentionally pin public output.
- Update user documentation in `README.md`, `docs/HARNESSES.md`, `docs/CONFIGURATION.md`, and `llm.txt`. Cover setup, version pinning, launch, `harness_args.copilot`, `~/.copilot` persistence, device-code OAuth, the two automatic environment variables, explicit opt-in for broader GitHub variables, and disabled self-updates. Add a user-visible entry to `CHANGELOG.md`.
- Keep the settled `CONTEXT.md` change defining GitHub Copilot CLI as a Harness. No ADR is required because the change follows the accepted Harness registry and shared-config architecture.

## Delivery Sequence

1. Extend registry contract tests to describe fixed runtime environment and add the complete Copilot descriptor.
2. Extend setup-state and Harness-volume tests, proving latest/pinned npm installation and hash compatibility for Copilot-disabled users.
3. Extend Docker argument tests for `~/.copilot`, scoped passthrough variables, and `COPILOT_AUTO_UPDATE=false`, including precedence relative to configured environment entries.
4. Extend launch, alias, CLI, check, info, config-validation, and help tests through their existing public pure-function seams; verify no implicit `--allow-all` argument.
5. Update user-facing documentation, generated/reference text, the domain dictionary, and changelog.
6. Run the complete test suite, then the required `clj-kondo --lint src test`. If Docker and a Copilot subscription are available, manually smoke-test setup, device-code login persistence, direct launch, in-Sandbox alias launch, pinned version reporting, and update behavior; automated acceptance must not depend on external authentication.

## Testing Decisions

- Use the registry interface as the primary seam: descriptor completeness, canonical identity and label, capability filters, config path, npm install metadata, scoped environment declarations, fixed runtime environment, and launch argv.
- Reuse setup resolution tests to cover plain setup, `--with-copilot`, pinned values, omitted/disabled state, and `--reuse-config` inheritance and override.
- Reuse Harness-volume pure functions to assert `@github/copilot@latest` and exact version command construction. Preserve the frozen pre-Copilot hash oracle for existing configurations, then add explicit Copilot-enabled normalization/hash coverage rather than rewriting history into that oracle.
- Reuse Docker argument construction tests with controlled host environment bindings to assert that `~/.copilot` is created and mounted only when enabled; only `COPILOT_GITHUB_TOKEN` and `COPILOT_GH_HOST` pass automatically; broad GitHub variables, `COPILOT_HOME`, and cache directories do not; and the fixed auto-update value wins over ordinary configured environment values.
- Reuse CLI and output seams to assert the setup flag, pass-through subcommand, installed-command visibility, registry order, canonical label, check/info/setup summaries, and help text.
- Reuse launch and alias argv tests to assert config defaults precede CLI arguments and neither path injects `--allow-all` or `--yolo`.
- Prefer table-driven coverage over Copilot-specific branching tests. Do not require Docker, GitHub network access, a real token, or a Copilot subscription in the automated suite.

## Out of Scope

- Installing Copilot with Homebrew, WinGet, GitHub release binaries, or GitHub's install script.
- Automatically forwarding `GH_TOKEN`, `GITHUB_TOKEN`, `GH_HOST`, BYOK provider credentials, telemetry variables, model variables, or arbitrary `COPILOT_*` values.
- Supporting a custom `COPILOT_HOME` or persisting `COPILOT_CACHE_HOME`.
- Adding per-project Copilot state isolation or splitting `~/.copilot` into configuration, session, credential, and machine-state overlays.
- Installing or authenticating GitHub CLI (`gh`) for Copilot's fallback authentication.
- Automatically enabling Copilot `--allow-all`, `--yolo`, experimental sandboxing, autopilot, plugins, hooks, MCP servers, or BYOK.
- Adding network-mode allowlists or changing the egress-proxy work recorded by ADR 0006.
- Changing the generic `env` or `docker_args` escape hatches.
- Implementing the feature in this planning ticket.

## Further Notes

- Official installation documentation: https://docs.github.com/en/copilot/how-tos/copilot-cli/set-up-copilot-cli/install-copilot-cli
- Official authentication documentation: https://docs.github.com/en/copilot/how-tos/copilot-cli/set-up-copilot-cli/authenticate-copilot-cli
- Official configuration-directory reference: https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-config-dir-reference
- Repository hard rules apply: run `clj-kondo --lint src test` before commit and include no AI attribution in commit messages or trailers.

## Notes

**2026-09-08T20:07:08.607879786Z**

Added GitHub Copilot CLI as a registry-driven Harness with npm version setup, persistent state, scoped credentials, fixed update policy, shared launch semantics, tests, and documentation.

**2026-09-08T21:20:57.010263514Z**

Descoped during code review on PR #2: npm distribution tags (latest, prerelease) are not accepted by --with-copilot. Copilot follows the same semver-only validation as the other npm Harnesses; prerelease versions such as 1.0.0-beta.1 still pin. Tag support can return as its own ticket if a user needs it. The ticket text above was edited in the same review round to match; this note is the record of that descoping.

---
id: aix-01m222qwn34n
title: Restore the host terminal when a docker session dies
status: closed
type: bug
priority: 1
mode: hitl
created: '2026-09-09T03:17:47.810938870Z'
updated: '2026-09-09T03:25:50.557542598Z'
closed: '2026-09-09T03:25:50.557542598Z'
tags:
- terminal
- attach
acceptance:
- title: wrap-with-restore produces the expected argv, pinned in terminal_test.clj including modes 1003, 1006 and the kitty pop
  done: true
- title: Killing the Owning session leaves an Attached session's terminal accepting input with no mouse-report garbage
  done: true
- title: docs/HERDR.md and docs/TROUBLESHOOTING.md updated
  done: true
---

## Description

When the Owning session exits, the Sandbox is removed (`docker run --rm`) and every Attached session dies with it. The harness inside is killed before it can undo the terminal modes it enabled, and those modes live in the host terminal emulator, so they survive the container.

Observed on `aishell attach -- claude` after `exit` in an `aishell --name main` pane:

- Mouse motion reports print as text at the host prompt (`35;17;1M` fragments — SGR mouse, DEC private modes 1003 + 1006 left on).
- After Ctrl-C, typed characters are silently swallowed. Most likely the kitty keyboard protocol left pushed (`CSI > 1 u`): keystrokes arrive as `ESC[97u` and the line editor discards them. Possibly compounded by the docker client failing to restore termios when its exec stream dies.

Nothing in aishell is positioned to clean up: `p/exec` at src/aishell/attach.clj:106 replaces the aishell process with the docker client, so no aishell code outlives it. src/aishell/run.clj:270 has the same hole, reachable by `docker kill` or a daemon restart.

## Design

Keep `p/exec`, but exec a `sh` wrapper instead of docker directly. The docker argv flows through `"$@"` intact, so nothing needs shell-quoting:

    sh -c saved=$(stty -g); "$@"; s=$?; printf "<escapes>"; stty "$saved" 2>/dev/null || stty sane; exit $s _ docker exec -it ...

Rejected: running docker as a child and cleaning up after the wait (leaves babashka as the pane foreground process — worse for herdr, which identifies a pane by its foreground process); documentation only.

New namespace `aishell.terminal` holding the escape payload and one pure `wrap-with-restore : argv -> argv`. Both `attach.clj` and `run.clj` wrap immediately before exec. Not in `attach/invocation.clj` — that namespace is the docker *exec* builder and `run.clj` has no business depending on it.

Payload — mouse tracking off (1000/1002/1003/1006/1015), bracketed paste off (2004), focus reporting off (1004), kitty keyboard flags popped (`ESC[<u`), modifyOtherKeys off (`ESC[>4;0m`), cursor shown (25h), SGR reset. Deliberately excludes `ESC[?1049l`: no harness in the registry uses the alternate screen, and a mistaken screen swap would corrupt the pane buffer herdr reads to classify state.

Always emit, on every exit. The sequences are no-ops when the modes are already off, and conditional cleanup would mean trusting an exit code to say whether a TUI finished its teardown.

Windows keeps its existing child-process branch and prints the escapes only — no `stty` equivalent, and the termios half is not the Windows failure mode.

### Manual verification

Reproduce without the fix, then discriminate blind: type `stty sane` + Enter. If typing recovers, termios was the cause. If not, type `printf \033[<u` + Enter. Record which one restores input.

## Notes

**2026-09-09T03:20:09.398072243Z**

Implementation landed: aishell.terminal with wrap-with-restore, wired into attach.clj and run.clj (Unix wrapper, Windows escapes-only). terminal_test.clj pins the argv shape and the payload. Lint clean, 218 tests pass.

The wrapper script was verified directly outside a tty: exit status propagates (checked with a command exiting 7), escapes reach stdout as real ESC bytes, and stty emits no stderr noise when there is no terminal.

Still unverified: the actual repro. No Docker access in the session that wrote this. Run the discriminator in the Design section before closing.

**2026-09-09T03:25:50.557542598Z**

Fixed by exec'ing a sh wrapper that saves the tty, runs docker, then replays the mode-reset escapes and restores the tty. Both attach and run paths wrapped; Windows replays the escapes only. Live repro verified by hand.

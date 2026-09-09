# Running aishell under herdr

[herdr](https://herdr.dev) is a terminal multiplexer with an agent layer: it reads each pane,
marks the harness running there as working, blocked or idle, and shows that in a sidebar. Run one
sandbox per pane and you can see at a glance which project is waiting on you.

herdr runs on the **host**, outside the sandbox. aishell needs no configuration for this.

> **A note on words.** What herdr calls an *agent*, aishell calls a **Harness** (see
> [CONTEXT.md](../CONTEXT.md)). This page uses herdr's word when talking about herdr's features.

## Invocation

Launch the harness through aishell as usual, with one environment variable in front:

```bash
HERDR_AGENT=claude aishell claude
```

`HERDR_AGENT` names the harness hiding behind the wrapper. herdr identifies a pane by its
foreground process; under aishell that process is `docker`, so without the hint herdr sees a
container runtime rather than Claude Code. The hint makes it apply the right screen manifest.

**Set it on the host command, not inside the sandbox.** herdr reads the variable from the pane's
own process. A value exported inside the container is invisible to it.

Everything else already works. aishell passes `TERM` and `COLORTERM` through to the container, so
colours, mouse reporting and the harness's own TUI behave the same as they do outside herdr. If you
need further variables in the sandbox, use the `env:` list in `.aishell/config.yaml` — see
[Configuration](CONFIGURATION.md).

The same pattern applies to every harness:

```bash
HERDR_AGENT=codex aishell codex
HERDR_AGENT=opencode aishell opencode
```

## Workflow

One pane per project. Each pane holds its own sandbox — separate container, separate name, no
shared state — and herdr's sidebar tells you which of them has stopped and needs an answer.

Because herdr's control surface lives on the host, host-side scripting works normally against
sandboxed panes: `herdr pane split`, `herdr agent start`, `herdr agent prompt` and `herdr agent
read` drive the pane's terminal, and the sandbox on the other side of it is none the wiser. You can
script a pane that runs `aishell claude`, prompt it, and read the result back.

### Detaching and container lifetime

aishell containers are ephemeral (`docker run --rm`): the container lives exactly as long as the
harness process.

- **Detaching a herdr session leaves the pane running**, so the sandbox stays up and the harness
  keeps working. Reattach later and it is where you left it.
- **Closing the pane, or killing the herdr session, ends the harness process** and the container
  goes with it. Nothing is left behind to clean up, and nothing is preserved either.

herdr's own session restore does not recover a sandbox: the container it referred to is gone.

### Several sessions in one sandbox

One pane per project is the simple case. For several Claude Code sessions on the *same* project,
start the sandbox in one pane and attach the rest to it:

```bash
# Pane 1 — owns the container
HERDR_AGENT=claude aishell claude

# Panes 2, 3, … — extra sessions in that same container
HERDR_AGENT=claude aishell attach -- claude
```

The hint works the same way here: the pane's foreground process is `docker exec` rather than
`docker run`, still a wrapper, so herdr still needs telling what is behind it.

Sharing one sandbox is the right default for concurrent sessions — one PID namespace and one
`/tmp` are what Claude Code's own Agent View needs to see them all. The reasoning, and when to
reach for a separate `--name` container instead, is in
[Harnesses](HARNESSES.md#several-claude-sessions-one-sandbox).

Two consequences that matter in a multiplexer specifically:

- **Pane 1 is load-bearing.** It holds the container; the other panes hold Attached sessions. Close
  it and every other Claude in that sandbox dies at once. herdr will not warn you — the panes simply
  go, back to a usable shell prompt: aishell restores the terminal on the way out, so a pane whose
  harness was killed mid-run is not left echoing mouse reports or swallowing your typing.
  Starting the container with a plain shell (`aishell shell`) makes the owning pane obviously
  infrastructure rather than a session you might tidy away.
- **Attached panes do not self-close.** `-- claude` runs Claude and then execs a login shell. The
  pane survives, still carrying `HERDR_AGENT=claude`, so herdr goes on matching Claude manifests
  against a bash prompt and reads the pane as idle or unknown rather than done.

## What this setup deliberately does not do

### The herdr control socket is not mounted into sandboxes

herdr's socket API can split panes and send keystrokes **on the host**. Exposing it to a sandbox
would give the harness inside arbitrary command execution on the host machine, which is the exact
boundary aishell exists to draw. aishell does not mount it, and there is no configuration key to.

The cost is limited and worth naming:

- **State detection is heuristic.** herdr classifies the harness by reading the pane's visible
  buffer. Installing a lifecycle-hook integration (`herdr integration install claude`) would make
  the harness report its own state authoritatively — but the hook runs inside the container and
  could not reach the host's socket.
- **No session identity.** herdr cannot tie a pane to the harness's session id. Largely moot here:
  the container is ephemeral, so there is no session on the host to restore.
- **No inside-out spawning.** The harness inside a sandbox cannot split a pane or start a sibling
  harness. Driving panes from the host works fine; initiating from within does not.

If pane-buffer detection turns out to misread states in practice, the fix is *not* to mount the
socket. A hook inside the container can print a distinctive marker, and a custom herdr screen
manifest on the host can match it — state reported over the terminal, the one channel that already
legitimately crosses the boundary. Nobody has needed this yet.

### `herdr machine add` does not reach a sandbox

herdr connects to other machines over SSH and requires herdr installed on the far end. aishell
sandboxes run no SSH daemon and carry no herdr binary. Use the pane, not a machine profile.

### herdr does not run inside the sandbox

Putting a multiplexer with a background server inside a container that disappears on exit gains
nothing: no cross-project view, and its server dies with the session. herdr belongs on the host.

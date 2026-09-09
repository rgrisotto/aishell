(ns aishell.terminal
  "Restoring the host terminal after a docker session ends.

   A harness killed mid-run never undoes the terminal modes it switched on,
   and those modes live in the host terminal emulator rather than in the
   Sandbox, so they outlive the container. That is the normal case, not an
   edge case: an Attached session dies whenever its Owning session exits."
  (:require [clojure.string :as str]))

(def restore-sequence
  "Escapes that undo every terminal mode a killed harness can leave behind.

   Each is a no-op when the mode is already off, so this is safe to emit on
   every exit rather than only on a crash. Deliberately absent: `?1049l`
   (leave alternate screen) — no harness in the registry uses it, and a
   mistaken screen swap would corrupt the pane buffer herdr reads to
   classify a pane's state."
  (str "\033[?1000l"   ; mouse: normal tracking
       "\033[?1002l"   ; mouse: button-event tracking
       "\033[?1003l"   ; mouse: any-event tracking
       "\033[?1006l"   ; mouse: SGR encoding
       "\033[?1015l"   ; mouse: urxvt encoding
       "\033[?1004l"   ; focus reporting
       "\033[?2004l"   ; bracketed paste
       "\033[<u"       ; kitty keyboard protocol: pop flags
       "\033[>4;0m"    ; xterm modifyOtherKeys
       "\033[?25h"     ; cursor visible
       "\033[0m"))     ; character attributes

(def ^:private restore-script
  "Save the tty, run the argv, restore whatever it left behind.

   The command reaches this shell as \"$@\", so the caller's argv passes
   through with no quoting of its own. `stty -g` captures the real settings
   rather than resetting to `sane`, which would clobber a customization the
   surviving shell in that terminal still wants.

   The escapes go in as the literal text \\033, left for printf to interpret,
   so the argv stays printable ASCII and legible in `ps`."
  (str "saved=$(stty -g 2>/dev/null); "
       "\"$@\"; status=$?; "
       "printf '" (str/replace restore-sequence "\033" "\\033") "'; "
       "if [ -n \"$saved\" ]; then stty \"$saved\" 2>/dev/null || stty sane 2>/dev/null; fi; "
       "exit $status"))

(defn wrap-with-restore
  "Wrap `argv` so the terminal is restored once it exits, however it exits.

   Wrapping rather than waiting on a child keeps the process replaceable by
   `p/exec`: the pane's foreground process stays a shell holding the docker
   client, which is what herdr reads `HERDR_AGENT` from. Waiting would leave
   babashka itself as the foreground process — one more wrapper for herdr to
   see through."
  [argv]
  (into ["sh" "-c" restore-script "_"] argv))

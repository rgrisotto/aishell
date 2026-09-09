(ns aishell.terminal-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [aishell.terminal :as terminal]))

(deftest restore-sequence-covers-the-observed-leaks
  (testing "mouse tracking modes, the pair seen printing at a host prompt"
    (is (str/includes? terminal/restore-sequence "\033[?1003l"))
    (is (str/includes? terminal/restore-sequence "\033[?1006l")))
  (testing "kitty keyboard flags, which swallow typing when left pushed"
    (is (str/includes? terminal/restore-sequence "\033[<u")))
  (testing "the alternate screen is left alone on purpose"
    (is (not (str/includes? terminal/restore-sequence "1049")))))

(deftest wrap-with-restore-keeps-the-argv-intact
  (testing "the command follows the sentinel, unquoted and unmodified"
    (is (= ["sh" "-c" :script "_" "docker" "exec" "-it" "c" "/bin/bash" "--login"]
           (-> (terminal/wrap-with-restore ["docker" "exec" "-it" "c" "/bin/bash" "--login"])
               (assoc 2 :script))))))

(deftest wrap-with-restore-script-shape
  (let [script (nth (terminal/wrap-with-restore ["docker"]) 2)]
    (testing "the argv reaches the shell as \"$@\", so nothing needs escaping"
      (is (str/includes? script "\"$@\"")))
    (testing "the tty is saved and restored, not reset to defaults"
      (is (str/includes? script "saved=$(stty -g"))
      (is (str/includes? script "stty \"$saved\"")))
    (testing "the wrapped command's exit status is propagated"
      (is (str/includes? script "exit $status")))
    (testing "escapes stay printable ASCII, for printf to interpret"
      (is (str/includes? script "\\033[?1003l"))
      (is (not (str/includes? script "\033"))))))

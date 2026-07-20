#!/bin/sh
unavailable='CodeDefense hook launcher is unavailable.'
script_dir=${0%/*}
[ "$script_dir" = "$0" ] && script_dir=.
plugin_root=$(CDPATH= cd -- "$script_dir/.." 2>/dev/null && pwd) || {
  printf '%s\n' "$unavailable" >&2
  exit 1
}
jar=$plugin_root/cli/codedefense.jar
[ -f "$jar" ] || {
  printf '%s\n' "$unavailable" >&2
  exit 1
}

java_command=
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  java_command=$JAVA_HOME/bin/java
elif command -v java >/dev/null 2>&1; then
  java_command=java
else
  printf '%s\n' "$unavailable" >&2
  exit 1
fi

"$java_command" -jar "$jar" codex-hook status
exit $?

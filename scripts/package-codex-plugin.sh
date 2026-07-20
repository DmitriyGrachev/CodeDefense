#!/bin/sh
set -eu

script_dir=${0%/*}
[ "$script_dir" = "$0" ] && script_dir=.
repository_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
source_jar=$repository_root/target/codedefense.jar
if [ ! -f "$source_jar" ]; then
  printf '%s\n' 'Build target/codedefense.jar before packaging the Codex plugin.' >&2
  exit 1
fi

plugin_root=$repository_root/plugins/codedefense
plugin_jar=$plugin_root/cli/codedefense.jar
stage_root=$repository_root/target/codex-plugin-package
stage_plugin=$stage_root/codedefense
archive=$repository_root/target/codedefense-codex-plugin.zip

cp "$source_jar" "$plugin_jar"
rm -rf "$stage_root"
mkdir -p "$stage_root"
cp -R "$plugin_root" "$stage_plugin"
rm -f "$archive"

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/jar" ]; then
  jar_command=$JAVA_HOME/bin/jar
elif command -v jar >/dev/null 2>&1; then
  jar_command=jar
else
  printf '%s\n' 'Java jar tool is unavailable.' >&2
  exit 1
fi

"$jar_command" --create --file "$archive" -C "$stage_root" codedefense
printf '%s\n' "$archive"

#!/usr/bin/env bash
set -euo pipefail

command -v codex

codex --version
codex login status

mvn \
  -Dcodedefense.live.codex=true \
  -Dtest=CodexLiveSmokeTest \
  test

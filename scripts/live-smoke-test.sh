#!/usr/bin/env bash
set -euo pipefail

codex --version
codex login status

mvn \
  -Dcodedefense.live.codex=true \
  -Dtest=CodexLiveSmokeTest \
  test

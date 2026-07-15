$ErrorActionPreference = "Stop"

codex --version
codex login status

mvn `
  "-Dcodedefense.live.codex=true" `
  "-Dtest=CodexLiveSmokeTest" `
  test

$ErrorActionPreference = "Stop"

Get-Command codex -All |
    Select-Object CommandType, Source, Path
where.exe codex

codex --version
codex login status

mvn `
  "-Dcodedefense.live.codex=true" `
  "-Dtest=CodexLiveSmokeTest" `
  test

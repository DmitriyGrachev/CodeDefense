$ErrorActionPreference = "Stop"

Get-Command codex -All |
    Select-Object CommandType, Source, Path
where.exe codex

codex --version

# Codex reports its successful ChatGPT login message on stderr.  Under
# $ErrorActionPreference = "Stop", Windows PowerShell otherwise promotes that
# native stderr output to NativeCommandError despite the zero exit code.
$previousErrorActionPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = "Continue"
    codex login status
    $loginExitCode = $LASTEXITCODE
}
finally {
    $ErrorActionPreference = $previousErrorActionPreference
}

if ($loginExitCode -ne 0) {
    throw "codex login status failed with exit code $loginExitCode."
}

mvn `
  "-Dcodedefense.live.codex=true" `
  "-Dtest=CodexLiveSmokeTest" `
  test

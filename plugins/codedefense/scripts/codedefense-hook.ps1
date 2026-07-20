$ErrorActionPreference = "Stop"
$Unavailable = "CodeDefense hook launcher is unavailable."

try {
    $PluginRoot = Split-Path -Parent $PSScriptRoot
    $Jar = Join-Path $PluginRoot "cli\codedefense.jar"
    if (-not (Test-Path -LiteralPath $Jar -PathType Leaf)) {
        [Console]::Error.WriteLine($Unavailable)
        exit 1
    }

    $Java = $null
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $Candidate = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path -LiteralPath $Candidate -PathType Leaf) {
            $Java = $Candidate
        }
    }
    if ($null -eq $Java) {
        $Command = Get-Command java.exe -CommandType Application -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($null -ne $Command) {
            $Java = $Command.Source
        }
    }
    if ($null -eq $Java) {
        [Console]::Error.WriteLine($Unavailable)
        exit 1
    }

    & $Java -jar $Jar codex-hook status
    exit $LASTEXITCODE
} catch {
    [Console]::Error.WriteLine($Unavailable)
    exit 1
}

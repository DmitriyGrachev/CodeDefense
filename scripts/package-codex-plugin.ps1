$ErrorActionPreference = "Stop"

$RepositoryRoot = Split-Path -Parent $PSScriptRoot
$SourceJar = Join-Path $RepositoryRoot "target\codedefense.jar"
if (-not (Test-Path -LiteralPath $SourceJar -PathType Leaf)) {
    [Console]::Error.WriteLine("Build target/codedefense.jar before packaging the Codex plugin.")
    exit 1
}

$PluginRoot = Join-Path $RepositoryRoot "plugins\codedefense"
$PluginJar = Join-Path $PluginRoot "cli\codedefense.jar"
$StageRoot = Join-Path $RepositoryRoot "target\codex-plugin-package"
$StagePlugin = Join-Path $StageRoot "codedefense"
$Archive = Join-Path $RepositoryRoot "target\codedefense-codex-plugin.zip"

$RootPrefix = [IO.Path]::GetFullPath($RepositoryRoot).TrimEnd('\') + '\'
$ResolvedStage = [IO.Path]::GetFullPath($StageRoot)
if (-not $ResolvedStage.StartsWith($RootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Unsafe plugin staging path."
}

Copy-Item -LiteralPath $SourceJar -Destination $PluginJar -Force
if (Test-Path -LiteralPath $StageRoot) {
    Remove-Item -LiteralPath $StageRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $StageRoot -Force | Out-Null
Copy-Item -LiteralPath $PluginRoot -Destination $StagePlugin -Recurse
if (Test-Path -LiteralPath $Archive) {
    Remove-Item -LiteralPath $Archive -Force
}
Compress-Archive -LiteralPath $StagePlugin -DestinationPath $Archive
Write-Output $Archive

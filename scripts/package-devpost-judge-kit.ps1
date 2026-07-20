$ErrorActionPreference = "Stop"

$RepositoryRoot = Split-Path -Parent $PSScriptRoot
$TargetRoot = Join-Path $RepositoryRoot "target"
$StageRoot = Join-Path $TargetRoot "devpost-judge-kit"
$KitRoot = Join-Path $StageRoot "CodeDefense-Judge-Kit"
$ScreenshotsRoot = Join-Path $KitRoot "screenshots"
$Archive = Join-Path $TargetRoot "codedefense-devpost-judge-kit-v0.1.0.zip"
$MaximumArchiveBytes = 35MB

$RequiredFiles = [ordered]@{
    "codedefense.jar" = Join-Path $TargetRoot "codedefense.jar"
    "codedefense-codex-plugin.zip" = Join-Path $TargetRoot "codedefense-codex-plugin.zip"
    "codedefense-jetbrains-0.1.0.zip" = Join-Path $RepositoryRoot "jetbrains-plugin\build\distributions\codedefense-jetbrains-0.1.0.zip"
    "README-JUDGES.md" = Join-Path $RepositoryRoot "docs\devpost\README-JUDGES.md"
}

$GalleryRoot = Join-Path $RepositoryRoot "docs\assets\devpost"
$GalleryFiles = foreach ($Number in 1..6) {
    $Prefix = "{0:D2}-" -f $Number
    $Matches = @(Get-ChildItem -LiteralPath $GalleryRoot -File -Filter "$Prefix*.png")
    if ($Matches.Count -ne 1) {
        throw "Expected exactly one gallery image with prefix $Prefix"
    }
    $Matches[0]
}

foreach ($Entry in $RequiredFiles.GetEnumerator()) {
    if (-not (Test-Path -LiteralPath $Entry.Value -PathType Leaf)) {
        throw "Missing judge-kit input: $($Entry.Value)"
    }
}

$ResolvedTarget = [IO.Path]::GetFullPath($TargetRoot).TrimEnd('\') + '\'
$ResolvedStage = [IO.Path]::GetFullPath($StageRoot)
if (-not $ResolvedStage.StartsWith($ResolvedTarget, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Unsafe judge-kit staging path."
}

if (Test-Path -LiteralPath $StageRoot) {
    Remove-Item -LiteralPath $StageRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $ScreenshotsRoot -Force | Out-Null

foreach ($Entry in $RequiredFiles.GetEnumerator()) {
    Copy-Item -LiteralPath $Entry.Value -Destination (Join-Path $KitRoot $Entry.Key)
}
foreach ($File in $GalleryFiles) {
    Copy-Item -LiteralPath $File.FullName -Destination (Join-Path $ScreenshotsRoot $File.Name)
}

$ChecksumNames = @(
    "codedefense.jar"
    "codedefense-codex-plugin.zip"
    "codedefense-jetbrains-0.1.0.zip"
)
$ChecksumLines = foreach ($Name in $ChecksumNames) {
    $Path = Join-Path $KitRoot $Name
    $Hash = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    "$Hash  $Name"
}
$ChecksumText = ($ChecksumLines -join "`n") + "`n"
$Utf8WithoutBom = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText((Join-Path $KitRoot "SHA256SUMS.txt"), $ChecksumText, $Utf8WithoutBom)

if (Test-Path -LiteralPath $Archive) {
    Remove-Item -LiteralPath $Archive -Force
}
Compress-Archive -LiteralPath $KitRoot -DestinationPath $Archive

$ArchiveFile = Get-Item -LiteralPath $Archive
if ($ArchiveFile.Length -ge $MaximumArchiveBytes) {
    throw "Judge kit exceeds the 35 MB Devpost limit: $($ArchiveFile.Length) bytes."
}

Write-Output $ArchiveFile.FullName
Write-Output "Bytes: $($ArchiveFile.Length)"

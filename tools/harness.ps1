<#
.SYNOPSIS
  Boots a visible dev client, auto-runs the actor self-test in front of you, and verdicts it.

.DESCRIPTION
  The hands-off test harness: launches the game window (visible, so you watch
  the same ten-second cast a manual run would show), opens a replay, starts
  /actor selftest, waits for the verdict file the mod writes, prints the
  verdict plus the report tail, and closes the game -- unless -KeepOpen,
  which leaves it up for you to poke at.

  Arming is per-boot via environment (see Harness.java): nothing persists,
  and without this script the mod never self-drives.

  Usage (from the repo root):
    powershell -ExecutionPolicy Bypass -File tools/harness.ps1
    powershell -ExecutionPolicy Bypass -File tools/harness.ps1 -Version 1.21.11 -KeepOpen
    powershell -ExecutionPolicy Bypass -File tools/harness.ps1 -Replay path\to\replay.zip
#>
param(
    [string]$Version = '26.3',       # version leaf under versions/
    [string]$Replay = '',            # replay .zip to open; '' = newest in that version's replays dir
    [string]$ConvertInput = '',      # optional .mcpr fixture to convert with this build before playback
    [switch]$VisualFocus,
    [string]$ArtifactDirectory = '',
    [int]$TimeoutSec = 600,          # verdict budget (boot alone is ~1 min)
    [switch]$KeepOpen                # leave the game running after the verdict
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
$ProjDir = Join-Path $Root "versions/$Version"
if (-not (Test-Path $ProjDir)) { throw "No such version dir: $ProjDir" }
$TaskPath = ":versions:$Version"
$Artifacts = Join-Path $Root "build/release-qa/$Version"
if ($ConvertInput) { $Artifacts = Join-Path $Root "build/release-qa/$Version-conversion" }
if ($VisualFocus) { $Artifacts = Join-Path $Root "build/release-qa/$Version-focus" }
if ($ArtifactDirectory) { $Artifacts = [IO.Path]::GetFullPath($ArtifactDirectory) }
New-Item -ItemType Directory -Force -Path $Artifacts | Out-Null
$VerdictFile = Join-Path $Artifacts 'verdict.txt' 
if (Test-Path $VerdictFile) { Remove-Item $VerdictFile -Force }

if ($Replay -eq '') {
    $replaysDir = Join-Path $ProjDir 'run/flashback/replays'
    $Replay = Get-ChildItem $replaysDir -Filter *.zip -File -ErrorAction SilentlyContinue |
        Sort-Object @{ Expression = { $_.Name -match '\(converted\)' }; Ascending = $true }, @{ Expression = 'LastWriteTime'; Descending = $true } |
        Select-Object -First 1 -ExpandProperty FullName
    if (-not $Replay) { throw "No replays in $replaysDir -- pass -Replay explicitly." }
}
if (-not (Test-Path $Replay)) { throw "No replay at $Replay" }
if ($ConvertInput) { $ConvertInput = (Resolve-Path -LiteralPath $ConvertInput).Path }
# Give the fixture its own replay ID, so tests cannot overwrite a user's sidecars.
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$Fixture = Join-Path $Artifacts 'fixture.zip'
Copy-Item -LiteralPath $Replay -Destination $Fixture -Force
$archive = [IO.Compression.ZipFile]::Open($Fixture, [IO.Compression.ZipArchiveMode]::Update)
try {
    $entry = $archive.GetEntry('metadata.json')
    if (-not $entry) { throw 'Replay fixture has no metadata.json' }
    $reader = New-Object IO.StreamReader($entry.Open())
    try { $metadata = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
    $metadata.uuid = [Guid]::NewGuid().ToString()
    $entry.Delete()
    $entry = $archive.CreateEntry('metadata.json')
    $writer = New-Object IO.StreamWriter($entry.Open())
    try { $writer.Write(($metadata | ConvertTo-Json -Depth 100)) } finally { $writer.Dispose() }
} finally { $archive.Dispose() }
$Replay = $Fixture
Write-Host "replay : $Replay"

# Clean leftovers of THIS version only, then launch visible.
$needle = 'versions\' + $Version + '\'
Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -match '^javaw?\.exe$' -and $_.CommandLine -like "*$needle*" -and $_.CommandLine -like "*net.fabricmc.devlaunchinjector.Main*" } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

$env:FBACTORS_HARNESS_REPLAY = $Replay
$env:FBACTORS_HARNESS_REPORT = $VerdictFile
$env:FBACTORS_RELEASE_QA_DIR = $Artifacts
$env:FBACTORS_HARNESS_CONVERT = $ConvertInput
$env:FBACTORS_VISUAL_FOCUS = if($VisualFocus) { '1' } else { $null }
$Started = Get-Date
try {
    $proc = Start-Process -FilePath (Join-Path $Root 'gradlew.bat') `
        -ArgumentList @("$TaskPath`:runClient", '--console=plain') `
        -WorkingDirectory $Root -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $Artifacts "gradle.log") `
        -RedirectStandardError (Join-Path $Artifacts "gradle-error.log")
    Write-Host "client : booting $Version (launcher pid $($proc.Id)) -- watch the window."

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    $verdict = $null
    while ((Get-Date) -lt $deadline) {
        if (Test-Path $VerdictFile) {
            $verdict = (Get-Content $VerdictFile -TotalCount 1) -replace '^VERDICT=', ''
            break
        }
        if ($proc.HasExited) { throw "Client launcher exited ($($proc.ExitCode)); see $Artifacts/gradle.log" }
        Start-Sleep -Milliseconds 250
    }
    if (-not $verdict) { throw "Timed out after $TimeoutSec s with no verdict file." }

    Write-Host ''
    Get-Content $VerdictFile
    Write-Host ''
    Write-Host '--- selftest-report.txt (tail) ---'
    $report = Join-Path $ProjDir 'run/flashback/fbactors/selftest-report.txt'
    if ((Test-Path $report) -and (Get-Item -LiteralPath $report).LastWriteTime -ge $Started) {
        Get-Content $report -Tail 25
    } else {
        Write-Host '(no report file yet)'
    }

    if ($verdict -eq 'PASS') { $code = 0 } elseif ($verdict -eq 'FAIL') { $code = 1 } else { $code = 2 }
} finally {
    foreach ($source in @((Join-Path $ProjDir 'run/logs/latest.log'), (Join-Path $ProjDir 'run/flashback/fbactors/selftest-report.txt'))) {
        if ((Test-Path -LiteralPath $source) -and (Get-Item -LiteralPath $source).LastWriteTime -ge $Started) {
            Copy-Item -LiteralPath $source -Destination (Join-Path $Artifacts (Split-Path $source -Leaf)) -Force
        }
    }
    $env:FBACTORS_HARNESS_REPLAY = $null
    $env:FBACTORS_HARNESS_REPORT = $null
    $env:FBACTORS_RELEASE_QA_DIR = $null
    $env:FBACTORS_HARNESS_CONVERT = $null
    $env:FBACTORS_VISUAL_FOCUS = $null
    if (-not $KeepOpen) {
        Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match '^javaw?\.exe$' -and $_.CommandLine -like "*$needle*" -and $_.CommandLine -like "*net.fabricmc.devlaunchinjector.Main*" } |
            ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
        try { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue } catch { }
        Write-Host 'client closed.'
    } else {
        Write-Host 'client left open (-KeepOpen). Close the game when done.'
    }
}
exit $code

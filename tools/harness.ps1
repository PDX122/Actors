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
    [int]$TimeoutSec = 600,          # verdict budget (boot alone is ~1 min)
    [switch]$KeepOpen                # leave the game running after the verdict
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
$ProjDir = Join-Path $Root "versions/$Version"
if (-not (Test-Path $ProjDir)) { throw "No such version dir: $ProjDir" }
$TaskPath = ":versions:$Version"
$VerdictFile = Join-Path $Root 'build/harness-verdict.txt'
if (Test-Path $VerdictFile) { Remove-Item $VerdictFile -Force }

if ($Replay -eq '') {
    $replaysDir = Join-Path $ProjDir 'run/flashback/replays'
    $Replay = Get-ChildItem $replaysDir -Filter *.zip -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
    if (-not $Replay) { throw "No replays in $replaysDir -- pass -Replay explicitly." }
}
if (-not (Test-Path $Replay)) { throw "No replay at $Replay" }
Write-Host "replay : $Replay"

# Clean leftovers of THIS version only, then launch visible.
$needle = 'versions\' + $Version
Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -match '^javaw?\.exe$' -and $_.CommandLine -like "*$needle*" } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

$env:FBACTORS_HARNESS_REPLAY = $Replay
$env:FBACTORS_HARNESS_REPORT = $VerdictFile
try {
    $proc = Start-Process -FilePath (Join-Path $Root 'gradlew.bat') `
        -ArgumentList @("$TaskPath`:runClient", '--console=plain') `
        -WorkingDirectory $Root -PassThru
    Write-Host "client : booting $Version (launcher pid $($proc.Id)) -- watch the window."

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    $verdict = $null
    while ((Get-Date) -lt $deadline) {
        if (Test-Path $VerdictFile) {
            $verdict = (Get-Content $VerdictFile -TotalCount 1) -replace '^VERDICT=', ''
            break
        }
        Start-Sleep -Seconds 5
    }
    if (-not $verdict) { throw "Timed out after $TimeoutSec s with no verdict file." }

    Write-Host ''
    Get-Content $VerdictFile
    Write-Host ''
    Write-Host '--- selftest-report.txt (tail) ---'
    $report = Join-Path $ProjDir 'run/flashback/fbactors/selftest-report.txt'
    if (Test-Path $report) {
        Get-Content $report -Tail 25
    } else {
        Write-Host '(no report file yet)'
    }

    if ($verdict -eq 'PASS') { $code = 0 } elseif ($verdict -eq 'FAIL') { $code = 1 } else { $code = 2 }
} finally {
    $env:FBACTORS_HARNESS_REPLAY = $null
    $env:FBACTORS_HARNESS_REPORT = $null
    if (-not $KeepOpen) {
        Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match '^javaw?\.exe$' -and $_.CommandLine -like "*$needle*" } |
            ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
        try { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue } catch { }
        Write-Host 'client closed.'
    } else {
        Write-Host 'client left open (-KeepOpen). Close the game when done.'
    }
}
exit $code

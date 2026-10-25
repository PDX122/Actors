<#
.SYNOPSIS
  Boots one version's dev client with Flashback loaded and verdicts the run.

.DESCRIPTION
  Launches ./gradlew :versions/<mc>:runClient, watches the game log for
  failure markers (mixin descriptor errors, crashes) or success milestones
  (resource reload and beyond -- all past the point where the reported
  InvalidInjectionException died), then kills the client. Prints a short
  human-readable verdict and optionally writes a JSON result for
  tools/smoke_all.ps1 to collect.

  Reusable standalone:
    powershell -ExecutionPolicy Bypass -File tools/smoke_client.ps1 versions/1.21.11

  Exit codes: 0 = PASS, 1 = FAIL, 2 = TIMEOUT/INCONCLUSIVE.
#>
param(
    [Parameter(Mandatory = $true)][string]$Project,   # e.g. versions/1.21.11
    [int]$TimeoutSec = 420,
    [string]$ResultFile = '',                          # optional JSON summary path
    [string]$WorldName = '',                           # when set, quickPlay into this SP world (exercises server mixins)
    [switch]$NoClean                                   # skip killing leftover clients first
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
$ProjDir = Join-Path $Root $Project
$Leaf = Split-Path $Project -Leaf
$TaskPath = ':' + ($Project -replace '[\\/]', ':')
$Clock = [Diagnostics.Stopwatch]::StartNew()

$Candidates = @(
    (Join-Path $ProjDir 'run/logs/latest.log'),
    (Join-Path $ProjDir 'logs/latest.log')
)

# Absent from any healthy boot; any hit fails the run.
$FailMarkers = @(
    'Mixin apply for mod fbactors failed',
    'InvalidInjectionException',
    'Mixin transformation of',
    'Game crashed!'
)

# The reported crash died inside Minecraft.<init> before resource reload, so
# none of these appear in a crashing log. First hit passes a boot-only run; a
# world run keeps going until a world milestone lands (those load the
# integrated server classes, so the server-side mixins have applied too).
$PassMarkers = @(
    'Reloading ResourceManager',
    'Narrator library',
    'OpenAL backend',
    'Sound engine started',
    'Created: 1024x1024'
)

# World-join milestones: the integrated server is up and the player is in.
# Reaching any of these means every server-side mixin has applied.
$WorldMarkers = @(
    'logged in with entity id'
)

function Stop-Leftovers {
    # A previous aborted run may still own the window. Only touch java
    # processes whose command line points at THIS version's tree.
    $needle = 'versions\' + $Leaf
    Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^javaw?\.exe$' -and $_.CommandLine -like "*$needle*" } |
        ForEach-Object {
            Write-Host "  cleaning leftover pid $($_.ProcessId)"
            Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        }
}

function Read-NewLog {
    foreach ($log in $Candidates) {
        if (-not (Test-Path $log)) { continue }
        if ((Get-Item $log).LastWriteTime -lt $script:BootTime) { continue }
        try { return @{ Path = $log; Text = [IO.File]::ReadAllText($log) } }
        catch { continue }
    }
    return $null
}

function Get-Props {
    $p = @{}
    $f = Join-Path $ProjDir 'gradle.properties'
    if (Test-Path $f) {
        Get-Content $f | ForEach-Object {
            if ($_ -match '^\s*([^#=]+?)\s*=\s*(.*?)\s*$') { $p[$Matches[1]] = $Matches[2] }
        }
    }
    return $p
}

Write-Host ""
Write-Host "== smoke: $Project (timeout ${TimeoutSec}s) ==" -ForegroundColor Cyan
$script:BootTime = Get-Date
if (-not $NoClean) { Stop-Leftovers }

$GradleLog = Join-Path $ProjDir 'smoke-gradle.log'
$gradleArgs = @("$TaskPath`:runClient", '--console=plain')
if ($WorldName -ne '') { $gradleArgs += "-PsmokeWorld=$WorldName" }
$proc = Start-Process -FilePath (Join-Path $Root 'gradlew.bat') `
    -ArgumentList $gradleArgs `
    -WorkingDirectory $Root -PassThru -WindowStyle Hidden `
    -RedirectStandardOutput $GradleLog -RedirectStandardError "$GradleLog.err"
Write-Host "  launched runClient (pid $($proc.Id)); game window opens, closes itself on verdict."
if ($WorldName -ne '') { Write-Host "  quickPlay into '$WorldName': PASS needs a world milestone." }

function Test-Markers($text, $markers, $suffix) {
    foreach ($m in $markers) {
        if ($text.Contains($m)) { return "reached '$m'$suffix" }
    }
    return $null
}

$Result = 'TIMEOUT'
$Detail = 'no verdict markers before timeout'
$bootSeen = $false
$Deadline = (Get-Date).AddSeconds($TimeoutSec)
while ((Get-Date) -lt $Deadline) {
    Start-Sleep -Seconds 5
    $entry = Read-NewLog
    if ($null -ne $entry) {
        $hit = Test-Markers $entry.Text $FailMarkers ''
        if ($null -ne $hit) { $Result = 'FAIL'; $Detail = $hit; break }
        if ($WorldName -ne '') {
            $hit = Test-Markers $entry.Text $WorldMarkers ''
            if ($null -ne $hit) { $Result = 'PASS'; $Detail = $hit; break }
            if (-not $bootSeen) {
                $hit = Test-Markers $entry.Text $PassMarkers ''
                if ($null -ne $hit) {
                    $bootSeen = $true
                    Write-Host "  boot milestone $hit; waiting for world join..."
                }
            }
        } else {
            $hit = Test-Markers $entry.Text $PassMarkers ''
            if ($null -ne $hit) { $Result = 'PASS'; $Detail = $hit; break }
        }
        if ($Result -ne 'TIMEOUT') { break }
    }
    if ($proc.HasExited) {
        $Result = 'FAIL'
        $Detail = "client exited (code $($proc.ExitCode)) before any milestone; see smoke-gradle.log"
        break
    }
}

if (-not $proc.HasExited) {
    try {
        & taskkill /PID $proc.Id /T /F 2>$null | Out-Null
    } catch {
    }
    try {
        $proc.WaitForExit(20000) | Out-Null
    } catch {
    }
    # A marker may have landed while killing; one last scan.
    if ($Result -eq 'TIMEOUT') {
        $entry = Read-NewLog
        if ($null -ne $entry) {
            $hit = Test-Markers $entry.Text $FailMarkers ' (at kill)'
            if ($null -ne $hit) { $Result = 'FAIL'; $Detail = $hit }
            elseif ($WorldName -ne '') {
                $hit = Test-Markers $entry.Text $WorldMarkers ' (at kill)'
                if ($null -ne $hit) { $Result = 'PASS'; $Detail = $hit }
                elseif ($bootSeen) { $Detail = 'booted, but world never joined before timeout' }
            } else {
                $hit = Test-Markers $entry.Text $PassMarkers ' (at kill)'
                if ($null -ne $hit) { $Result = 'PASS'; $Detail = $hit }
            }
        }
    }
}
$Clock.Stop()

$entry = Read-NewLog
$LogPath = ''
$Tail = @()
if ($null -ne $entry) {
    $LogPath = $entry.Path
    $Tail = ($entry.Text -split "`r?`n" | Where-Object { $_ -ne '' } | Select-Object -Last 8)
}

$props = Get-Props
$modsDir = Join-Path $ProjDir 'run/mods'
$fbJar = ''
if (Test-Path $modsDir) {
    $hit = Get-ChildItem $modsDir -Filter 'Flashback-*.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -ne $hit) { $fbJar = $hit.Name }
}

$summary = [ordered]@{
    project      = $Project
    mcVersion    = $props['mc_version']
    compat       = $props['compat']
    flashbackJar = $fbJar
    world        = $WorldName
    result       = $Result
    detail       = $Detail
    seconds      = [int]$Clock.Elapsed.TotalSeconds
    logPath      = $LogPath
}
if ($ResultFile -ne '') {
    ($summary | ConvertTo-Json) | Set-Content -Path $ResultFile -Encoding UTF8
}

$color = 'Yellow'
if ($Result -eq 'PASS') { $color = 'Green' }
if ($Result -eq 'FAIL') { $color = 'Red' }
Write-Host "== smoke: $Project -> $Result ($Detail, $([int]$Clock.Elapsed.TotalSeconds)s) ==" -ForegroundColor $color
foreach ($line in $Tail) { Write-Host "  | $line" }

if ($Result -eq 'PASS') { exit 0 }
if ($Result -eq 'TIMEOUT') { exit 2 }
exit 1

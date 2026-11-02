<#
.SYNOPSIS
  Opens one version's dev client for MANUAL testing and leaves it running.

.DESCRIPTION
  Part of the manual review loop: opens the game window (quickPlayed into a
  world when -WorldName is given), records the launcher pid in
  build/smoke-open.pid, and returns immediately. The game stays open until
  you say "good" (or report a problem) -- then run tools/smoke_close.ps1
  and open the next version with this script.

  Usage (from the repo root):
    powershell -ExecutionPolicy Bypass -File tools/smoke_open.ps1 versions/1.21.11
    powershell -ExecutionPolicy Bypass -File tools/smoke_open.ps1 versions/26.3 -WorldName smoke
#>
param(
    [Parameter(Mandatory = $true)][string]$Project,   # e.g. versions/1.21.11
    [string]$WorldName = 'smoke'                       # '' = stop at title screen
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
$ProjDir = Join-Path $Root $Project
$Leaf = Split-Path $Project -Leaf
$TaskPath = ':' + ($Project -replace '[\\/]', ':')
$PidFile = Join-Path $Root 'build/smoke-open.pid'

# Clean leftovers of THIS version only, then launch visible.
$needle = 'versions\' + $Leaf
Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -match '^javaw?\.exe$' -and $_.CommandLine -like "*$needle*" } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

$gradleArgs = @("$TaskPath`:runClient", '--console=plain')
if ($WorldName -ne '') { $gradleArgs += "-PsmokeWorld=$WorldName" }
$proc = Start-Process -FilePath (Join-Path $Root 'gradlew.bat') `
    -ArgumentList $gradleArgs -WorkingDirectory $Root -PassThru
"$Leaf $($proc.Id)" | Set-Content -Path $PidFile -Encoding ASCII

Write-Host "opened $Project (launcher pid $($proc.Id))."
Write-Host "Say 'good' and I will close it; say what broke and I will dig into the log."

<#
.SYNOPSIS
  Closes the manually opened dev client (see tools/smoke_open.ps1).

.DESCRIPTION
  Reads build/smoke-open.pid, kills that launcher's whole process tree
  (gradle client + game), and clears the pid file. Safe to run when
  nothing is open (reports that and exits 0).
#>

$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
$PidFile = Join-Path $Root 'build/smoke-open.pid'

if (-not (Test-Path $PidFile)) { Write-Host 'nothing open (no pid file).'; exit 0 }
$parts = (Get-Content $PidFile -Raw).Trim() -split '\s+'
Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
$leaf = $parts[0]
$id = [int]$parts[1]
$p = Get-Process -Id $id -ErrorAction SilentlyContinue
if ($null -eq $p) {
    Write-Host "launcher for $leaf already gone."
} else {
    & taskkill /PID $id /T /F 2>$null | Out-Null
    Write-Host "closed $leaf."
}
# Belt and suspenders: any game java still pointing at this version's tree.
$needle = 'versions\' + $leaf
Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -match '^javaw?\.exe$' -and $_.CommandLine -like "*$needle*" } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
exit 0

<#
.SYNOPSIS
  Boots every version's game client with Flashback and reviews the matrix.

.DESCRIPTION
  Reusable end-to-end game test for the whole mod matrix:
    1. Preflight: static mixin-signature + Flashback-jar check
       (tools/verify_mixins.py) -- fails fast on descriptor drift.
    2. Boot: one dev client per version via tools/smoke_client.ps1, each
       verdict PASS/FAIL/TIMEOUT from its own game log.
    3. Review: terminal summary table plus a markdown report written to
       build/smoke-report.md (per-version milestones, durations, and the
       exact mixin error excerpt on failures).

  Usage (from the repo root):
    powershell -ExecutionPolicy Bypass -File tools/smoke_all.ps1
    powershell -ExecutionPolicy Bypass -File tools/smoke_all.ps1 -Versions 1.21.11,26.3
    powershell -ExecutionPolicy Bypass -File tools/smoke_all.ps1 -TimeoutSec 600 -SkipPreflight

  Each client opens a real game window and is closed automatically. With
  -JoinWorld, quickPlay also joins a singleplayer world, so the integrated
  server classes load and the server-side mixins are exercised too.
  Exit code: 0 when every booted client passed, 1 otherwise.
#>
param(
    [string[]]$Versions = @(),          # version dir names; default = all discovered
    [int]$TimeoutSec = 900,             # per-client budget, seconds
    [string]$Report = 'build/smoke-report.md',
    [switch]$JoinWorld,                 # quickPlay into -WorldName (covers server mixins)
    [string]$WorldName = 'smoke',
    [switch]$SkipPreflight
)

$ErrorActionPreference = 'Stop'
$Root = $PSScriptRoot | Split-Path -Parent
Set-Location $Root

function Get-Props([string]$dir) {
    $p = @{}
    $f = Join-Path $dir 'gradle.properties'
    if (Test-Path $f) {
        Get-Content $f | ForEach-Object {
            if ($_ -match '^\s*([^#=]+?)\s*=\s*(.*?)\s*$') { $p[$Matches[1]] = $Matches[2] }
        }
    }
    return $p
}

$allDirs = Get-ChildItem (Join-Path $Root 'versions') -Directory |
    Where-Object { Test-Path (Join-Path $_.FullName 'gradle.properties') } |
    Sort-Object Name
if ($Versions.Count -gt 0) {
    $allDirs = $allDirs | Where-Object { $Versions -contains $_.Name }
}
if ($allDirs.Count -eq 0) { Write-Host 'No version targets selected.' -ForegroundColor Red; exit 1 }

$total = @($allDirs).Count
Write-Host ''
Write-Host "=== flashback-actors client matrix: $total target(s) ===" -ForegroundColor Cyan

# ---- 1. preflight ----
$prePass = $true
$preOut = ''
if (-not $SkipPreflight) {
    Write-Host '-- preflight: verify_mixins.py --' -ForegroundColor Cyan
    $preOut = & python3 tools/verify_mixins.py 2>&1 | Out-String
    $prePass = ($LASTEXITCODE -eq 0)
    Write-Host $preOut
    if ($prePass) { Write-Host 'preflight: PASS' -ForegroundColor Green }
    else { Write-Host 'preflight: FAIL (booting anyway for in-game evidence)' -ForegroundColor Red }
} else {
    $preOut = '(skipped)'
}

# ---- 2. boot matrix ----
$results = @()
$i = 0
foreach ($d in $allDirs) {
    $i++
    $proj = "versions/$($d.Name)"
    Write-Host ''
    Write-Host "--- [$i/$total] booting $proj ---" -ForegroundColor Cyan
    $tmp = Join-Path ([IO.Path]::GetTempPath()) ("fbactors-smoke-$($d.Name).json")
    if (Test-Path $tmp) { Remove-Item $tmp -Force }
    if ($JoinWorld) {
        & powershell -ExecutionPolicy Bypass -File (Join-Path $Root 'tools/smoke_client.ps1') `
            $proj -TimeoutSec $TimeoutSec -ResultFile $tmp -WorldName $WorldName
    } else {
        & powershell -ExecutionPolicy Bypass -File (Join-Path $Root 'tools/smoke_client.ps1') `
            $proj -TimeoutSec $TimeoutSec -ResultFile $tmp
    }
    if (Test-Path $tmp) {
        $results += (Get-Content $tmp -Raw | ConvertFrom-Json)
        Remove-Item $tmp -Force
    } else {
        $props = Get-Props $d.FullName
        $results += [pscustomobject]@{
            project = $proj; mcVersion = $props['mc_version']; compat = $props['compat']
            flashbackJar = ''; result = 'FAIL'; detail = 'no result file from smoke_client'
            seconds = 0; logPath = ''
        }
    }
}

# ---- 3. terminal review ----
Write-Host ''
Write-Host '=== matrix result ===' -ForegroundColor Cyan
$results | Select-Object `
    @{n = 'Target'; e = { ($_.project -replace 'versions/', '') } },
    @{n = 'MC'; e = { $_.mcVersion } },
    @{n = 'Flashback'; e = { $_.flashbackJar } },
    @{n = 'Result'; e = { $_.result } },
    @{n = 'Milestone'; e = { $_.detail } },
    @{n = 'Time'; e = { "$($_.seconds)s" } } | Format-Table -AutoSize | Out-String | Write-Host

$pass = @($results | Where-Object { $_.result -eq 'PASS' }).Count
$fail = @($results | Where-Object { $_.result -eq 'FAIL' }).Count
$tout = $total - $pass - $fail
if ($fail -eq 0 -and $tout -eq 0) { Write-Host "ALL GREEN: $pass/$total clients booted cleanly." -ForegroundColor Green }
else { Write-Host "MIXED: $pass pass, $fail fail, $tout timeout of $total." -ForegroundColor Red }

# ---- 4. markdown review report ----
$FailMarkers = @('Mixin apply for mod fbactors failed', 'InvalidInjectionException',
    'Mixin transformation of', 'Game crashed!')
$stamp = (Get-Date).ToUniversalTime().ToString('yyyy-MM-dd HH:mm') + ' UTC'
$md = @()
$md += '# Client smoke matrix -- review'
$md += ''
$md += ("_" + $stamp + " | $pass/$total PASS ($fail fail, $tout timeout) | preflight: " + $(if ($prePass) { 'PASS' } else { 'FAIL' }) + "_")
$md += ''
$md += 'Each row is a real dev-client boot with the Flashback jar from that'
if ($JoinWorld) {
    $md += "target's run/mods, quickPlayed into the '$WorldName' singleplayer world."
    $md += 'PASS means the client booted past mixin application AND joined the'
    $md += 'world, which loads the integrated server classes - so the server-side'
    $md += 'mixins (damage gate, block capture, container hooks) have applied too.'
} else {
    $md += "target's run/mods. PASS means the boot sailed past mixin application"
    $md += 'to resource reload / sound init - i.e. past the point where the'
    $md += '1.21.11 InvalidInjectionException killed the game. Server-side mixins'
    $md += 'are NOT exercised in this mode; re-run with -JoinWorld for that.'
}
$md += ''
$md += '## Preflight (static signature check)'
$md += ''
$md += '```'
$md += $preOut.Trim()
$md += '```'
$md += ''
$md += '## Results'
$md += ''
$md += '| Target | MC | Flashback (run/mods) | Result | Milestone | Time |'
$md += '|---|---|---|---|---|---|'
foreach ($r in $results) {
    $t = ($r.project -replace 'versions/', '')
    $md += "| $t | $($r.mcVersion) | $($r.flashbackJar) | $($r.result) | $($r.detail) | $($r.seconds)s |"
}
$md += ''
foreach ($r in $results) {
    $t = ($r.project -replace 'versions/', '')
    $md += "## $t -- $($r.result)"
    $md += ''
    $md += "- MC: $($r.mcVersion) | compat: $($r.compat) | Flashback: $($r.flashbackJar)"
    $md += "- Milestone: $($r.detail) | boot time: $($r.seconds)s"
    $md += '- Game log: `' + $r.logPath + '`'
    if ($r.result -ne 'PASS' -and $r.logPath -ne '' -and (Test-Path $r.logPath)) {
        $md += ''
        $md += 'Error excerpt:'
        $md += '```'
        $lines = Get-Content $r.logPath -ErrorAction SilentlyContinue
        for ($k = 0; $k -lt $lines.Count; $k++) {
            $hit = $false
            foreach ($m in $FailMarkers) { if ($lines[$k].Contains($m)) { $hit = $true } }
            if ($hit) {
                $from = [Math]::Max(0, $k - 1)
                $to = [Math]::Min($lines.Count - 1, $k + 6)
                $md += $lines[$from..$to]
                $md += '...'
                break
            }
        }
        $md += '```'
    }
    $md += ''
}
if ($fail -eq 0 -and $tout -eq 0) {
    $md += '## Review'
    $md += ''
    $md += "All $total clients booted cleanly past mixin application with Flashback loaded. No action needed."
} else {
    $md += '## Review'
    $md += ''
    $md += 'Failing targets show a mixin descriptor error in the excerpt above.'
    $md += 'Fix the `@Inject` handler signature in that target''s'
    $md += '`compat/<name>/java/pdx/dev/fbactors/mixin/MixinEntityRenderer.java`'
    $md += 'to match the `javap` signature in `tools/verify_mixins.py`, then re-run:'
    $md += '```'
    $md += 'powershell -ExecutionPolicy Bypass -File tools/smoke_all.ps1'
    $md += '```'
}

$reportPath = Join-Path $Root $Report
$parent = Split-Path $reportPath -Parent
if (-not (Test-Path $parent)) { New-Item -ItemType Directory $parent | Out-Null }
$md -join "`n" | Set-Content -Path $reportPath -Encoding UTF8
Write-Host "review written to $Report" -ForegroundColor Cyan

if ($fail -eq 0 -and $tout -eq 0) { exit 0 }
exit 1

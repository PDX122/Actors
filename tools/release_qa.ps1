<# Build once and collect all version evidence in parallel before visual review. #>
param(
    [string[]]$Versions = @('1.21.1','1.21.4','1.21.5','1.21.8','1.21.10','1.21.11','26.1','26.2','26.3'),
    [int]$Parallelism = 0,
    [switch]$VisualFocus,
    [switch]$SkipBuild,
    [string]$OutputDirectory = ''
)
$arguments = @('-3.13', (Join-Path $PSScriptRoot 'release_batch.py'), '--jobs', $Parallelism, '--versions') + $Versions
if ($VisualFocus) { $arguments += '--focus' }
if ($SkipBuild) { $arguments += '--skip-build' }
if ($OutputDirectory) { $arguments += @('--output', $OutputDirectory) }
& py @arguments
exit $LASTEXITCODE

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
& python (Join-Path $PSScriptRoot 'run-dedicated-function-acceptance.py') @args
exit $LASTEXITCODE

$ErrorActionPreference = 'Stop'
python (Join-Path $PSScriptRoot 'run-dedicated-tags-recipes-safety-acceptance.py')
exit $LASTEXITCODE

$ErrorActionPreference = 'Stop'
python (Join-Path $PSScriptRoot 'run-dedicated-tags-recipes-acceptance.py')
exit $LASTEXITCODE

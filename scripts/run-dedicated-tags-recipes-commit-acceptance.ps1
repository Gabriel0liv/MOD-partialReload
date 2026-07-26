$ErrorActionPreference = 'Stop'
python (Join-Path $PSScriptRoot 'run-dedicated-tags-recipes-commit-acceptance.py')
exit $LASTEXITCODE

param()
$ErrorActionPreference = 'Stop'
python "$PSScriptRoot/run-dedicated-tag-acceptance.py"
exit $LASTEXITCODE

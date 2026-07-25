param()
$ErrorActionPreference = 'Stop'
python "$PSScriptRoot/run-dedicated-kubejs-recipe-acceptance.py"
exit $LASTEXITCODE

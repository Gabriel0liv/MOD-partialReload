$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$acceptanceLog = Join-Path $root 'run\dedicated-function-acceptance.log'
Set-Content -LiteralPath $acceptanceLog -Value 'ACCEPTANCE_START'
try {
$lines = [System.Collections.Concurrent.ConcurrentQueue[string]]::new()
$psi = [System.Diagnostics.ProcessStartInfo]::new()
$psi.FileName = 'cmd.exe'
$psi.WorkingDirectory = $root
$psi.Arguments = "/c `"$(Join-Path $root 'gradlew.bat')`" --no-daemon runServer --console=plain"
$psi.UseShellExecute = $false
$psi.CreateNoWindow = $true
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$process = [System.Diagnostics.Process]::new()
$process.StartInfo = $psi
$null = $process.Start()
Add-Content -LiteralPath $acceptanceLog -Value "PROCESS_STARTED:$($process.Id)"

$outputHandler = [System.Diagnostics.DataReceivedEventHandler]{ param($sender, $event)
    if ($null -ne $event.Data) { $lines.Enqueue($event.Data); Write-Host $event.Data }
}
$process.add_OutputDataReceived($outputHandler)
$process.add_ErrorDataReceived($outputHandler)
Add-Content -LiteralPath $acceptanceLog -Value 'EVENTS_ATTACHED'
$process.BeginOutputReadLine()
Add-Content -LiteralPath $acceptanceLog -Value 'STDOUT_ATTACHED'
$process.BeginErrorReadLine()
Add-Content -LiteralPath $acceptanceLog -Value 'STDERR_ATTACHED'

$transcript = [System.Collections.Generic.List[string]]::new()
Add-Content -LiteralPath $acceptanceLog -Value 'READY_TO_WAIT'
function Wait-ForPattern([string]$pattern, [int]$timeoutSeconds = 120) {
    $deadline = [DateTime]::UtcNow.AddSeconds($timeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        $logPath = Join-Path $root 'run\logs\latest.log'
        if (Test-Path $logPath) {
            foreach ($lineFromLog in (Get-Content $logPath -Tail 300 -ErrorAction SilentlyContinue)) {
                if (-not $transcript.Contains($lineFromLog)) { $transcript.Add($lineFromLog) }
                if ($lineFromLog -match $pattern) { return $lineFromLog }
            }
        }
        $line = $null
        while ($lines.TryDequeue([ref]$line)) {
            $transcript.Add($line)
            if ($line -match $pattern) { return $line }
        }
        Start-Sleep -Milliseconds 100
    }
    throw "Timeout waiting for pattern: $pattern"
}
function Send-Command([string]$command) {
    Write-Host "> $command"
    $transcript.Add("> $command")
    $process.StandardInput.WriteLine($command)
    $process.StandardInput.Flush()
}
function Wait-CommandText([string]$pattern, [int]$timeoutSeconds = 30) {
    return Wait-ForPattern $pattern $timeoutSeconds
}

    Wait-ForPattern 'Done \([0-9.]+s\)! For help' 180 | Out-Null

    Send-Command 'partialreload status'
    Wait-CommandText 'Mode: FUNCTION_COMMIT_SUPPORTED' | Out-Null
    Send-Command 'partialreload active functions'
    Wait-CommandText 'Active functions:' | Out-Null

    Send-Command 'scoreboard objectives add pr_dedicated dummy'
    Start-Sleep -Seconds 1
    Send-Command 'scoreboard players set result pr_dedicated 1'
    Start-Sleep -Seconds 1
    Send-Command 'function partialreload:gametest/valid'
    Start-Sleep -Seconds 1

    Send-Command 'partialreload scan'
    Wait-CommandText 'Scan complete:' 90 | Out-Null
    Send-Command 'partialreload changed'
    Wait-CommandText 'Changed resources:' | Out-Null
    Send-Command 'partialreload prepare functions'
    Wait-CommandText 'Function preparation started' | Out-Null
    Send-Command 'partialreload prepared'
    Wait-CommandText 'Technically applicable: true' 90 | Out-Null
    Send-Command 'partialreload apply prepared'
    Wait-CommandText 'queued for the next safe point' | Out-Null
    Start-Sleep -Seconds 2
    Send-Command 'partialreload transaction'
    Wait-CommandText 'Status: SUCCESS' 30 | Out-Null
    Wait-CommandText 'Verification: true' 30 | Out-Null
    Wait-CommandText 'Load policy: DO_NOT_RUN' 30 | Out-Null

    Send-Command 'partialreload active functions'
    Wait-CommandText 'Load pending: false' | Out-Null

    Send-Command 'partialreload prepare loot'
    Wait-CommandText 'Joint loot data preparation started' | Out-Null
    Start-Sleep -Seconds 3
    Send-Command 'partialreload apply prepared'
    Wait-CommandText 'Commit is not implemented for loot data' 30 | Out-Null
    Send-Command 'partialreload discard'
    Wait-CommandText 'Prepared artifact discarded' | Out-Null

    Send-Command 'partialreload rollback functions'
    Wait-CommandText 'Rollback transaction .* queued' | Out-Null
    Start-Sleep -Seconds 2
    Send-Command 'partialreload transaction'
    Wait-CommandText 'Status: ROLLED_BACK' 30 | Out-Null

    Send-Command 'partialreload scan'
    Wait-CommandText 'Scan complete:' 90 | Out-Null
    Send-Command 'partialreload changed'
    Wait-CommandText 'Changed resources:' | Out-Null

    Send-Command 'stop'
    Wait-ForPattern 'ThreadedAnvilChunkStorage: All dimensions are saved|ThreadedAnvilChunkStorage: All dimensions are saved' 120 | Out-Null
    $process.WaitForExit(120000)
    if (-not $process.HasExited) { throw 'Dedicated server did not exit after stop' }
    if ($process.ExitCode -ne 0) { throw "Dedicated server exit code: $($process.ExitCode)" }
    $transcript | Set-Content $acceptanceLog
    Write-Host 'DEDICATED_FUNCTION_ACCEPTANCE_PASSED'
    exit 0
}
catch {
    $transcript | Set-Content $acceptanceLog
    try { $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush() } catch { }
    if (-not $process.HasExited) { $process.WaitForExit(30000) }
    Write-Error $_
    exit 1
}

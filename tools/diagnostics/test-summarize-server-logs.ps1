Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Join-Path ([IO.Path]::GetTempPath()) ('aco-log-audit-' + [Guid]::NewGuid().ToString('N'))
$logs = Join-Path $root 'logs'
$out = Join-Path $root 'report'
$null = [IO.Directory]::CreateDirectory($logs)
$data = @'
[12:00:00] [DEBUG] ACO-DIAG event=planning_started output=test:root requested=1
[12:00:01] [DEBUG] ACO-DIAG event=planning_declined reason=AMBIGUOUS_PRODUCER output=test:root requested=1
[12:00:02] [DEBUG] [Thunderbolt Core][crafting-planner] finished: output=test:root requested=1 wallMs=2000.125 selected=vanilla
[12:00:03] [DEBUG] ACO unrelated event="not_a_planning_event"
'@
$bytes = [Text.Encoding]::UTF8.GetBytes($data)
$plain = Join-Path $logs 'latest.log'
[IO.File]::WriteAllBytes($plain, $bytes)
$gzip = [IO.Compression.GZipStream]::new([IO.File]::Create((Join-Path $logs 'previous.log.gz')), [IO.Compression.CompressionMode]::Compress)
try { $gzip.Write($bytes, 0, $bytes.Length) } finally { $gzip.Dispose() }
$hashBefore = (Get-FileHash -LiteralPath $plain).Hash
& (Join-Path $PSScriptRoot 'summarize-server-logs.ps1') -LogDirectory $logs -OutputDirectory $out
$report = Get-Content -Raw -LiteralPath (Join-Path $out 'aco-log-history.json') | ConvertFrom-Json
if ($report.files.Count -ne 2 -or $report.events.Count -ne 6) { throw 'Wrong file/event counts' }
if (@($report.files | Where-Object { $null -ne $_.readError }).Count -ne 0) { throw 'Read error' }
if (@($report.events | Where-Object { $_.elapsedMs -eq 2000.125 }).Count -ne 2) { throw 'Lost fractional timing' }
if (@($report.events | Where-Object reason -EQ 'AMBIGUOUS_PRODUCER').Count -ne 2) { throw 'Lost decline reason' }
if ((Get-FileHash -LiteralPath $plain).Hash -ne $hashBefore) { throw 'Original log changed' }
'PASS: plain/gzip events, fractional timing, reasons, unrelated event exclusion and unchanged input'

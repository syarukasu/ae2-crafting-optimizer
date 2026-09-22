param(
    [Parameter(Mandatory = $true)][string]$LogDirectory,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $LogDirectory).Path
$output = [IO.Path]::GetFullPath($OutputDirectory)
if ($output.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or $output -eq $root) {
    throw 'Keep the audit outside the original logs directory.'
}
$null = [IO.Directory]::CreateDirectory($output)
$files = @(Get-ChildItem -LiteralPath $root -File | Where-Object Name -Match '\.log(\.gz)?$' | Sort-Object Name)
$summaries = [Collections.Generic.List[object]]::new()
$events = [Collections.Generic.List[object]]::new()
$interesting = [regex]::new('ACO|AE2CraftingOptimizer|ae2_crafting_optimizer|ae2-crafting-optimizer|aco[0-9]|\[crafting-planner\]', 'IgnoreCase')
$timing = [regex]::new('(?:elapsed(?:Ms)?|wallMs)[= ]+(\d+(?:\.\d+)?)(?:\s*ms)?|elapsed (\d+) ms')
$eventPattern = [regex]::new('ACO-DIAG event=([a-z_]+)')
$reasonPattern = [regex]::new('reason=([^\s,)]+)')
$routePattern = [regex]::new('(?:planner route |route=)([^\s,]+)')
$rootPattern = [regex]::new('(?:output=|requested |compile )([a-z0-9_.-]+:[a-z0-9_./-]+)')
foreach ($file in $files) {
    $counts = @{}
    $versions = [Collections.Generic.HashSet[string]]::new()
    $lineNumber = 0L
    $matched = 0L
    $stream = $null
    $reader = $null
    $errorText = $null
    $beforeLength = $file.Length
    $beforeWrite = $file.LastWriteTimeUtc
    try {
        $stream = [IO.File]::Open($file.FullName, 'Open', 'Read', 'ReadWrite')
        if ($file.Extension -eq '.gz') {
            $stream = [IO.Compression.GZipStream]::new($stream, [IO.Compression.CompressionMode]::Decompress)
        }
        $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8, $true)
        while ($null -ne ($line = $reader.ReadLine())) {
            $lineNumber++
            if (-not $interesting.IsMatch($line)) { continue }
            $matched++
            foreach ($version in [regex]::Matches($line, '(?:aco|ae2-crafting-optimizer[-_])([0-9][0-9A-Za-z.+_-]*?)(?:\.jar|\s|\||$)', 'IgnoreCase')) {
                $null = $versions.Add($version.Groups[1].Value)
            }
            $kind = $null
            $em = $eventPattern.Match($line)
            if ($em.Success) { $kind = $em.Groups[1].Value }
            elseif ($line -match '\[crafting-planner\] (started|finished|failed):') { $kind = 'thunderbolt_' + $Matches[1] }
            elseif ($line -match 'Slow AE2 crafting calculation') { $kind = 'legacy_planning_slow' }
            elseif ($line -match 'could not compile') { $kind = 'compile_declined' }
            elseif ($line -match 'quarantin') { $kind = 'quarantined' }
            elseif ($line -match 'fell back|entered recovery') { $kind = 'batch_fallback_recovery' }
            elseif ($line -match 'ACO active:|initialized|initialized with|ACO statistics:') { $kind = 'startup_or_statistics' }
            elseif ($line -match 'ERROR|WARN') { $kind = 'aco_warning_or_error' }
            if ($null -eq $kind) { continue }
            if (-not $counts.ContainsKey($kind)) { $counts[$kind] = 0L }
            $counts[$kind]++
            $tm = $timing.Match($line)
            $rm = $routePattern.Match($line)
            $reason = $reasonPattern.Match($line)
            $item = $rootPattern.Match($line)
            # Store only bounded ACO evidence. Never include chat, addresses or player data.
            if ($tm.Success -or $kind -in @('planning_started', 'planning_complete', 'planning_declined', 'exact_started', 'exact_completed', 'compile_declined', 'quarantined', 'batch_fallback_recovery', 'aco_warning_or_error')) {
                $elapsed = $null
                if ($tm.Success) {
                    $value = if ($tm.Groups[1].Success) { $tm.Groups[1].Value } else { $tm.Groups[2].Value }
                    $elapsed = [double]::Parse($value, [Globalization.CultureInfo]::InvariantCulture)
                }
                $events.Add([pscustomobject]@{
                    file = $file.Name; line = $lineNumber; kind = $kind
                    timePrefix = if ($line -match '^\[([^\]]+)\]') { $Matches[1] } else { $null }
                    output = if ($item.Success) { $item.Groups[1].Value } else { $null }
                    route = if ($rm.Success) { $rm.Groups[1].Value } else { $null }
                    reason = if ($reason.Success) { $reason.Groups[1].Value } else { $null }
                    elapsedMs = $elapsed
                })
            }
        }
    } catch {
        $errorText = $_.Exception.GetType().Name
    } finally {
        if ($null -ne $reader) { $reader.Dispose() }
        elseif ($null -ne $stream) { $stream.Dispose() }
    }
    $file.Refresh()
    $summaries.Add([pscustomobject]@{
        file = $file.Name; bytesBefore = $beforeLength; linesRead = $lineNumber
        acoLines = $matched; versions = @($versions | Sort-Object); counts = $counts
        changedDuringRead = ($file.Length -ne $beforeLength -or $file.LastWriteTimeUtc -ne $beforeWrite)
        readError = $errorText
    })
}
$report = [pscustomobject]@{
    generatedUtc = [DateTime]::UtcNow.ToString('o')
    note = 'Files may overlap. Counts are log lines, not unique jobs. Missing version/route is unknown, not absent.'
    files = @($summaries.ToArray())
    events = @($events.ToArray())
}
$jsonPath = Join-Path $output 'aco-log-history.json'
[IO.File]::WriteAllText($jsonPath, ($report | ConvertTo-Json -Depth 10), [Text.UTF8Encoding]::new($false))
$events | Export-Csv -LiteralPath (Join-Path $output 'aco-log-events.csv') -NoTypeInformation -Encoding UTF8
[pscustomobject]@{
    files = $files.Count; acoLines = ($summaries | Measure-Object acoLines -Sum).Sum
    events = $events.Count; readErrors = @($summaries | Where-Object { $null -ne $_.readError }).Count
    report = $jsonPath
} | ConvertTo-Json

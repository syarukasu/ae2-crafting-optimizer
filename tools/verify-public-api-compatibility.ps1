param(
    [Parameter(Mandatory)][string]$BaselineClasses,
    [Parameter(Mandatory)][string]$CandidateClasses,
    [string]$Javap = (Join-Path $env:JAVA_HOME 'bin/javap.exe'),
    [string]$Report = 'build/compat/public-api.json'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$prefix = 'com/syaru/ae2craftingoptimizer/api'
$baseline = (Resolve-Path -LiteralPath $BaselineClasses).Path
$candidate = (Resolve-Path -LiteralPath $CandidateClasses).Path
$classes = @(Get-ChildItem -LiteralPath (Join-Path $baseline $prefix) -Filter '*.class' -Recurse |
    ForEach-Object { $_.FullName.Substring($baseline.Length + 1).Replace('\', '.').Replace('/', '.') -replace '\.class$', '' })

function Read-Api([string]$Root) {
    $lines = & $Javap -classpath $Root -public -s @classes 2>&1
    if ($LASTEXITCODE -ne 0) { throw "javap failed for $Root : $lines" }
    $blocks = @{}
    $header = $null
    foreach ($line in $lines) {
        $text = "$line".Trim()
        if ($text.EndsWith('{')) {
            $header = $text
            $blocks[$header] = [System.Collections.Generic.List[string]]::new()
        } elseif ($text -eq '}') {
            $header = $null
        } elseif ($null -ne $header -and $text -ne '') {
            $blocks[$header].Add($text)
        }
    }
    return $blocks
}

$old = Read-Api $baseline
$new = Read-Api $candidate
$missing = [System.Collections.Generic.List[string]]::new()
foreach ($header in $old.Keys) {
    if (!$new.ContainsKey($header)) {
        $missing.Add($header)
        continue
    }
    foreach ($member in $old[$header]) {
        if (!$new[$header].Contains($member)) { $missing.Add("$header :: $member") }
    }
}
$result = [ordered]@{
    baseline = $baseline
    candidate = $candidate
    classes = $old.Count
    missingPublicDeclarationsOrDescriptors = @($missing.ToArray())
    compatible = ($missing.Count -eq 0)
    scope = 'Public declarations and JVM descriptors; not external packaged runtime acceptance'
}
$result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $Report -Encoding utf8
if ($missing.Count -gt 0) { throw ($missing -join "`n") }
Write-Output "Public API compatible: $($old.Count) baseline class files; no removed declarations/descriptors."

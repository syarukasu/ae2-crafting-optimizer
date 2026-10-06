param(
    [Parameter(Mandatory = $true)][string]$ModsDir,
    [ValidateSet('upstream', 'uelm')][string]$Variant = 'upstream'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$partners = (Resolve-Path -LiteralPath $ModsDir).Path
$expected = @{
    'cloth-config-11.1.136-forge.jar' = '1E895E85CF5B1E1905EF3178EC155C8BADFE22A1577B92C09143A5AA1F4CE0F2'
    'insaneae-1.20.1-1.0.4.jar' = 'C0CA68FDA754AE0537622A9A0BB8A9A2D6D7B948958D5E0BF666F2B7CC291752'
    'mega-cells-622112-6175008.jar' = '19F59CC793950A3FA80DC1B29EF3BDAD587344CD5EFE25C5E010F4DB1EA4A79C'
}
$jars = @(Get-ChildItem -LiteralPath $partners -File -Filter '*.jar')
if ($jars.Count -ne $expected.Count) { throw 'Use the pinned three-JAR fixture directory only.' }
foreach ($jar in $jars) {
    $hash = (Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA256).Hash
    if ($expected[$jar.Name] -ne $hash) { throw "Unpinned runtime fixture: $($jar.Name) ($hash)" }
}

$variables = @('ORG_GRADLE_PROJECT_ae2Variant', 'ORG_GRADLE_PROJECT_acoGameTestModsDir',
    'ORG_GRADLE_PROJECT_acoGameTestScope', 'ORG_GRADLE_PROJECT_acoGameTestBaseline',
    'ORG_GRADLE_PROJECT_acoThunderboltJar')
$previous = @{}
foreach ($name in $variables) { $previous[$name] = [Environment]::GetEnvironmentVariable($name) }
$output = Join-Path $root "build/issue161-replay/$Variant"
New-Item -ItemType Directory -Path $output -Force | Out-Null
Push-Location $root
try {
    $env:ORG_GRADLE_PROJECT_ae2Variant = $Variant
    $env:ORG_GRADLE_PROJECT_acoGameTestModsDir = $partners
    [Environment]::SetEnvironmentVariable('ORG_GRADLE_PROJECT_acoThunderboltJar', $null)
    foreach ($run in @('baseline', 'providers')) {
        $env:ORG_GRADLE_PROJECT_acoGameTestScope = if ($run -eq 'baseline') { 'historical' } else { 'providers' }
        $env:ORG_GRADLE_PROJECT_acoGameTestBaseline = if ($run -eq 'baseline') { 'true' } else { 'false' }
        & .\gradlew.bat runGameTestServer --no-daemon --console=plain *> (Join-Path $output "$run.log")
        $code = $LASTEXITCODE
        foreach ($file in @('results.xml', 'results.xml.expected.json')) {
            $source = Join-Path $root "build/gametest-ae2/$file"
            if (Test-Path -LiteralPath $source) {
                Copy-Item -LiteralPath $source -Destination (Join-Path $output "$run-$file")
            }
        }
        if ($code -ne 0) { throw "$run regression failed; see $output" }
    }
    Write-Output "Pinned native/VM provider regressions passed. Evidence: $output"
} finally {
    foreach ($name in $variables) { [Environment]::SetEnvironmentVariable($name, $previous[$name]) }
    Pop-Location
}

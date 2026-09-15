param(
    [Parameter(Mandatory)][string]$ServerRoot,
    [Parameter(Mandatory)][string]$Destination
)
$ErrorActionPreference = 'Stop'
$source = (Resolve-Path -LiteralPath $ServerRoot).Path
$dest = [IO.Path]::GetFullPath($Destination)
if (Test-Path -LiteralPath $dest) { throw 'Destination must be new; existing files will not be replaced.' }
if ($dest.StartsWith($source + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Keep the isolated server outside the production directory.'
}
New-Item -ItemType Directory -Path $dest | Out-Null
$copied = @('mods', 'config', 'defaultconfigs', 'kubejs', 'libraries', '.arclight',
    'ldlib', 'local', 'modernfix', 'moonlight-global-datapacks', 'tacz')
foreach ($directory in $copied) {
    $inputDir = Join-Path $source $directory
    if (!(Test-Path -LiteralPath $inputDir)) { continue }
    & robocopy $inputDir (Join-Path $dest $directory) /E /NFL /NDL /NJH /NJS /NP /R:1 /W:1 /XJ
    if ($LASTEXITCODE -ge 8) { throw "Copy failed: $directory ($LASTEXITCODE)" }
}
foreach ($name in @('arclight-forge-1.20.1-1.0.6-SNAPSHOT-6de9fec.jar', 'arclight.conf',
    'bukkit.yml', 'spigot.yml', 'commands.yml', 'eula.txt', 'rhino.local.properties', 'ae2-jit-workaround.json')) {
    $inputFile = Join-Path $source $name
    if (Test-Path -LiteralPath $inputFile) { Copy-Item -LiteralPath $inputFile -Destination $dest }
}
foreach ($directory in @('world/serverconfig', 'world/datapacks')) {
    $inputDir = Join-Path $source $directory
    if (!(Test-Path -LiteralPath $inputDir)) { continue }
    & robocopy $inputDir (Join-Path $dest $directory) /E /NFL /NDL /NJH /NJS /NP /R:1 /W:1 /XJ
    if ($LASTEXITCODE -ge 8) { throw "Copy failed: $directory" }
}
$settings = [ordered]@{
    'server-ip' = '127.0.0.1'; 'server-port' = '25585'; 'enable-query' = 'false'
    'enable-rcon' = 'false'; 'level-name' = 'world'; 'level-type' = 'minecraft:flat'
    'generate-structures' = 'false'; 'view-distance' = '2'; 'simulation-distance' = '2'
    'max-tick-time' = '180000'; 'white-list' = 'true'; 'enforce-whitelist' = 'true'
}
$properties = Get-Content -LiteralPath (Join-Path $source 'server.properties')
$properties = @($properties | Where-Object { ($_ -split '=', 2)[0] -notin $settings.Keys })
$properties += @($settings.GetEnumerator() | ForEach-Object { $_.Key + '=' + $_.Value })
[IO.File]::WriteAllLines((Join-Path $dest 'server.properties'), $properties, [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $dest 'whitelist.json'), '[]', [Text.UTF8Encoding]::new($false))
$scriptDir = Join-Path $dest 'kubejs/server_scripts/aco_capture'
New-Item -ItemType Directory -Path $scriptDir -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $dest 'industrial-capture') | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'capture.js') -Destination (Join-Path $scriptDir 'capture.js')
[IO.File]::WriteAllText((Join-Path $dest 'kubejs/aco_capture_enabled.json'), '{"enabled":true}', [Text.UTF8Encoding]::new($false))
$manifest = foreach ($directory in @('mods', 'kubejs/server_scripts', 'kubejs/startup_scripts', 'kubejs/data', 'config', 'world/serverconfig', 'world/datapacks')) {
    $inputDir = Join-Path $dest $directory
    if (Test-Path -LiteralPath $inputDir) {
        Get-ChildItem -LiteralPath $inputDir -File -Recurse | ForEach-Object {
            [ordered]@{ path = $_.FullName.Substring($dest.Length + 1).Replace('\', '/');
                bytes = $_.Length; sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash }
        }
    }
}
$metadata = [ordered]@{ schema = 1; sourceRoot = $source; createdUtc = [DateTime]::UtcNow.ToString('o');
    note = 'Private fresh-world recipe capture. World data/inventory not copied. Do not publish this directory.';
    propertyOverrides = $settings; files = @($manifest) }
[IO.File]::WriteAllText((Join-Path $dest 'capture-inputs.private.json'), ($metadata | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
Write-Output "Isolated capture ready: $dest"

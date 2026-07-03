# Fetches the libretro core .so files from the latest successful build-cores CI run,
# strips them, and places them into core-emulator/src/main/jniLibs/<abi>/.
# Cores are never committed to git — CI (pinned upstream source + 16KB readelf gate) is
# the single source of truth. Requires: gh CLI (authenticated), Android NDK 28.2.13676358.

$ErrorActionPreference = "Stop"
$gh = "C:\Program Files\GitHub CLI\gh.exe"
$repo = "lawrencema000-gif/retrovault"
$root = Split-Path $PSScriptRoot -Parent
$ndkBin = "$env:LOCALAPPDATA\Android\Sdk\ndk\28.2.13676358\toolchains\llvm\prebuilt\windows-x86_64\bin"

$runId = & $gh run list --repo $repo --workflow build-cores --status success --limit 1 --json databaseId --jq ".[0].databaseId"
if (-not $runId) { throw "No successful build-cores run found." }
Write-Host "Fetching cores from run $runId"

$dl = Join-Path $env:TEMP "pulsar-cores-fetch"
if (Test-Path $dl) { Remove-Item -Recurse -Force $dl }
New-Item -ItemType Directory -Force -Path $dl | Out-Null
& $gh run download $runId --repo $repo --dir $dl

foreach ($abi in "arm64-v8a", "x86_64") {
    $src = Get-ChildItem -Recurse -Filter "*.so" $dl | Where-Object { $_.FullName -match [regex]::Escape($abi) }
    $dest = Join-Path $root "core-emulator\src\main\jniLibs\$abi"
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
    foreach ($so in $src) {
        $out = Join-Path $dest $so.Name
        Copy-Item $so.FullName $out -Force
        & "$ndkBin\llvm-strip.exe" --strip-unneeded $out
        $loads = & "$ndkBin\llvm-readelf.exe" -l $out | Select-String "LOAD"
        $bad = $loads | Where-Object { $_ -notmatch "0x4000$" }
        if ($bad) { throw "$($so.Name) [$abi] is not 16KB-aligned!" }
        Write-Host ("  {0} [{1}]  {2:N1} MB  16KB-OK" -f $so.Name, $abi, ((Get-Item $out).Length / 1MB))
    }
}
Write-Host "Done."

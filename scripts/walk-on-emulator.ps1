# Walks a route on an emulator by sending GPS fixes with `adb emu geo fix`.
# Run the app on the emulator first; the cards update as each fix arrives.
#
#   .\scripts\walk-on-emulator.ps1 -Serial emulator-5556
param(
    [string]$Serial = "emulator-5556",
    [double]$FromLat = 51.5000,
    [double]$FromLon = -0.1200,
    [double]$ToLat = 51.5045,
    [double]$ToLon = -0.1200,
    [int]$Steps = 10,
    [int]$PauseSeconds = 8
)

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$invariant = [Globalization.CultureInfo]::InvariantCulture

for ($i = 0; $i -le $Steps; $i++) {
    $t = $i / $Steps
    $lat = $FromLat + ($ToLat - $FromLat) * $t
    $lon = $FromLon + ($ToLon - $FromLon) * $t
    # adb expects longitude first, then latitude.
    & $adb -s $Serial emu geo fix $lon.ToString($invariant) $lat.ToString($invariant) | Out-Null
    Write-Host ("Step {0}/{1}: {2:F6}, {3:F6}" -f $i, $Steps, $lat, $lon)
    Start-Sleep -Seconds $PauseSeconds
}

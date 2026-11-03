# Replays the GPX route given in $env:GPX_FILE on the emulator named in $env:EMU_SERIAL, one fix per track point at the file's timestamps.
$serial = $env:EMU_SERIAL
[xml]$track = Get-Content -Path $env:GPX_FILE -Raw
$points = $track.gpx.trk.trkseg.trkpt
$previous = $null
foreach ($point in $points) {
    $time = [datetime]$point.time
    if ($previous) { Start-Sleep -Milliseconds ([int](($time - $previous).TotalMilliseconds)) }
    & adb -s $serial emu geo fix $point.lon $point.lat | Out-Null
    $previous = $time
}
Write-Output "Replayed $($points.Count) points"

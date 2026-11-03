# Demo videos

Three 30-second demo videos of NearGems on the Android emulator. Each has the phone on the left and a map on the right. The map shows the route, and a heading arrow shows the direction of travel.

| File | Watch | What it shows |
| --- | --- | --- |
| `walk-with-map.mp4` | [YouTube Short](https://youtube.com/shorts/1EACPdiCcDg) | Walking 42 m on Trafalgar Square at 1.4 m/s. |
| `drive-with-map.mp4` | [YouTube Short](https://youtube.com/shorts/qwB2o0JGtzI) | Driving 356 m along Victoria Embankment at 12 m/s, with the simulated car connected. |
| `background-drive-with-map.mp4` | [YouTube Short](https://youtube.com/shorts/H9L4roCcprM) | The same drive, with the app in the background. The notification shade shows the live notification, which changes as the car moves. |

The routes are in `walk-route.gpx` and `drive-route.gpx`. `replay-gpx.ps1` replays a route on the emulator.

## Setup

- **Emulator:** Medium Phone, API 37 (Google Play image), 1080×2400, run with a window or headless. Use only an emulator; these steps don't target a physical phone.
- **App:** a debug build of `main`.
- **Location priority:** the shipped app asks for balanced-power location, which the emulator's GPS fixes don't reach. The replays used a **temporary, uncommitted** build where both `PRIORITY_BALANCED_POWER_ACCURACY` occurrences in `androidApp/src/main/kotlin/com/neargems/android/platform/AndroidPlatform.kt` are replaced with `PRIORITY_HIGH_ACCURACY`. Build and install that, then restore the file. Don't commit it.
- **Permissions and settings:** grant location and notification permissions, and set location mode to high accuracy:
  ```
  adb -s emulator-5560 shell pm grant com.neargems.app android.permission.ACCESS_FINE_LOCATION
  adb -s emulator-5560 shell pm grant com.neargems.app android.permission.ACCESS_COARSE_LOCATION
  adb -s emulator-5560 shell pm grant com.neargems.app android.permission.POST_NOTIFICATIONS
  adb -s emulator-5560 shell settings put secure location_mode 3
  ```
- **Car watch (drive videos):** in Settings, turn on "Keep watching for a car in the background" and "Testing: pretend a car is connected". The car icon then shows in the app's top bar.

## Routes

The routes are generated from the Whitehall route in `androidApp/src/test/resources/routes/whitehall-drive.gpx`, and checked against OpenStreetMap:
- **Walk:** the first 42 m of the route, sampled every 1.4 m (one fix per second, 1.4 m/s). Every fix is within 13 m of a pedestrian area.
- **Drive:** OpenStreetMap way 45114498 (Victoria Embankment, a primary road, 356 m), sampled every 12 m (one fix per second, 12 m/s). Every fix is on that road.

Each track point has a timestamp one second after the previous one. The replay uses these timestamps.

## Recording

1. Start the replay with `replay-gpx.ps1`. It sends each track point with `adb emu geo fix <longitude> <latitude>`, at the timestamps in the GPX file:
   ```
   EMU_SERIAL=emulator-5560 GPX_FILE=docs/videos/walk-route.gpx pwsh -File docs/videos/replay-gpx.ps1
   ```
2. Record the screen at the same time. `screenrecord` gets its own command, not wrapped in `timeout`, which would stop it early:
   ```
   adb -s emulator-5560 shell screenrecord --size 1080x2400 --bit-rate 6000000 --time-limit 40 /sdcard/walk-demo.mp4
   adb -s emulator-5560 pull /sdcard/walk-demo.mp4
   ```
   Start `screenrecord` about 3 seconds before the replay, and trim that lead-in when combining.
3. For the background video, press Home and expand the notification shade (`adb shell cmd statusbar expand-notifications`) before replaying.

## Map composition

The map is built from OpenStreetMap tiles (© OpenStreetMap contributors), fetched at zoom 18 for the walk and zoom 17 for the drive. A 540×1200 panel is stitched from the tiles with ffmpeg overlays.

The moving pointer is a 30 px arrow image, rotated to each second's bearing along the route and placed on the fix for that second. The phone recording is scaled to 540×1200 and joined to the map with `hstack`. Both are trimmed to 30 seconds. Each arrow position and rotation is a piecewise-linear expression of the video time `t`, so it moves smoothly between the one-second fixes.

Only the map and arrow are composed. The phone footage is the raw emulator recording.

## Limits

- These are emulator recordings. A real-phone recording may still be needed for the Play foreground-service declaration.
- The replays use the temporary high-accuracy build, not the shipped build.
- The map shows the route, but there's no trail line. The arrow shows only the current position and heading.

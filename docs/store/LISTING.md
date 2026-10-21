# Play Store listing (draft)

**App name:** NearGems (no space: the launcher truncates a longer name)

**Short description (80 characters max):**
Nearby places with a read-aloud summary, for drivers and people on foot.

**Full description:**
Near Gems tells you about interesting places close to where you are: a restaurant, a café, a bar,
or a landmark, with a short summary you can have read aloud.

- Suggestions ranked by how interesting they are, minus a penalty for distance, so a nearby good
  place can beat a far-away great one.
- Live distance and direction, with a pointer that turns as you turn, for walking or driving.
- Walking and driving time estimates. These are rough, not routing.
- Read aloud, ducking music the way navigation prompts do. Next moves to the next place, and Back
  brings the last one back.
- Landmark photos and summaries from Wikipedia, with a link to each photo's licence.
- A map of the place from your position, with OpenStreetMap attribution.
- Choose one card at a time, or a list of nearby places to pick from.
- New and Seen badges, so you can see what is new since you last looked. Kept only on your phone.
- Change the search distance and new places appear as they are found.
- Works in portrait and landscape.
- A visit history, kept only on your phone.
- Light, dark, or system theme.

Places come from OpenStreetMap and Wikipedia. Near Gems does not show ads.

**Category:** Travel & Local

**Contact and privacy policy:** add the public URL of PRIVACY_POLICY.md before submitting.

## Data safety answers (draft, check against the final build)

- **Location (precise):** collected and shared with OpenStreetMap (Overpass API) and Wikipedia to
  find nearby places, and with OpenStreetMap for map tiles. Required: the app needs it to work.
  Also used in the background only when car watch is turned on (a notification shows while it
  runs). Not sold; the developer does not receive it.
- **Visit history and New/Seen badges:** stored on the device only, in a local database. Not
  collected by the developer.
- **Data sent to third parties:** the device's approximate position is sent to OpenStreetMap's
  Overpass API and Wikipedia to find places. Map tiles come from OpenStreetMap.
- **Data encrypted in transit:** yes (HTTPS).
- **Users can request deletion:** clearing app data or uninstalling removes everything the app
  stores.

## Release notes

**1.1.0** (versionCode 2)
- Choose between one card at a time and a list of nearby places.
- New and Seen badges on each place, kept on your phone.
- Changing the search distance searches again, and new places appear as they are found.
- Landscape layout for phones.
- Tap the map to open the place in your maps app.
- Larger photo viewer, and descriptions that end at a full sentence.

**1.0.0** (first release)
Nearby suggestions with read-aloud, distance and direction, a map preview, and a visit history on
your phone.

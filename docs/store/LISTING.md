# Play Store listing (draft)

**App name:** Near Gem

**Short description (80 characters max):**
Nearby places with a read-aloud summary, for drivers and people on foot.

**Full description:**
Near Gem tells you about interesting places close to where you are: a restaurant, a café, a bar,
or a landmark, with a short summary you can have read aloud.

- Suggestions ranked by how interesting they are, minus a penalty for distance, so a nearby good
  place can beat a far-away great one.
- Live distance and direction, with a pointer that turns as you turn, for walking or driving.
- Walking and driving time estimates. These are rough, not routing.
- Read aloud, ducking music the way navigation prompts do. Next moves to the next place, and Back
  brings the last one back.
- Landmark photos and summaries from Wikipedia, with a link to each photo's licence.
- A map of the place from your position, with OpenStreetMap attribution.
- A visit history, kept only on your phone.
- Light, dark, or system theme.

Places come from OpenStreetMap and Wikipedia. Near Gem does not show ads.

**Category:** Travel & Local

**Contact and privacy policy:** add the public URL of PRIVACY_POLICY.md before submitting.

## Data safety answers (draft, check against the final build)

- **Location:** collected, used to find nearby places and to show distance. Not shared with
  anyone. Processed on the device and in requests to the place-data services. Optional: the app
  asks for it on first launch and needs it to work.
- **Visit history:** stored on the device only. Not collected by the developer.
- **Data sent to third parties:** the device's approximate position is sent to OpenStreetMap's
  Overpass API and Wikipedia to find places. Map tiles come from OpenStreetMap.
- **Data encrypted in transit:** yes (HTTPS).
- **Users can request deletion:** clearing app data or uninstalling removes everything the app
  stores.

## Release notes (first release, 1.0.0)

First release. Nearby suggestions with read-aloud, distance and direction, a map preview, and a
visit history on your phone.

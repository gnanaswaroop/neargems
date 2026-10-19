# TODO

## Automotive support (later)

- [ ] **Android Auto head unit**: add a Car App Library screen (`androidx.car.app`, POI category) that shows the top pick with Read aloud and Next. Needs `PoiCarAppService`, a session, a screen, a host allowlist before release, and testing on the Desktop Head Unit. The phone already detects projection through `CarConnectionMonitor`.
- [ ] **Apple CarPlay**: needs a Kotlin Multiplatform iOS target, a Swift CarPlay scene, and an Apple entitlement. Check first whether a POI-suggestion app fits an allowed CarPlay category. It likely does not, so the iOS plan depends on that answer.

## Phone app

- [ ] Use the Compose Multiplatform screens in the design artifact. Bundle the Fraunces and Source Sans 3 fonts; the current build uses the system serif and sans.
- [ ] Show a photo for the top pick where the Wikipedia article has a licensed image.
- [ ] Reduce the chance of area articles slipping through the city filter (`WikipediaSource.isAreaArticle`).

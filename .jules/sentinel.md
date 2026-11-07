## 2024-11-06 - Sentinel: Fix Overpass API Query Injection via Regex Escaping

**Vulnerability:** Query injection in the Overpass API query inside `OverpassSource.kt`.
**Learning:** `Regex.escape()` uses Java's `\Q...\E` syntax to quote literal strings. However, Overpass API's query language does not support `\Q...\E`. A malicious user can input characters like `"` to break out of the string context in the Overpass query and potentially execute unexpected queries or cause Denial of Service (DoS) errors due to malformed requests. Overpass API requires a specific escaping format where literal characters like `"` and `\` are escaped and regex characters are double-escaped.
**Prevention:** Instead of using standard `Regex.escape()` for building queries for external services like Overpass API, write a custom escaping function that correctly neutralizes special characters for the specific query language.

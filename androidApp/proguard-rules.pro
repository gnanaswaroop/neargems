# Release builds shrink and obfuscate the app. The libraries (Ktor, Coil, kotlinx.serialization,
# Compose, Car App) ship their own rules. Only the app's own entry points need a note here: the
# Application, Activity and Service are named in AndroidManifest.xml, so R8 keeps them already.

# Keep line numbers in crash reports from Play Console readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

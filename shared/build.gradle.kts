plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    android {
        namespace = "com.nearwise.shared"
        compileSdk = 37
        minSdk = 26
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            implementation("io.ktor:ktor-client-core:3.6.0")
            implementation("io.ktor:ktor-client-logging:3.6.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("io.coil-kt.coil3:coil-compose:3.6.3")
            implementation("io.coil-kt.coil3:coil-network-ktor3:3.6.3")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.10.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

compose.resources {
    packageOfResClass = "com.nearwise.shared.generated.resources"
}

// The Android library does not wire Compose resources into its assets, so the copy goes to a
// build folder that the app reads from. See androidApp/build.gradle.kts.
tasks.matching { it.name == "copyAndroidMainComposeResourcesToAndroidAssets" }.configureEach {
    withGroovyBuilder {
        setProperty("outputDirectory", layout.buildDirectory.dir("generated/compose/androidAssets").get().asFile)
    }
}

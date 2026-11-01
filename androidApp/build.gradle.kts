import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The upload key lives outside the repo, so it is never committed. See docs/store/RELEASING.md.
val uploadSigning = Properties().apply {
    val file = File(System.getProperty("user.home"), ".neargems/upload-signing.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.neargems.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.neargems.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = "1.1.2"
    }

    signingConfigs {
        create("upload") {
            if (uploadSigning.isNotEmpty()) {
                storeFile = file(uploadSigning.getProperty("storeFile"))
                storePassword = uploadSigning.getProperty("storePassword")
                keyAlias = uploadSigning.getProperty("keyAlias")
                keyPassword = uploadSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (uploadSigning.isNotEmpty()) signingConfig = signingConfigs.getByName("upload")
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = false
    }

    sourceSets {
        // Compose resources (the logo and the fonts) come from the shared module as assets.
        getByName("main") {
            assets.srcDir(project(":shared").layout.buildDirectory.dir("generated/compose/androidAssets").get().asFile)
        }
        // Test fakes live in one folder and are compiled into both the JVM and the on-device tests.
        getByName("test") { kotlin.srcDir("src/sharedTest/kotlin") }
        getByName("androidTest") { kotlin.srcDir("src/sharedTest/kotlin") }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.activity:activity-compose:1.10.1")
    // Lint requires Fragment 1.3+ for registerForActivityResult; some libraries bring in older ones.
    implementation("androidx.fragment:fragment:1.8.5")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.car.app:app:1.4.0")
    implementation("io.ktor:ktor-client-okhttp:3.6.0")
    implementation("io.coil-kt.coil3:coil:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-ktor3:3.6.3")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.ktor:ktor-client-mock:3.6.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.12.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("io.ktor:ktor-client-mock:3.6.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.12.1")
}

tasks.named("preBuild") {
    dependsOn(":shared:copyAndroidMainComposeResourcesToAndroidAssets")
}

// Gradle installs and runs device tests on every attached device unless ANDROID_SERIAL names one. These guards
// stop a run that names no device, so a plain build never lands on a phone. Device tests may only use an emulator.
tasks.matching { it.name.startsWith("install") && it.name.endsWith("Debug") || it.name.endsWith("DebugAndroidTest") }
    .configureEach {
        doFirst {
            val serial = System.getenv("ANDROID_SERIAL")
            require(!serial.isNullOrBlank()) {
                "Set ANDROID_SERIAL to the device to use, for example ANDROID_SERIAL=emulator-5560 or the Pixel's serial."
            }
        }
    }

tasks.matching { it.name.startsWith("connected") && it.name.endsWith("AndroidTest") }.configureEach {
    doFirst {
        val serial = System.getenv("ANDROID_SERIAL").orEmpty()
        require(serial.startsWith("emulator-")) {
            "Device tests run only on an emulator. Set ANDROID_SERIAL to a running emulator, for example emulator-5560."
        }
    }
}

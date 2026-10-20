import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The upload key lives outside the repo, so it is never committed. See docs/store/RELEASING.md.
val uploadSigning = Properties().apply {
    val file = File(System.getProperty("user.home"), ".nearwise/upload-signing.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.nearwise.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nearwise.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
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

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nearwise.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nearwise.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = false
    }

    // Test fakes live in one folder and are compiled into both the JVM and the on-device tests.
    sourceSets {
        getByName("test") { kotlin.srcDir("src/sharedTest/kotlin") }
        getByName("androidTest") { kotlin.srcDir("src/sharedTest/kotlin") }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.activity:activity-compose:1.10.1")
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

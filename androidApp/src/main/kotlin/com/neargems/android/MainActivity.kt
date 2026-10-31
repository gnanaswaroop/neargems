package com.neargems.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import com.neargems.android.service.CarWatchService
import com.neargems.app.DriveContainer
import com.neargems.ui.NearGemsApp

class MainActivity : ComponentActivity() {
    private val container: DriveContainer
        get() = (application as NearGemsApplication).container

    /** True while location has not been granted. The app shows its disclosure before asking for it. */
    private val locationNeeded = mutableStateOf(false)

    /** True once the user has refused location. The app cannot work without it, so it says so. */
    private val locationDenied = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            locationNeeded.value = false
            locationDenied.value = false
            container.refresh()
        } else {
            locationDenied.value = true
        }
    }

    /** The app works without notifications, so a refusal is not reported. */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationNeeded.value = !hasLocationPermission()
        setContent {
            NearGemsApp(
                container = container,
                onStart = ::onAppStarted,
                onWatchChanged = ::onWatchChanged,
                locationNeeded = locationNeeded.value,
                locationDenied = locationDenied.value,
                onAllowLocation = ::requestLocation,
                onOpenSettings = ::openAppSettings,
                onQuit = ::quit,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        container.setVisible(true)
    }

    override fun onStop() {
        container.setVisible(false)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the settings screen: if location was switched on there, carry on.
        if (hasLocationPermission()) {
            locationNeeded.value = false
            if (locationDenied.value) {
                locationDenied.value = false
                container.refresh()
            }
        }
    }

    /** Loads suggestions when location is already granted. Otherwise the disclosure screen asks first. */
    private fun onAppStarted() {
        // Notifications show the place on screen and announce new ones. Android 13 and later ask first.
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (hasLocationPermission()) {
            container.refresh()
            // The watch stops when the app is swiped away, so bring it back if the user left it on.
            if (container.settings.settings.value.watchInBackground) CarWatchService.start(this)
        }
    }

    private fun requestLocation() {
        permissionLauncher.launch(LOCATION_PERMISSIONS)
    }

    /** Opens this app's permission page, for when the system will not ask again. */
    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
        )
    }

    private fun hasLocationPermission(): Boolean =
        LOCATION_PERMISSIONS.any { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    /** Shuts NearGems down: stops car watching, removes the app from recents, and ends the process. */
    private fun quit() {
        CarWatchService.stop(this)
        getSystemService(android.app.NotificationManager::class.java).cancel(2)
        finishAndRemoveTask()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun onWatchChanged(on: Boolean) {
        if (on) CarWatchService.start(this) else CarWatchService.stop(this)
    }

    private companion object {
        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}

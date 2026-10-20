package com.neargem.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import com.neargem.android.service.CarWatchService
import com.neargem.app.DriveContainer
import com.neargem.ui.NearGemApp

class MainActivity : ComponentActivity() {
    private val container: DriveContainer
        get() = (application as NearGemApplication).container

    /** True once the user has refused location. The app cannot work without it, so it says so. */
    private val locationDenied = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            locationDenied.value = false
            container.refresh()
        } else {
            locationDenied.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NearGemApp(
                container = container,
                onStart = ::onAppStarted,
                onWatchChanged = ::onWatchChanged,
                locationDenied = locationDenied.value,
                onAllowLocation = ::requestLocation,
                onOpenSettings = ::openAppSettings,
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
        if (locationDenied.value && hasLocationPermission()) {
            locationDenied.value = false
            container.refresh()
        }
    }

    /** Asks for location when the app opens, then loads suggestions. */
    private fun onAppStarted() {
        if (hasLocationPermission()) container.refresh() else requestLocation()
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

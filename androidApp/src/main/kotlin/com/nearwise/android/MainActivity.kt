package com.nearwise.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.nearwise.android.service.CarWatchService
import com.nearwise.app.DriveContainer
import com.nearwise.ui.NearwiseApp

class MainActivity : ComponentActivity() {
    private val container: DriveContainer
        get() = (application as NearwiseApplication).container

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { container.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NearwiseApp(
                container = container,
                onStart = ::onAppStarted,
                onWatchChanged = ::onWatchChanged,
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

    /** Asks for location once, then loads suggestions. */
    private fun onAppStarted() {
        val missing = LOCATION_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) container.refresh() else permissionLauncher.launch(missing.toTypedArray())
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

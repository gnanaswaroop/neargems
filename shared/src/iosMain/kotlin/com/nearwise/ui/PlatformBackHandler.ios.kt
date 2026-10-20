package com.nearwise.ui

import androidx.compose.runtime.Composable

/** iOS has no system back button; the Done button in the title bar closes settings. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

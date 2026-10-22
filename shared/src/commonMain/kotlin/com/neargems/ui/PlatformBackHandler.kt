package com.neargems.ui

import androidx.compose.runtime.Composable

/** While [enabled], the system back action calls [onBack] instead of leaving the screen. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

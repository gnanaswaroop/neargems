package com.neargems.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neargems.shared.generated.resources.Res
import com.neargems.shared.generated.resources.neargems_logo
import org.jetbrains.compose.resources.painterResource

/**
 * The app's title: the NearGems lettering image, at the start of the title row. The image keeps
 * its own proportions, so the connection icon and Settings keep their places beside it.
 */
@Composable
fun Wordmark(modifier: Modifier = Modifier, height: Dp = TITLE_HEIGHT) {
    Image(
        painter = painterResource(Res.drawable.neargems_logo),
        contentDescription = "NearGems",
        contentScale = ContentScale.Fit,
        alignment = Alignment.CenterStart,
        modifier = modifier.height(height).semantics { heading() },
    )
}

private val TITLE_HEIGHT = 56.dp

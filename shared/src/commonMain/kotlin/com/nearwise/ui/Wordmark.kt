package com.nearwise.ui

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
import com.nearwise.shared.generated.resources.Res
import com.nearwise.shared.generated.resources.nearwise_logo
import org.jetbrains.compose.resources.painterResource

/**
 * The app's title: the Nearwise logo. The image keeps its proportions and sits at the start of
 * the title row, so the connection icon and Settings keep their places beside it.
 */
@Composable
fun Wordmark(modifier: Modifier = Modifier, height: Dp = TITLE_HEIGHT) {
    Image(
        painter = painterResource(Res.drawable.nearwise_logo),
        contentDescription = "Nearwise",
        contentScale = ContentScale.Fit,
        alignment = Alignment.CenterStart,
        modifier = modifier.height(height).semantics { heading() },
    )
}

private val TITLE_HEIGHT = 40.dp

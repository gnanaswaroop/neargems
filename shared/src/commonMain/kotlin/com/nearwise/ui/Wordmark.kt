package com.nearwise.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nearwise.shared.generated.resources.Res
import com.nearwise.shared.generated.resources.nearwise_mark
import org.jetbrains.compose.resources.painterResource

/** The app's title: the app icon artwork, then the name in Fraunces. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(Res.drawable.nearwise_mark),
            contentDescription = null,
            modifier = Modifier.size(TITLE_MARK_SIZE),
        )
        Text(
            "Nearwise",
            modifier = Modifier.padding(start = 10.dp),
            fontFamily = displayFont(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            letterSpacing = (-0.3).sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

private val TITLE_MARK_SIZE = 40.dp

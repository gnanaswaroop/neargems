package com.neargem.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.neargem.shared.generated.resources.Res
import com.neargem.shared.generated.resources.neargem_logo
import com.neargem.shared.generated.resources.neargem_mark
import org.jetbrains.compose.resources.painterResource

/**
 * The app's title: the V5 app icon mark, then the NearGem lettering image. The lettering keeps
 * its own proportions, and both sit centred on one row.
 */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(Res.drawable.neargem_mark),
            contentDescription = null,
            modifier = Modifier.size(TITLE_MARK_SIZE),
        )
        Image(
            painter = painterResource(Res.drawable.neargem_logo),
            contentDescription = "NearGem",
            contentScale = ContentScale.Fit,
            modifier = Modifier.padding(start = 8.dp).height(TITLE_TEXT_HEIGHT),
        )
    }
}

private val TITLE_MARK_SIZE = 40.dp
private val TITLE_TEXT_HEIGHT = 30.dp

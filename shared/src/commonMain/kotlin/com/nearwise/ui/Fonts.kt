package com.nearwise.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.nearwise.shared.generated.resources.Res
import com.nearwise.shared.generated.resources.fraunces_variable
import com.nearwise.shared.generated.resources.source_sans_3_variable
import org.jetbrains.compose.resources.Font

/**
 * Fraunces, for place names and titles. Packaged as a Compose resource (composeResources/font); the
 * weight picks the variation of the variable font. SIL Open Font License, see LICENSES/Fraunces-OFL.txt.
 */
@Composable
fun displayFont(): FontFamily = FontFamily(
    Font(Res.font.fraunces_variable, FontWeight.Normal),
    Font(Res.font.fraunces_variable, FontWeight.SemiBold),
)

/**
 * Source Sans 3, for body text and buttons. SIL Open Font License, see LICENSES/SourceSans3-OFL.txt.
 */
@Composable
fun bodyFont(): FontFamily = FontFamily(
    Font(Res.font.source_sans_3_variable, FontWeight.Normal),
    Font(Res.font.source_sans_3_variable, FontWeight.SemiBold),
)

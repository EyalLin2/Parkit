package com.parkit.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.parkit.app.R

val SuezOne = FontFamily(Font(R.font.suez_one, FontWeight.Normal))
val Heebo = FontFamily(Font(R.font.heebo_regular, FontWeight.Normal))

val ParkItTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontFamily = SuezOne, fontWeight = FontWeight.Normal),
        headlineMedium = base.headlineMedium.copy(fontFamily = SuezOne, fontWeight = FontWeight.Normal),
        headlineSmall = base.headlineSmall.copy(fontFamily = SuezOne, fontWeight = FontWeight.Normal),
        titleLarge = base.titleLarge.copy(fontFamily = Heebo, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = Heebo, fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.copy(fontFamily = Heebo, fontWeight = FontWeight.Bold),
        bodyLarge = base.bodyLarge.copy(fontFamily = Heebo),
        bodyMedium = base.bodyMedium.copy(fontFamily = Heebo),
        bodySmall = base.bodySmall.copy(fontFamily = Heebo),
        labelLarge = base.labelLarge.copy(fontFamily = Heebo, fontWeight = FontWeight.Bold),
        labelMedium = base.labelMedium.copy(fontFamily = Heebo, fontWeight = FontWeight.Bold),
        labelSmall = base.labelSmall.copy(fontFamily = Heebo),
    )
}

val HeroButtonText = TextStyle(fontFamily = Heebo, fontWeight = FontWeight.Bold, fontSize = 20.sp)

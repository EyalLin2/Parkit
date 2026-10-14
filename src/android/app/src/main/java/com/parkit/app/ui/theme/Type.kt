package com.parkit.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.parkit.app.R

/** Brand wordmark only (login screen) — a decorative display face reads as
 * "hobby project" once it's on every screen title, which is exactly where
 * it used to be. */
val SuezOne = FontFamily(Font(R.font.suez_one, FontWeight.Normal))

/** Real Medium/Bold/ExtraBold cuts instead of one Regular file that Android
 * fake-bolds — synthetic bold is a big part of why text looked "off".
 * Heebo covers Hebrew and Latin with one consistent design. */
val Heebo = FontFamily(
    Font(R.font.heebo_regular, FontWeight.Normal),
    Font(R.font.heebo_medium, FontWeight.Medium),
    Font(R.font.heebo_bold, FontWeight.SemiBold),
    Font(R.font.heebo_bold, FontWeight.Bold),
    Font(R.font.heebo_extrabold, FontWeight.ExtraBold),
)

val ParkItTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontFamily = Heebo, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineLarge = base.headlineLarge.copy(fontFamily = Heebo, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = Heebo, fontWeight = FontWeight.ExtraBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = Heebo, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = Heebo, fontWeight = FontWeight.Bold, fontSize = 20.sp),
        titleMedium = base.titleMedium.copy(fontFamily = Heebo, fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.copy(fontFamily = Heebo, fontWeight = FontWeight.Medium, fontSize = 15.sp),
        bodyLarge = base.bodyLarge.copy(fontFamily = Heebo),
        bodyMedium = base.bodyMedium.copy(fontFamily = Heebo),
        bodySmall = base.bodySmall.copy(fontFamily = Heebo),
        labelLarge = base.labelLarge.copy(fontFamily = Heebo, fontWeight = FontWeight.Bold),
        labelMedium = base.labelMedium.copy(fontFamily = Heebo, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontFamily = Heebo, fontWeight = FontWeight.Medium),
    )
}

val HeroButtonText = TextStyle(fontFamily = Heebo, fontWeight = FontWeight.Bold, fontSize = 20.sp)

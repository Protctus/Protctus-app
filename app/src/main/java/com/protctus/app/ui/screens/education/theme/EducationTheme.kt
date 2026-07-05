package com.protctus.app.ui.screens.education.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.protctus.app.ui.theme.*

/**
 * Protctus Education Design System
 *
 * STRICT RULE: Every color references a value from Color.kt.
 * No Color() declarations. No hardcoded hex. No cyan. No neon. No glow.
 */
object EduColors {
    // ── Backgrounds ──
    val Background = DarkBackground
    val SurfaceLight = SurfaceColor
    val SurfaceDark = SurfaceColor.copy(alpha = 0.5f)
    val SurfaceElevated = SurfaceColor

    // ── Primary Accent (Red) ──
    val Primary = ErrorColor
    val PrimaryMuted = ErrorColor.copy(alpha = 0.15f)
    val PrimaryBorder = ErrorColor.copy(alpha = 0.3f)

    // ── Secondary (Muted steel) ──
    val Secondary = AccentColor
    val SecondaryMuted = AccentColor.copy(alpha = 0.15f)

    // ── Accent (Gold) ──
    val Accent = PremiumGold
    val AccentMuted = PremiumGold.copy(alpha = 0.15f)

    // ── Semantic States ──
    val Success = SuccessColor
    val SuccessMuted = SuccessColor.copy(alpha = 0.15f)
    val SuccessBorder = SuccessColor.copy(alpha = 0.3f)
    val Error = ErrorColor
    val ErrorMuted = ErrorColor.copy(alpha = 0.15f)
    val ErrorBorder = ErrorColor.copy(alpha = 0.3f)
    val Warning = PremiumGold

    // ── Text ──
    val TextPrimary = PrimaryText
    val TextSecondary = SecondaryText
    val TextDisabled = OutlineColor

    // ── Gamification ──
    val StreakFlame = ErrorColor
    val XpGold = PremiumGold

    // ── Structural ──
    val CardBorder = OutlineColor
    val Divider = OutlineColor.copy(alpha = 0.5f)
    val Overlay = DarkBackground.copy(alpha = 0.92f)
    val SubtleBorder = GlassBorder
}

/**
 * Consistent spacing system based on a 4dp grid.
 * Eliminates magic numbers across the Education module.
 */
object EduDimens {
    val SpacingXxs: Dp = 2.dp
    val SpacingXs: Dp = 4.dp
    val SpacingSm: Dp = 8.dp
    val SpacingMd: Dp = 12.dp
    val SpacingLg: Dp = 16.dp
    val SpacingXl: Dp = 20.dp
    val SpacingXxl: Dp = 24.dp
    val SpacingHuge: Dp = 32.dp
    val SpacingMassive: Dp = 40.dp

    val ScreenPadding: Dp = 20.dp
    val CardPadding: Dp = 20.dp

    val TouchTargetMin: Dp = 48.dp
    val ButtonHeight: Dp = 56.dp
    val IconSizeSm: Dp = 20.dp
    val IconSizeMd: Dp = 24.dp
    val IconSizeLg: Dp = 32.dp
    val IconSizeXl: Dp = 40.dp

    val ProgressBarHeight: Dp = 6.dp
    val AvatarSizeSm: Dp = 40.dp
    val AvatarSizeMd: Dp = 64.dp
    val AvatarSizeLg: Dp = 100.dp

    val MascotSizeSm: Dp = 60.dp
    val MascotSizeMd: Dp = 100.dp
    val MascotSizeLg: Dp = 140.dp
}

/**
 * Consistent corner radius system.
 */
object EduShapes {
    val RadiusSm: Dp = 8.dp
    val RadiusMd: Dp = 12.dp
    val RadiusLg: Dp = 16.dp
    val RadiusXl: Dp = 24.dp
    val RadiusFull: Dp = 100.dp
}

/**
 * Typography scale for the Education module.
 * Uses EduColors for default text colors — no hardcoded colors.
 */
object EduTypography {
    val H1 = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.ExtraBold,
        color = EduColors.TextPrimary,
        lineHeight = 36.sp
    )
    val H2 = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = EduColors.TextPrimary,
        lineHeight = 30.sp
    )
    val Title = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = EduColors.TextPrimary,
        lineHeight = 26.sp
    )
    val BodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = EduColors.TextPrimary,
        lineHeight = 24.sp
    )
    val Body = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        color = EduColors.TextSecondary,
        lineHeight = 22.sp
    )
    val Label = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = EduColors.TextSecondary,
        letterSpacing = 0.3.sp
    )
    val Caption = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = EduColors.TextSecondary,
        lineHeight = 18.sp
    )
    val Micro = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = EduColors.TextDisabled,
        letterSpacing = 0.8.sp
    )
}

package com.ueiol.yixue.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = FdPrimaryBlueLight,
    onPrimary = FdOnBrandLight,
    primaryContainer = FdPrimaryBlueContainerLight,
    onPrimaryContainer = FdOnPrimaryBlueContainerLight,
    // secondary/tertiary 统一走品牌蓝系：M3 的 FilterChip 选中态等默认取 secondaryContainer，
    // 之前给的是青绿/紫，导致界面里出现「偏蓝的淡绿色」
    secondary = FdPrimaryBlueLight,
    onSecondary = FdOnBrandLight,
    secondaryContainer = FdPrimaryBlueContainerLight,
    onSecondaryContainer = FdOnPrimaryBlueContainerLight,
    tertiary = FdPrimaryBlueLight,
    onTertiary = FdOnBrandLight,
    tertiaryContainer = FdPrimaryBlueContainerLight,
    onTertiaryContainer = FdOnPrimaryBlueContainerLight,
    error = FdErrorLight,
    onError = FdOnErrorLight,
    errorContainer = FdErrorContainerLight,
    onErrorContainer = FdOnErrorContainerLight,
    background = FdSurfaceLight,
    onBackground = FdOnSurfaceLight,
    surface = FdSurfaceLight,
    onSurface = FdOnSurfaceLight,
    surfaceVariant = FdSurfaceContainerLight,
    onSurfaceVariant = FdOnSurfaceVariantLight,
    surfaceDim = FdSurfaceDimLight,
    surfaceBright = FdSurfaceBrightLight,
    surfaceContainerLowest = FdSurfaceLowestLight,
    surfaceContainerLow = FdSurfaceLowLight,
    surfaceContainer = FdSurfaceContainerLight,
    surfaceContainerHigh = FdSurfaceHighLight,
    surfaceContainerHighest = FdSurfaceHighestLight,
    outline = FdOutlineLight,
    outlineVariant = FdOutlineVariantLight,
    inverseSurface = FdInverseSurfaceLight,
    inverseOnSurface = FdInverseOnSurfaceLight,
    scrim = FdScrimLight
)

private val DarkColorScheme = darkColorScheme(
    primary = FdPrimaryBlueDark,
    onPrimary = FdOnBrandDark,
    primaryContainer = FdPrimaryBlueContainerDark,
    onPrimaryContainer = FdOnPrimaryBlueContainerDark,
    // 同浅色：secondary/tertiary 统一品牌蓝系
    secondary = FdPrimaryBlueDark,
    onSecondary = FdOnBrandDark,
    secondaryContainer = FdPrimaryBlueContainerDark,
    onSecondaryContainer = FdOnPrimaryBlueContainerDark,
    tertiary = FdPrimaryBlueDark,
    onTertiary = FdOnBrandDark,
    tertiaryContainer = FdPrimaryBlueContainerDark,
    onTertiaryContainer = FdOnPrimaryBlueContainerDark,
    error = FdErrorDark,
    onError = FdOnErrorDark,
    errorContainer = FdErrorContainerDark,
    onErrorContainer = FdOnErrorContainerDark,
    background = FdSurfaceDark,
    onBackground = FdOnSurfaceDark,
    surface = FdSurfaceDark,
    onSurface = FdOnSurfaceDark,
    surfaceVariant = FdSurfaceContainerDark,
    onSurfaceVariant = FdOnSurfaceVariantDark,
    surfaceDim = FdSurfaceDimDark,
    surfaceBright = FdSurfaceBrightDark,
    surfaceContainerLowest = FdSurfaceLowestDark,
    surfaceContainerLow = FdSurfaceLowDark,
    surfaceContainer = FdSurfaceContainerDark,
    surfaceContainerHigh = FdSurfaceHighDark,
    surfaceContainerHighest = FdSurfaceHighestDark,
    outline = FdOutlineDark,
    outlineVariant = FdOutlineVariantDark,
    inverseSurface = FdInverseSurfaceDark,
    inverseOnSurface = FdInverseOnSurfaceDark,
    scrim = FdScrimDark
)

/*
 * M3 色板之外的业务扩展色：对错反馈、科目色、状态色。
 */
@Immutable
data class YxExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val pressed: Color,
    val disabled: Color,
    val disabledContent: Color,
    // 科目色（浅/夜各一套，用于错题本、笔记、科目标签）
    val subjectBlue: Color,
    val subjectGreen: Color,
    val subjectOrange: Color,
    val subjectPurple: Color,
    val subjectYellow: Color,
    val subjectCyan: Color,
    val subjectRed: Color
) {
    /** 科目调色板，顺序与 SubjectEntity.colorIndex 对应。 */
    val subjectPalette: List<Color>
        get() = listOf(
            subjectBlue, subjectGreen, subjectOrange, subjectPurple,
            subjectYellow, subjectCyan, subjectRed
        )
}

private val LightExtendedColors = YxExtendedColors(
    success = FdPrimaryGreenLight,
    onSuccess = FdOnBrandLight,
    successContainer = FdPrimaryGreenContainerLight,
    onSuccessContainer = FdOnPrimaryGreenContainerLight,
    warning = FdPrimaryYellowLight,
    onWarning = FdOnPrimaryYellowContainerLight,
    warningContainer = FdPrimaryYellowContainerLight,
    onWarningContainer = FdOnPrimaryYellowContainerLight,
    pressed = FdStatePressedLight,
    disabled = FdStateDisabledLight,
    disabledContent = FdStateDisabledContentLight,
    subjectBlue = FdPrimaryBlueLight,
    subjectGreen = FdPrimaryGreenLight,
    subjectOrange = FdPrimaryOrangeLight,
    subjectPurple = FdPrimaryPurpleLight,
    subjectYellow = FdPrimaryYellowLight,
    subjectCyan = FdPrimaryCyanLight,
    subjectRed = FdPrimaryRedLight
)

private val DarkExtendedColors = YxExtendedColors(
    success = FdPrimaryGreenDark,
    onSuccess = FdOnBrandDark,
    successContainer = FdPrimaryGreenContainerDark,
    onSuccessContainer = FdOnPrimaryGreenContainerDark,
    warning = FdPrimaryYellowDark,
    onWarning = FdOnPrimaryYellowContainerDark,
    warningContainer = FdPrimaryYellowContainerDark,
    onWarningContainer = FdOnPrimaryYellowContainerDark,
    pressed = FdStatePressedDark,
    disabled = FdStateDisabledDark,
    disabledContent = FdStateDisabledContentDark,
    subjectBlue = FdPrimaryBlueDark,
    subjectGreen = FdPrimaryGreenDark,
    subjectOrange = FdPrimaryOrangeDark,
    subjectPurple = FdPrimaryPurpleDark,
    subjectYellow = FdPrimaryYellowDark,
    subjectCyan = FdPrimaryCyanDark,
    subjectRed = FdPrimaryRedDark
)

val LocalYxExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/** 取扩展色的便捷入口：YxExt.current.success */
val YxExt: YxExtendedColors
    @Composable get() = LocalYxExtendedColors.current

/**
 * 易学主题（Yixue 设计规范 范式）。
 * 深色模式默认跟随系统；[forceDark] 传值可强制指定（用于设置页手动切换）。
 * 不启用动态取色，保持 Yx 品牌蓝视觉一致性。
 */
@Composable
fun YixueTheme(
    forceDark: Boolean? = null,
    content: @Composable () -> Unit
) {
    val darkTheme = forceDark ?: isSystemInDarkTheme()
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalYxExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = YxShapes,
            content = content
        )
    }
}

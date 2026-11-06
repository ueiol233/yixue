package com.ueiol.yixue.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * Yixue 设计规范 圆角令牌（fd_sys_radius_corner_*）：
 * extra_mini 4 / mini 8 / small 10 / regular 12 / medium 16 / large 20 / heavy 24 / extra_heavy 32
 * 描边宽度统一 1dp（fd_sys_radius_corner_stroke_width）。
 */
object YxRadius {
    val None = 0.dp
    val ExtraMini = 4.dp
    val Mini = 8.dp
    val Small = 10.dp
    val Regular = 12.dp
    val Medium = 16.dp
    val Large = 20.dp
    val Heavy = 24.dp
    val ExtraHeavy = 32.dp
    val StrokeWidth = 1.dp
}

val YxShapes = Shapes(
    extraSmall = RoundedCornerShape(YxRadius.Mini),
    small = RoundedCornerShape(YxRadius.Small),
    medium = RoundedCornerShape(YxRadius.Regular),
    large = RoundedCornerShape(YxRadius.Medium),
    extraLarge = RoundedCornerShape(YxRadius.Large)
)

// 页面内容卡片（错题卡/笔记卡/会话卡）统一使用
val YxCardShape = RoundedCornerShape(YxRadius.Medium)
// 底部弹出层（答题卡、公式键盘）使用大圆角
val YxSheetShape = RoundedCornerShape(YxRadius.Heavy)

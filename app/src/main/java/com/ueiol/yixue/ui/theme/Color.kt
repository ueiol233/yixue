package com.ueiol.yixue.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Yixue 设计规范 设计令牌（提取自 calc/down 工程 res/values/colors.xml 与 values-night/colors.xml，
 * 命名对应 fd_sys_color_* 系列）。
 */

// ---------- 浅色 ----------
val FdBrandLight = Color(0xFF206CFF)
val FdOnBrandLight = Color(0xFFFFFFFF)
val FdBrandContainerLight = Color(0xFFE1ECFE)
val FdOnBrandContainerLight = Color(0xFF001749)

val FdPrimaryBlueLight = Color(0xFF206CFF)
val FdPrimaryBlueContainerLight = Color(0xFFE1ECFE)
val FdOnPrimaryBlueContainerLight = Color(0xFF001749)

val FdPrimaryCyanLight = Color(0xFF02BEBF)
val FdPrimaryCyanContainerLight = Color(0xFFE2F8F8)
val FdOnPrimaryCyanContainerLight = Color(0xFF002020)

val FdPrimaryGreenLight = Color(0xFF35C44F)
val FdPrimaryGreenContainerLight = Color(0xFFEFFBF3)
val FdOnPrimaryGreenContainerLight = Color(0xFF002106)

val FdPrimaryOrangeLight = Color(0xFFFF7024)
val FdPrimaryOrangeContainerLight = Color(0xFFFCEDE5)
val FdOnPrimaryOrangeContainerLight = Color(0xFF2D1600)

val FdPrimaryPurpleLight = Color(0xFF7434DC)
val FdPrimaryPurpleContainerLight = Color(0xFFF7EDFF)
val FdOnPrimaryPurpleContainerLight = Color(0xFF260059)

val FdPrimaryYellowLight = Color(0xFFFFBE0A)
val FdPrimaryYellowContainerLight = Color(0xFFFFF2CE)
val FdOnPrimaryYellowContainerLight = Color(0xFF261900)

val FdPrimaryRedLight = Color(0xFFE42D22)
val FdPrimaryRedContainerLight = Color(0xFFFFEDEA)
val FdOnPrimaryRedContainerLight = Color(0xFF410001)

val FdErrorLight = Color(0xFFFF0000)
val FdOnErrorLight = Color(0xFFFFFFFF)
val FdErrorContainerLight = Color(0xFFFFF7F7)
val FdOnErrorContainerLight = Color(0xFF3F0A0C)

val FdOnSurfaceLight = Color(0xFF000000)
val FdOnSurfaceVariantLight = Color(0x993C3C43)
val FdOutlineLight = Color(0xFFD9D9D9)
val FdOutlineVariantLight = Color(0x0D000000)

val FdSurfaceLight = Color(0xFFF5F5F5)
val FdSurfaceDimLight = Color(0xFFDADADA)
val FdSurfaceBrightLight = Color(0xFFFFFFFF)
val FdSurfaceLowestLight = Color(0xFFFFFFFF)
val FdSurfaceLowLight = Color(0xFFEBEBEC)          // action_bar_search_layout_bg_color_immersive
val FdSurfaceContainerLight = Color(0xFFEBEBEC)    // 同上
val FdSurfaceHighLight = Color(0xFFE7E7E9)         // action_bar_search_layout_bg_pressed_color
val FdSurfaceHighestLight = Color(0xFFDADADA)      // surface_dim

val FdInverseSurfaceLight = Color(0xFF1C1C1E)
val FdInverseOnSurfaceLight = Color(0xFFF2F0F4)
val FdScrimLight = Color(0x1A000000)

val FdStatePressedLight = Color(0x1A767680)
val FdStateDisabledLight = Color(0x33000000)
val FdStateDisabledContentLight = Color(0x61000000)  // 禁用文字/图标（38% 黑）

// ---------- 夜间 ----------
val FdBrandDark = Color(0xFF1564ED)
val FdOnBrandDark = Color(0xFFEFF6FF)
val FdBrandContainerDark = Color(0xFF172747)
val FdOnBrandContainerDark = Color(0xFFE1ECFE)

val FdPrimaryBlueDark = Color(0xFF1564ED)
val FdPrimaryBlueContainerDark = Color(0xFF172747)
val FdOnPrimaryBlueContainerDark = Color(0xFFE1ECFE)

val FdPrimaryCyanDark = Color(0xFF03A8A8)
val FdPrimaryCyanContainerDark = Color(0xFF012B2C)
val FdOnPrimaryCyanContainerDark = Color(0xFF66F8F6)

val FdPrimaryGreenDark = Color(0xFF29B15B)
val FdPrimaryGreenContainerDark = Color(0xFF012D09)
val FdOnPrimaryGreenContainerDark = Color(0xFF72FE88)

val FdPrimaryOrangeDark = Color(0xFFDC6222)
val FdPrimaryOrangeContainerDark = Color(0xFF2D1600)
val FdOnPrimaryOrangeContainerDark = Color(0xFFFFDCBF)

val FdPrimaryPurpleDark = Color(0xFF722AE4)
val FdPrimaryPurpleContainerDark = Color(0xFF2D0065)
val FdOnPrimaryPurpleContainerDark = Color(0xFFEBDBFF)

val FdPrimaryYellowDark = Color(0xFFDCA50C)
val FdPrimaryYellowContainerDark = Color(0xFF392600)
val FdOnPrimaryYellowContainerDark = Color(0xFFFFE251)

val FdPrimaryRedDark = Color(0xFFBA1A1A)
val FdPrimaryRedContainerDark = Color(0xFF410001)
val FdOnPrimaryRedContainerDark = Color(0xFFFFDAD5)

val FdErrorDark = Color(0xFFE7000C)
val FdOnErrorDark = Color(0xFFFFEDEA)
val FdErrorContainerDark = Color(0xFF221B1D)
val FdOnErrorContainerDark = Color(0xFFFFDAD5)

val FdOnSurfaceDark = Color(0xFFEEEEEE)
val FdOnSurfaceVariantDark = Color(0x73EBEBF5)
val FdOutlineDark = Color(0xFF262626)
val FdOutlineVariantDark = Color(0x0DFFFFFF)

val FdSurfaceDark = Color(0xFF0B0B0C)
val FdSurfaceDimDark = Color(0xFF000000)
val FdSurfaceBrightDark = Color(0xFF17171A)
val FdSurfaceLowestDark = Color(0xFF1C1C1E)        // surface_container_lowest / popup 背景
val FdSurfaceLowDark = Color(0xFF222227)           // action_bar_search_layout_bg_color 夜间
val FdSurfaceContainerDark = Color(0xFF222227)
val FdSurfaceHighDark = Color(0xFF2E2E33)          // 夜间按压色
val FdSurfaceHighestDark = Color(0xFF2F2F31)       // history_long_click_bg_color 夜间

val FdInverseSurfaceDark = Color(0xFFE4E2E6)
val FdInverseOnSurfaceDark = Color(0xFF1B1B1F)
val FdScrimDark = Color(0x73000000)

val FdStatePressedDark = Color(0x24767680)
val FdStateDisabledDark = Color(0x33EEEEEE)
val FdStateDisabledContentDark = Color(0x61EEEEEE)

package com.ueiol.yixue.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ueiol.yixue.ui.theme.YxCardShape
import com.ueiol.yixue.ui.theme.YxExt
import com.ueiol.yixue.ui.theme.YxRadius
import com.ueiol.yixue.ui.theme.YxSheetShape

/**
 * Yx 大标题页头：大字号粗体标题 + 可选副标题 + 可选右侧操作，随状态栏内嵌。
 */
@Composable
fun YxLargeTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        actions()
    }
}

/**
 * Yx 胶囊按钮：主操作实心品牌色，次操作浅容器色，禁用态走 disabledContent。
 * 页面内的主要/次要操作统一用它，不要直接堆 M3 Button。
 * 高度规范两档：44dp（页面主操作）/ 40dp（紧凑场景如弹窗、卡片内）。
 */
@Composable
fun YxPill(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 44.dp,
    onClick: () -> Unit
) {
    val container = when {
        primary && enabled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val content = when {
        !enabled -> YxExt.disabledContent
        primary -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        color = container,
        shape = RoundedCornerShape(YxRadius.Heavy),
        modifier = modifier
            .height(height)
            .clickable(enabled = enabled) { onClick() }
    ) {
        // 不要用 fillMaxSize：在未指定宽度的 Row 里会撑满整行，把相邻按钮挤成 0 宽
        Box(
            modifier = Modifier.padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = content,
                maxLines = 1
            )
        }
    }
}

/** Yx 工具动作胶囊：小图标 + 文字，用于工具栏动作入口（图片/公式/函数图等）。 */
@Composable
fun YxActionChip(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(YxRadius.Heavy),
        // 40dp：对齐 Yx 最小触控目标（36dp 视觉高度不足）
        modifier = modifier.height(40.dp).clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

/**
 * Yx 附件气泡：主题色胶囊 + 左侧预览/图标槽 + 名称 + 右侧移除叉。
 * 输入框的所有可挂附件（图片 / 答题报告 / 未来的文件）统一用它，保证视觉一致。
 */
@Composable
fun YxAttachmentBubble(
    title: String,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    leading: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(percent = 50),
        modifier = modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 4.dp, end = if (onRemove != null) 6.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading()
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1
            )
            onRemove?.let {
                Spacer(modifier = Modifier.size(2.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { it() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "移除附件",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Yx 空状态占位：居中图标 + 主提示 + 次提示。
 */
@Composable
fun YxEmptyState(
    icon: ImageVector,
    title: String,
    hint: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Yx 卡片：列表卡片统一容器（surfaceContainerLowest + YxCardShape，无阴影）。
 * 需要 onClick 时直接传，不额外套 clickable；[containerColor] 用于选中态等特殊底色。
 */
@Composable
fun YxCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    content: @Composable () -> Unit
) {
    val clickableMod = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    Surface(
        color = containerColor,
        shape = YxCardShape,
        modifier = modifier.fillMaxWidth().then(clickableMod)
    ) {
        content()
    }
}

/**
 * Yx 胶囊进度条：统一高度与颜色，底 surfaceContainerHigh、前景 primary。
 * [progress] 取值 0f..1f。
 */
@Composable
fun YxProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(YxRadius.Heavy))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(6.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(YxRadius.Heavy))
        )
    }
}

/**
 * Yx 确认弹窗：标题（titleLarge + SemiBold）+ 可选说明 + 底部双胶囊。
 * [danger] 为 true 时确认按钮走错误色语义文字。
 */
@Composable
fun YxConfirmDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    text: String? = null,
    confirmText: String = "确定",
    cancelText: String = "取消",
    danger: Boolean = false,
    confirmEnabled: Boolean = true,
    extraContent: @Composable () -> Unit = {}
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = YxSheetShape, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (text != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                extraContent()
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    YxPill(
                        text = confirmText,
                        primary = !danger,
                        enabled = confirmEnabled,
                        height = 40.dp,
                        modifier = Modifier.weight(1f)
                    ) { onConfirm() }
                    YxPill(text = cancelText, height = 40.dp, modifier = Modifier.weight(1f)) { onDismiss() }
                }
            }
        }
    }
}

/**
 * Yx 输入弹窗：标题 + 可选说明 + 单行输入 + 可选附加区 + 底部双胶囊。
 */
@Composable
fun YxInputDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    placeholder: String = "",
    hint: String? = null,
    confirmText: String = "确定",
    cancelText: String = "取消",
    confirmEnabled: Boolean = true,
    extraContent: @Composable () -> Unit = {}
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = YxSheetShape, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (hint != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                extraContent()
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    YxPill(text = confirmText, primary = true, enabled = confirmEnabled, height = 40.dp, modifier = Modifier.weight(1f)) { onConfirm() }
                    YxPill(text = cancelText, height = 40.dp, modifier = Modifier.weight(1f)) { onDismiss() }
                }
            }
        }
    }
}
